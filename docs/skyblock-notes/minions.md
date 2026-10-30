# Minions (feature `minions`, package `win.vampyuyu.survival.minions`)

## Player-facing description

Craft a minion, right-click a block on your island to place it, right-click the minion to open its menu. It works
inside a square around itself (5x5 at tiers I-VIII, 7x7 at IX-XI, plus 1 with a Minion Expander) and puts what it
makes into its own storage, **also while you are offline or the chunk is unloaded** (see "Catch-up").

26 types (tiers I-XI each, so 286 minion items):

| Kind | Types | What it really does in the world |
|---|---|---|
| Mining | Cobblestone, Coal, Iron, Gold, Diamond, Redstone, Lapis, Emerald, Clay, Sand | places its ore/block on a free spot of the area, then breaks it (cobble generator style). Sand needs a solid block below the spot |
| Farming | Wheat, Carrot, Potato, Pumpkin, Melon | plants on farmland (also uses its own supply), then harvests. Needs farmland in the area |
| Foraging | Oak, Spruce, Birch | plants a sapling, grows and chops a log. Needs a free spot with dirt below |
| Animal | Cow, Chicken, Pig, Sheep | spawns a prop animal in the area and kills it for drops. Needs free space |
| Fishing | Fishing | needs water (at least `fishing-min-water` water blocks in the layer under it), casts, splash particles |
| Combat | Zombie, Skeleton, Slime | spawns a prop mob (no AI, no drops of its own, no XP) and kills it |

Block work (placing, breaking, spawning, arm swing, particles) only happens while a player is within
`visual-range` blocks. Otherwise the same production is computed from timestamps without touching blocks.
Prop mobs and the minion bodies are tagged, invulnerable and removed when their chunk loads (`EntitiesLoadEvent`)
so nothing can be left behind after a crash.

The body is a small Paper `Mannequin` (player model scaled by `settings.scale`, no AI, immovable, invulnerable,
non persistent) plus a billboard `TextDisplay` label above it (`MinionVisuals`): chestplate by tier, tool in hand per
type, hat (or the chosen cosmetic hat), label with name, tier, status and storage, arm swing on every action and
block particles/sounds from `Terrain`.

States shown above the minion and in the menu: Working, Storage full, No space, Invalid terrain, No water,
Island missing, Area outside island (each with a hint on how to fix it). A blocked minion also tells its owner in chat, at most once per
`chat-cooldown-seconds` per minion.

### Menu (right-click the minion)

6-row menu: storage view (click: take a stack, shift-click: take all of it, "+N more stacks" overflow shown),
**Collect all**, **Compress resources** (turns raw resource in your inventory into Enchanted items, used for tier-ups),
status, tier upgrade button (cost: compacted or super compacted form of the main resource, see `tiers.tier-up-*`),
fuel slot, two upgrade slots, hat slot, **Pick up** (returns the minion item; storage must be collected first), and a
counter of items produced. Sub-menus list what you can add from your inventory. Nothing can be moved in or out by
clicking (the core's GUI listener cancels every click); everything goes through the buttons, so there is no way to
duplicate through the menu.

### Fuel, upgrades, hats

* Fuels (burn only while the minion is actually working): Coal, Coal Block (vanilla items), Enchanted Coal,
  Enchanted Charcoal, Enchanted Lava Bucket, Hamster Wheel (farming only), Foul Flesh (everything except farming),
  Catalyst, Hyper Catalyst. Speeds of fuel and upgrades add up (`MinionLogic.speeds`), the final interval is
  `action-seconds * tier factor / (1 + sum)`.
* Upgrades (2 slots): Auto Smelter, Compactor, Super Compactor 3000 (replaces the Compactor), Diamond Spreading,
  Minion Expander (+1 radius), Flycatcher (+20 percent speed), Mithril Infusion (+10 percent speed, fuel lasts 25
  percent longer).
* Hats (cosmetic, 10): Pumpkin Hat, Golden Crown, Diamond Helm, Turtle Shell, Creeper / Skeleton / Zombie / Piglin /
  Wither / Dragon heads.
* Fuels, upgrades and hats have **no crafting recipe** in this feature (they are rewards and shop goods: `/minion
  give` for admins; the economy/boss features can hand them out through `/sbgive` ids `minion_fuel_*`,
  `minion_upgrade_*`, `minion_skin_*`).

### Slots and caps

Slots = `base-slots` (5) + 1 per threshold in `slot-thresholds` reached by the number of DIFFERENT minions (type +
tier) the player has ever crafted or upgraded to, capped at `max-slots` (30). Island cap: `per-island-cap` (48) for
all players together. Placing needs: an island (IslandApi), membership of that island, `canBuild`, 2 free blocks of
air above a solid block, and the whole working area inside the island border (otherwise status "Outside island").
Members of the island can open and use the minions of the owner; only the minion owner, the island owner or an admin
can pick one up.

## Commands and permissions

| Command | Permission | Description |
|---|---|---|
| `/minion` (alias `/minions`) `help`, `info`, `list` | `vampyuyu.sb.minions.use` (default TRUE) | help, slots and how to get more, where your minions are |
| `/minion list <player\|all>` | `vampyuyu.sb.minions.admin` (OP) | list someone else's minions |
| `/minion give <type> <tier> [player]` | `vampyuyu.sb.minions.admin` | gives a minion item |
| `/minion give fuel\|upgrade\|hat <id> [amount] [player]` | `vampyuyu.sb.minions.admin` | gives a fuel, upgrade or hat |
| `/minion purge <player\|orphans> [confirm]` | `vampyuyu.sb.minions.admin` | removes minions; everything they held is owed to the owner |

Tab completion exists for every argument. Admin sub-commands refuse a sender without the admin permission.

## Config (`plugins/VampyuyuSurvival/minions.yml`, defaults in the jar)

`settings.*` (tick period, visual range, scan interval, catch-up cap, island check interval, slots, caps, body
scale, compact ratios, spread every N, fuel cap, upgrade slots, fishing water, chat cooldown, actions per cycle),
`tiers.*` (11 values each: action factor, storage slots, radius, rarity, chestplate; 10 values for the tier-up item
kind and amount), `fuels.*`, `upgrades.*`, `skins.*`, `smelt.*` (smelter table), `types.*` (kind, action seconds,
hand, hat, block, drops with min/max/chance, weighted mode, mob, sapling, recipe, colour). A bad entry is skipped with
a warning, the rest loads. There is no hot reload.

## Data layout

* Placed minions: `plugins/VampyuyuSurvival/minions/<owner uuid>.json` (one file per owner, written atomically by
  `core.store()` after every state change that matters: place, pick up, tier up, upgrade, collect, and on shutdown).
  `minions/ledger.json`: ledger of spent item uids (bounded).
* Profile section `minions` (`MinionProfile`): `crafted` (set of "type:tier" used for slots) and `owed` (items owed to
  the player: island reset, removed from a co-op, purge; delivered at join and whenever there is room).
* Entities carry PDC tags (minion id) only. Their data is never read from the entity, always from the files.

### Anti-dupe

* Every minion item handed out by this feature (`/minion give`, pick up, owed delivery) carries a fresh `uid`. Placing
  marks the uid spent in `ledger.json` (an item with a spent uid, for example a copy or an inventory restored from a
  backup, is refused with "already used"). A minion item made by a crafting table has no uid yet and gets one when it
  is placed, so a copy of a never-placed crafted item is NOT caught (crafting output is a fresh item anyway).
* Pick up returns the exact storage: it is moved to the owner first and the pick-up is refused while anything is
  left (full inventory). The data is saved (minion removed) BEFORE the items are handed over, and the owed list of an
  island reset / purge is written before the owner is given anything: a crash in between can lose items but can never
  duplicate them. Leftovers that do not fit the inventory are dropped at the player's feet.
* Chunk reload/unload only drops the body (entities are re-created from the data); storage lives in the data file.
* Menu: no slot is a real inventory slot, all moves are buttons with a main-thread atomic check of inventory space.

### Catch-up

Every minion has `lastTick` (epoch millis). `Production` turns an elapsed window into whole items: it is capped by
`max-catchup-hours`, by the storage capacity (full storage = blocked, time does not accumulate), by the fuel left (fuel
burns only for the time the minion could work) and applies compactor/smelter/spread. Fractions of an item carry over.
Unloaded chunks (and unloaded worlds) are never ticked: the minion is simply skipped, `lastTick` stays, and the
whole elapsed window is produced on the first tick after the chunk loads. Loaded minions with no player within
`visual-range` are produced the same way each tick without block work or bodies; their terrain is re-scanned every
`scan-seconds`.

## API and events

* Publishes `MinionApi` (`slotsFor(UUID)`, `placedCount(UUID)`), registered last in `enable()`.
* Consumes `IslandApi` (lazy lookup, every call copes with empty). No events published or consumed.
* **Collections / skills: NOT credited.** `SkillsApi` only has `addXp`, `level`, `xp`, `maxLevel`; there is no documented
  collection method, and Hypixel minions do not give skill XP, so minions credit nothing to skills.
* **Island reset: no documented hook** in `IslandApi`. The minion checks `islandAt(location)` every
  `island-check-seconds`; if the island id changed or the owner is no longer a member on two checks in a row, the
  minion is removed and everything it holds is owed to the owner (`owed`). If the world or chunk is not loaded the minion
  is not ticked (frozen) and is kept as is.

## Resource pack

`resource-pack/art/minions_icons.py` (stdlib only, deterministic) writes 40 icons (26 minions, 7 fuels, 7 upgrades)
plus their models and item definitions under `assets/vampyuyu/{textures/item,models/item,items}/minions/`. Item
models are `vampyuyu:minions/<type>` (the tier is not in the model: the rarity colour and the name show it),
`vampyuyu:minions/fuel_<id>`, `vampyuyu:minions/upgrade_<id>`. `make test-pack` passes. Enchanted resources and hats
use vanilla models on purpose.

## Known gaps / NOT verified in game

* **No real screenshot and no run on a Paper server**: everything was exercised with MockBukkit and unit tests
  (production math, catch-up, fuel stacking, storage, store round trips, config parsing, manager actions, GUI
  construction, commands, anti-dupe ledger). MockBukkit does not simulate entity rendering, ItemDisplay/armor-stand
  visuals, particles, block growth or chunk unload, so the look of the minion body, the arm swing, the label and
  particles, the PDC cleanup of leftover entities after a crash, the real chunk-unload/load path and the GUI layout in
  a 6-row window are NOT verified. Run `scripts/e2e_client.py` with a minion scenario before trusting the visuals.
* Icons were checked as an enlarged contact sheet (not in a client with the real pack).
* The body uses the Paper `Mannequin` API (it compiles against paper-api 26.3); how it renders in a client (scale 0.7,
  default profile skin) and whether `swingMainHand` animates it was not seen.
* Fuels, upgrades and hats have no recipes and no source besides `/minion give` and other features' loot or shops.
* Tier upgrades are bought in the menu with the compacted resource instead of a crafting recipe (a recipe cannot ask
  for 160 of an item; ingredients stay config-driven).
* No hot reload; no per-island upgrade integration (`IslandApi` has no documented method for extra minion slots; slots
  come from the crafted-minions thresholds only).
* Island reset is detected by polling (up to `island-check-seconds` late), not by a hook.
* No collection credit (see above).

## NEEDS (contract changes the foundation owner could apply)

* `IslandApi`: a reset / delete hook or an `IslandResetEvent` (minions would then be returned instantly instead of by polling).
* `SkillsApi`: a collection method (for example `addCollection(UUID, Material, long amount)`) so minion output can credit
  collections.
* `Stat`/`StatsApi`: nothing needed.
