# netmenu: the Skyblock card of `/menu`, the Redis handoff, archives

Not part of `survival-plugin`: this work lives in `network-plugin/` (the `/menu` world selector on lobby, creative and
survival), `lobby/` (the Skyblock gate), `scripts/generate-lobby.py`, the resource pack art, `scripts/world_archive.py`
and a ready patch for the panel's backup code.

## What players see

`/menu` (and the lobby compass) now offers four cards: **Creative** (left arch), **Skyblock** (the centre card, a floating
island with a tree in a dawn sky), **Survival** (right arch) and a **Lobby** link under the Skyblock card. The old
"Vampyuyu / Choose your world" hub star is gone; the centre belongs to Skyblock.

* Hovering any of the six slots of the Skyblock card shows one tooltip: name, one-line pitch, what is inside (skills,
  minions, bosses, dungeons), the number of players online on the network (when known and above zero), and the three
  click types below.
* **Click types** on the Skyblock card: **left-click** enters Skyblock (`dest=skyblock`, the survival side decides: hub for a
  new player, island for one who has one), **shift-click** goes to "my island" (`dest=island`), **right-click** visits the
  hub (`dest=hub`). The other cards react to left, right and shift clicks; number keys, drops and double clicks do
  nothing. On Connect the player hears a launch sound and sees a "SKYBLOCK" title; a request is followed by a 3 s cooldown.
* `/menu [creative|survival|skyblock|lobby] [hub|island]` (tab completion included): with no argument it opens the menu,
  with one it goes straight there. The lobby gates use it (see "Lobby gates").
* When the Skyblock routing (Redis) is unreachable or not configured the tooltip adds, in rose, "Skyblock routing is
  offline right now: you will join the Survival server instead." and the click falls back to a plain connect, at once
  (no wait for a Redis timeout while Redis is known to be down).
* Without the menu pack (plain glass-pane layout) the Skyblock card is a grass block in the centre slot (13).
* All text is English; static captions on the art are "SKYBLOCK / Build your island." and "Back to the lobby".

Click behaviour (all cards share ONE in-flight guard per player and a 3 s cooldown after a request finishes):

| Player is on | Click | What happens |
|---|---|---|
| lobby, creative | Skyblock | `SETEX sb:transfer:<uuid> 30 {"dest":"<skyblock, island or hub>","ts":<ms>}` (async), then `Connect survival` (main thread). Redis known down or failing: the connect still happens, with a chat line saying routing is unavailable. |
| survival | Skyblock | A `Connect survival` is a no-op on the same server (no join event), so the request is handed to the local survival plugin (see "Same-server handoff"). No Redis is involved. With nobody listening the player gets "Skyblock is not ready on this server yet". |
| any | Survival, Creative, Lobby | async `DEL sb:transfer:<uuid>` FIRST, then `Connect <server>`. A key left by a Skyblock click that never completed (survival restarting) can therefore no longer redirect the player to Skyblock up to 30 s later. Redis known down: no DEL, immediate connect. |

A player leaving mid-request is not connected and the key is deleted. Because the guard is shared, a Skyblock click and a
Survival click can never run as two parallel async tasks (a DEL overtaking the SETEX would leave the key behind).

## Contract for the `menu` feature (survival-plugin)

Remote arrivals (from lobby or creative), exactly as in `docs/skyblock-design.md` section 12:

* Key `sb:transfer:<uuid>`, value `{"dest":"skyblock|island|hub","ts":<epoch millis>}`, TTL 30 s, written BEFORE the Connect.
* `ts` is always present. The network plugin never sets `arg`. `dest` is `skyblock` ("a normal Skyblock join, decided by
  your own policy"), `island` (shift-click) or `hub` (right-click).
* The survival side does `GETDEL` on join, off the main thread, and routes the player. **It must ignore a payload whose
  `ts` is older than 30 s** (the TTL normally guarantees it, a clock check is the second belt). A missing key means a plain join.
* A click on Survival, Creative or Lobby DELETES the key before connecting, so a surviving key always means "the player's
  LAST choice was Skyblock".

### Same-server handoff (player already on survival)

Two mechanisms, both optional for the survival plugin:

1. **Incoming plugin channel `vampyuyu:skyblock` (recommended, no reflection). THE CHANNEL IS CLIENT-REACHABLE:** Paper
   hands a custom payload sent by a (modified) client to every registered incoming listener, with no check of the
   sender. A listener that acts on the bytes alone lets a cheater trigger routing with no click, no cooldown. So the
   network plugin also sets the player metadata `vampyuyu:skyblock` (value = a random nonce) for the duration of the
   synchronous dispatch, and puts the same nonce in the payload. A client cannot set server-side metadata. The listener
   must do exactly this:
   ```java
   getServer().getMessenger().registerIncomingPluginChannel(plugin, "vampyuyu:skyblock", (channel, player, message) -> {
       String body = new String(message, StandardCharsets.UTF_8);          // {"dest":"hub","ts":..,"nonce":"..."}
       boolean real = player.getMetadata("vampyuyu:skyblock").stream()
               .anyMatch(value -> body.contains("\"nonce\":\"" + value.asString() + "\""));
       if (!real) return;                                                    // forged by a client: ignore
       /* route the player to "dest" (hub, island, or your own policy for skyblock) */
   });
   ```
   The metadata is removed right after the dispatch returns: do any deferred work from the parsed payload, not from the
   metadata. The listener should still apply its own per-player cooldown. There is NO Redis key for this path.
2. **`SkyblockGateway` service** (`win.vampyuyu.network.SkyblockGateway`, one method `boolean sendToSkyblock(Player)`, main
   thread). The survival plugin cannot depend on network-plugin at build time, so it would load the interface through the
   network plugin's class loader and register a `java.lang.reflect.Proxy` in the `ServicesManager`. Kept because the task
   asked for it; the channel is simpler and carries the landing. The gateway is only asked for a plain `skyblock`
   request (no landing parameter) and wins over the channel there; a gateway returning false falls through to the channel.
   `island` and `hub` requests only use the channel.

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

## Lobby gates (lobby plugin, `lobby/`)

* The lobby palace now has a THIRD gate, **Skyblock**, at the south end of the red avenue (glass wall at z=38, lime glass,
  pillars at x=+-5, wall sign west of the gate), next to the existing Creative (west) and Survival (east) gates. Walking in
  (4 s cooldown like the others) runs `/vampyuyunetwork:menu skyblock`; the Creative and Survival gates now run
  `/vampyuyunetwork:menu creative|survival` so they also clear a stale Skyblock key. The raw BungeeCord connect stays as
  the fallback if that command is unavailable. The join message names Skyblock.
* The gate is blocks only (the lobby deletes every entity each second, so no hologram or NPC is possible). A new lobby gets
  it from `build_once`. An EXISTING lobby world never re-runs that: run `function vampyuyu:skyblock_gate` once (a block-only,
  idempotent patch generated by `scripts/generate-lobby.py` into `infra/lobby-palace/.../skyblock_gate.mcfunction`), then
  restart the lobby so the sign text is set. The trigger is inert until the lime glass at (0, 68, 38) exists.
* Position chosen to avoid the avenue lamps (z=27, 34), the floor lights (z=24, 32) and the balustrade (z>=43).

## Commands, permissions, config

No new commands, permissions or config files. `/menu` keeps its plugin.yml entry (now with the optional arguments above, no permission, like before); `vampyuyu.sb.*` permissions do not apply.

## Resource pack

* `resource-pack/art/render.py`: `lobby()` repainted (`menu_lobby_*` tiles only; `menus.json` and `layout.py` unchanged).
  New `skyblock_card()`. The Survival arch was 12 css px off the slot columns and is now centred on slot columns 6-8
  (mirror of Creative). Regenerate with `python3 resource-pack/art/render.py menu_lobby` (needs headless Chrome, network for
  Google Fonts and Pillow; Pillow was missing on this machine and came from `nix build` of `python3.withPackages`).
* `resource-pack/art/skyblock_icon.py` (stdlib only, deterministic, run by `render.py` as part of `make art`; test
  `resource-pack/art/test_skyblock_icon.py`, run by `make test-pack`, pins its sha256 and checks the committed PNG is what the
  script draws; an enlarged reference is committed as `docs/skyblock-notes/netmenu-icon-preview.png`): the 16x16 pixel-art icon, item model
  `vampyuyu:icon/skyblock` (`items/icon/skyblock.json`, `models/item/icon/skyblock.json`,
  `textures/item/icon/skyblock.png`). The `/menu` itself does NOT show it: the painted layout uses invisible items over the art and the plain layout
  cannot use pack models (a player WITHOUT the pack would see a missing-model cube, and the plain layout is exactly the
  no-pack case), so the asset ships for the survival menu feature (quests, hub navigation) to put on items.
* `make pack` and `make test-pack` pass.

## Archives

On Paper 26.x a plugin-made world lives INSIDE the main world: `world/dimensions/<namespace>/<name>/` (`ww_sky` is there
today). So `sb_hub`, `sb_world`, `sb_nether` and `sb_end` are already part of the `world` root of every survival archive
and backup, with no change. What was missing is the exclusion of the throw-away dungeon worlds `sb_dungeon_*`:

* `scripts/world_archive.py` (`make zip`, `make export`, `make import` previous-state ZIP): `transient_world_path()` skips
  `<root>/dimensions/<ns>/sb_dungeon_*` and a top-level `sb_dungeon_*` folder (older layout). Test: `make test-archives`.
* `docs/world-archives.md` says so.
* The panel's Backups page (Go, `internal/backup`) is NOT changed here (not in this agent's scope). The change is delivered
  as a ready, tested patch: `docs/skyblock-notes/netmenu-backup-dungeons.patch` (`git apply` it; includes
  `TestDungeonWorldsAreSkipped`; `go test ./internal/...` was run green with it applied, then reverted, and
  `git apply --check` passes on the clean tree). See NEEDS 5.

## Velocity and the panel

Checked, no change needed for a Skyblock mode on the existing `survival` server id (the one exception is the backup exclusion of NEEDS 5): `infra/velocity.toml` lists
lobby/creative/survival and `try = ["lobby"]`; the network plugin's server allowlist and the panel's controller/players code
use the same three ids; the panel's world listings and rules iterate `Bukkit.getWorlds()` generically; player data stays in the
main world (`world/players/data`), which is what `PlayerInventoryStore` reads.

## NEEDS (for the foundation owner / menu agent)

1. **`/menu` name clash.** `docs/skyblock-design.md` gives the menu feature a `/menu`. The network plugin already owns `/menu`
   (plugin.yml) on every server, and it is the world selector (now with arguments, see above). Whichever plugin registers
   second only gets `/vampyuyusurvival:menu`, and which one that is depends on load order. The survival `menu` feature should
   use another name (`/skyblock`, `/sbmenu`, `/hub`) and leave `/menu` to the selector.
2. **Same-server listener.** The menu feature should register the incoming channel `vampyuyu:skyblock` with the NONCE CHECK
   above (a listener without it can be triggered by a cheating client) and route the player to `dest`; otherwise the Skyblock
   card does nothing for a player who is already on survival.
3. **The Survival card.** Per design section 12, a join without a key lands on `sb_hub`. If the survival server becomes all
   Skyblock, the Survival card is just a second way in. If the old vanilla world must stay reachable, the menu feature needs a
   `dest` for it (for example `"dest":"world"`) and the selector can write it for the Survival card: today the Survival card
   writes nothing (it only deletes a stale key), by design, because no consumer understands another dest yet.
4. **"Your island / last visited" on the card.** Needs survival-side data (does the player own an island, when did they last
   play). The network plugin has no access to it; the three click types (left / shift = island / right = hub) are the
   substitute. A survival-side service could expose it through the gateway or a Redis key the selector reads.
5. **Panel backups must skip the dungeon worlds** (`internal/backup/backup.go`, not edited here): apply
   `docs/skyblock-notes/netmenu-backup-dungeons.patch`. A live backup walks `world/` while the dungeon feature creates and
   deletes `world/dimensions/<ns>/sb_dungeon_<run>/`, which can fail the backup or archive a half-written world.
6. The join router must accept `"dest":"skyblock"`, `"island"` and `"hub"` (`skyblock` is not in the design doc's example list).

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
* Clicks are throttled by the in-flight guard and a 3 s cooldown after a request finishes.
* The online count on the card is the network-wide count from the role bridge (it includes lobby and creative), refreshed
  every few seconds; there is no per-server "on Skyblock" figure.
* NOT verified in game: the new Skyblock lobby gate (blocks, sign text, trigger), the click types in a real client, the title
  and sound, `/menu skyblock island`. Unit tests cover the route, the payloads, the nonce guard and the argument parsing.
  The gate patch has never been applied to a real lobby world.
* The menu card art / the new tooltip were not re-screenshotted after this review round (no headless Chrome or Pillow on this
  machine); the committed reference is only the icon preview.
* If the Redis write is slow (up to 2 s connect + 3 s socket timeout) while Redis is still considered up, the player waits
  that long after the click before the connect, with the "Preparing your trip" line shown meanwhile. The same applies to
  the DEL of a Survival or Creative click.
* The panel's live backup freezes saves with `save-all flush` over RCON; whether Paper 26.x flushes plugin-made worlds there
  is the same assumption as for `ww_sky` today.
