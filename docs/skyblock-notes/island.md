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
  pistons, liquid flow, hoppers, dispensers, fire, mob griefing, projectiles, spawning, PvP, signs (edit/open), beds,
  item pickup/drop and fishing hooks, plus the per-island radius (server-side move cancel + per-player WorldBorder),
  enter/leave events, void rescue (no death, message).
  * `visitor_interact` is an allow-list: doors, trapdoors, fence gates, buttons, pressure plates, levers and pure GUI
    blocks (crafting table, ender chest, enchanting table, stonecutter, loom, cartography/smithing table, grindstone,
    bell). Anything else that reacts to a right-click (`Material.isInteractable`: repeaters, comparators, note blocks,
    daylight detectors, signs, cake, beds, anvils...) needs BUILD. Blocks that do nothing when clicked (and fences, listed only because of leads) need nothing, so
    eating, throwing and shooting at stone is never blocked. When a click is denied only because of the block (the held
    item is not a build tool), just the block use is denied: eating or drawing a bow while pointing at a repeater works.
  * Item pickup: visitors and banned players cannot take items lying on someone else's island unless they threw or own
    them (`protection.visitor-pickup`); visitors cannot drop items there (`protection.visitor-drop`). A fishing hook
    cannot pull items or animals out of another island, nor pull players where PvP is off.
  * Void rescue only cancels the void damage when it found somewhere to send the player; with no island and no hub pad
    the player takes the damage (and respawns normally) instead of falling forever. `hub.enabled: false` now means "no
    full hub, only the small spawn pad" so that fallback always exists.
* Upgrades (persisted): radius tiers, member slots. The "Minion slots" upgrade exists in the data and in
  `IslandUpgradesApi` but is **hidden and refused** (`upgrades.minions-enabled: false`) because no other feature can
  read it yet (see NEEDS). Coins through `EconomyApi` (looked up lazily; without it a paid tier is refused and nothing
  changes).
* Entry rules also apply after login: a player who was banned (or locked out) while offline and logs in standing on
  the island is moved out `protection.join-check-delay-ticks` (default 120) after joining. The delay is deliberate:
  the `menu` feature is the only join router, so its handoff gets time first; the check uses the island CELL, not the
  radius.
* `/island nether` and `/island end` (config `portals.shortcuts`, cooldown `portals.shortcut-cooldown-seconds`) go
  straight to the own platform once `platformsReady`.
* Level and `/is top`: weighted block values (`level.weights`) scanned in batches by `ScanJob`, cached on the island.

## Commands and permissions

`/island` (aliases `/is`, `/sb-island`), permission `vampyuyu.sb.island.use` (TRUE). No argument opens the menu.
Subcommands: `create [type]`, `home|go`, `sethome`, `visit <player>`, `invite|accept|deny`, `leave` (confirm by repeating),
`kick`, `ban`, `unban`, `trust`, `untrust`, `setrole <player> <co-owner|member>`, `members`, `settings`,
`flag <flag> <on|off>`, `time <time>`, `upgrades`, `upgrade <radius|members>` (`minions` once enabled), `nether`, `end`, `reset [type]` (confirm by
repeating, cooldown), `info [player]`, `level`, `top`, `warp [set <name>|remove|list|<player>]`, `lock`, `unlock`,
`transfer <player>` (confirm), `border`, `hub`, `help`. Tab completion for all of them.

`/hub` goes to the hub spawn, permission `vampyuyu.sb.island.hub` (TRUE).
`/islandadmin` (alias `/isadmin`): `info|tp|level|rebuild|platforms|delete|list [player]`, permission `vampyuyu.sb.island.admin`
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
New in the review pass: `generation.retries|retry-delay-ticks`, `upgrades.minions-enabled`,
`protection.visitor-pickup|visitor-drop|join-check-delay-ticks`. Removed: `reset.wipe-min-y|wipe-max-y` (a wipe and a
level scan always cover the full world height; old files may keep the keys, they are ignored).

## Island lifecycle (build, reset, delete, recovery)

* **Per-cell job order.** `PlanRunner.submit(lane, job)`: jobs of one lane run strictly in submission order, lanes run
  side by side (max 3 jobs per tick). Lane = world + cell, so a wipe or reset can never overlap a generation or scan of
  the same cell, and a `delete` waits for the plan still running. The hub has its own lane.
* **Build token.** `Island.generation` is bumped by create, reset, rebuild, platform rebuild and delete. Every callback
  (island READY, platforms, level scan, wipe retries) captures it and does nothing when it is stale, so an old platform
  job or scan cannot write into a reset island. It is also mixed into the generator seeds (a reset gives a slightly
  different island; a retried step of the same generation repeats exactly the same plan).
* **Owed first-time kit.** `create`/`reset` only mark `Island.kitOwed` / `lootOwed` (from the player's
  `starterClaimed` / `lootClaimed`) and remember that player in `Island.kitFor` / `lootFor`. That player (not whoever
  owns the island by then, a transfer can happen mid-build) is marked as having received the kit when the island turns
  READY (loot: when the platforms are ready). `rebuild` and crash recovery reuse the owed flags, so an interrupted first build is
  rebuilt with the real kit instead of the repeat chest. Window left: a crash between READY and the next profile save
  could let a deleted-and-recreated island give the kit twice.
* **Failures.** A failed wipe is retried (`generation.retries`, backoff); on exhaustion the island stays RESETTING, the
  coop and the online admins are told and nothing is built over it (`/islandadmin rebuild` or the next start retries).
  A failed overworld build or platform build is retried the same way and is reported at the end. A failed delete wipe
  keeps the cell parked (never reused dirty).
* **Recovery at start.** Dirty cells are wiped, unfinished islands rebuilt, and READY islands whose
  `platformsReady` is false get their Nether/End wiped and regenerated (`/islandadmin platforms <player>` does the
  same by hand).
* **Entities.** The wipe waits (up to 100 ticks) for the chunk's entities to load before sweeping them (chest
  minecarts, frames, dropped items), then removes every non-player entity of the area. Whole height, every Y.
* **Dungeon runs.** `/hub`, `/island home|hub|visit|warp|nether|end` and the menu buttons refuse while
  `DungeonApi.inDungeon(player)` (or the world is `sb_dungeon_*`). `IslandApi.teleportHome/teleportHub` stay unguarded
  on purpose: the dungeon and menu features use them to take players out of a run.

## Data layout

* `island/islands.json` (via `core.store()`): version, `nextIndex`, islands (id, owner, cell, template, state, home,
  members, trusted, banned, flags, timeLock, locked, upgrade tiers, value/level, warp, `generation`, `kitOwed`,
  `lootOwed`, `kitFor`, `lootFor`...), free cells, dirty cells (cells
  of deleted islands, wiped before reuse; crash recovery wipes them at the next start), name cache. Written by one
  ordered IO thread, snapshot taken on the main thread, flushed synchronously on disable.
* `island/hub.json`: hub build marker.
* Profile section `island` (`PlayerIslandData`): starter chest claimed once (set when the island is READY), loot claimed once (set when the platforms are READY), lastLostAt (create
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

Do NOT copy `grid.cell-size` / `island-y` out of `island.yml`. Everything is already in the shared `IslandApi`:

* `IslandApi.islandAt(location in sb_end)` returns an `IslandInfo` whose `center()` is `spot(0, 1, 0)` of the cell **in
  the world you asked about**, and the dragon altar (the standing spot on the 5x5 obsidian slab) is exactly that point:
  `END_ALTAR = {0, 1, 0}`. So the altar of the island the player stands in (in `sb_end`) is
  `islandAt(player.getLocation()).get().center()`.
* From an island seen from anywhere else: `islandOf(uuid).get().center()` is the **`sb_world` centre**, not the End
  one. The offsets are identical in every dimension, so take the same x/y/z and swap the world for
  `Bukkit.getWorld(SkyblockWorlds.END)`.
* `IslandInfo.cellX/cellZ` are also available if a feature needs the cell.

`IslandAltarApi` (in this package) stays the richer path (Nether and End spawns, hub pads) for when the foundation
exposes it.

## NEEDS (shared files, not done by this feature)

* Rule 4 forbids other features importing `IslandAltarApi` and `IslandUpgradesApi`. The foundation owner should add to
  `api/`: `IslandApi.int minionSlotBonus(UUID)` (and optionally `dragonAltar(UUID islandId)`), and make `IslandService`
  implement them (it already has the methods). The minions feature must then ADD that bonus to its `slotsFor`.
  Until both exist the "Minion slots" upgrade stays disabled (`upgrades.minions-enabled: false`); flip it to true only
  when the minions feature consumes the bonus.
* **World spawn.** `sb_world`, `sb_nether` and `sb_end` all have their Bukkit world spawn at `(0, island-y+1, 0)`, over
  the reserved cell 0,0, while every island is 400+ blocks away; Bukkit has one spawn per world, so the island feature
  cannot give a per-island spawn. Any feature that measures a distance to `world.getSpawnLocation()` (the boss summon
  rule `maxSpawnDistance` for instance) must measure to the island centre instead
  (`IslandApi.islandAt(location).get().center()`, see the altar convention above).
* **E2E scenario (`scripts/e2e_client.py`, shared file, not touched).** A `skyblock` scenario should: create an island
  with each template and screenshot the island, the Nether and End platforms and the hub; walk through both portals;
  jump into the void and check the rescue; `/is reset` right after READY; `/islandadmin delete` of an island with a
  chest at y=250 and check the next `/is create` on that cell starts clean; ban a player while offline and check the
  login eviction. None of this has run on a real server (see below).
* The menu feature (join router) calls `IslandApi.teleportHub/teleportHome`. `teleportHub` returns false until the
  spawn pad exists (a few ticks after a first start); the router should retry or fall back.
* No resource-pack assets are used (menus are vanilla icons with tooltips).

## Known gaps / NOT verified in game

Verified only with JUnit + MockBukkit (island tests: grid, roles matrix, config, registry round trip, invites,
confirms, portals, templates, hub plan, commands, protection events, upgrades guard, void rescue, menus, and the review
regression suite `IslandReviewFixesTest` with scripted block jobs). Not run on a real Paper 26.3 server:

* world creation with the void generator and typed game rules, the real chunk ticket / async chunk path of `PlanJob`,
  `WipeJob` and `ScanJob` (MockBukkit does not implement `addPluginChunkTicket`; the lifecycle tests drive scripted
  jobs, the real sweep, the full-height range beyond MockBukkit's 256 and `Chunk.isEntitiesLoaded` never ran), hub and
  platform appearance, biome painting, sign and chest filling;
* the real `Material.isInteractable()` list (the allow-list tests use MockBukkit's), `PlayerOpenSignEvent`,
  `PlayerBedEnterEvent` and real fishing hooks (only constructed events / a stand-in `Item` were used);
* per-player `WorldBorder`, per-player time/weather, `teleportAsync` timing, void rescue by real falling, the join-time
  entry check against the real join router;
* real portal behaviour: whether Paper accepts `setTo` into another world for End portals, and whether the vanilla
  exit portal or dragon fight is entirely suppressed in `sb_end`; Nether portal creation inside the destination cell;
* the level scan performance on large islands, reset of a big island, crash recovery of dirty cells; the wipe of a
  full-height island costs more chunk visits than before only where something was built;
* `hub.enabled: false` (pad-only hub) has no test; a full (45000 block) hub build time and the 200 block hub border;
* menu look and click feel in the real client. `ENTITY` portals, ender pearls and mob griefing special cases have no unit test;
* an island whose old data was built before the review keeps `generation = 1` and no owed flags: a platform rebuild of
  such an island uses the new seeds (a wipe of the Nether/End precedes it, so nothing is mixed);
* the block value of a placed block is counted, not the block's rarity: no per-chunk caching, a full rescan each time.
