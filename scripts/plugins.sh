#!/usr/bin/env bash
# Fast plugin iteration: build one plugin jar, drop it as an override on the servers that carry it and
# restart only those servers. No image rebuild, no full deployment. Paper's /reload is never used: the
# plugins bind ports, hold executors, Redis subscriptions and arena snapshot journals.
#
#   scripts/plugins.sh build   <plugin>...                 Build jars into their target/ or build/ dir
#   scripts/plugins.sh test    <plugin> [pattern]          Run the tests (maven plugins), e.g. pattern CultModuleTest
#   scripts/plugins.sh push    <plugin> [server...]        Build, install locally, restart (make plugin)
#   scripts/plugins.sh install <plugin> <jar> [server...]  Install a built jar here and restart
#   scripts/plugins.sh clear   [server...]                 Drop overrides; the image's jars come back
#   scripts/plugins.sh status                              List active overrides
#   scripts/plugins.sh data    [file...] [-s server...]    Push werewolf data (roles, ambience, decor) and /ww reload, NO restart
#   scripts/plugins.sh data-clear [server...]              Drop the data overrides and /ww reload (back to the jar's copy)
#   scripts/plugins.sh remote  push|clear|status|data|data-clear [...]   Same on production over SSH (.env.remote)
#
# Plugins: network, arena, lobby, claims, survival. Servers default to every server that carries the plugin.
# COMPOSE=compose.prod.yaml selects production. FAST=1 skips tests. FORCE=1 restarts even while an
# arena game is running. Overrides live in <state>/<server>/plugin-overrides and are applied by
# infra/start.sh at each start; scripts/deploy.sh clears them so a deployment never stays shadowed.
set -euo pipefail
cd "$(dirname "$0")/.."
compose=${COMPOSE:-compose.local.yaml}
[[ $compose == compose.local.yaml || $compose == compose.prod.yaml ]] || { echo "Invalid COMPOSE: $compose" >&2; exit 1; }

# plugin -> source dir, built jar (relative to it), installed jar name, servers carrying it
plugin_dir() { case $1 in network) echo network-plugin ;; arena) echo arena-plugin ;; lobby) echo lobby ;; claims) echo claims-plugin ;; survival) echo survival-plugin ;; *) return 1 ;; esac; }
plugin_jar() {
 case $1 in
  network) echo target/network-display.jar ;;
  arena) echo target/nexus-arena-engine.jar ;;
  lobby) echo target/lobby-selector.jar ;;
  survival) echo target/survival-plugin.jar ;;
  # The image renames the versioned shadow jar; read the version from the Dockerfile so both agree.
  claims) echo "build/libs/$(grep -o 'SimpleClaimSystem-[0-9.]*\.jar' Dockerfile | head -n 1)" ;;
 esac
}
plugin_name() { case $1 in network) echo network-display.jar ;; arena) echo nexus-arena-engine.jar ;; lobby) echo lobby-selector.jar ;; claims) echo SimpleClaimSystem.jar ;; survival) echo survival-plugin.jar ;; esac; }
plugin_servers() { case $1 in network | arena) echo lobby creative survival ;; lobby) echo lobby ;; claims) echo creative ;; survival) echo survival ;; esac; }
known() { plugin_dir "$1" > /dev/null 2>&1 || { echo "Unknown plugin '$1' (network, arena, lobby, claims, survival)" >&2; exit 1; }; }

state_dir() {
 local dir=${PLUGIN_STATE_DIR:-}
 [[ -n $dir ]] && { echo "$dir"; return; }
 dir=$(sed -n 's/^STATE_DIR=//p' .env 2> /dev/null | tail -n 1)
 echo "${dir:-./state}"
}

# Build images come from the Dockerfile so local jars match CI (JDK, Maven, Gradle versions).
image_of() { grep -o "$1@sha256:[0-9a-f]*" Dockerfile | head -n 1; }

build() {
 local plugin=$1 dir cache
 dir=$(plugin_dir "$plugin")
 cache=${XDG_CACHE_HOME:-$HOME/.cache}/vampyuyu
 mkdir -p "$cache/m2" "$cache/gradle"
 # Earlier root-run containers may have left build output this user cannot overwrite.
 for out in "$dir/target" "$dir/build" "$dir/.gradle"; do
  if [[ -e $out && ! -w $out ]]; then
   docker run --rm -v "$PWD/$dir:/p" alpine:3.22 chown -R "$(id -u):$(id -g)" "/p/${out##*/}" >&2
  fi
 done
 echo "Building $plugin…" >&2
 if [[ $plugin == claims ]]; then
  docker run --rm --user "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/gradle -e HOME=/tmp \
   -v "$PWD/$dir:/build" -v "$cache/gradle:/gradle" -w /build "$(image_of gradle:jdk25)" \
   gradle shadowJar --no-daemon --console=plain --quiet >&2
 else
  docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 \
   -v "$PWD/$dir:/build" -v "$cache/m2:/m2" -w /build "$(image_of maven:3-eclipse-temurin-25)" \
   mvn --batch-mode --quiet -Dmaven.repo.local=/m2 ${FAST:+-DskipTests} package >&2
 fi
 local jar="$dir/$(plugin_jar "$plugin")"
 [[ -f $jar ]] || { echo "Build finished without $jar" >&2; exit 1; }
 echo "$jar"
}

# Runs the unit tests of a maven plugin, all of them or those matching a surefire pattern.
test_plugin() {
 local plugin=$1 pattern=${2:-} dir cache
 dir=$(plugin_dir "$plugin")
 [[ $plugin != claims ]] || { echo "claims is a gradle plugin: no test shortcut" >&2; exit 1; }
 cache=${XDG_CACHE_HOME:-$HOME/.cache}/vampyuyu
 mkdir -p "$cache/m2"
 docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e MAVEN_CONFIG=/tmp/.m2 \
  -v "$PWD/$dir:/build" -v "$cache/m2:/m2" -w /build "$(image_of maven:3-eclipse-temurin-25)" \
  mvn --batch-mode --quiet -Dmaven.repo.local=/m2 ${pattern:+-Dtest=$pattern -Dsurefire.failIfNoSpecifiedTests=false} test
}

dc() { docker compose -f "$compose" "$@"; }
running() { dc ps --status running --services 2> /dev/null | grep -qx "$1"; }

# Refuses to cut a live arena game short unless FORCE=1: "/arena list" prints "id (PHASE)" per arena
# and every phase other than WAITING means players are inside.
guard() {
 local server=$1 listing busy
 [[ ${FORCE:-} == 1 ]] && return 0
 running "$server" || return 0
 if ! listing=$(dc exec -T "$server" rcon-cli arena list 2> /dev/null); then
  echo "$server is running but did not answer /arena list; retry or set FORCE=1." >&2
  exit 1
 fi
 listing=$(sed 's/\xc2\xa7.//g; s/§.//g' <<< "$listing")
 busy=$(grep -oE '[^ ,:]+ \([A-Z_]+\)' <<< "$listing" | grep -v '(WAITING)' || true)
 if [[ -n $busy ]]; then
  echo "$server has arena activity: $(tr '\n' ' ' <<< "$busy")- retry later or set FORCE=1." >&2
  exit 1
 fi
}

restart() {
 local server=$1
 if ! running "$server"; then
  echo "$server is stopped; the override applies at its next start" >&2
  return 0
 fi
 dc exec -T "$server" rcon-cli say "Server restarting for a plugin update; back in about 30 seconds." > /dev/null 2>&1 || true
 sleep 3
 dc restart --timeout 75 "$server" > /dev/null
 echo "Restarted $server" >&2
}

install_jar() {
 local plugin=$1 jar=$2 state name server
 shift 2
 known "$plugin"
 [[ -f $jar ]] || { echo "Missing jar: $jar" >&2; exit 1; }
 state=$(state_dir)
 name=$(plugin_name "$plugin")
 local servers=("$@")
 [[ ${#servers[@]} -gt 0 ]] || read -ra servers <<< "$(plugin_servers "$plugin")"
 for server in "${servers[@]}"; do
  grep -qw "$server" <<< "$(plugin_servers "$plugin")" || { echo "$plugin does not run on $server" >&2; exit 1; }
  guard "$server"
 done
 for server in "${servers[@]}"; do
  mkdir -p "$state/$server/plugin-overrides"
  cp "$jar" "$state/$server/plugin-overrides/$name"
  echo "Override $name on $server ($(sha256sum "$jar" | cut -c1-12))" >&2
  restart "$server"
 done
}

clear_overrides() {
 local state server servers=("$@") found
 state=$(state_dir)
 [[ ${#servers[@]} -gt 0 ]] || servers=(lobby creative survival)
 for server in "${servers[@]}"; do
  found=$(find "$state/$server/plugin-overrides" -name '*.jar' 2> /dev/null || true)
  [[ -n $found ]] || continue
  guard "$server"
  rm -f "$state/$server/plugin-overrides/"*.jar
  echo "Cleared overrides on $server" >&2
  restart "$server"
 done
}

# Werewolf data a running server re-reads on "/ww reload" (see DataFiles.java): paths relative to the
# arena resources, installed under <server>/plugins/NexusArenaEngine/overrides/ on the same relative path.
DATA_ROOT=${DATA_ROOT:-arena-plugin/src/main/resources}
data_allowed() { [[ $1 =~ ^roles/[A-Z_]+\.json$ || $1 == werewolf/ambience.json || $1 == werewolf/decor/default.json ]]; }

# Reloads the data on a running server and fails when the plugin refuses a file.
reload_data() {
 local server=$1 out
 running "$server" || { echo "$server is stopped; the files apply at its next start" >&2; return 0; }
 out=$(dc exec -T "$server" rcon-cli ww reload 2>&1) || { echo "$server: /ww reload did not run: $out" >&2; return 1; }
 out=$(sed 's/\xc2\xa7.//g; s/§.//g' <<< "$out")
 if grep -qi 'reloaded' <<< "$out"; then echo "$server: $out" >&2; else echo "$server refused the data: $out" >&2; return 1; fi
}

# data [file...] [-s server...]: no file means the data files that differ from git HEAD. Files are checked
# (allowed path, valid JSON) before any server is touched; servers default to every one carrying the plugin.
push_data() {
 local files=() servers=() state server file dest failed=0
 while (($#)); do
  if [[ $1 == -s ]]; then shift; servers=("$@"); break; fi
  files+=("${1#"$DATA_ROOT"/}"); shift
 done
 if ((${#files[@]} == 0)); then
  while IFS= read -r file; do
   file=${file#"$DATA_ROOT"/}
   data_allowed "$file" && files+=("$file")
  done < <(git diff --name-only HEAD -- "$DATA_ROOT" 2> /dev/null; git ls-files --others --exclude-standard -- "$DATA_ROOT" 2> /dev/null)
 fi
 ((${#files[@]})) || { echo "No changed data file under $DATA_ROOT (roles/*.json, werewolf/ambience.json, werewolf/decor/default.json); pass files explicitly." >&2; return 1; }
 for file in "${files[@]}"; do
  data_allowed "$file" || { echo "Not reloadable data: $file" >&2; return 1; }
  python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$DATA_ROOT/$file" || { echo "Invalid JSON: $file" >&2; return 1; }
 done
 state=$(state_dir)
 ((${#servers[@]})) || read -ra servers <<< "$(plugin_servers arena)"
 for server in "${servers[@]}"; do
  grep -qw "$server" <<< "$(plugin_servers arena)" || { echo "arena does not run on $server" >&2; return 1; }
  for file in "${files[@]}"; do
   dest="$state/$server/plugins/NexusArenaEngine/overrides/$file"
   mkdir -p "$(dirname "$dest")"
   cp "$DATA_ROOT/$file" "$dest"
  done
  echo "$server: ${files[*]}" >&2
  reload_data "$server" || failed=1
 done
 return $failed
}

clear_data() {
 local state server servers=("$@") failed=0
 state=$(state_dir)
 ((${#servers[@]})) || read -ra servers <<< "$(plugin_servers arena)"
 for server in "${servers[@]}"; do
  rm -rf "$state/$server/plugins/NexusArenaEngine/overrides"
  echo "$server: data overrides cleared" >&2
  reload_data "$server" || failed=1
 done
 return $failed
}

# Lists active overrides on stderr (empty output when none); used by scripts/up.sh before a rebuild.
warn_active() {
 local state jar
 state=$(state_dir)
 for jar in "$state"/{lobby,creative,survival}/plugin-overrides/*.jar; do
  [[ -f $jar ]] && echo "  ${jar#"$state"/}" >&2
 done
 return 0
}

status() {
 local state server jar any=
 state=$(state_dir)
 for server in lobby creative survival; do
  for jar in "$state/$server/plugin-overrides/"*.jar; do
   [[ -f $jar ]] || continue
   any=1
   printf '%-9s %-26s %s  %s\n' "$server" "${jar##*/}" "$(date -r "$jar" '+%Y-%m-%d %H:%M')" "$(sha256sum "$jar" | cut -c1-12)"
  done
 done
 [[ -n $any ]] || echo "No plugin overrides: every server runs the jars from its image."
}

# Production over SSH. .env.remote (gitignored): PROD_HOST=user@host, PROD_PATH=/path/to/clone,
# optional PROD_PORT. The clone must already contain this script and the matching infra/start.sh.
remote() {
 local config=${PLUGINS_REMOTE_ENV:-.env.remote}
 [[ -f $config ]] || { echo "Create $config with PROD_HOST=user@host and PROD_PATH=/path/to/clone" >&2; exit 1; }
 local PROD_HOST= PROD_PATH= PROD_PORT=22
 # shellcheck disable=SC1091
 source "$config"
 [[ -n $PROD_HOST && -n $PROD_PATH ]] || { echo "$config needs PROD_HOST and PROD_PATH" >&2; exit 1; }
 local ssh=(ssh -p "$PROD_PORT" -o BatchMode=yes "$PROD_HOST") action=${1:-} path
 path=$(printf '%q' "$PROD_PATH")
 shift || true
 local env="COMPOSE=compose.prod.yaml FORCE=${FORCE:-}"
 case $action in
  push)
   [[ $# -gt 0 ]] || { echo 'usage: plugins.sh remote push <plugin> [server...]' >&2; exit 1; }
   known "$1"
   local jar upload
   jar=$(build "$1")
   upload=".plugin-upload/$(plugin_name "$1")"
   "${ssh[@]}" "mkdir -p $path/.plugin-upload"
   scp -q -P "$PROD_PORT" -o BatchMode=yes "$jar" "$PROD_HOST:$PROD_PATH/$upload"
   "${ssh[@]}" "cd $path && test -x scripts/plugins.sh || { echo 'Update the production clone first (git pull)' >&2; exit 1; }; $env scripts/plugins.sh install $(printf '%q ' "$1" "$upload" "${@:2}")"
   ;;
  data)
   # Same file selection as locally, resolved here; the files travel, the clone only needs this script.
   local list=() f
   local -a rest=()
   while (($#)) && [[ $1 != -s ]]; do list+=("${1#"$DATA_ROOT"/}"); shift; done
   rest=("$@")
   if ((${#list[@]} == 0)); then
    while IFS= read -r f; do f=${f#"$DATA_ROOT"/}; data_allowed "$f" && list+=("$f"); done < <(git diff --name-only HEAD -- "$DATA_ROOT"; git ls-files --others --exclude-standard -- "$DATA_ROOT")
   fi
   ((${#list[@]})) || { echo 'No changed data file; pass files explicitly.' >&2; exit 1; }
   for f in "${list[@]}"; do data_allowed "$f" || { echo "Not reloadable data: $f" >&2; exit 1; }; done
   "${ssh[@]}" "rm -rf $path/.data-upload && mkdir -p $path/.data-upload"
   for f in "${list[@]}"; do
    "${ssh[@]}" "mkdir -p $path/.data-upload/$(dirname "$f")"
    scp -q -P "$PROD_PORT" -o BatchMode=yes "$DATA_ROOT/$f" "$PROD_HOST:$PROD_PATH/.data-upload/$f"
   done
   "${ssh[@]}" "cd $path && test -x scripts/plugins.sh || { echo 'Update the production clone first (git pull)' >&2; exit 1; }; $env DATA_ROOT=.data-upload scripts/plugins.sh data $(printf '%q ' "${list[@]}" "${rest[@]}")"
   ;;
  data-clear)
   local dargs=
   (($#)) && dargs=$(printf '%q ' "$@")
   "${ssh[@]}" "cd $path && $env scripts/plugins.sh data-clear $dargs"
   ;;
  clear | status)
   local args=
   (($#)) && args=$(printf '%q ' "$@")
   "${ssh[@]}" "cd $path && $env scripts/plugins.sh $action $args"
   ;;
  *) echo 'usage: plugins.sh remote push|clear|status|data|data-clear [...]' >&2; exit 1 ;;
 esac
}

action=${1:-}
shift || true
case $action in
 test) [[ $# -gt 0 ]] || { echo 'usage: plugins.sh test <plugin> [pattern]' >&2; exit 1; }
  known "$1"; test_plugin "$1" "${2:-}" ;;
 build) [[ $# -gt 0 ]] || { echo 'usage: plugins.sh build <plugin>...' >&2; exit 1; }
  for p in "$@"; do known "$p"; build "$p" > /dev/null; done ;;
 push) [[ $# -gt 0 ]] || { echo 'usage: plugins.sh push <plugin> [server...]' >&2; exit 1; }
  known "$1"; jar=$(build "$1"); install_jar "$1" "$jar" "${@:2}" ;;
 install) [[ $# -ge 2 ]] || { echo 'usage: plugins.sh install <plugin> <jar> [server...]' >&2; exit 1; }
  install_jar "$@" ;;
 clear) clear_overrides "$@" ;;
 data) push_data "$@" ;;
 data-clear) clear_data "$@" ;;
 status) status ;;
 warn-active) warn_active ;;
 remote) remote "$@" ;;
  *) sed -n '2,/^set /p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 1 ;;
esac
