#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
compose=${1:-compose.local.yaml}
# --check validates the configuration only; scripts/deploy.sh runs it before switching images.
check_only=${2:-}
[[ "$compose" == compose.prod.yaml || "$compose" == compose.local.yaml ]] || { echo 'Invalid Compose file' >&2; exit 1; }
config=$(docker compose -f "$compose" config --format json)
python3 -c '
import json,sys,pathlib,re,platform
c=json.load(sys.stdin);s=c["services"]
if s["creative"]["environment"]["EULA"].upper()!="TRUE":sys.exit("Set EULA=TRUE after accepting the Minecraft EULA")
if sys.argv[1]=="compose.prod.yaml":
 if platform.machine()!="x86_64":sys.exit("Production images currently target linux/amd64")
 if not re.fullmatch(r".*:(src-)?[0-9a-f]{40}",s["panel"]["image"]):sys.exit("Set IMAGE_TAG to a published 40-character commit SHA")
 if not s["panel"]["environment"]["PUBLIC_ORIGIN"].startswith("https://"):sys.exit("Production PUBLIC_ORIGIN must use HTTPS")
for name in ("lobby","creative","survival"):
 service=s[name];version=service["environment"]["VERSION"]
 data=pathlib.Path(next(v["source"] for v in service["volumes"] if v["target"]=="/data"))
 marker=data/".network-version"
 if not marker.exists() or marker.read_text().strip()!=version:sys.exit(name+": run offline configure with the selected Minecraft version first")
 if not (data/"config/paper-global.yml").is_file():sys.exit(name+": forwarding configuration missing")
' "$compose" <<< "$config"
# (Also done for --check: scripts/deploy.sh only calls this script with --check before it recreates services.)
# The controller runs as PUID:PGID. Docker would create a missing bind-mounted folder owned by root, and one
# made earlier by a root-run `make backup` is root's too: either way the controller could not write the
# backups or its settings. Create them, and hand them to the controller's user when run as root.
while read -r owner dir; do
 [[ -n $dir ]] || continue
 install -d -m 700 "$dir"
 if [[ $(id -u) -eq 0 ]]; then chown -R "$owner" "$dir"; fi
done < <(python3 -c '
import json,sys
c=json.load(sys.stdin)["services"]["controller"]
for v in c["volumes"]:
 if v.get("target") in ("/backups","/state"): print(c.get("user","1000:1000"),v["source"])
' <<< "$config")
[[ "$check_only" == --check ]] && exit 0
if [[ "$compose" == compose.local.yaml ]]; then
 # A rebuilt image must not stay shadowed by an older hotfix: say which overrides still win.
 active=$(scripts/plugins.sh warn-active 2>&1)
 if [[ -n $active ]]; then
  printf '\nWARNING: these plugin overrides still replace the freshly built jars:\n%s\nRun make plugin-clear to use the image jars.\n\n' "$active" >&2
 fi
 docker compose -f "$compose" up -d --build
else
 docker compose -f "$compose" pull
 docker compose -f "$compose" up -d
fi
