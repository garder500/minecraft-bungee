#!/bin/sh
set -eu
case "$1" in
 paper)
  test -f /data/.network-version && test "$(cat /data/.network-version)" = "$VERSION" || {
   echo 'Startup refused: run offline configure with the selected Minecraft version.' >&2; exit 1;
  }
  test -s /data/config/paper-global.yml || { echo 'Forwarding configuration missing' >&2; exit 1; }
  if [ "${SERVER_ID:-}" = lobby ] && [ ! -f /data/world/level.dat ]; then
   mkdir -p /data/world/datapacks
   mkdir -p /data/world/datapacks/vampyuyu-palace
   cp -R /opt/vampyuyu-lobby-palace/. /data/world/datapacks/vampyuyu-palace/
   touch /data/.vampyuyu-palace
   # This script runs as root: hand the copy to the data owner so panel restores can replace it.
   chown -R "$(stat -c %u:%g /data)" /data/world/datapacks /data/.vampyuyu-palace
  fi
  # A lobby built by the palace keeps its blocks and settings; only our own copy of the datapack follows the image.
  if [ "${SERVER_ID:-}" = lobby ] && [ -f /data/.vampyuyu-palace ] && [ -d /data/world/datapacks/vampyuyu-palace ]; then
   rm -rf /data/world/datapacks/vampyuyu-palace
   mkdir -p /data/world/datapacks/vampyuyu-palace
   cp -R /opt/vampyuyu-lobby-palace/. /data/world/datapacks/vampyuyu-palace/
   chown -R "$(stat -c %u:%g /data)" /data/world/datapacks/vampyuyu-palace
  fi
  # Claims exist only on Creative; the jar comes from the image, claim data stays in /data.
  if [ "${SERVER_ID:-}" = creative ]; then
   # Drop any previously installed copy so two jars never claim the same plugin name.
   rm -f /data/plugins/SimpleClaimSystem*.jar
   cp /opt/claims/SimpleClaimSystem.jar /plugins/SimpleClaimSystem.jar
  fi
  # Plugin hotfixes (scripts/plugins.sh) replace an image jar of the same name until the next deployment.
  overrides=${PLUGIN_OVERRIDES:-/data/plugin-overrides}
  image_plugins=${IMAGE_PLUGINS:-/plugins}
  for jar in "$overrides"/*.jar; do
   [ -f "$jar" ] || continue
   cp "$jar" "$image_plugins/"
   echo "PLUGIN OVERRIDE ACTIVE: ${jar##*/} (sha256 $(sha256sum "$jar" | cut -c1-12)); run scripts/plugins.sh clear to go back to the image" >&2
  done
  # The image syncs /plugins into /data/plugins but skips files that are newer there, so a cleared
  # override would keep running. Removing our copies first makes /plugins always win.
  for jar in "$image_plugins"/*.jar; do
   [ -f "$jar" ] && rm -f "${DATA_PLUGINS:-/data/plugins}/${jar##*/}"
  done
  exec /image/scripts/start
  ;;
 velocity)
  test -s /server/forwarding.secret || { echo 'Startup refused: run offline configure first.' >&2; exit 1; }
  exec /__cacert_entrypoint.sh /usr/bin/run-bungeecord.sh
  ;;
 *) exit 1 ;;
esac
