# Energy: Flux, passive generators and compression (GDD 7.3, 7.4; issue #27)

Package `win.vampyuyu.survival.quarry`. Every number is a `quarry.machines.*` setting in `db/seed/quarry.sql` (SQLite
`skyblock.db`), edited in the panel; `MachineSettings` only holds fallbacks.

## Flux numbers (GDD 7.1 and 7.3)

| Machine | Flux/s |
|---|---:|
| Coal Generator (1 coal = 40 s = 200 Flux) | +5 |
| Solar Panel (daylight, open sky) | +2 |
| Spring Wheel / Heat Tap / Void Reactor / Astral Lattice | +10 / +30 / +80 / +200 |
| Miner I / Miner II | -2 / -6 |

(The Coal Generator and the Solar Panel used to make 10 and 6, the miners to use 8 and 20: all moved to the GDD scale together, so one
coal generator still runs about two Miner I.)

## Passive generators (`PassiveGenerator`, pure; checks in `MachineService.cellsAround`)

No fuel. Each is a machine that joins the Flux network (cables, batteries) and makes Flux only when **its cells touch it
(six faces)** and **its tier Block is inside**:

| Generator | Cells | Block |
|---|---|---|
| Spring Wheel | a Spring Stone | Bronzite Block |
| Heat Tap | a magma block (craftable from netherrack and coal) | Sunstone Block |
| Void Reactor | a Void Cell = crying obsidian (craft: obsidian + a Voidglass) | Voidglass Block |
| Astral Lattice | a Void Cell and a block of amethyst (the geode), open sky above (skylight 15, day or night) | Starcore Block |

Forge recipes (GDD 7.3): Spring Wheel 80 Bronzite + 30 Frostium + 8 Spring Stones; Heat Tap 60 Frostium + 40 Sunstone; Void
Reactor 50 Voidglass + 40 Sunstone + 8 diamonds; Astral Lattice 40 Starcore + 40 Voidglass.

## Compression (GDD 7.4)

9 ore -> 1 Bar -> 1 Block (81 ores), for the five tiered ores (Bronzite, Frostium, Sunstone, Voidglass, Starcore). Crafting
table recipes in `QuarryFeature.addStartRecipes`, both ways (a Bar or a Block gives its nine back). Bars and Blocks are custom
items on non-placeable base materials.

Right-click a generator holding its Block: the first one **activates** it, up to three more **overload** it (+25 % each, x1.75
at most; `quarry.machines.overload.blocks` and `.bonus`). Sneak with an empty hand takes one out (the last one switches it
off). Breaking the generator gives back the machine and every Block (`MachineData.blocks`, saved with the machine).
The Frostium Block has no generator yet.

## No arbitrary machine cap

There is no limit per island. A network runs as many Miners as its Flux allows (`PassiveGeneratorTest.theNumberOfMinersIsLimitedByFluxNotByACap`:
one Astral Lattice with four Blocks, 350 Flux/s, runs 58 Miner II). What limits a base is the Flux, the space, the cables,
the batteries, the build cost and the weather (Lightning Rod).

## Tests

`PassiveGeneratorTest` (pure), `EnergyNetworkTest`, `LightningRodTest`, `QuarrySeedTest`. The pure ones can be run with
plain javac and JUnit and stubs for `org.bukkit.Material` and `ItemStack` when Paper is not reachable.
