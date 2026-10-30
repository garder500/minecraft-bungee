# Feature: dungeon (survival-plugin, package `win.vampyuyu.survival.dungeon`)

Instanced, procedurally generated dungeons ("the Catacombs") for parties of 1 to 5, seven floors. Config:
`dungeon.yml` (every key commented, `/dungeon admin reload`). Implements `DungeonApi`.

## What players get

* **Party** (`/party`, `/pc`): create, invite, accept, deny, leave, kick, promote, disband, list, warp, chat (sticky
  with `/pc` or one-off `/pc <text>`), ready check (`readycheck`, `ready`, `notready`) and a menu (`/party` alone).
  Size 1 to `party.max-size` (5). Invites expire, a party with no member left disappears, the next member leads when
  the leader leaves, nobody can leave or be kicked during a run. **Parties live in memory only** and end with the
  server (quitting also leaves the party). Chat lines are plain text: a player cannot inject markup.
* **Floor menu** (`/dungeon`, alias `/dg`, `/catacombs`): 7 floors, each with a palette (stone brick, mossy, deepslate,
  blackstone, nether, end, crimson), grid size, room, puzzle and secret counts, monster level, health and damage
  multipliers, par time, reward multiplier, Combat level requirement (needs the skills feature) and "clear the previous
  floor" requirement. Buttons for party, leaderboard, record, reward claim, leave.
* **Leaving**: `/dungeon leave` (or the menu button) abandons the run. The last one to leave closes the run at once
  (its world slot and the party are free again, no 45 s wait). Leaving a run that is in progress (or quitting) records a
  failed run for that player (runs +1, their kills, secrets and deaths); leaving while the crypt is still being built
  records nothing and the player is never teleported in afterwards. The "Building floor" bar is hidden for everybody
  who leaves or when the run is aborted. A player found in a dungeon world without a run (crash) is told at login to
  type `/dungeon leave`, which sends them out and resets spectator/adventure mode.
* **Run**: the leader starts a floor (`/dungeon start <floor>`); a party of 2+ runs a ready check first. The crypt is
  generated off the main thread, the void world `sb_dungeon_<n>` is created, the blocks are placed a few ms per tick
  (progress shown on a boss bar), everybody is teleported to the entrance room, a countdown runs with the doors barred.
* **Rooms** (a grid of 32 block cells, code templates, no schematics): entrance (dais), mob rooms (waves, doors bar
  until cleared, some give a Wither Key), parkour trap rooms (stepping stones over the void, magma on deep floors), puzzle
  rooms, secret rooms behind a hidden lever, mini-boss rooms (Bone Warlord and adds), the blood room, fairy grotto
  (heal and 2 minute buffs for the team), merchant room (Spirit Leaf, Healing Flask for coins), boss room.
* **Puzzles**: Echo Levers (replay the lamp pattern), Pressure Plates (numbers written in different ways, in order),
  Crypt Tic-Tac-Toe (the crypt plays back, harder per floor), Three Weirdos (one tells the truth, wrong chests are
  traps), Boulder Path (push the boulder onto the gold block, reset button). Solving opens the caged reward chest.
* **Keys and doors**: Wither doors need a team Wither Key (earned in key-carrier mob rooms reachable before the door,
  the generator guarantees the floor is solvable). Secret doors open with a hidden lever (counts as a secret). The blood
  door opens with the blood key, forged when all of the Watcher's guards (sent progressively, one per newly visited
  room, the rest when the blood room is entered) are dead.
* **Boss**: by default a scripted Crypt Lord whose health and damage follow the floor multipliers and the party size
  (phases at 66% and 33% that summon adds, ground slam, boss bar). With `boss-external-from-floor: N` (default 0 =
  never) floors N and up use `BossApi.spawn("crypt_lord", ...)` when the boss feature has that id (looked up lazily);
  that boss has the fixed HP of `boss.yml` and does NOT follow the floor multipliers (see NEEDS). Boss timeout
  `boss-timeout-minutes`.
* **Deaths**: lethal damage never kills. The first `rules.deaths-allowed` (3) deaths make a ghost (spectator, body
  marker). A ghost returns by itself after `ghost-seconds`, or a teammate sneaks next to the body for `revive-seconds`,
  or a Spirit Leaf is used. The next death eliminates the player (spectator). Everybody eliminated or gone = failed.
  Nothing is dropped, ever. Falling into the void costs `rules.void-damage` and returns you to the last doorway.
* **Rules**: no pearls, chorus fruit, elytra, flight, teleport commands (all commands outside
  `rules.allowed-commands` are refused, namespaced forms included), no block break or place, no fire spread, no
  explosions griefing, no container or workstation use, no friendly fire, mobs never hurt each other.
  `vampyuyu.sb.dungeon.admin` bypasses the lock.
* **Score** (0-325, S+ to D): speed 100 (par time, decay per second), exploration 100 (rooms visited, secrets needed
  share), skill 100 (fights, puzzles, kills, minus a penalty per death), bonus 25 (no deaths, mini-bosses, all puzzles,
  all secrets). Thresholds in `scoring.thresholds`.
* **Rewards**: per grade coins and Combat XP times the floor multiplier (`EconomyApi.add`, `SkillsApi.addXp`, both
  optional) plus weighted item rolls (custom items or `mc:MATERIAL`) into the inventory; what does not fit goes to
  `/dungeon claim` (nothing is dropped, nothing is lost, a closed menu recaps the reward chest). Hidden and puzzle chests
  pay a small instant reward when opened (the chest vanishes: no real inventory, so no duplication).
* **Statistics**: per floor runs, clears, best time, best score and grade, S+ count, secrets, kills, deaths
  (`/dungeon stats`), and the leaderboard of the 10 fastest clears per floor (`/dungeon top`).
* **Catacombs level**: every cleared run adds Catacombs XP (a share, `catacombs.xp-share` 0.5, of the Combat XP paid);
  level n needs n^2 x `catacombs.level-base` (400) XP in total, up to `catacombs.max-level` (50). Each level adds
  `catacombs.reward-percent-per-level` (1%) to the coins and Combat XP of every clear (the level the run started with).
  Shown in `/dungeon stats`, the floor menu and the end screen (level-up line). Only the XP is stored.
* **Crypt Forge** (`/dungeon forge`, menu button, `/dungeon forge buy <trade>`): the sink for the dungeon materials.
  Default trades (all in `dungeon.yml` `forge.trades`): Spirit Leaf (16 bone fragments + 4 crypt shards), 3 Healing
  Flasks (12 bone fragments), Crypt Cache (12 crypt shards + 3 wither essence: 4 item rolls of the rank B table on the
  highest floor you cleared), Spirit Cache (12 essence + 4 spirit dust: 5 rolls, rank S table), Dark Scroll (5 relics +
  16 spirit dust), and two coin trades (64 bone fragments = 600 coins, 10 essence = 1500 coins; need the economy
  feature). A purchase checks everything, rolls, verifies the exact count, removes the payment and only then delivers
  (overflow goes to `/dungeon claim`): it cannot duplicate or lose items. Closed while you are in a run.
* **Entrance hint**: standing within `entrance.radius` of the configured hub point shows a clickable
  "enter the Catacombs" line. The island feature owns the hub: its signposts should point at
  `dungeon.yml` `entrance.x/y/z` in world `entrance.world` (default `sb_hub`, 33.5 / 101.0 / 0.5 = the "Dungeon Gate"
  pad of the island feature's hub); the action is `/dungeon`. ASSUMPTION taken from the island feature's work in
  progress (not verified in game): the pad is at x=33, z=0 and the hub height is 100, so the block above it is y=101.
  If the island feature moves the pad or the hub, change the three numbers.
* **End screen**: besides the score lines, one line per player (kills, secrets, deaths) and an MVP line (best of kills +
  5 x secrets - 3 x deaths, only with 2+ players and some activity). The sidebar lists teammates with their hearts.

## Commands and permissions

| Command | Permission (default) |
|---|---|
| `/dungeon [start <floor>\|leave\|floors\|stats\|top [floor]\|claim\|forge [buy <trade>]\|help]` | `vampyuyu.sb.dungeon.enter` (TRUE) |
| `/dungeon admin <list\|abort <id\|all>\|start <floor> [seed]\|cleanup\|reload>` | `vampyuyu.sb.dungeon.admin` (OP) |
| `/party ...`, `/pc` | `vampyuyu.sb.dungeon.party` (TRUE) |

`/dungeon admin start <floor> <seed>` replays a layout (same floor and seed = same dungeon, tested).

## Config keys (`dungeon.yml`)

Top level: `max-concurrent-runs`, `close-delay-seconds`, `run-timeout-minutes`, `boss-timeout-minutes`,
`boss-external-from-floor`,
`ready-countdown-seconds`, `sidebar`, `darkness-effect`, `require-previous-floor`.
Sections: `party.*` (max-size, invite-expire-seconds, ready-check-seconds, warp-cooldown-seconds, members-can-invite),
`generation.*` (max-ms-per-tick, base-y, blocks-per-check), `entrance.*`, `catacombs.*` (level-base, max-level, xp-share,
reward-percent-per-level), `forge.trades` (id, name, icon, cost map, and one of give+amount / cache-rolls+cache-grade /
coins), `rules.*` (deaths-allowed, ghost-seconds,
revive-seconds, revive-health-percent, void-damage, allowed-commands), `mobs.*` (max-per-room, max-per-run, party scales,
watcher guards), `shop.*` (prices, flask), `scoring.*`, `rewards.tiers` and `rewards.items`, and `floors.<n>` (name,
palette, grid, rooms, puzzles, traps, secret-rooms, mini-bosses, shop, fairy, wither-doors, loops, hp, damage, mob-level,
par-seconds, secrets-needed-percent, reward, min-combat-level). Invalid values are clamped and listed in the log.

## Data layout

* Profile section `dungeon` (`DungeonData`): `floors.<n>` stats, `pending` reward items, totals, highest floor cleared,
  `catacombsXp`.
* `plugins/VampyuyuSurvival/dungeon/leaderboard.json` (written off the main thread after a cleared run).
* Worlds: `<world container>/sb_dungeon_<n>`, created per run and deleted after it. Folders named exactly
  `sb_dungeon_<digits>` that no loaded world uses are removed at enable and at disable (never anything else, symlinks
  are never followed: `WorldFiles`).
* Custom items (ids, pack models `vampyuyu:dungeon/<name>`): `dungeon_bone_fragment`, `dungeon_crypt_shard`,
  `dungeon_wither_essence`, `dungeon_spirit_dust`, `dungeon_relic`, `dungeon_dark_scroll`, `dungeon_spirit_leaf`,
  `dungeon_healing_flask`. Icons: `python3 resource-pack/art/dungeon_icons.py` (deterministic).

## API and events

* Publishes `DungeonApi.inDungeon(Player)`: true for a participant (also a ghost) standing in a `sb_dungeon_*` world,
  and for an admin (`vampyuyu.sb.dungeon.admin`) standing there. A stray (a login into a dungeon world without a run)
  is NOT "in the dungeon", so the join router may handle them; `/dungeon leave` also gets them out. Also registers its own `DungeonService` (tests and the feature's menus, not for other
  features).
* Consumes (all lazy, all optional): `IslandApi.teleportHub` (everybody leaves through it; a `true` answer is trusted
  because the island feature teleports asynchronously, and the fallback to the hub world's spawn only runs if the
  player is STILL in a dungeon world about 2 s later; quit and disable move the player synchronously), `EconomyApi`, `SkillsApi`,
  `BossApi`. Consumes `BossKillEvent` (boss id `crypt_lord` in a dungeon world ends the run as a win).
* Dungeon entities carry persistent data (`dungeon_run`, `dungeon_room`, `dungeon_mob`, `dungeon_kind`,
  `dungeon_damage`, `dungeon_level`, `dungeon_npc`) so other features can recognise them.
* Never writes the action bar (the items feature owns the HUD). It uses a sidebar scoreboard, boss bars, titles, chat.

For the items and economy features: the material ids above are what the dungeon drops; the economy feature may
sell/buy them, the items feature may use them in recipes. The dungeon's own sinks are the forge trades.

## NEEDS (contract changes the foundation owner may want)

* `BossApi.spawn` returns a boolean only: there is no handle on the spawned boss and no despawn. A run therefore detects
  the win through `BossKillEvent` (boss id and world), and a boss that despawns or never reports ends by timeout.
  A `BossApi.spawn(...)` returning the entity (or a `despawn(UUID)`) would let the dungeon clean up and track it.
* The menu feature draws its own sidebar; during a run the dungeon replaces the player's scoreboard and restores the
  previous one on exit. If the menu refreshes its board by re-assigning it, the two will fight: an API to "suspend the
  sidebar while `inDungeon`" is the clean fix.

* Join backstop: the menu router should send a login inside a dungeon world without a run to the hub and reset a
  leftover SPECTATOR/ADVENTURE game mode. `DungeonApi.inDungeon` is now false for such a stray, but the menu's
  `JoinFlow` also tests `SkyblockWorlds.isDungeon(world)` itself in places (read in its work in progress), so a stray
  may still be left alone there: `/dungeon leave` is the escape. Exposing hub pads on `IslandApi` (`dungeon_gate`
  etc.) would let the dungeon read the entrance instead of `entrance.x/y/z`.
* `BossApi.spawn(bossId, location, player)` should take an hp scale and a damage scale (the boss feature's
  `BossSession` already has an hp override) or return a handle. Attribute changes on the spawned entity do nothing:
  the boss feature keeps its own health pool and caps the vanilla attribute at 1024. Until then the external
  Crypt Lord is opt-in (`boss-external-from-floor`).
* Command labels `/dungeon` (`/dg`, `/catacombs`), `/party` (`/dparty`), `/pc` (`/partychat`): checked against the
  `plugin.yml` commands of arena (`eventadmin`, `arena`, `ww`), lobby (`lobbybuild`) and network (`warp*`, `profil`,
  `moneyadmin`, `menu`, `fly`, `invsee`, `exitchat`, `vampyuyuoperator`): no clash. No Velocity plugin source is in the
  repo, so a proxy-side `/party` was not checked.

## Known gaps / NOT verified in game

Everything below was only covered by unit and MockBukkit tests (all green); none of it was played:

* Real world creation with the custom void generator on Paper 26.3, the typed `GameRules`, `unloadWorld` and the folder
  layout of custom worlds on 26.x (the deletion looks for the actual world folder and, as a fallback, for the name under
  the world container; orphan scan goes 4 levels deep).
* The block placer (chunk tickets, async chunk loads) and the time per tick on floor 7 (about 30 rooms). `max-ms-per-tick`
  bounds the placement, but the plan generation runs on an async thread and was only timed in unit tests.
* Real monsters: spawning, targeting (iron golems, witches), arrows, the scripted boss phases, the damage multipliers
  against the items feature's damage formula (the dungeon scales the vanilla hit; whoever changes damage at HIGHEST
  priority may reorder).
* Geometry by eye: doorways, lighting, the parkour pads, puzzle layouts, the reward cage, ghost bodies, boss bars and
  titles; no screenshots were taken.
* Ghost spectating inside the dungeon (spectator teleports to other worlds are refused) and the interplay with the menu
  feature's sidebar.
* Quitting mid-run: the player's game mode, sidebar and effects are restored and they are teleported out
  synchronously (the hub world's spawn, else the first non-dungeon world) **inside `PlayerQuitEvent`**. That synchronous
  teleport during the quit event is unverified on real Paper. The dungeon never teleports anybody on join (the menu
  feature is the only join router). There is NO reconnect: a disconnect is final for the run (a grace period to come
  back as a ghost is a possible extension).
* World creation runs on the main thread (Bukkit only creates worlds there) with `keepSpawnLoaded(FALSE)`; there is no
  `spawn_chunk_radius` game rule in the 26.3 typed `GameRules`. The creation time is logged when `debug: true`. There
  is no world pool (a world per run, deleted after): a pre-created idle world is a possible optimisation.
* Damage order: the floor multipliers run inside the HIGHEST `EntityDamageEvent` handler, after the items feature's
  formula (HIGH) and before the lethal check; tested with a stand-in HIGH listener. The balance numbers against the
  real items feature were not measured.
* Crash case: a player saved as a SPECTATOR ghost inside a dungeon world whose folder is deleted at the next enable logs
  into the default world still in spectator mode (nothing on the dungeon side can run at that time).
* Party warp uses `teleportAsync` with no check of the island protection rules.
* Balance (health, damage, scoring, prices) is a first pass: everything is in `dungeon.yml`.
* Still open from the review (not done, with the reason): player classes with active abilities and a party-composition
  bonus, a Heroic mode per floor, a weekly modifier, ice-slide puzzle and multi-cell or trap rooms (these need
  in-game screenshots to judge, CLAUDE.md step 3), a reconnect grace period, a world pool, a real sealed-chest opening
  screen with a reroll, a dungeon-only gear set, and a health perk from the Catacombs level (it would need a stat
  modifier removed on every exit path).
* Players who leave early get a failed run but share no reward even if they were there for most of the run.
* Room variety: one template per room type (plus random decoration), one path puzzle, no multi-cell rooms, no real
  trap rooms (dispensers, collapsing floors). Needs screenshots to do properly.
