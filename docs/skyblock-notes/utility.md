# Utility feature (`win.vampyuyu.survival.utility`, plus `back`)

Quality-of-life layer of the Skyblock/survival server. Profile section: `utility`. Config: `utility.yml`.

## Player-facing description

* **Homes**: `/sethome [name]`, `/home [name]`, `/delhome <name>`, `/homes` (menu with one coloured bed per home:
  click to teleport, shift + right click to delete). The number of homes comes from the permission
  `vampyuyu.sb.utility.homes.<n>` (biggest granted node wins, `homes.unlimited` = the configured maximum, default 3).
  A player above their limit (rank loss) keeps and can use their homes but cannot add new ones.
  Homes in Skyblock worlds only where `IslandApi.canBuild` is true (checked when setting AND at every teleport);
  none in the hub or in dungeons. Unsafe spots (no ground within 4 blocks, lava, fire, blocked body) are refused
  when setting and when arriving.
* **Spawn**: `/spawn` goes to the spawn of the survival world; inside any Skyblock world it goes to the hub through
  `IslandApi.teleportHub`. `/setspawn` (admin) sets the survival world spawn.
* **Requests**: `/tpa`, `/tpahere`, `/tpaccept [player]`, `/tpdeny [player]`, `/tpcancel`, `/tptoggle`. 60 s expiry,
  one open request per sender, 10 s between two requests, at most 5 waiting requests per target, clickable
  [Accept]/[Deny] in chat. A request is consumed when answered (no double accept).
* **/rtp** (alias `/wild`): random safe place in the vanilla survival world (only usable from that world), chunks
  loaded asynchronously, 10 minute cooldown that starts only when the teleport really happened.
* **Portable blocks**: `/ec` (`/enderchest`), `/workbench`, `/anvil`, `/grindstone`, `/stonecutter`, `/loom`. Never in
  dungeons. `/ec <player>` (online) needs `vampyuyu.sb.utility.ec.others`.
* **/hat**, **/sit** (an invisible non-persistent `Interaction` entity is the seat; sneak to get up), **/trash**
  (closing destroys the content, except `trash.keep-ids` and custom items of `trash.keep-rarity` or better, which are
  handed back; "Take everything back" button), **/playtime [player]**, **/near [radius]**, **/seen <player>**,
  **/ping [player]**, **/utility help|reload**.
* **/back** is unchanged (5 minute cooldown that starts at use, persisted in `back.json`) but now goes through the
  teleport rules below (warm-up, combat, dungeon refusal; the cooldown starts when the teleport happens).
* **Death**: the victim (only) gets a private message with the coordinates and world.
* **Every teleport** (`/home /spawn /back /tpa* /rtp`) uses the same door (`TeleportService`): warm-up (3 s by default,
  cancelled by moving, taking damage, entering combat), one teleport at a time per player, refusal when the player is in
  a dungeon (`DungeonApi.inDungeon` or an `sb_dungeon_*` world) or when the destination is a dungeon world, refusal
  while combat tagged (hit by a player in the last 10 s, mobs only with `combat.include-mobs`; cleared on death and quit). The player gets a chat line when the tag starts and when it ends (`combat.notify`).

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/home /sethome /delhome /homes` | `vampyuyu.sb.utility.home` | everyone |
| `/spawn` | `vampyuyu.sb.utility.spawn` | everyone |
| `/setspawn`, `/utility reload`, `/tpa` to players who disabled requests | `vampyuyu.sb.utility.admin` | OP |
| `/tpa /tpahere /tpaccept /tpdeny /tpcancel /tptoggle` | `vampyuyu.sb.utility.tpa` | everyone |
| `/rtp` | `vampyuyu.sb.utility.rtp` | everyone |
| `/ec`, `/workbench` | `vampyuyu.sb.utility.ec`, `.workbench` | everyone (config `portable.<name>.public`) |
| `/anvil /grindstone /stonecutter /loom` | `vampyuyu.sb.utility.<name>` | OP (config `portable.<name>.public`) |
| `/ec <player>` | `vampyuyu.sb.utility.ec.others` | OP |
| `/hat` | `vampyuyu.sb.utility.hat` | everyone |
| `/sit` | `vampyuyu.sb.utility.sit` | everyone |
| `/trash` | `vampyuyu.sb.utility.trash` | everyone |
| `/playtime /near /seen /ping /utility` | `vampyuyu.sb.utility.info` | everyone |
| `/ping <player>` | `vampyuyu.sb.utility.ping.others` | everyone |
| home count | `vampyuyu.sb.utility.homes.<n>`, `.homes.unlimited` | config default / OP |
| bypasses | `vampyuyu.sb.utility.bypass.warmup`, `.bypass.cooldown`, `.bypass.combat` | OP |
| `/back` | `vampyuyu.back`, `vampyuyu.back.bypass` (plugin.yml, unchanged) | everyone / OP |

## Config keys (`utility.yml`, all documented in the file)

`teleport.{warmup-seconds, move-tolerance, cancel-on-damage, show-title}`, `combat.{enabled, tag-seconds,
include-mobs, notify}`, `homes.{default-limit, max-limit, max-name-length, default-name}`, `tpa.{expire-seconds,
send-cooldown-seconds, max-pending-per-target, teleport-cooldown-seconds}`, `spawn.world`, `rtp.{enabled, world,
cooldown-seconds, min-radius, max-radius, use-world-spawn, center-x, center-z, max-attempts, search-timeout-seconds}`,
`portable.<name>.public`, `sit.{seat-height, seat-offset}`, `trash.{keep-ids, keep-rarity}`,
`near.{default-radius, max-radius}`. `/utility reload` re-reads everything except the permission defaults of the
portable commands and the registered commands themselves (restart for those).

## Data layout

* Profile section `utility` (`UtilityData`): `homes` (lower case name to `{world, x, y, z, yaw, pitch, created}`, creation
  order) and `tpaDisabled`.
* `back.json` at the data root: unchanged format (`BackService`).
* Nothing else is persisted: tpa requests, combat tags, cooldowns (rtp, tpa) and seats are in memory. An `rtp` cooldown
  therefore resets on a restart (it is a convenience limit, not an economy limit).

## API / events

* Publishes `TeleportService` through `core.register` (class lives in the utility package, so other features cannot import
  it; it exists for tests and the foundation owner). No `api/` interface is implemented and no event is fired.
* Consumes lazily: `IslandApi` (`canBuild`, `teleportHub`), `DungeonApi.inDungeon`, `Items` (trash keep rules), `Guis`,
  `PlayerProfiles`.
* `TeleportGate` (package `back`): the seam /back uses; `TeleportService` implements it.

## Resource pack

No custom items and no pack art: the menus use vanilla bed icons as requested. Nothing was added to `resource-pack/`.

## Known gaps / NOT verified in game

* Nothing was run on a real Paper 26.3 server or client. MockBukkit has no `teleportAsync` or async chunk loading, so
  tests replace those two calls by synchronous versions (`Platform.useSynchronous`); the real `teleportAsync` /
  `getChunkAtAsync` paths are untested.
* `/sit`: whether a player riding an `Interaction` entity looks seated at the right height needs eyes on a real client.
  Tune `sit.seat-height` / `sit.seat-offset`. "Dismount on move" is the vanilla sneak dismount (a rider cannot walk);
  there is no custom movement detection.
* `/rtp` was tested against MockBukkit's flat blocks, not against real terrain (trees, oceans, caves). The rules reject
  liquids, leaves, ice, hazards and blocked bodies and require height >= sea level - 1; extreme biomes are unchecked.
* The combat tag is PvP only by default (`combat.include-mobs: false`); damage from environment (fall, lava) never
  tags. The tag is announced in chat only: the action bar belongs to the items feature.
* The combat tag is PARTIAL: only the utility commands (and `/back`) consult it. `/is`, hub items and the menu feature
  teleport players themselves. The gate is published as `core.service(TeleportGate.class)` (`back.TeleportGate`, a
  foundation interface any feature may import): `island` and `menu` can call `denial(player, destination)` before
  routing a player, or `teleport(...)` to get the warm-up too. A global `PlayerTeleportEvent` interceptor was
  rejected on purpose: void rescue, dungeon exits and join routing use the same cause and must never be blocked.
  `TeleportService` is also registered, for this feature's tests only.
* Tpa onto an island does not ask the island feature anything itself. A banned or locked-out player is stopped by the
  island feature's own `PlayerTeleportEvent` entry rules (`IslandTracker.onTeleport`, branch `sb/island`); utility then
  says "The destination refused the teleport." and starts no cooldown. This depends on the island feature being merged;
  tested here only with a stand-in listener. Non-members may otherwise visit a friend's island by request.
* Safe spot checks use real collision boxes (slab, stair, carpet, path, chest floors work). Only the pure geometry and
  a shape stand-in are unit tested (MockBukkit cannot describe shapes): slab and stair floors are NOT verified in game.
* `/home` and the tpa teleport do not search a safe spot: an unsafe home is refused with an explanation.
* The anti-spam send cooldown of /tpa survives a relog but not a restart.
* `/playtime` and `/seen` for offline players depend on `Bukkit.getOfflinePlayerIfCached` (a name the server never saw
  is "unknown") and read statistics off the main thread.
* The warm-up title (`teleport.show-title`) is a Title, not the action bar (the items feature owns the action bar).

## NEEDS

* `island` and `menu` should consult `core.service(TeleportGate.class)` (lazy lookup, may be empty) before they teleport
  a player on their own; until they do, the combat tag does not apply to them. An `api/TeleportApi` is not needed.
* The foundation owner should keep `plugin.yml` `back` untouched; no other change is required.
