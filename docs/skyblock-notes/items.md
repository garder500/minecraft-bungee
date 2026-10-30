# Items feature (`items`)

Package `win.vampyuyu.survival.items`, config `items.yml`, implements `StatsApi`, profile sections `items` (reserved),
`pets`, `accessories`. Pack art: `resource-pack/art/items_icons.py` (159 deterministic 16x16 icons: 151 registered items plus 8 pet species models).

## What players get

* **Stats**: held weapon or tool (main hand, only for the types in `stats.held-types`), the four armor pieces, the
  accessory bag, the active pet, full armor set bonuses and external modifiers (`StatsApi.addModifier`). Recomputed on
  equip, held-slot change, inventory click/drag/close/pickup/drop, swap, item break, join, respawn, world change and
  every modifier change, debounced every `stats.flush-ticks`; a full scan runs at most every 10 ticks and only touches
  players whose item signature changed. `StatsRecalculateEvent` is fired on each recompute.
  Applied to Paper attributes only in `sb_*` worlds: max health (real value up to 1024, client bar scaled to 20 hearts
  by `attributes.health-scale`), movement speed, attack speed, mining speed, vanilla armor zeroed (Defense replaces it).
  Modifier keys are unique and removed on quit and plugin disable. Health and mana are remembered as ratios when the
  attributes are removed (logout, leaving a Skyblock world) and restored on the next first apply, so relogging never
  heals; only a respawn or a player with no record starts full. Ability cooldowns and the Phoenix revive cooldown
  survive a relog (expiry timestamps in memory). An empty drill (fuel 0) contributes no stats.
* **Combat**: `damage = (weapon + 5) * (1 + Strength/100) * (crit: 1 + CritDamage/100) * enchant multipliers`, incoming
  damage is scaled then divided by `1 + Defense/100`, true damage for void/starvation/kill, ferocity strikes, ability
  damage (Intelligence, Ability Damage), floating TextDisplay damage numbers (pooled, short lived), optional mob health
  names. Formula hits ignore the victim's vanilla armor, Resistance and protection enchants
  (`EntityDamageEvent.DamageModifier` ARMOR / RESISTANCE / MAGIC are zeroed on non-player victims).
* **Mobs**: natural mobs listed in `mobs.table` get a level, an absolute max health and optional damage and a
  `[Lv3] Zombie 120/150` name; unlisted natural mobs keep the `combat.mob-health-multiplier` health. Opt-in for other
  features (dungeon, boss adds, minions) with no import: write the INTEGER PDC key `vampyuyu:sb_level` on the mob; it is
  scaled once on its first damage (health x multiplier x (1 + level x `mobs.health-per-level`), damage likewise). Mobs
  without the key and with a spawn reason outside `combat.scaled-spawn-reasons` are never touched.
* **Getting items** (no admin needed): vanilla crafting recipes for the starting gear (`recipes.*`, 32 recipes: Rookie
  Sword, Warrior's Blade, Miner's Pickaxe, Farmer's Hoe, Digger's Shovel, Grappling Hook, Warp Pearl, Hot Potato Book and
  the 24 tier 1 armor pieces; only in Skyblock worlds, never with custom items as ingredients; the same guards run for Crafter blocks, and the recipes are unlocked in the recipe book of players in Skyblock worlds) and loot tables
  (`loot.*`): boss kills (a `BossApi` loot hook registered a few ticks after enable, retried for 30 s), natural mob
  kills and fishing. Rows: high level books (VI/VII), the five ultimates, reforge stones, tier 2/3 armor, Recombobulator,
  Volta, Wither Blade, Titan Drill, pet eggs. Chances are multiplied by the killer's Magic Find, pet rows by Pet Luck.
  Spawner, spawn egg and summoned mobs never drop loot.
* **HUD**: the only action bar writer. `health/max  defense  mana/max (+overflow)`, notices (cooldown, not enough mana)
  and a 3 second skill XP popup from `SkillXpEvent`. Updated 4 times a second, resent only when the text changed (plus
  a keep-alive).
* **Mana**: Intelligence is the pool, regenerates a percent per second, abilities spend regular mana first then overflow
  mana, overflow decays.
* **Catalog** (151 registered items, asserted by a test; 159 pack icons because 8 are pet species models): 12 weapons, 10 tools, 72 armor pieces (6 sets x 4 pieces x 3 tiers, set bonuses), 36 accessories
  (9 families x 4 rarities), upgrade consumables (Recombobulator, Hot Potato Book, 3 singularities, 3 fuels), Warp Pearl,
  4 reforge stones, Enchanted Book, pet egg / candy and 5 pet held items. Abilities: Instant Transmission, Wither
  Impact, Triple Shot, Grapple, Legendary Pull, Eruption, Mining Speed Boost, Heal, Leap, Frost Nova, Fire Beam,
  Overflux, Treecapitator, Jungle Might, drill fuel, replant.
* **Enchants** (32, PDC only, levels up to X, 5 ultimates), enchanting table menu (replaces the vanilla table in sb
  worlds, costs coins and an Enchanting skill level), anvil menu (books, same-item merge, coins or levels).
* **Reforges** (24, scaled by rarity, 4 stone-only): `/reforge` (blacksmith menu) or a stone clicked onto an item.
* **Accessory bag** (`/accessories`): slots grow by paid upgrades, one counted accessory per family, Magical Power and
  stats per point.
* **Pets** (`/pets`): 8 species, levels 1-100 from skill XP, rarity tiers, held items, auto-pet, a floating ItemDisplay
  that follows the owner. Pets can be turned back into egg items (shift click) and eggs re-added (right click).

## Commands and permissions

| Command | Permission (default) |
|---|---|
| `/accessories` (`/acc`, `/bag`), `/pets`, `/reforge`, `/stats` | `vampyuyu.sb.items.use` (TRUE) |
| `/enchanttable`, `/anvil` (open from anywhere) | `vampyuyu.sb.items.remote` (OP) |
| `/sbitems book <enchant> <level> [player]`, `enchant <enchant> <level>`, `reforge <id\|none>`, `pet <type> <rarity> [level] [player]`, `mana <player> <n>`, `stats <player>`, `list <enchants\|reforges\|pets\|items>`, `reload` | `vampyuyu.sb.items.admin` (OP) |

Tab completion on `/sbitems`. Menus and `/sbgive <id>` come from the core.

## Config (`items.yml`)

Every key has the same default in code (a test compares the shipped file with the code defaults). Sections: `menus`,
`stats`, `attributes`, `combat`, `mobs`, `recipes`, `loot`, `mana`, `hud` (incl. `xp-curve`), `fortune`, `costs`, `hot-potato`, `singularity`, `enchants` (per-enchant
overrides), `reforges`, `accessories`, `pets`, `abilities.<id>.*`, `sets.<set>.*`, `items.<id>.damage|stats.<stat>`.
`/sbitems reload` re-reads the numeric settings; enchant/reforge definitions and the catalog need a restart.

## Data layout

* Profile `pets`: `{active, autoPet, pets:[{id,type,rarity,xp,held}]}`.
* Profile `accessories`: `{slots:[base64 item], upgrades}` (see `AccessoryData`).
* Accessory bag: entries stored beyond the current slot count (the limit was lowered) are kept and come back when it
  grows again; a save never truncates them.
* Item PDC (namespace `vampyuyu`): `enchants`, `reforge`, `recomb`, `hpb`, `singularity`, `fuel`, `stored_enchant`,
  `pet_*`, `rendered` (lore renderer version, lets `RenderUpgrader` redraw items created by `/sbgive` or loot).

## API and events

* Publishes `StatsApi` (last step of `enable()`), fires `StatsRecalculateEvent`.
* Consumes `SkillXpEvent` (HUD popup, pet XP), `SkillsApi.level` (popup, enchanting level), `EconomyApi` (all prices; free
  when absent unless `costs.require-economy`), `IslandApi.canBuild` (block abilities).
* Stat sources used: `held`, `armor`, `set`, `accessory`, `pet`, plus `set:timber`; other features own `skill:*`,
  `wings`, `fairy`, `island`.

## NEEDS (contract gaps, nothing applied)

* **XP popup progress** (`412/1,000`): `SkillsApi` exposes level and total XP but not the XP needed per level. The popup
  calls `SkillsApi.xpForLevel(Skill, int)` (cumulative XP needed to reach a level) by reflection when the implementation
  has it, otherwise uses `hud.xp-curve` (off by default) only if it reproduces the level the skills feature reports,
  otherwise shows `+3.2 Mining (Lv 7, 1,234 XP)`. Adding that one method to `SkillsApi` fixes it with no change here.
* **HUD outlet for other features**: the items HUD is the only action bar writer, but `api/` has no way for wings
  (flight energy), island (enter / leave), dungeon (room counter) or boss to show anything. Proposal:
  `HudApi.notice(UUID player, String legacyText, long millis)` (and optionally `HudApi.segment(UUID, String key, String
  text)` for a persistent segment) implemented by `items` and published with `core.register`. Until then they must not
  write the action bar themselves (it flickers against the HUD).
* **Shops** (economy feature): everything in the loot tables can also be sold. Suggested stock: tier 1 armor and starting
  weapons, Hot Potato Book, Warp Pearl, Fuel Cell / Volta / Oil Barrel, Pet Candy, `reforge_stone_*` (expensive), pet
  eggs of COMMON rarity, books up to table-max, accessory tier 1 and 2. Items are created with `core.items().create(id)`
  followed by nothing else; books and eggs through `Gear.createBook` / `PetService.newEgg` (same package) or by the item
  ids above plus `/sbitems` style PDC; there is no cross-feature API for building books and eggs yet, so a shop would
  need `ItemsApi.createBook(String enchant, int level)` and `createPet(String type, Rarity, int level)` in `api/`.
* **Mob levels from other features**: the PDC convention `vampyuyu:sb_level` (INTEGER) described above should be added
  to `docs/skyblock-design.md` so dungeon and boss adds can opt in without importing `items`.
* Enchanting skill XP for table purchases is awarded by the skills feature API call only if `SkillsApi.addXp` is
  present; nothing else fires `SkillXpEvent`.

## Known gaps / NOT verified in game

* All of it ran only under MockBukkit and pure unit tests (formulas, state machines, menus' pure previews, commands,
  persistence round trips, pack structure). Nothing was run on a real Paper server or client: attribute application,
  health scaling, ability effects (teleport, blasts, treecapitation), TextDisplay indicators, the pet ItemDisplay follow,
  the action bar, and every menu's click handling still need an in-game pass (`scripts/e2e_client.py` screenshots).
* Icons were viewed on a contact sheet only, not in a client with the real pack. Pet display uses the egg item with the
  `items/pet_<id>` model.
* `StatsEngine` recomputes held items on the main thread every 10 ticks per online player in sb worlds (hash checked);
  not profiled under load.
* Mob health scaling (`MobScaling`) and health bars apply on spawn and on damage; mobs that existed before the plugin started are unscaled.
* Smithing table as blacksmith is off by default (`menus.smithing-table-is-blacksmith`).
* A `Reload` does not re-register catalog items, so changed `items.<id>` stats apply after a restart.
* Placed-block memory (no fortune on player placed ores, logs, pumpkins, melons, cane, cactus) is in memory, capped at
  `fortune.placed-block-memory` and lost on restart; pistons moving a placed block are not tracked.
* Recipes, loot hook, mob drops and fishing drops, the armor-mitigation zeroing and the health restore passed only
  MockBukkit tests: `PrepareItemCraftEvent` / `CrafterCraftEvent` (world and custom-ingredient guard), `PlayerFishEvent`, mob kills and the
  real `DamageModifier` behaviour are untested in game. The `BossApi` hook is only registered when the boss feature
  publishes `BossApi` within about 30 seconds of the items feature enabling.
* Mob table numbers (`mobs.table`) and loot chances are first guesses, not balanced by play.
* Ability cooldowns are never purged (entries are tiny and expire when read); they live until the server restarts.
