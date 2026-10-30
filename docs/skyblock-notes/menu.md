# Feature: menu (survival-plugin, package `win.vampyuyu.survival.menu`)

The player's guide through Skyblock and the only join router. Config: `menu.yml` (every key commented, reload with
`/sbmenu reload`).

## What players get

* **Join router**: on join the feature reads the cross-server key `sb:transfer:<uuid>` (Redis `GETDEL`, short
  timeouts, back-off after a failure so a dead Redis never delays a login). Skyblock key -> own island, or `sb_hub`
  with a clickable "create your island" prompt when there is none. Dungeon key -> rejoin only if
  `DungeonApi.inDungeon`, otherwise the plain survival spawn. **No key = the player is not moved**
  (`join.no-key: stay`; `hub` is available as an option). Respawn in a `sb_*` world goes to the island (or hub),
  a bed or anchor always wins (`respawn.route`).
* **Skyblock Menu item** (nether star, model `vampyuyu:menu/skyblock_menu`): in the 9th hotbar slot while in a `sb_*`
  world, removed on leaving. It cannot be dropped, moved, swapped, put in containers, or lost on death (restored in
  the slot); right-click opens the menu. `/sbmenu` does the same.
* **Menu GUI**: profile head (stats via StatsApi, skills average via SkillsApi, coins via EconomyApi, island info),
  Skills, Collections, Pets, Accessories, Bazaar, Auction House, Island, Quests, Fairy Souls, Warps, Settings,
  Leaderboards, Recipe Book (plus Minions, Bank, Boss, Dungeon when those commands exist). Entries dispatch the other
  features' commands as the player (first existing and permitted alias from `commands:`); entries whose command does
  not exist are greyed out.
* **Warps**: Hub, My Island, Nether, End, public island warps (opt-in registry), visit a player's island, boss arena
  info. Warm-up with cancel on move or damage, cooldown, no cross-world cheat (an IslandApi visit check is used; there
  is no combat-tag API, so none is enforced).
* **Quests**: tutorial questline (create island, hub, wood, crafting, stone, crops, first kill, fishing, minion,
  coins, bazaar, auction, fairy souls, enchanting, skill level, nether, first boss) with prerequisites, and daily quests
  (3 a day from a pool of 25, deterministic per day and player, configurable reset time and zone, streak bonus).
  Progress comes from Bukkit events plus `SkillLevelUpEvent`, `BossKillEvent`, `IslandEnterEvent`, `CoinsChangeEvent`.
  Placed blocks do not count for break quests. Rewards: coins (EconomyApi), skill XP (SkillsApi), optional items (Items
  lookups). `/quests` GUI, tracked quest shown on the sidebar.
* **Fairy Souls**: glowing `ItemDisplay`s created per soul and shown only to players who have not found them
  (`Player.showEntity` / `hideEntity`, spawned only near players, non persistent). Walk up to collect. A milestone every
  5 souls pays coins and a permanent stat (`StatsApi.addModifier("fairy", ...)`, re-applied on join). `/fairy` shows
  progress and points a compass-like particle trail and action-free chat hint at the nearest uncollected soul.
  Fixed souls in `menu.yml`, generic island-relative offsets, and admin-added ones (`menu/fairy_souls.json`).
* **Sidebar**: private per-player scoreboard, one entry per line, a line is rewritten only when its text changes,
  refreshed every second: island date and clock, location name (config zones by world and box, else island/owner),
  purse, island members online, tracked quest, footer. Main scoreboard restored on leaving Skyblock, toggling off or
  disable. The action bar and the TAB list are never touched.
* **Settings**: sidebar, quest tracker, damage indicators (floating damage numbers), sounds. First join: welcome title
  and sounds.

## Commands and permissions

| Command (aliases) | Permission | Default |
| --- | --- | --- |
| `/sbmenu` (`/skyblockmenu`), `/sbmenu reload` | `vampyuyu.sb.menu.use`, reload needs `vampyuyu.sb.menu.admin` | TRUE / OP |
| `/quests [tutorial\|daily\|track <id>\|list]` | `vampyuyu.sb.menu.quests` | TRUE |
| `/fairy`, `/fairy admin add [id]\|remove <id>\|list` | `vampyuyu.sb.menu.fairy`, admin needs `vampyuyu.sb.menu.admin` | TRUE / OP |
| `/sbwarps` (`/skyblockwarps`) `[hub\|island\|nether\|end\|visit <p>\|public on\|off\|list [note]]`, `/hub` | `vampyuyu.sb.menu.warps` | TRUE |
| `/sbsettings [sidebar\|quest-tracker\|damage-indicators\|sounds] [on\|off]` | `vampyuyu.sb.menu.settings` | TRUE |

Tab completion on all of them.

Name clashes avoided on purpose: the network plugin (also loaded on the survival server) already owns `/menu` (world
selector), `/warp` and `/warps` (creative warps), so this feature uses `/sbmenu` and `/sbwarps` and registers no
`menu`, `warp` or `warps` alias. `/hub` is registered here (Velocity config defines no `/hub`).

## Handoff value format (`sb:transfer:<uuid>`)

Accepted (never throws, unknown shapes are tolerated):
* JSON `{"dest":"skyblock","arg":"hub|island","ts":<epoch millis>}`: only `dest` matters, `arg` and `ts` optional.
  The network plugin writes `{"dest":"skyblock"}` with a 30 s TTL.
* A bare word (`skyblock`, `dungeon`).
* `dest`: `skyblock` (aliases `hub`, `island`, `home`), `dungeon`. Anything else, empty, or another JSON shape counts as
  `skyblock` (the `sb:` prefix means "comes for Skyblock"). A `ts` older than `join.max-age-seconds` is ignored.

Redis connection: `join.redis.*`, defaulting to the arena plugin's env (`ARENA_REDIS_HOST`,
`ARENA_REDIS_PASSWORD_FILE`).

## Config keys (menu.yml)

`join.*`, `respawn.route`, `menu-item.*`, `commands.*`, `warps.*`, `sidebar.*` (title, footer, zones, default-zones),
`damage-indicators.*`, `sounds.*`, `fairy-souls.*` (view-distance, collect-radius, every, coins-per-milestone,
stat-cycle, glow-color, fixed, island), `quests.*` (daily reset-time, time-zone, count, bonus and streak, ignore reasons,
pool of 25, tutorial).

## Data layout

Profile sections (via `core.profiles()`): `menu` (settings, welcomed flag), `quests` (tutorial state, daily day key and
progress, streak, tracked quest, completed-once ledger), `fairy_souls` (found ids, milestones paid).
Files (via `core.store()`): `menu/public_warps.json`, `menu/fairy_souls.json` (admin souls).

## API / events

Publishes nothing new. Consumes `IslandApi`, `StatsApi`, `SkillsApi`, `EconomyApi`, `DungeonApi`, `BossApi` lazily
(`core.service(...)` may be empty) and the events `SkillLevelUpEvent`, `BossKillEvent`, `IslandEnterEvent`,
`CoinsChangeEvent`.

## Pack

`resource-pack/art/menu_icons.py` (stdlib, deterministic) draws `textures/item/menu/{skyblock_menu,fairy_soul}.png` and
their model and item definition JSON (`vampyuyu:menu/skyblock_menu`, `vampyuyu:menu/fairy_soul`). `make test-pack` passes.

## NEEDS

Nothing blocking. Wishes: a combat tag API (warps cannot refuse in combat), a `QuestHook` / generic quest event so
other features can add their own objectives without this feature knowing them.

## Known gaps / NOT verified in game

* Everything was verified with MockBukkit unit tests only; no real Paper or client run (no screenshots of the GUI,
  sidebar, soul displays, or the pack icons in game; the icons were only viewed as enlarged PNGs).
* MockBukkit does not implement `Score.customName`, so the sidebar's visible line text is not asserted; the line
  builder, zone logic and the no-rebuild behaviour are. On a server without per-score names the sidebar lines stay
  blank (fallback, logged nowhere).
* Redis `GETDEL` is exercised through a fake `HandoffSource`; the real Jedis path is untested against a live Redis.
* Per-player soul visibility uses `showEntity`/`hideEntity` with `visibleByDefault=false`; behaviour on chunk reload
  and across world changes is untested live.
* Quest objectives that depend on other features (minion crafted, bazaar and auction opened, enchanting) rely on
  generic Bukkit events or command dispatch and will only progress once those features are live.
* No Fairy Souls are seeded in `sb_hub` (`fairy-souls.fixed: []`): the hub layout is not known to this feature, so an admin
  places them standing on the spot with `/fairy admin add`. Island souls (8 offsets) work out of the box.
* Damage indicators are display entities near the victim; performance under mass combat was not measured.
