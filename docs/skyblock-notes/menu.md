# Feature: menu (survival-plugin, package `win.vampyuyu.survival.menu`)

The player's guide through Skyblock and the only join router. Config: `menu.yml` (every key commented, reload with
`/sbmenu reload`).

## What players get

* **Join router**: on join the feature reads the cross-server key `sb:transfer:<uuid>` (Redis `GETDEL`, short
  timeouts, back-off after a failure so a dead Redis never delays a login). Skyblock key -> own island, or `sb_hub`
  with a clickable "create your island" prompt when there is none. **No key (or a stale one) = a normal join: the
  player is sent to `sb_hub`** (design section 12.3, `join.no-key: hub`, the default), unless they already stand in a
  Skyblock world, so returning players are never moved. `join.no-key: stay` is an opt-in that never moves anyone.
  A player inside a dungeon run is never moved (the router cannot rejoin a run, see NEEDS). When the hub is still
  generating, `teleportHub` is retried every 20 ticks for 10 seconds (stops on quit or when the player is in a
  Skyblock world). The create-island prompt is shown whenever a player without an island stands in `sb_hub` after
  routing, whether or not a move happened.
* **Respawns belong to the island feature** (`IslandTracker.onRespawn` checks the island is ready). `respawn.route`
  defaults to `none`. The opt-in values `island` / `hub` only teleport one tick AFTER the respawn through
  `IslandApi.teleportHome` / `teleportHub`; the respawn location is never edited. A bed or anchor always wins.
* **Skyblock Menu item** (nether star, model `vampyuyu:menu/skyblock_menu`): in the 9th hotbar slot while in a `sb_*`
  world, removed on leaving. It cannot be dropped, moved, swapped, put in containers, or lost on death (restored in
  the slot); right-click opens the menu. `/sbmenu` does the same.
* **Menu GUI**: profile head (stats via StatsApi, skills average via SkillsApi, coins via EconomyApi, island info;
  click runs `/stats`). Doors to the other features are laid out in order without gaps: Skills, Collections, Pets,
  Accessories, Bazaar, Auction House, Bank, Minions, Island (reads "Create Island" and runs `island create` while the
  player has none), Bosses, Slayer, Dungeons, Leaderboards, Recipe Book. A door whose commands do not exist at all
  (feature not installed) is hidden; one whose command exists but is forbidden to the player is greyed out. Aliases
  are in `commands:` (they match the real commands of the sibling branches: `/pets`, `/accessories` (`acc`, `bag`),
  `/bank`, `/minion`, `/boss`, `/slayer`, `/dungeon`, `/baltop`, `/skilltop`). **Leaderboards** opens a menu-owned
  submenu with `/baltop` and `/skilltop` unless a real `/leaderboards` command exists. Menu-owned entries: Quests,
  Fairy Souls (own GUI), Warps, Settings. The **Recipe Book** door stays hidden: no feature registers a recipe
  browser and the Core exposes no recipe list (see NEEDS).
* **Warps**: Hub, My Island, Nether, End (warm-up with cancel on move or damage, cooldown, no cross-world cheat; no
  combat-tag API exists, so none is enforced), boss arena info. **Public islands and visits are the island feature's**:
  the "Public Islands" button runs `/island warp`, "Visit a Player" suggests `/island visit <player>`,
  `/sbwarps visit [player]` and `/sbwarps public` forward to the same commands. The menu keeps no registry of its own
  (the old `menu/public_warps.json` is no longer read; delete it). `/hub` is registered by the island feature
  (permission `vampyuyu.sb.island.hub`), not here; the Warps GUI Hub button keeps the menu's warm-up.
* **Quests**: tutorial questline (create island, hub, wood, crafting, stone, crops, first kill, fishing, minion,
  coins, bazaar, auction, fairy souls, enchanting, skill level, nether, first boss) with prerequisites, and daily quests
  (3 a day from a pool of 25, deterministic per day and player, configurable reset time and zone, streak bonus).
  Progress comes from Bukkit events plus `SkillLevelUpEvent`, `BossKillEvent`, `IslandEnterEvent`, `CoinsChangeEvent`.
  `COMMAND` quests only count a command that exists and that the player may run (the event fires before dispatch).
  `CRAFT_ITEM` targets: `custom:<id prefix>` or `custom:re:<regex on the whole id>` (the minion quests use
  `minion_(?!fuel_|upgrade_|skin_|enchanted_|super_)[a-z_]+_[0-9]+`, so Enchanted Cobblestone is not a minion; a bad
  regex is reported at load and the quest skipped). Placed blocks do not count for break quests. Rewards: coins
  (EconomyApi), skill XP (SkillsApi), optional items (Items lookups). `/quests` GUI, tracked quest on the sidebar.
* **Fairy Souls**: glowing `ItemDisplay`s created per soul and shown only to players who have not found them
  (`Player.showEntity` / `hideEntity`, spawned only near players, non persistent). Walk up to collect. 43 souls ship:
  16 in the hub (plaza, plaza edge, ring road; coordinates derived from `island/HubPlan`, plaza surface y=100), 14 on
  the overworld island, 8 on the Nether platform, 5 on the End platform (island souls are offsets from the platform
  centre of the world named in `world:`; an `island` entry without `world` applies to any island world). A milestone
  every 5 souls pays coins (250 + 50 per earlier milestone) and a permanent stat from an 8 step cycle
  (`StatsApi.addModifier("fairy", ...)`, re-applied on join). The **Fairy Souls GUI** (slot 39 of the Skyblock Menu) shows found/total per zone with a progress bar, the next milestone and its reward, and a button that points
  at the nearest uncollected soul (chat hint, compass target, particle trail). `/fairy` still prints the hint.
  Admin souls: `/fairy admin add [id]` (kept in `menu/fairy_souls.json`).
* **Sidebar**: private per-player scoreboard, one entry per line, a line is rewritten only when its text changes,
  refreshed every second: island date and clock, location name (config zones by world and box, else island/owner),
  purse, island members online, tracked quest, server name (`sidebar.server-name`, default "Skyblock"), footer. Main
  scoreboard restored on leaving Skyblock, toggling off or disable. **The menu never touches a scoreboard it did not
  build**: in `sb_dungeon_*` worlds, during a run (`DungeonApi.inDungeon`) and whenever the player holds a board that is
  neither the main board nor one carrying our `sbside` objective (the dungeon feature's `dungeon` board, another
  plugin), it stays out. A board of ours handed back after a run is adopted, not replaced. The action bar and the TAB
  list are never touched.
* **Settings**: sidebar, quest tracker, sounds. First join: welcome title and sounds.
* **Damage numbers** are the items feature's (`items/combat/DamageIndicators`, `items.yml damage-indicators`); the menu
  ships none and no per-player toggle for them (see NEEDS).

## Commands and permissions

| Command (aliases) | Permission | Default |
| --- | --- | --- |
| `/sbmenu` (`/skyblockmenu`), `/sbmenu reload` | `vampyuyu.sb.menu.use`, reload needs `vampyuyu.sb.menu.admin` | TRUE / OP |
| `/quests [tutorial\|daily\|track <id>\|list]` | `vampyuyu.sb.menu.quests` | TRUE |
| `/fairy`, `/fairy admin add [id]\|remove <id>\|list` | `vampyuyu.sb.menu.fairy`, admin needs `vampyuyu.sb.menu.admin` | TRUE / OP |
| `/sbwarps` (`/skyblockwarps`) `[hub\|island\|nether\|end\|visit [player]\|public]` | `vampyuyu.sb.menu.warps` | TRUE |
| `/sbsettings [sidebar\|quest-tracker\|sounds] [on\|off]` | `vampyuyu.sb.menu.settings` | TRUE |

Tab completion on all of them.

Name clashes avoided on purpose: the network plugin (also loaded on the survival server) already owns `/menu` (world
selector), `/warp` and `/warps` (creative warps), so this feature uses `/sbmenu` and `/sbwarps`. `/hub` belongs to the
island feature, `/stats`, `/pets`, `/accessories` to items.

## Handoff value format (`sb:transfer:<uuid>`)

Written by the lobby side: the `sb/netmenu` branch (network-plugin, `docs/skyblock-notes/netmenu.md`, not merged into this
branch) writes `{"dest":"skyblock|island|hub","ts":<ms>}` with a 30 s TTL before the `Connect`. Nothing in this branch writes
it. Accepted (never throws, unknown shapes are tolerated):
* JSON `{"dest":"skyblock","arg":"hub|island","ts":<epoch millis>}`: only `dest` matters, `arg` and `ts` optional.
* A bare word (`skyblock`, `dungeon`).
* `dest`: `skyblock` (aliases `hub`, `island`, `home`), `dungeon`. Anything else, empty, or another JSON shape counts as
  `skyblock` (the `sb:` prefix means "comes for Skyblock"). A `ts` older than `join.max-age-seconds` is ignored.

Redis connection: `join.redis.*`, defaulting to the arena plugin's env (`ARENA_REDIS_HOST`,
`ARENA_REDIS_PASSWORD_FILE`).

## Config keys (menu.yml)

`join.*` (`no-key: hub|stay`, default hub), `respawn.route` (`none|island|hub`, default none), `menu-item.*`,
`commands.*` (door aliases, including `stats`, `baltop`, `skilltop`), `warps.*`, `sidebar.*` (title, server-name, footer,
zones, default-zones), `sounds.*`, `fairy-souls.*` (view-distance, collect-radius, every, coins-per-milestone,
coins-increase-per-milestone, stat-cycle, glow-color, fixed, island), `quests.*` (daily reset-time, time-zone, count,
bonus and streak, ignore reasons, pool of 25, tutorial).

## Data layout

Profile sections (via `core.profiles()`): `menu` (settings, welcomed flag), `quests` (tutorial state, daily day key and
progress, streak, tracked quest, completed-once ledger), `fairy_souls` (found ids, milestones paid).
Files (via `core.store()`): `menu/fairy_souls.json` (admin souls). `menu/public_warps.json` of earlier builds is
ignored.

## API / events

Publishes nothing new. Consumes `IslandApi`, `StatsApi`, `SkillsApi`, `EconomyApi`, `DungeonApi`, `BossApi` lazily
(`core.service(...)` may be empty) and the events `SkillLevelUpEvent`, `BossKillEvent`, `IslandEnterEvent`,
`CoinsChangeEvent`.

## Pack

`resource-pack/art/menu_icons.py` (stdlib, deterministic) draws `textures/item/menu/{skyblock_menu,fairy_soul}.png` and
their model and item definition JSON (`vampyuyu:menu/skyblock_menu`, `vampyuyu:menu/fairy_soul`). `make test-pack` passes.

## NEEDS

Nothing blocking. Requests for the foundation owner or the other features:

* `IslandApi.publicWarps()` (list of public warps with owner and name) so the menu could show them in its own GUI. Until
  then the Public Islands button runs `/island warp`.
* `DungeonApi.rejoin(Player)` (and a way to ask whether a run is rejoinable): the router never moves a player who is in a
  run, and a `dungeon` key leaves the player where the server put them; there is no rejoin.
* Items feature: a per-player damage indicator preference (profile section `items` or a setting hook) so the menu can
  offer a "Damage Indicators" toggle again. Sections are private, so the menu cannot flip it itself.
* A recipe list in the Core (or a `/recipes` command in a feature) to light up the hidden Recipe Book door.
* A combat tag API (warps cannot refuse in combat), and a `QuestHook` / generic quest event so other features can add
  their own objectives without this feature knowing them.
* A world-boss / party door: `/worldboss` and `/party` exist but the menu has no slot left for them (14 door slots).

## Known gaps / NOT verified in game

* Everything was verified with MockBukkit unit tests only; no real Paper or client run (no screenshots of the GUI,
  sidebar, soul displays, or the pack icons in game; the icons were only viewed as enlarged PNGs).
* MockBukkit does not implement `Score.customName`, so the sidebar's visible line text is not asserted; the line
  builder, zone logic, ownership of the board and the no-rebuild behaviour are. On a server without per-score names the
  sidebar lines stay blank (a warning is logged once).
* The dungeon sidebar interplay was checked against `sb/dungeon`'s `Sidebar` by reading it (objective `dungeon`, `hide`
  gives back the board captured on entry), not by running both features together.
* Redis `GETDEL` is exercised through a fake `HandoffSource`; the real Jedis path is untested against a live Redis.
* Fairy soul coordinates were computed from `HubPlan` and `IslandTemplates` (radius 11.5, Nether/End platforms of
  radius 15), not checked in game: some may sit inside a decoration or off an edge. Adjust with `fairy-souls.fixed` /
  `island` in `menu.yml` or `/fairy admin`. Nether/End island souls assume `islandAt` returns the platform centre in the
  Nether/End world.
* Per-player soul visibility uses `showEntity`/`hideEntity` with `visibleByDefault=false`; behaviour on chunk reload
  and across world changes is untested live.
* Quest objectives that depend on other features (minion crafted, bazaar and auction opened, enchanting) rely on
  generic Bukkit events or command dispatch and will only progress once those features are live. The minion regex
  assumes the id scheme `minion_<type>_<tier>` of `sb/minions`.
* Menu doors run commands with `performCommand`; commands that need arguments or are console only are not handled.
* The island feature's `IslandTracker` and this feature's respawn route were compared by reading; with the default
  `respawn.route: none` there is no overlap.
* `core.featureConfig("menu")` copies `menu.yml` out of the jar only once. A server that already ran an earlier build keeps
  its old `no-key: stay`, `respawn.route: island`, `custom:minion` targets and the 8 old souls; delete or regenerate
  `plugins/VampyuyuSurvival/menu.yml` (or update those keys) when upgrading, otherwise most of these fixes do nothing there.
* The sidebar yields to any foreign scoreboard. Only the arena plugin's Werewolf HUD sets one among the plugins on the
  survival server (read, not run), and it does so in arena games, not in Skyblock worlds.
* The hub souls assume `HubManager` builds the plan at x=0, z=0 in `sb_hub` (it passes 0, hubY, 0 to the plan job).
