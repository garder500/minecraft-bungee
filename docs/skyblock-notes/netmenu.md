# netmenu: the Skyblock card of `/menu`, the Redis handoff, archives

Not part of `survival-plugin`: this work lives in `network-plugin/` (the `/menu` world selector on lobby, creative and
survival), the resource pack art, `scripts/world_archive.py` and the panel's backup code.

## What players see

`/menu` (and the lobby compass) now offers four cards: **Creative** (left arch), **Skyblock** (the centre card, a floating
island with a tree in a dawn sky), **Survival** (right arch) and a **Lobby** link under the Skyblock card. The old
"Vampyuyu / Choose your world" hub star is gone; the centre belongs to Skyblock.

* Hovering any of the six slots of the Skyblock card shows one tooltip: name, one-line pitch, what is inside (skills,
  minions, bosses, dungeons), "Click to enter".
* When the Skyblock routing (Redis) is unreachable or not configured the tooltip adds, in rose, "Skyblock routing is
  offline right now: you will join the Survival server instead." and the click falls back to a plain connect.
* Without the menu pack (plain glass-pane layout) the Skyblock card is a grass block in the centre slot (13).
* All text is English; static captions on the art are "SKYBLOCK / Build your island." and "Back to the lobby".

Click behaviour:

| Player is on | Click | What happens |
|---|---|---|
| lobby, creative | Skyblock | `SETEX sb:transfer:<uuid> 30 {"dest":"skyblock","ts":<ms>}` (async), then `Connect survival` (main thread). If Redis fails the connect still happens, with a chat line saying routing is unavailable. |
| survival | Skyblock | A `Connect survival` is a no-op on the same server (no join event), so the request is handed to the local survival plugin (see "Same-server handoff"). With nobody listening the player gets "Skyblock is not ready on this server yet" and the key is deleted. |
| any | Survival | unchanged: plain `Connect survival`, no key. |

A player has at most one Skyblock request in flight (spam-clicking sends one write and one connect). A player who leaves
mid-request is not connected and the key is deleted.

## Contract for the `menu` feature (survival-plugin)

Remote arrivals (from lobby or creative), exactly as in `docs/skyblock-design.md` section 12:

* Key `sb:transfer:<uuid>`, value `{"dest":"skyblock","ts":<epoch millis>}`, TTL 30 s, written BEFORE the Connect.
* `ts` is always present. The network plugin never sets `arg`. `dest` is always `skyblock` today.
* The survival side does `GETDEL` on join, off the main thread, and routes the player (hub, island, wherever its join
  router decides). A missing key means a plain join.

### Same-server handoff (player already on survival)

Two mechanisms, tried in this order, both optional for the survival plugin:

1. **Incoming plugin channel `vampyuyu:skyblock` (recommended, no reflection).** The network plugin calls
   `Bukkit.getMessenger().dispatchIncomingMessage(player, "vampyuyu:skyblock", bytes)` on the main thread; `bytes` are the
   UTF-8 bytes of the same JSON body as the key (no length prefix). The survival plugin only needs:
   ```java
   getServer().getMessenger().registerIncomingPluginChannel(plugin, "vampyuyu:skyblock",
       (channel, player, message) -> { /* route the player: hub or island */ });
   ```
   The network plugin sees that a listener exists through `Messenger.getIncomingChannelRegistrations`; no registration, no
   dispatch. The key was written first (when Redis is up): the listener should `GETDEL sb:transfer:<uuid>` (best effort,
   async) so it cannot be consumed twice. The payload itself is authoritative when Redis is down.
2. **`SkyblockGateway` service** (`win.vampyuyu.network.SkyblockGateway`, one method `boolean sendToSkyblock(Player)`,
   main thread). The survival plugin cannot depend on network-plugin at build time, so it would load the interface through
   the network plugin's class loader and register a `java.lang.reflect.Proxy` in the `ServicesManager`. Kept because the task
   asked for it; the channel is simpler. The gateway wins when both exist; a gateway returning false falls through to the
   channel.

Until the survival plugin registers one of them, a click on Skyblock from survival says "not ready" and does nothing else.

## Redis client (network-plugin)

* `SkyblockHandoff`: Jedis 6.2.0 (Gson and SLF4J excluded, Paper provides them), shaded and relocated to
  `win.vampyuyu.network.lib.redis|pool|json` (`pom.xml`, same recipe as arena and survival).
* Settings like the arena plugin: `ARENA_REDIS_HOST` (default `redis`), optional `ARENA_REDIS_PORT` (default 6379),
  `ARENA_REDIS_PASSWORD_FILE` (default `/run/secrets/arena_redis_password`, at least 32 characters). Connect timeout 2 s,
  socket timeout 3 s, pool of 4 with a 2 s wait. All three game services already inherit these and list the secret in
  `compose.local.yaml` and `compose.prod.yaml`: no compose change was needed.
* A missing or short secret, a bad port or an unreachable Redis never breaks the plugin: it logs ONE warning, the Skyblock
  card shows the offline tooltip and falls back to a plain connect. A background ping every 30 s (async) keeps the tooltip
  honest and logs when Redis comes back; the menu never blocks on Redis when opening.
* Redis is only touched off the main thread. Every key has a 30 s TTL (the Redis instance runs with `noeviction`).
* The `sb:` prefix is Skyblock's. The arena plugin owns `nexus:transfer:*`.

## Commands, permissions, config

No new commands, permissions or config files. `/menu` keeps its plugin.yml entry; `vampyuyu.sb.*` permissions do not apply.

## Resource pack

* `resource-pack/art/render.py`: `lobby()` repainted (`menu_lobby_*` tiles only; `menus.json` and `layout.py` unchanged).
  New `skyblock_card()`. The Survival arch was 12 css px off the slot columns and is now centred on slot columns 6-8
  (mirror of Creative). Regenerate with `python3 resource-pack/art/render.py menu_lobby` (needs headless Chrome, network for
  Google Fonts and Pillow; Pillow was missing on this machine and came from `nix build` of `python3.withPackages`).
* `resource-pack/art/skyblock_icon.py` (stdlib only, deterministic): the 16x16 pixel-art icon, item model
  `vampyuyu:icon/skyblock` (`items/icon/skyblock.json`, `models/item/icon/skyblock.json`,
  `textures/item/icon/skyblock.png`). The `/menu` itself does NOT show it: the painted layout uses invisible items over the art and the plain layout
  cannot use pack models, so the asset ships for the survival menu feature (quests, hub navigation) to put on items.
* `make pack` and `make test-pack` pass.

## Archives

On Paper 26.x a plugin-made world lives INSIDE the main world: `world/dimensions/<namespace>/<name>/` (`ww_sky` is there
today). So `sb_hub`, `sb_world`, `sb_nether` and `sb_end` are already part of the `world` root of every survival archive
and backup, with no change. What was missing is the exclusion of the throw-away dungeon worlds `sb_dungeon_*`:

* `scripts/world_archive.py` (`make zip`, `make export`, `make import` previous-state ZIP): `transient_world_path()` skips
  `<root>/dimensions/<ns>/sb_dungeon_*` and a top-level `sb_dungeon_*` folder (older layout). Test: `make test-archives`.
* `docs/world-archives.md` says so.
* The panel's Backups page (Go, `internal/backup`) is NOT changed here (not in this agent's scope); see NEEDS 5.

## Velocity and the panel

Checked, no change needed for a Skyblock mode on the existing `survival` server id (the one exception is the backup exclusion of NEEDS 5): `infra/velocity.toml` lists
lobby/creative/survival and `try = ["lobby"]`; the network plugin's server allowlist and the panel's controller/players code
use the same three ids; the panel's world listings and rules iterate `Bukkit.getWorlds()` generically; player data stays in the
main world (`world/players/data`), which is what `PlayerInventoryStore` reads.

## NEEDS (for the foundation owner / menu agent)

1. **`/menu` name clash.** `docs/skyblock-design.md` gives the menu feature a `/menu`. The network plugin already owns `/menu`
   (plugin.yml) on every server, and it is the world selector. Whichever plugin registers second only gets
   `/vampyuyusurvival:menu`, and which one that is depends on load order. The survival `menu` feature should use another
   name (`/skyblock`, `/sbmenu`, `/hub`) and leave `/menu` to the selector.
2. **Same-server listener.** The menu feature should register the incoming channel `vampyuyu:skyblock` (snippet above) and route
   the player; otherwise the Skyblock card does nothing for a player who is already on survival.
3. **The Survival card.** Per design section 12, a join without a key lands on `sb_hub`. If the survival server becomes all
   Skyblock, the Survival card is just a second way in. If the old vanilla world must stay reachable, the menu feature needs a
   `dest` for it (for example `"dest":"world"`) and the selector can write it for the Survival card: one line in `WorldSelector`.
4. Lobby portals and NPCs (`lobby/`, `LobbySelector`) are untouched: they still only offer creative and survival. Adding a
   Skyblock portal there would need the same `SkyblockHandoff` code in the lobby plugin (it has no Redis).

5. **Panel backups must skip the dungeon worlds** (`internal/backup/backup.go`, not edited here). A live backup walks
   `world/` while the dungeon feature creates and deletes `world/dimensions/<ns>/sb_dungeon_<run>/`, which can fail the backup
   or archive a half-written world. Patch (tried in this worktree, `go test ./internal/...` green, then reverted): add

   ```go
   func transientWorld(rel string) bool {
   	parts := strings.Split(rel, "/")
   	if strings.HasPrefix(parts[0], "sb_dungeon_") {
   		return true
   	}
   	for i, part := range parts {
   		if part == "dimensions" && i+2 < len(parts) && strings.HasPrefix(parts[i+2], "sb_dungeon_") {
   			return true
   		}
   	}
   	return false
   }
   ```
   call `!transientWorld(name)` in the `worldName(name)` case of `roots()`, and at the top of the `WalkDir` callback in `Create`
   (right after `rel` is computed): `if transientWorld(rel) { if d.IsDir() { return filepath.SkipDir }; return nil }`.
   A test creates `world/dimensions/minecraft/{sb_hub,sb_dungeon_a1b2}` and a top-level `sb_dungeon_c3d4/level.dat` and checks
   that only the dungeon paths are missing from the zip.
6. The join router must accept `"dest":"skyblock"`: it is not in the design doc's example list (`hub|island|dungeon|...`). It
   means "a normal Skyblock join, decided by your own policy" (hub for a new player, island for one who has one...).

## Known gaps / NOT verified in game

* Verified with a real client (`e2e_client.py` harness with a private wrapper, SERVER_ID=survival, no Redis): the painted
  menu, the hover tooltip of the three cards (Skyblock with the offline line), the click on Skyblock with no listener
  ("not ready" message). Screenshots were checked, not committed.
* Verified with a real Redis 8.2 (`SkyblockRedisLiveTest`, skipped unless `SKYBLOCK_TEST_REDIS_HOST/PASSWORD` are set): key,
  body, TTL, delete, and the bounded failure against an unreachable host.
* Verified with the SHADED jar inside a real Paper 26.3 with a real client and a Redis on 127.0.0.1 (`MONITOR` showed
  `AUTH`, a `PING` every 30 s, then `SETEX sb:transfer:<uuid> 30 {"dest":"skyblock","ts":...}` and `DEL` when nothing listened):
  the relocated Jedis loads, and the online tooltip has no offline line.
* NOT verified: the full Connect from lobby to survival through Velocity with the key read by a real survival plugin (the
  reading side belongs to the menu feature); the plain glass-pane layout in a client; the same-server channel/gateway with a real
  listener (unit tested with MockBukkit and a recording listener); a production Redis password file in the container.
* Per-player clicks are throttled only by the in-flight guard: a player can start a new request as soon as the last one
  finished.
* If the Redis write is slow (up to 2 s connect + 3 s socket timeout) the player waits that long after the click before the
  connect, with the "Preparing your trip" line shown meanwhile.
* The panel's live backup freezes saves with `save-all flush` over RCON; whether Paper 26.x flushes plugin-made worlds there
  is the same assumption as for `ww_sky` today.
