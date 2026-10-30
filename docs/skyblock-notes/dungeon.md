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
* **Boss**: `BossApi.spawn("crypt_lord", ...)` when the boss feature has that id (looked up lazily); otherwise a scripted
  Crypt Lord (phases at 66% and 33% that summon adds, ground slam, boss bar). Boss timeout `boss-timeout-minutes`.
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
* **Entrance hint**: standing within `entrance.radius` of the configured hub point shows a clickable
  "enter the Catacombs" line. The island feature owns the hub: its signposts should point at
  `dungeon.yml` `entrance.x/y/z` in world `entrance.world` (default `sb_hub`, 0.5 / 80 / 24.5); the action is `/dungeon`.

## Commands and permissions

| Command | Permission (default) |
|---|---|
| `/dungeon [start <floor>\|leave\|floors\|stats\|top [floor]\|claim\|help]` | `vampyuyu.sb.dungeon.enter` (TRUE) |
| `/dungeon admin <list\|abort <id\|all>\|start <floor> [seed]\|cleanup\|reload>` | `vampyuyu.sb.dungeon.admin` (OP) |
| `/party ...`, `/pc` | `vampyuyu.sb.dungeon.party` (TRUE) |

`/dungeon admin start <floor> <seed>` replays a layout (same floor and seed = same dungeon, tested).

## Config keys (`dungeon.yml`)

Top level: `max-concurrent-runs`, `close-delay-seconds`, `run-timeout-minutes`, `boss-timeout-minutes`,
`ready-countdown-seconds`, `sidebar`, `darkness-effect`, `require-previous-floor`.
Sections: `party.*` (max-size, invite-expire-seconds, ready-check-seconds, warp-cooldown-seconds, members-can-invite),
`generation.*` (max-ms-per-tick, base-y, blocks-per-check), `entrance.*`, `rules.*` (deaths-allowed, ghost-seconds,
revive-seconds, revive-health-percent, void-damage, allowed-commands), `mobs.*` (max-per-room, max-per-run, party scales,
watcher guards), `shop.*` (prices, flask), `scoring.*`, `rewards.tiers` and `rewards.items`, and `floors.<n>` (name,
palette, grid, rooms, puzzles, traps, secret-rooms, mini-bosses, shop, fairy, wither-doors, loops, hp, damage, mob-level,
par-seconds, secrets-needed-percent, reward, min-combat-level). Invalid values are clamped and listed in the log.

## Data layout

* Profile section `dungeon` (`DungeonData`): `floors.<n>` stats, `pending` reward items, totals, highest floor cleared.
* `plugins/VampyuyuSurvival/dungeon/leaderboard.json` (written off the main thread after a cleared run).
* Worlds: `<world container>/sb_dungeon_<n>`, created per run and deleted after it. Folders named exactly
  `sb_dungeon_<digits>` that no loaded world uses are removed at enable and at disable (never anything else, symlinks
  are never followed: `WorldFiles`).
* Custom items (ids, pack models `vampyuyu:dungeon/<name>`): `dungeon_bone_fragment`, `dungeon_crypt_shard`,
  `dungeon_wither_essence`, `dungeon_spirit_dust`, `dungeon_relic`, `dungeon_dark_scroll`, `dungeon_spirit_leaf`,
  `dungeon_healing_flask`. Icons: `python3 resource-pack/art/dungeon_icons.py` (deterministic).

## API and events

* Publishes `DungeonApi.inDungeon(Player)`: **world based**, true for anyone standing in a `sb_dungeon_*` world
  (participant, ghost, admin). Also registers its own `DungeonService` (tests and the feature's menus, not for other
  features).
* Consumes (all lazy, all optional): `IslandApi.teleportHub` (everybody leaves through it), `EconomyApi`, `SkillsApi`,
  `BossApi`. Consumes `BossKillEvent` (boss id `crypt_lord` in a dungeon world ends the run as a win).
* Dungeon entities carry persistent data (`dungeon_run`, `dungeon_room`, `dungeon_mob`, `dungeon_kind`,
  `dungeon_damage`, `dungeon_level`, `dungeon_npc`) so other features can recognise them.
* Never writes the action bar (the items feature owns the HUD). It uses a sidebar scoreboard, boss bars, titles, chat.

## NEEDS (contract changes the foundation owner may want)

* `BossApi.spawn` returns a boolean only: there is no handle on the spawned boss and no despawn. A run therefore detects
  the win through `BossKillEvent` (boss id and world), and a boss that despawns or never reports ends by timeout.
  A `BossApi.spawn(...)` returning the entity (or a `despawn(UUID)`) would let the dungeon clean up and track it.
* The menu feature draws its own sidebar; during a run the dungeon replaces the player's scoreboard and restores the
  previous one on exit. If the menu refreshes its board by re-assigning it, the two will fight: an API to "suspend the
  sidebar while `inDungeon`" is the clean fix.

* Join backstop: the menu router should send a login inside a dungeon world without a run (`DungeonApi.inDungeon`
  true but no run) to the hub, and reset a leftover SPECTATOR game mode. The dungeon feature cannot do it itself.
* Command labels `/dungeon` (`/dg`, `/catacombs`), `/party` (`/dparty`), `/pc` (`/partychat`): checked against the
  `plugin.yml` commands of arena (`eventadmin`, `arena`, `ww`), lobby (`lobbybuild`) and network (`warp*`, `profil`,
  `moneyadmin`, `menu`, `fly`, `invsee`, `exitchat`, `vampyuyuoperator`): no clash. No Velocity plugin source is in the
  repo, so a proxy-side `/party` was not checked.

## Known gaps / NOT verified in game

Everything below was only covered by unit and MockBukkit tests (169 tests in the module, all green); none of it was played:

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
* Quitting mid-run: the player's game mode, sidebar and effects are restored and they are teleported out (hub through
  `IslandApi`, else the first non-dungeon world's spawn) **inside `PlayerQuitEvent`**. A teleport during the quit event
  is unverified on real Paper. The dungeon never teleports anybody on join (the menu feature is the only join router).
* Crash case: a player saved as a SPECTATOR ghost inside a dungeon world whose folder is deleted at the next enable logs
  into the default world still in spectator mode (nothing on the dungeon side can run at that time).
* Party warp uses `teleportAsync` with no check of the island protection rules.
* Balance (health, damage, scoring, prices) is a first pass: everything is in `dungeon.yml`.
* Leaving a run early records nothing (neither a clear nor a failed run); only players still in at the end get stats.
