# Minions (feature `minions`, package `win.vampyuyu.survival.minions`)

## Player-facing description

Craft a minion, right-click a block on your island to place it, right-click the minion to open its menu. It works
inside a square around itself (5x5 at tiers I-VIII, 7x7 at IX-XI, plus 1 with a Minion Expander) and puts what it
makes into its own storage, **also while you are offline or the chunk is unloaded** (see "Catch-up").

26 types (tiers I-XI each, so 286 minion items):

| Kind | Types | What it really does in the world |
|---|---|---|
| Mining | Cobblestone, Coal, Iron, Gold, Diamond, Redstone, Lapis, Emerald, Clay, Sand | places its ore/block on a free spot of the area, then breaks it (cobble generator style). Every mining type needs AIR WITH A SOLID BLOCK BELOW: on a pillar or over the void it shows "Invalid terrain" and fills nothing |
| Farming | Wheat, Carrot, Potato, Pumpkin, Melon | plants on farmland (also uses its own supply), then harvests. Needs farmland in the area |
| Foraging | Oak, Spruce, Birch | puts a log (the type's `block`, e.g. OAK_LOG) on a free spot with dirt below and chops it at the end of the cycle. No sapling is planted, so nothing grows and nothing can be bone-mealed |
| Animal | Cow, Chicken, Pig, Sheep | spawns a prop animal in the area and kills it for drops. Needs free space |
| Fishing | Fishing | needs water (at least `fishing-min-water` water blocks in the layer under it), casts, splash particles |
| Combat | Zombie, Skeleton, Slime | spawns a prop mob (no AI, no drops of its own, no XP) and kills it |

### The block a minion places is protected, and it is the ONLY block it removes

Mining, farming and foraging minions place a real block (ore, full-grown crop, log) half a cycle before they remove
it. That exact position is recorded in the minion (`placed`, `spotX/Y/Z`, `spotMaterial` in the owner file), so a
restart or a chunk reload still finishes the cycle. Rules (`Terrain`, `PlacedBlockGuard`):

* The harvest removes only the recorded block, and only while it still is that material. There is NO fallback to
  "any block of my kind in the area": a cobblestone wall, an oak cabin, a sand floor or a wheat field built by the
  player next to the minion is never touched. (An earlier version had that fallback after a restart; it is gone.)
* Nobody can break the recorded block: `BlockBreakEvent` is cancelled at LOWEST priority (so no skill XP or custom
  drop is awarded by another feature), with a rate-limited chat hint (never the action bar). Also blocked: pistons
  moving it, explosions, an enderman or falling block taking it, water flowing into it, fire, farmland trampling
  and drying, bone meal and tree growth, shearing, a bucket emptied onto it, and breaking the ground under a
  non-solid placed block (a crop). Paper's `BlockDestroyEvent` (fires before a block is destroyed by physics or
  water, before the drops) is cancelled for placed blocks; `BlockDropItemEvent` is cleared as defence in depth, and an
  `ItemSpawnEvent` guard is a last resort only (vanilla makes the drops before it clears the block, so it may never
  fire; `BlockDestroyEvent` is the real guard and has only been tested with a synthetic event).
* Pick-up, purge and island return remove the placed block silently (no drop) when its chunk is loaded. The placed
  block of a minion that is purged while its chunk is unloaded stays in the world as a normal block (rare, admin only).
* The minion never fills water or lava, and a placed block that nobody removed stays until the cycle ends (a full
  storage keeps the cycle ready, so the block stays too).

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

The menu is redrawn in place every second while it is open (same inventory, no flicker, stops when closed or when the
feature is disabled), so the storage view, status and numbers stay current. The status and info icons show
**items per hour** and **"Storage full: in about 3h 20m"** (primary resource, at the current speed and fuel).

6-row menu: storage view (click: take a stack, shift-click: take all of it, "+N more stacks" overflow shown),
**Collect all**, **Compress resources** (turns raw resource in your inventory into Enchanted items, used for tier-ups),
status, tier upgrade button (cost: compacted or super compacted form of the main resource, see `tiers.tier-up-*`),
fuel slot, two upgrade slots, hat slot, **Pick up** (returns the minion item; storage must be collected first), and a
counter of items produced. Sub-menus list what you can add from your inventory. Nothing can be moved in or out by
clicking (the core's GUI listener cancels every click); everything goes through the buttons, so there is no way to
duplicate through the menu.

### Fuel, upgrades, hats

* Fuels (burn only while the minion is actually working): Coal, Coal Block (vanilla items), Enchanted Coal,
  Enchanted Charcoal (these two ARE the Enchanted items the menu's Compress button makes from coal and charcoal: the
  fuel list accepts `minion_enchanted_coal` / `minion_enchanted_charcoal`, there is no second item with the same
  name; `compressed:` in `fuels.*`). **Breaking change for other features: the item ids `minion_fuel_enchanted_coal`
  and `minion_fuel_enchanted_charcoal` no longer exist** (`items.create` on them throws); use
  `minion_enchanted_coal` / `minion_enchanted_charcoal`. The other custom fuels keep `minion_fuel_<id>`, Enchanted Lava Bucket, Hamster Wheel (farming only), Foul Flesh (everything except farming),
  Catalyst, Hyper Catalyst. Speeds of fuel and upgrades add up (`MinionLogic.speeds`), the final interval is
  `action-seconds * tier factor / (1 + sum)`.
* Upgrades (2 slots): Auto Smelter, Compactor, Super Compactor 3000 (replaces the Compactor), Diamond Spreading,
  Minion Expander (+1 radius), Flycatcher (+20 percent speed), Mithril Infusion (+10 percent speed, fuel lasts 25
  percent longer).
* Hats (cosmetic, 10): Pumpkin Hat, Golden Crown, Diamond Helm, Turtle Shell, Creeper / Skeleton / Zombie / Piglin /
  Wither / Dragon heads.
* **Sources.** The `recipes:` section of `minions.yml` gives crafting recipes (shaped, exact item choices) to all 7
  upgrades, Enchanted Lava Bucket, Hamster Wheel, Foul Flesh and the Pumpkin Hat / Golden Crown / Diamond Helm,
  built from the Enchanted items of the menu's Compress button plus a vanilla centre (for example Compactor = 8
  Enchanted Redstone around a Piston; Super Compactor 3000 = 8 Enchanted Iron around a Compactor). The
  Catalysts and the rare heads (creeper, skeleton, zombie, piglin, wither, dragon) and the Turtle Shell have no recipe
  on purpose: they are boss/dungeon/shop goods (`/minion give` for admins, or `/sbgive` ids `minion_fuel_*`,
  `minion_upgrade_*`, `minion_skin_*`). The Enchanted items themselves cannot be a crafting recipe (a grid takes one
  item per slot, never 160), so they come from the menu's Compress button, which stays the only source.

### Slots and caps

Slots = `base-slots` (5) + 1 per threshold in `slot-thresholds` reached by the number of DIFFERENT minions (type +
tier) the player has ever crafted or upgraded to, capped at `max-slots` (30). Island cap: `per-island-cap` (48) for
all players together. Placing needs: an island (IslandApi), membership of that island, `canBuild`, 2 free blocks of
air above a solid block, and the whole working area inside the island border (otherwise status "Outside island").
Access always requires the minion's own island: the island id at the minion's coordinates must still equal the id
it was placed on, so when a freed island cell is given to somebody else the new owner gets nothing (the minion is
returned to its real owner by the island check).

| Who | Open the menu, add fuel / tier up (from their own inventory) | Take items out | Take upgrades / hat out, change the hat | Pick up |
|---|---|---|---|---|
| Minion owner, admin | yes | yes | yes | yes |
| Co-op member | yes | only with `settings.member-collect: true` (default false) | no | no |
| Island owner (not the minion owner) | yes | only with `member-collect` | no | yes, but everything goes to the MINION OWNER through the owed list, not to the picker |

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

`settings.*` (tick period, visual range, scan interval, catch-up cap, island check interval, `island-missing-hours`,
`member-collect`, `gui-click-millis`, slots, caps, body scale, compact ratios, spread every N, fuel cap, upgrade
slots, fishing water, chat cooldown, actions per cycle), `recipes.*` (crafting recipes of upgrades, hats and custom
fuels: `result`, `shape`, `ingredients`; a bad one is skipped with a warning),
`tiers.*` (11 values each: action factor, storage slots, radius, rarity, chestplate; 10 values for the tier-up item
kind and amount), `fuels.*`, `upgrades.*`, `skins.*`, `smelt.*` (smelter table), `types.*` (kind, action seconds,
hand, hat, block, drops with min/max/chance, weighted mode, mob, sapling (unused since foraging places the log
directly; kept so old files load), recipe, colour). A fuel with `compressed: MATERIAL` is the Enchanted item of that
resource. A bad entry is skipped with
a warning, the rest loads. There is no hot reload.

## Data layout

* Placed minions: `plugins/VampyuyuSurvival/minions/<owner uuid>.json` (one file per owner, written atomically by
  `core.store()` after every state change that matters: place, pick up, tier up, upgrade, collect, and on shutdown;
  it also holds the placed block of the current cycle and `seenProduced` for the join summary).
  `minions/ledger.json`: ledger of spent item uids (bounded).
* Profile section `minions` (`MinionProfile`): `crafted` (set of "type:tier" used for slots) and `owed` (items owed to
  the player: island reset, removed from a co-op, purge; delivered at join and whenever there is room).
* Entities carry PDC tags (minion id) only. Their data is never read from the entity, always from the files.

### Anti-dupe and the durable order (what is and is not guaranteed)

The rule is: a crash may LOSE an item but must not DUPLICATE one. The order is chosen per path:

* **Paying** (tier up, fuel, upgrade, hat): the payment is taken from the inventory, the player's data is forced to
  disk (`Player.saveData()`), THEN the minion changes and its file is written. A kill -9 in between loses the
  payment, it can no longer leave both the paid item and the new tier.
* **Placing**: the item leaves the hand after every check, the player's data is forced to disk, THEN the minion is
  created and saved (the ledger first, then the owner file). A crash in between loses the minion, never keeps both.
* **Receiving** (collect, pick up, remove an upgrade, owed items): the state (the minion without the item) is written
  first, then the items are handed over. A crash in between loses the items, never duplicates them.
* **Owed items** (island reset, purge, picked up by the island owner, island missing for hours): the minion file
  without the minion is written first, then the owed list is added to the player's profile and the profile is written
  synchronously (`PlayerProfiles.saveDirty()` + `awaitWrites()`, there is no per-profile save in the core; see NEEDS).
  Delivery empties the owed list and writes it BEFORE any item is given, and writes the leftovers back afterwards. A
  crash while items are being given loses them; a crash can never deliver them twice. The 30 s delivery pass writes
  nothing when the inventory has no free slot, so a full inventory costs no disk IO.
* Every minion item is created with a `uid` (`MinionItems.createMinion`, `/minion give`, pick up, owed items, and a
  crafted minion is stamped in `CraftItemEvent` for single crafts). Placing marks the uid spent in `ledger.json` (an
  item with a spent uid, for example a copy or an inventory restored from a backup after a crash, is refused with
  "already used"). `loadAll` re-spends the uid of every placed minion. An item that still has no uid (made by
  `items.create` directly, a stack crafted by shift-click, loot built elsewhere) is stamped when placed; with the
  consume-then-save order above that item cannot be duplicated by a crash either, but a COPY of it made by a bug in
  another feature cannot be caught by the ledger.
* **Crafting**: shift-click on a minion recipe is cancelled with a hint (craft one at a time), so a prepared result
  can never be shared by several minions. `CrafterCraftEvent` is cancelled for the minion and item recipes (a Crafter
  would produce items with no uid and no slot credit).
* Chunk reload/unload only drops the body (entities are re-created from the data); storage lives in the data file.
  The placed block of the cycle is kept in the data file too.
* Menu: no slot is a real inventory slot, all moves are buttons with a main-thread atomic check of inventory space.
  A click cooldown (`gui-click-millis`, 150) and "write the file once per action" (collect all writes ONE file for
  every kind of item) keep click spam from queueing fsyncs. The save before an item changes hands still waits for
  the disk by design (that is what makes the order above true). Placing also rewrites the spent-uid ledger (up to
  50,000 uids): it is serialised on the writer thread, not the main thread, and queued before the owner file on the
  same single writer, but the main thread still waits for both files to reach the disk, so a placement costs two
  fsyncs. Everything else costs one per user action.
* Production is not tied to the placed block any more than to the clock: breaking it is impossible, so there is no
  way to get vanilla drops on top of the virtual output.

### What one cycle stores

A cycle rolls the primary resource and possibly more (leather with beef, seeds with wheat, a fish, the Diamond
Spreading bonus). The items are stored primary first, as far as they fit; whatever has no room is dropped. The minion
stalls ("Storage full") only when the storage is COMPLETELY full: every slot a full stack, so no item of any kind
fits. A tier I (1 slot) cow minion therefore fills its slot with beef (leather never fits) instead of freezing at the
first leather, a tier I fisher keeps fishing until its one kind is a full stack, and the bonus diamond of Diamond
Spreading never blocks a one slot cobblestone minion. The elapsed window is never thrown away because of a bonus
item. The spread counter always advances. (`MinionLogic.produce`)

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
  minion is removed and everything it holds is owed to the owner (`owed`). If the island is simply gone (deleted,
  no replacement) the status becomes "Island missing" and the minion is returned the same way once it has been
  missing for `island-missing-hours` (6) of running time; the clock is not saved (a restart, or an island registry
  that is still loading, never counts as missing time) and it only runs while `IslandApi` is available.
  `/minion purge orphans` asks the island registry now, so it also works on minions in chunks that have not loaded
  since the restart (it does not need the ticker). If the world or chunk is not loaded the minion is not ticked
  (frozen) and is kept as is.
* **While you were away.** Two messages, never both for the same items. (1) On join (3 s after, with the owed
  delivery) the owner gets one line: items made since they last looked at their minions (`MinionData.seenProduced`,
  updated when a menu is opened) and how many are full. (2) When a minion catches up on a window of 5 minutes or more
  (its chunk loaded again after the owner was elsewhere, the hub router sent them to the hub first) and the owner is
  online, they are told once per ticker pass how many items it made, and those items count as seen.

### Custom items are inert

`MinionItemGuard`: every `minion_*` item (made of vanilla materials such as FURNACE, PISTON, COAL, LAVA_BUCKET) is
refused for block placing, eating, bucket pouring, furnace fuel and smelting, dispensers and Crafter blocks, and
the crafting table never uses them as ingredients of a vanilla recipe. Villager trades and cartography/other
exotic recipe stations are NOT guarded (gap).

## Resource pack

`resource-pack/art/minions_icons.py` (stdlib only, deterministic) writes 40 icons (26 minions, 7 fuels, 7 upgrades)
plus their models and item definitions under `assets/vampyuyu/{textures/item,models/item,items}/minions/`. Item
models are `vampyuyu:minions/<type>` (the tier is not in the model: the rarity colour and the name show it),
`vampyuyu:minions/fuel_<id>`, `vampyuyu:minions/upgrade_<id>`. `make test-pack` passes. Enchanted resources and hats
use vanilla models on purpose.

## Known gaps / NOT verified in game

* **No real screenshot and no run on a Paper server**: everything was exercised with MockBukkit and unit tests
  (production math, catch-up, partial drops, fuel stacking, storage, store round trips, config parsing, manager
  actions, placed-block protection events, GUI construction and in-place redraw, commands, anti-dupe ledger and the
  save order through test hooks). MockBukkit does not simulate entity rendering, ItemDisplay/armor-stand visuals,
  particles, block growth, physics pops or chunk unload, so the look of the minion body, the arm swing, the label and
  particles, the PDC cleanup of leftover entities after a crash, the real chunk-unload/load path and the GUI layout in
  a 6-row window are NOT verified. Run `scripts/e2e_client.py` with a minion scenario before trusting the visuals.
* **Placed-block protection is tested through synthetic events.** The real server paths (piston `getBlocks()` for
  destroyable blocks like crops, farmland trampling by a mob, physics pops) are covered by the handlers listed above,
  not by a run. Some indirect paths may remain (a plugin that sets blocks without events, /setblock, WorldEdit):
  those leave a stale record that is cleaned at the end of the cycle (the block is then only removed if it still
  is the recorded material).
* `Player.saveData()` is the forced player save. It is a main-thread write of one small file per payment / placement;
  `PlayerMock` may not implement it (tests use a hook), and a server that does not honour it falls back to the
  vanilla autosave (the order above is then not guaranteed).
* Crafting: `CraftItemEvent.setCurrentItem` stamping, the shift-click cancel and the `CrafterCraftEvent` cancel are
  not covered by a test (MockBukkit cannot simulate the crafting grid or a Crafter); a failure leaves an unstamped item, which is stamped at placement.
  The upgrade/hat/fuel recipes use `ExactChoice` of the custom items; their matching in a real crafting grid was not
  seen.
* Villager trades are not guarded against `minion_*` items.
* Icons were checked as an enlarged contact sheet (not in a client with the real pack). The art for
  `fuel_enchanted_coal` and `fuel_enchanted_charcoal` is no longer used (the Enchanted items of the menu have no
  custom model); the files are harmless.
* The body uses the Paper `Mannequin` API (it compiles against paper-api 26.3); how it renders in a client (scale 0.7,
  default profile skin) and whether `swingMainHand` animates it was not seen.
* Tier upgrades are bought in the menu with the compacted resource instead of a crafting recipe (a recipe cannot ask
  for 160 of an item; ingredients stay config-driven).
* No hot reload; no per-island upgrade integration (`IslandApi` has no documented method for extra minion slots; slots
  come from the crafted-minions thresholds only).
* Island reset is detected by polling (up to `island-check-seconds` late), not by a hook.
* No collection credit (see above).

### Still open (not done, listed in the review as depth features)

* Storage add-ons (chests / hoppers linked to a minion), auto-sell hopper (needs a lazy `EconomyApi` lookup and a
  price table), production leaderboard and per-minion names, a remote "collect from all minions" command, a particle
  preview of the working area, and tier pips on the minion icon (needs new art). (Two Flycatchers at once now work:
  speed upgrades stack, other upgrades still refuse a duplicate.)
* Rare hats and Catalysts have no recipe on purpose (see Fuel, upgrades, hats).
* The IO of a save before an item changes hands is still synchronous (one fsync per user action, two for a
  placement because of the ledger).

## NEEDS (contract changes the foundation owner could apply)

* `IslandApi`: a reset / delete hook or an `IslandResetEvent` (minions would then be returned instantly instead of by polling).
* `SkillsApi`: a collection method (for example `addCollection(UUID, Material, long amount)`) so minion output can credit
  collections.
* `PlayerProfiles`: a per-profile synchronous save (`saveNow(UUID)`) so the owed list can be made durable without
  `saveDirty()` (which writes every dirty profile on the calling thread) + `awaitWrites()`.
* `Stat`/`StatsApi`: nothing needed.
