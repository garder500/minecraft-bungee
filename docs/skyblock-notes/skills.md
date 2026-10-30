# Skyblock feature: skills, collections and collection-gated recipes

Package `win.vampyuyu.survival.skills`, feature id `skills`, profile section `skills`, config `skills.yml`,
service `SkillsApi`. Everything user-facing is English.

## What players get

* **8 skills** (the core enum): Combat, Mining, Farming, Foraging, Fishing, Enchanting, Alchemy, Carpentry.
  Levels 0-60 (Carpentry 0-50). A smooth curve `50 * 1.2^(n-1)` rounded to 2 digits: level 10 is about 1.3k total
  XP, level 30 about 60k, level 60 about 14M. Each level pays coins (and a x5 bonus every 10th level) and adds
  stat bonuses through `StatsApi.addModifier("skill:<skill>", ...)`:

  | Skill | Stats per level (defaults) |
  |---|---|
  | Combat | Crit Chance 0.4%, Strength 0.5 (0.75 after 20) |
  | Mining | Mining Fortune 2 (3 after 20), Defense 1 (2 after 14) |
  | Farming | Farming Fortune 2/3, Health 2 to 5 |
  | Foraging | Foraging Fortune 2/3, Strength 1 (0.5 after 14) |
  | Fishing | Health 2 to 5 |
  | Enchanting | Intelligence 1 (2 after 14) |
  | Alchemy | Intelligence 1 (2 after 14) |
  | Carpentry | Health 1 |

  Level-ups use chat (rewards box), a title, a sound and particles; milestone levels (every 10th) get the louder
  version. The skills feature never writes the action bar (the `items` feature shows XP popups from `SkillXpEvent`).
* **XP sources** (all numbers in `skills.yml`, section `xp`):
  * Mining / Foraging / Farming: breaking blocks. Player-placed resource blocks (ores, stone, logs, pumpkins...) give
    nothing (see "Anti-exploit"). Crops give XP only when fully grown (`xp.farming.mature_only`) and are NOT
    remembered when planted, so a field you planted yourself pays when ripe. Sugar cane, cactus and bamboo
    (`xp.farming.column_plants`) pay for every grown block of the column when you break the base; a hand-placed block
    pays nothing.
  * Combat: player kills by entity type (other hostile mobs: max health x 0.4, capped at 250 per kill).
  * Fishing: every catch by item, plus sea creature kills. A catch can summon a sea creature (squid, drowned,
    glow squid, guardian, elder guardian, `sea_creatures` in `skills.yml`): chance = base + Fishing level + the
    `SEA_CREATURE_CHANCE` stat, level-gated table, scaled health and a custom name, at most 3 alive per fisher, they
    vanish after 2 minutes and when the fisher quits. The kill pays Fishing XP and its drops count in collections.
  * Enchanting: enchanting table, by cost level and enchantment levels, and taking an anvil result (per level of the
    anvil cost, only if the levels were really paid). Alchemy: potions changed in a brewing stand (credited to the
    last player who opened it, and only while they are in the same world within `xp.alchemy.owner_radius` blocks,
    by ingredient). Carpentry: vanilla crafts by result item.
  * Enchanted resources crafted give Enchanting XP (per recipe `xp`).
* **Collections** (56 in 5 categories): block drops, mob drops and fishing catches count (not placed blocks, not
  immature crops, not spawner mobs, never items a player dropped and picked up again, because pickups are not counted at
  all). Block drops are read at the lowest priority, before other features edit or take them, so Telekinesis and
  similar do not hide a drop from the collection (extras another feature adds in place count too). Each tier pays
  coins once and unlocks recipes.
* **Fortune**: the `items` feature owns Mining/Farming/Foraging Fortune on block drops (it applies the stat to
  `BlockDropItemEvent`). `fortune.enabled` is therefore OFF by default here; turning it on while the items feature
  runs would multiply every drop twice. It exists for a server that runs skills without the items feature (Silk Touch
  tools get nothing, every drop type is multiplied, also logs and ripe crops).
* **Recipes**: 53 collection-gated shapeless recipes for `enchanted_*` resources (5 slots x 32 of the raw item, tier 2
  "compacted" ones 5 slots x 16 of the tier 1 item, for example Enchanted Diamond Block). Until the collection tier is
  reached the result slot shows a red "Recipe locked" barrier with the requirement. Recipes are added to the recipe book
  when unlocked. The result item's lore spells out the real cost ("5 slots x 32 Diamond") because the vanilla recipe
  book only shows one item per slot.
* **Recipe viewer** (`/recipes`): every recipe with its lock state; a detail view shows the 3x3 grid with the real
  per-slot amounts, the requirements and the XP, and a **Quick craft** button (also `/recipes craft <recipe>`) that
  takes the ingredients straight from the inventory (planned on a copy, result room checked, then applied in one
  step; nothing is taken when anything is missing, locked or the result does not fit). A collection tier icon opens
  the recipe it unlocks.
* **Enchanted resources keep none of the vanilla behaviour** (`EnchantedGuard`, `enchanted_guard.enabled`): they
  cannot be placed, planted, eaten, burned, smelted, thrown, dispensed, fed to animals, composted, brewed or used in
  vanilla recipes (namespace `minecraft`; recipes of other plugins still accept them). Right-clicking a chest or an NPC
  while holding one still works.
* **Menus and commands**: `/skills` (overview with progress bars, next-level rewards, bonuses so far; click a skill
  for every level over pages), `/collections` (categories, collections with next tier and unlocks, per-tier
  rewards; click a reached tier to claim coins that were waiting), `/skilltop` (cached leaderboards).

## Commands and permissions

| Command | Permission (default) | Notes |
|---|---|---|
| `/skills [skill]` (alias `/skill`) | `vampyuyu.sb.skills.use` (everyone) | overview or one skill |
| `/collections [category]` (alias `/collection`) | `vampyuyu.sb.skills.collections` (everyone) | |
| `/skilltop [skill\|average]` (alias `/skillboard`) | `vampyuyu.sb.skills.top` (everyone) | top 10 plus your own rank |
| `/recipes [recipe \| craft <recipe>]` (alias `/sbrecipes`) | `vampyuyu.sb.skills.recipes` (everyone) | recipe viewer, quick craft |
| `/skilladmin ...` | `vampyuyu.sb.skills.admin` (OP) | below |

`/skilladmin`: `setxp|addxp|setlevel <player> <skill> <n>`, `reset <player> [skill]`,
`setcollection <player> <COLLECTION> <amount>`, `info <player>`, `boost <multiplier> <minutes|0> [skill|all] [name]`,
`boosts`, `unboost <name|all>`, `reload`. `setxp/setlevel/setcollection/reset` work on offline players that joined this
server (the profile is edited off the main thread). They never pay coins: reached levels and tiers are marked as
claimed, higher ones are un-claimed (so earning them later pays them). `addxp` needs an online player and goes through
the normal pipeline (multipliers, `SkillXpEvent`, level-ups).

## XP boosts (hook for pets, accessories, events)

Pipeline of one grant: `amount x xp_multiplier x skills.<s>.multiplier x XpBoosts` then `SkillXpEvent`, then levels.

* Other features (pets, accessories, potions): call `SkillsApi.addXp` for their own XP, and boost somebody else's XP
  by listening to `SkillXpEvent` and calling `event.setAmount(...)` (it runs after the multipliers above; negative or
  NaN means nothing is granted; one grant is capped at 1e9).
* Config: `xp_multiplier` (global) and `skills.<key>.multiplier`.
* Internal modifier list (in memory, cleared on restart): `SkillsService.boosts()` (`XpBoosts`), named boosts per skill
  or for all, optional expiry, additive stacking (`1 + sum(m - 1)`, cap `boosts.max_total`). Driven by
  `/skilladmin boost`. Not exposed through `api/` (no contract change); a feature that needs it can look the
  `SkillsApi` service up and cast it only if the foundation owner promotes `XpBoosts` to an API.

## Tags other features can set (entity PDC, namespace `vampyuyu`, byte)

* `no_skill_xp`: this mob gives no XP and no collections. Always wins.
* `skill_xp`: give XP even though its spawn reason is blocked (plugin spawns have reason CUSTOM, which is blocked by
  default so bosses, dungeon mobs and quest mobs cannot be farmed for Combat XP; those features award XP with
  `SkillsApi.addXp(player, skill, amount, "boss:<id>")` instead). It can be set at any time before the kill, also
  after `spawn()` returned: the spawn handler only writes a private marker (`skills_spawn_blocked`) and the decision
  is taken at death. Order at death: `no_skill_xp` refuses, else a blocked spawn without `skill_xp` refuses.
* `sea_creature`: the kill gives Fishing XP (`xp.fishing.sea_creatures`) instead of Combat XP. This feature's own
  `SeaCreatures` sets it (with `skill_xp`) on the mobs it spawns; another feature may tag its own.

## Anti-exploit summary

* Placed blocks: a bit per block in each chunk's own PDC (`vampyuyu:skills_placed`, `long[]`). Every change is
  written to the chunk's PDC right after the event that made it (write-through), because each mark change goes with a
  block change in the same chunk, which already queues that chunk for the next save: an autosave between the two can
  not write the chunk without its marks, and a crash loses nothing the chunk save would have kept. Unload hands the
  event's own chunk to the backend; a world unload drops that world's cache. Marks follow pistons and falling blocks
  and are cleared by explosions and fire. Tracked types = every block with XP or a collection, minus the crops of
  `xp.farming.mature_only` (+ `placed_tracking.extra`).
* Same-spot diminishing returns for mobs (cell 4x4x4, 25 free kills per 2 minutes, then -4% each, floor 10%) and for
  blocks (same block, 4 free per 30 s, then -20% each, floor 10%): cobblestone generators and grinders.
* Block XP and collection are decided in `BlockBreakEvent` and consumed by the `BlockDropItemEvent` of the same break
  (what the block dropped is snapshotted at LOWEST, paid at MONITOR); a drop event without a break gets nothing.
* Crafting: the vanilla craft of our recipes is cancelled; the cost is taken by hand (cursor click, shift click with
  a capacity check on a scratch inventory), number keys, drops and double clicks are refused, crafter blocks are refused.
  Carpentry XP counts items that really reached the inventory.
* Creative and spectator players, non-Skyblock worlds (`only_skyblock_worlds`, `blocked_worlds`) earn nothing.
* Coins are paid once (`claimed` set). With no economy service they stay pending and are paid at the next join or
  `Pending rewards` click.

## Config keys (`skills.yml`)

`only_skyblock_worlds`, `blocked_worlds`, `xp_multiplier`, `boosts.max_total`, `levels.{default_max,base,growth,
significant_digits,xp_table}`, `rewards.coins.*`, `average.skills`, `skills.<skill>.{max_level,multiplier,stats.<STAT>[],
unlocks.<level>}`, `xp.mining|foraging|farming.blocks`, `xp.farming.mature_only`,
`xp.combat.{mobs,health_factor,max_per_kill,blocked_entities,blocked_spawn_reasons}`,
`xp.fishing.{catches,default_catch,sea_creatures,default_sea_creature}`, `xp.enchanting.{per_cost_level,
per_enchant_level,anvil_per_level}`, `xp.alchemy.{owner_radius,ingredients,default}`, `xp.farming.column_plants`,
`xp.carpentry.*`, `anti_farm.{mobs,blocks}`, `fortune.*` (off by default), `enchanted_guard.enabled`,
`sea_creatures.{enabled,base_chance,per_fishing_level,per_chance_stat,max_chance,max_alive_per_player,despawn_seconds,
creatures}`, `placed_tracking.extra`, `collections.{presets,items}`,
`enchanted_items`, `recipes`, `leaderboard.{refresh_seconds,size,max_files}`. A bad entry is skipped with a console
warning; `/skilladmin reload` applies the file (recipes are re-registered).

## Data layout

Profile section `skills` (`SkillsData`): `xp` (skill key to double), `collections` (collection id to long), `claimed`
(`lvl:<skill>:<n>`, `col:<ID>:<tier>`), `seenRecipes`. Nothing else is stored; placed-block marks live in chunk PDC.
Stat modifiers are re-applied on join (join handler at LOW priority, before the items feature) and removed on quit.

## Published items and ids

The skills feature owns the custom item ids `enchanted_*` (53, listed in `skills.yml` `enchanted_items`, textures from
`resource-pack/art/skills_icons.py`, item model `vampyuyu:skills/<id>`). An id the `items` feature already registered is
left alone. Recipe keys are `sbskills_<recipe id>` in the plugin namespace. Sell prices, minion use etc. belong to
other features: they can look the ids up with `core.items()`.

## API and events

Implements `SkillsApi` (published last in `enable()`); fires `SkillXpEvent` (cancellable, mutable) and
`SkillLevelUpEvent` (once per level gained, fired AFTER the stat modifiers of the new level were applied and the stats
recomputed, so a listener reading `StatsApi` sees the new values). Consumes optionally `StatsApi`, `EconomyApi` (lazy lookups, empty is fine).
Extra (not in `api/`) public surface: `SkillsService.grant(...)` returns the XP really added, `average(UUID)`.

## Known gaps / NOT verified in game

* Nothing was run on a real server or client. All tests are MockBukkit: the real crafting grid (PrepareItemCraftEvent
  result swap, locked barrier display, vanilla ExactChoice matching of plain vs custom stacks, recipe book), level-up
  title/sound/particles, menus on a real client (recipe viewer layout included), the PDC chunk store across a real
  chunk save and restart (the write-through design rests on "a mark change always comes with a block change", not on
  a Paper "needs saving" call, which the API does not have), piston and falling block events with real world physics,
  the anvil Enchanting XP (needs a real anvil inventory), the guard on real furnaces, dispensers, brewing stands and
  composters, and the sea creature spawn on a real bobber (spawn location, AI, drops) were not exercised.
* The level-30 sea creature "Abyssal Elder" is a vanilla Elder Guardian: its curse (Mining Fatigue) hits players near
  it. Remove it from `sea_creatures.creatures` if that is unwanted.
* Recipe shells use `ExactChoice`: a custom item whose definition changed after it was created (name or lore edited,
  and the recipe lines now written in the result lore) stops matching until it is re-created.
* Fortune on blocks belongs to the items feature (`fortune.enabled: false` here); the two must not both be on.
* Brewing XP is credited to the last player who opened the stand within `owner_radius` (hopper-fed stands included);
  there is no real ownership of a stand.
* Grindstone, smithing and custom-enchant XP are not sources; the items feature can call `SkillsApi.addXp`.
* Cobblestone generators still give reduced XP and full collection (diminishing returns only on XP).
* Level-up unlock texts are flavour only (they no longer promise mechanics). No gameplay perk beyond stats, coins and
  recipes exists per level; "access" tier rewards (spec) are not implemented, see NEEDS.
* No per-source XP breakdown in the skill menu (the profile only stores totals per skill).
* Leaderboard reads every `players/*.json` (max `leaderboard.max_files`) every 5 minutes; very large servers should
  raise the period.
* Offline `/skilladmin info` reads the profile on an async thread through `PlayerProfiles.section`.
* The Carpentry skill is only fed by vanilla crafts of wood items; furniture/minion crafts need to call `addXp`.
* Collections do not count pickups (rejected on purpose, see above); minion output, boss/dungeon loot and shop
  purchases therefore never count until a NEEDS item below exists.

## NEEDS (contract changes I did not make)

* `SkillsApi.addCollection(UUID player, String collectionId, long amount)` (and a read `long collection(UUID, String)`):
  so minions, boss, dungeon and economy loot can feed collections without pickups being countable. Today the
  implementation has `SkillsService.addCollection(Player, Material, long)`, which other features cannot reach
  (no package imports between features).
* "Access" collection/level rewards (spec): needs either a permission/flag query on `api/` (for example
  `SkillsApi.hasUnlocked(UUID, String key)`) or an agreement on permission nodes granted by this feature through
  `PermissionAttachment`. Not done: nothing in the other features consumes it yet.
* Optional: promote `XpBoosts` (named multipliers) to `api/` if pets/accessories want persistent boosts instead of
  `SkillXpEvent` listeners.
* Optional: an `api/` hook so the items feature can render XP popups with the multiplier breakdown (today only the
  final amount is in `SkillXpEvent`).
