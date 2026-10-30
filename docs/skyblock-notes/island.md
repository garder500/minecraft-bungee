# Island feature (package `win.vampyuyu.survival.island`)

The core of Skyblock: the four void worlds, the hub, the island grid, protection, co-op, upgrades, levels, warps.
Implements `IslandApi` and two extension interfaces (`IslandAltarApi`, `IslandUpgradesApi`, both in the island package).
All three are published last in `enable()` and are the same `IslandService` object.

## What players get

* `sb_hub`: a built-once void hub (spawn pad, plaza, fountain, avenues, ring road, four towers, ten NPC pads with
  signposts, island gate arch). Capped by `hub.max-blocks`. Idempotent: `island/hub.json` stores `{version, complete,
  hubY}`; a restart with a matching marker does not rebuild (but still marks the hub ready). The spawn pad (about 100
  blocks) is built first, the rest follows in batches.
* `sb_world` / `sb_nether` / `sb_end`: void worlds. Every island owns the same grid cell in all three (spiral allocation,
  cell 0,0 is reserved). Nether and End platforms are generated after the overworld island (batched, no stall).
* Five overworld templates chosen in a GUI at `/is create`: classic, desert, frozen, jungle, mushroom, each with a
  distinct starter chest (configurable). Nether platform: netherrack island, glowstone, fortress ruin with a chest (no
  spawner), lava pool, soul sand, nether wart, quartz ore. End platform: end stone island, six obsidian pillars,
  outpost with a chest, 5x5 obsidian dragon altar, exit gate back to the overworld.
* Portals stay inside the island dimensions: Nether portal sb_world <-> sb_nether, End portal sb_world <-> sb_end, same
  cell, 1:1 coordinates (no x8). Players never reach the creative or vanilla survival worlds. Gateways, entity portals and
  every other cause are cancelled. The vanilla End platform and the vanilla dragon fight are cancelled in `sb_end`
  (a dragon spawned with reason CUSTOM or COMMAND, as the boss feature does, is allowed).
* Roles: owner, co-owner, member (co-op), trusted guest (builds, no containers unless allowed), visitor (flags), banned.
* Settings GUI: pvp, visitor_interact, visitor_chests, mob_spawning, animals_spawning, fire_spread, tnt, weather, time
  lock (client-side per-player time), island lock.
* Protection: break, place, interact, buckets, containers, armor stands, item frames, vehicles, animals, explosions,
  pistons, liquid flow, hoppers, dispensers, fire, mob griefing, projectiles, spawning, PvP, plus the per-island radius
  (server-side move cancel + per-player WorldBorder), enter/leave events, void rescue (no death, message).
* Upgrades (persisted): radius tiers, member slots, minion slots (exposed via `IslandUpgradesApi`). Coins through
  `EconomyApi` (looked up lazily; without it a paid tier is refused and nothing changes).
* Level and `/is top`: weighted block values (`level.weights`) scanned in batches by `ScanJob`, cached on the island.

## Commands and permissions

`/island` (aliases `/is`, `/sb-island`), permission `vampyuyu.sb.island.use` (TRUE). No argument opens the menu.
Subcommands: `create [type]`, `home|go`, `sethome`, `visit <player>`, `invite|accept|deny`, `leave` (confirm by repeating),
`kick`, `ban`, `unban`, `trust`, `untrust`, `setrole <player> <co-owner|member>`, `members`, `settings`,
`flag <flag> <on|off>`, `time <time>`, `upgrades`, `upgrade <radius|members|minions>`, `reset [type]` (confirm by
repeating, cooldown), `info [player]`, `level`, `top`, `warp [set <name>|remove|list|<player>]`, `lock`, `unlock`,
`transfer <player>` (confirm), `border`, `hub`, `help`. Tab completion for all of them.

`/hub` goes to the hub spawn, permission `vampyuyu.sb.island.hub` (TRUE).
`/islandadmin` (alias `/isadmin`): `info|tp|level|rebuild|delete|list [player]`, permission `vampyuyu.sb.island.admin`
(OP). `delete` needs the command repeated.
`vampyuyu.sb.island.bypass` (OP): ignore protection, borders, bans and locks.
If another plugin owns `/hub` or `/is` on the server, ours stays reachable as `/vampyuyusurvival:hub` etc.

## Config (`island.yml`, every key is commented in the file)

`grid.cell-size|island-y|edge-margin`, `worlds.<hub|world|nether|end>.{time,border,weather-clear,gamerules}` (typed
`GameRules` names, lower case), `hub.{enabled,y,max-blocks,allow-interact}`, `generation.*`, `upgrades.{radius,members,
minions}` (list of `{radius|slots, cost}`, first tier free, radius clamped to `cell-size/2 - edge-margin`), `templates.<id>`
(`enabled,name,icon,biome,description,chest`), `starter-chest.*`, `platforms.*`, `protection.*`, `flags.defaults.*`,
`border.*`, `void.*`, `respawn.mode` (HOME or HUB), `portals.*`, `invites.*`, `reset.*`, `create.cooldown-minutes`,
`level.*` (divisor, interval, weights), `warps.*`, `commands.visit-cooldown-seconds`.

## Data layout

* `island/islands.json` (via `core.store()`): version, `nextIndex`, islands (id, owner, cell, template, state, home,
  members, trusted, banned, flags, timeLock, locked, upgrade tiers, value/level, warp...), free cells, dirty cells (cells
  of deleted islands, wiped before reuse; crash recovery wipes them at the next start), name cache. Written by one
  ordered IO thread, snapshot taken on the main thread, flushed synchronously on disable.
* `island/hub.json`: hub build marker.
* Profile section `island` (`PlayerIslandData`): starter chest claimed once, loot claimed once, lastLostAt (create
  cooldown), islandsCreated, lastIsland, borderVisible.

## API published

* `IslandApi` (as in `api/`): `islandAt`, `islandOf`, `isSkyblockWorld`, `canBuild`, `teleportHome`, `teleportHub`, `all`.
  `IslandInfo.center` is the overworld spawn of the cell seen from the world asked about; `radius` is the current tier.
* `IslandAltarApi`: `dragonAltar(islandId)`, `dragonAltarAt(location)`, `netherSpawn`, `endSpawn`, `hubSpawn()`,
  `hubPads()` (pad ids: merchant, banker, reforger, enchanter, quest_master, minion_vendor, boss_arena, fairy_souls,
  island_gate, dungeon_gate).
* `IslandUpgradesApi`: `minionSlotBonus(uuid)`, `radius(uuid)`, `memberSlots(uuid)`.
* Events fired (main thread): `IslandEnterEvent`, `IslandLeaveEvent` (on move, teleport, join, quit, world change).
* Consumed: `EconomyApi` (upgrades, lazy lookup).

### Altar convention (for features that cannot import the island package)

The dragon altar of an island is in `sb_end` at `x = cellX * cellSize + 0.5`, `z = cellZ * cellSize + 0.5`,
`y = grid.island-y + 1` (centre of the 5x5 obsidian slab, crying obsidian heart one block below the standing spot).
The island cell comes from `IslandInfo.cellX/cellZ` (`IslandApi.islandAt/islandOf`), `cellSize` is `grid.cell-size`
(default 400) in `plugins/VampyuyuSurvival/island.yml`.

## NEEDS (shared files, not done by this feature)

* Rule 4 forbids other features importing `IslandAltarApi` and `IslandUpgradesApi`. The foundation owner should add to
  `api/`: an `IslandApi.dragonAltar(UUID islandId)` (or a new `IslandLocationsApi`) and `int minionSlotBonus(UUID)`, and
  make `IslandService` implement them. Until then boss uses the coordinate convention above and minions cannot read the
  upgrade.
* The menu feature (join router) calls `IslandApi.teleportHub/teleportHome`. `teleportHub` returns false until the
  spawn pad exists (a few ticks after a first start); the router should retry or fall back.
* No resource-pack assets are used (menus are vanilla icons with tooltips).

## Known gaps / NOT verified in game

Verified only with JUnit + MockBukkit (53 tests: grid, roles matrix, config, registry round trip, invites, confirms,
portals, templates, hub plan, commands, protection events, upgrades guard, void rescue, menus opening and one click).
Not run on a real Paper 26.3 server:

* world creation with the void generator and typed game rules, the real chunk ticket / async chunk path of `PlanJob`
  (MockBukkit does not implement `addPluginChunkTicket`, so island generation itself never ran end to end), hub and
  platform appearance, biome painting, sign and chest filling;
* per-player `WorldBorder`, per-player time/weather, `teleportAsync` timing, void rescue by real falling;
* real portal behaviour: whether Paper accepts `setTo` into another world for End portals, and whether the vanilla
  exit portal or dragon fight is entirely suppressed in `sb_end` (`PortalCreateEvent` END_PLATFORM and dragon spawn are
  cancelled, other vanilla End structure code is untested);
* Nether portal creation inside the destination cell (relies on Paper's `setCanCreatePortal` with radius 8);
* the level scan performance on large islands, reset of a big island, crash recovery of dirty cells;
* a full (45000 block) hub build time, and the 200 block hub border vs the ring road;
* menu look and click feel in the real client; head icons are resolved by the client.
* `ENTITY` portals, ender pearls into the End from a cell and mob griefing special cases are covered by listeners but
  have no unit test.
* The block value of a placed block is counted, not the block's rarity: no per-chunk caching, a full rescan each time.
