# Quarry (feature `quarry`, package `win.vampyuyu.survival.quarry`)

Milestone 1 of the Skyblock game loop (design canvas "Skyblock GameLoop"). Config: `quarry.yml` (every number is a proposal).

## What players get

* **Cobble draw.** Cobblestone formed by water meeting lava on an island (a generator) is marked in its chunk PDC
  (`vampyuyu:marks_cobble`). Breaking a marked block rolls the ore table: iron nuggets, coal, raw copper, gold nuggets
  (tier II), diamonds (tier II) and the custom **Bronzite**, with a pity counter per ore and a geode (about 1 block in
  2000: 3 to 6 of the rarest ore). Placed cobblestone is never marked, so it cannot be farmed. Rolls only happen in
  `sb_world` and `sb_nether`, on an island, never in Survival and never in creative.
* **Mining luck** comes from the PDC long `vampyuyu:mining_luck` of the held pickaxe (Bronze Pick: 10), capped by
  `generator.luck-cap`. It multiplies the chances, never the pity.
* **Generator tier** is stored in the island owner's profile section `quarry` (`generatorTier`). The **Bronze Core**
  (right-click on your island) raises it to tier II.
* **Forge.** A placeable blast furnace item (shapeless recipe: blast furnace, 4 iron ingots, 4 coal; crafting it
  outside Skyblock is refused). Right-click opens the menu (also `/forge` in Skyblock). Jobs take real time, run while
  the player is offline, and are collected from the menu. Materials are taken all or nothing. Recipes: Bronze Core,
  Bronze Cleaver, Bronze Pick.

## Isolation

Every listener tests the world name first. The Forge item cannot be placed or crafted outside `sb_world` / `sb_nether`,
the Bronze Core refuses to work elsewhere. Inventories already do not cross modes (`ModeTravel`).

## Pack

`python3 resource-pack/art/quarry_items.py` writes the 5 icons (ore/bronzite, ore/bronze_core, forge/forge,
weapon/bronze_cleaver, tool/bronze_pick) under `pack/assets/vampyuyu`. Ore blocks are not custom blocks: a resource pack
cannot retexture one block of a type, so ores are item drops.

## Machines and Flux (milestone 4)

Machines are Forge recipes (`cable` x8, `coal_generator`, `battery`, `miner_1`, `miner_2`). Placed on your own island in
`sb_world` / `sb_nether` (6 per island, `machines.max-per-island`), each is a block recorded in `quarry/machines.json`.
Machines that touch (6 faces) share one network (`EnergyNetwork`, pure and unit tested). Once per second a network
burns fuel (Coal Generator: right-click holding coal, 40 s per coal, 10 Flux/s), pools production with the batteries
(10,000 each), lets every miner that can work draw its Flux and keeps the rest in the batteries. A miner (I: 8 Flux/s,
0.5 cycles/s; II: 20 Flux/s, 1 cycle/s) needs generator cobble and a chest or barrel next to it: it breaks the cobble
(the water and lava make the next one), rolls the ore table with the island owner's tier and puts the ore in the
container (cobble itself is not collected). Machines run only while their whole network is in loaded chunks; there is no
offline catch-up yet. Breaking one gives the item back and forgets its fuel and charge.

## End access and bosses (milestone 5)

* **Generators.** Water meeting lava makes cobblestone when the lava flows sideways and stone when it falls: both count.
* **The End is sealed.** The island has no End gate. `/island end` asks `EndAccessApi` (published by the Quarry): the
  player must forge a **Void Lens** (8 Sunstone, 8 Frostium, 4 diamonds, 3 h) and right-click it on their island once.
  The flag is `endUnlocked` in the profile section `quarry`. Without the Quarry published the End is open.
* **Bosses are live again** (`boss` feature, after `quarry`). Summon items are forged, not dropped: Matriarch Egg, Magma Core,
  Frost Heart, Leviathan Scale and the **Void Sigil** (2 Warden's Hearts, 4 Frost Shards, 4 Leviathan Teeth, 16 Sunstone: the
  materials come from the three other bosses). `mob-drops` is empty. The Voidwyrm is summoned at the island's own altar in
  `sb_end` and grants the Wings (damage share of 5 percent). World boss events are off: their spots were in the retired hub.

## Not done yet (next milestones)

Perfect forges, solar panels and the Void reactor, compactor and sorter machines, offline catch-up, bosses and dungeons (retired modules still target `sb_hub` and `sb_end`), skins. Decision needed first: the
design puts generator IV and the Voidwyrm in an End platform, which the live Skyblock does not have.
