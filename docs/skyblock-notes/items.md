# Items feature (`items`)

Package `win.vampyuyu.survival.items`, config `items.yml`, implements `StatsApi`, profile sections `items` (reserved),
`pets`, `accessories`. Pack art: `resource-pack/art/items_icons.py` (159 deterministic 16x16 icons).

## What players get

* **Stats**: held weapon or tool (main hand, only for the types in `stats.held-types`), the four armor pieces, the
  accessory bag, the active pet, full armor set bonuses and external modifiers (`StatsApi.addModifier`). Recomputed on
  equip, held-slot change, inventory click/drag/close/pickup/drop, swap, item break, join, respawn, world change and
  every modifier change, debounced every `stats.flush-ticks`; a full scan runs at most every 10 ticks and only touches
  players whose item signature changed. `StatsRecalculateEvent` is fired on each recompute.
  Applied to Paper attributes only in `sb_*` worlds: max health (real value up to 1024, client bar scaled to 20 hearts
  by `attributes.health-scale`), movement speed, attack speed, mining speed, vanilla armor zeroed (Defense replaces it).
  Modifier keys are unique and removed on quit and plugin disable.
* **Combat**: `damage = (weapon + 5) * (1 + Strength/100) * (crit: 1 + CritDamage/100) * enchant multipliers`, incoming
  damage is scaled then divided by `1 + Defense/100`, true damage for void/starvation/kill, ferocity strikes, ability
  damage (Intelligence, Ability Damage), floating TextDisplay damage numbers (pooled, short lived), optional mob health
  names. Natural mobs get `combat.mob-health-multiplier` more health.
* **HUD**: the only action bar writer. `health/max  defense  mana/max (+overflow)`, notices (cooldown, not enough mana)
  and a 3 second skill XP popup from `SkillXpEvent`. Updated 4 times a second, resent only when the text changed (plus
  a keep-alive).
* **Mana**: Intelligence is the pool, regenerates a percent per second, abilities spend regular mana first then overflow
  mana, overflow decays.
* **Catalog** (159 items): 12 weapons, 10 tools, 72 armor pieces (6 sets x 4 pieces x 3 tiers, set bonuses), 36 accessories
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
`stats`, `attributes`, `combat`, `mana`, `hud`, `fortune`, `costs`, `hot-potato`, `singularity`, `enchants` (per-enchant
overrides), `reforges`, `accessories`, `pets`, `abilities.<id>.*`, `sets.<set>.*`, `items.<id>.damage|stats.<stat>`.
`/sbitems reload` re-reads the numeric settings; enchant/reforge definitions and the catalog need a restart.

## Data layout

* Profile `pets`: `{active, autoPet, pets:[{id,type,rarity,xp,held}]}`.
* Profile `accessories`: `{slots:[base64 item], upgrades}` (see `AccessoryData`).
* Item PDC (namespace `vampyuyu`): `enchants`, `reforge`, `recomb`, `hpb`, `singularity`, `fuel`, `stored_enchant`,
  `pet_*`, `rendered` (lore renderer version, lets `RenderUpgrader` redraw items created by `/sbgive` or loot).

## API and events

* Publishes `StatsApi` (last step of `enable()`), fires `StatsRecalculateEvent`.
* Consumes `SkillXpEvent` (HUD popup, pet XP), `SkillsApi.level` (popup, enchanting level), `EconomyApi` (all prices; free
  when absent unless `costs.require-economy`), `IslandApi.canBuild` (block abilities).
* Stat sources used: `held`, `armor`, `set`, `accessory`, `pet`, plus `set:timber`; other features own `skill:*`,
  `wings`, `fairy`, `island`.

## NEEDS (contract gaps, nothing applied)

* The XP popup cannot show `412/1,000`: `SkillsApi` exposes level and total XP but not the XP needed per level. It shows
  `+3.2 Mining (Lv 7, 1,234 XP)`. A `SkillsApi.xpForLevel(Skill, int)` method would fix it.
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
