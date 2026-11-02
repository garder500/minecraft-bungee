# Abyssal Depths / Profondeurs Abyssales (Realm 7, GDD 3.1, 3.2, 12.2, 12.5, issue #45)

Package `depths/` (feature `depths`, listed after `ashes`). World `sb_depths` (void, overworld, border 500), built once by
`DepthsRealm` from `DepthsPlan`; layout in `DepthsLayout` (bump `VERSION` to rebuild, `/depths build` forces it).

- **Zone**: Pressure Landing, four suspended domes (Lantern Shoals, Kelp Spire, Hollow Trench, Chain Dome) = the lower half of a
  sphere of water in a prismarine shell, rim at the landing height; a Drowned Bell platform on the road; the sealed door of the
  Sarcophagus on the east side of the Chain Dome. Shells, rims, landing and the water cannot be broken, built on or bucketed.
- **Opening**: setting `realm.depths.gate-boss` (default `sylvan_matriarch`, a proposal) lights Gate 7 for the players who fought it.
  `/depths go`, `/depths go force` (admin), `/depths` journal.
- **Pressure** (`DepthsMath`): depth under the surface 0-6 free, 7-11 tier 1 (needs 20), 12+ tier 2 (needs 45). Resistance =
  Respiration x10 + turtle helmet 10 + item PDC `pressure_resist` + 20 after Pressure Attunement; Conduit Power = immune. Too little:
  damage every 2 s (never under 1 heart) and slowness. Depth also burns extra air (Respiration spares it).
- **Pumps** (liquid transport, world quest): three conduits on the rims, 3 Pressure Pearls each, per player. A running pump takes
  one tier off in its dome; all three give the Attunement. Quests: exploration (5 landmarks), narrative (lore per landmark),
  progression (15 catches), world (pumps), same shape and coin values as the Ashes (guesses).
- **Fishing**: every catch in the Depths counts, rolls the Pressure Pearl (`RealmResourceApi.roll(..., "pressure_pearl", "fish")`,
  8 % / 10 / 30, proposals) and a small extra table (`DepthsMath.extraCatch`: prismarine, nautilus shell, 1 % heart of the sea).
  Breaking sea lanterns / prismarine bricks and killing guardians / drowned also roll it (seed `realm.sql`).
- **Gravity** (GDD 12.5, no real gravity): LOW zones over the Shoals and the Kelp Spire (Slow Falling + Jump Boost II), a DRIFT zone
  over the Trench (Slow Falling + a 2 s Levitation pulse every 8 s). Short effects, gone when the zone is left.
- **Tide Module** (pumps and fluids for Miners): `MachineModule.TIDE`, forged from 3 Pressure Pearls + Miner I + 8 prismarine
  crystals + 8 redstone; Flux x0.6, speed x0.9, 20 % of cycles also give a prismarine shard (`quarry.machines.module.tide.*`).
- **Guardian**: boss `abyssal_leviathan` renamed Chained Leviathan, Guardian row (resource Pressure Pearl, component `leviathan_core`
  with hard pity, spoken lines). New ability `chain_anchors` (`Abilities.anchors`, `BossSession.startAnchors`): from phase 2 on,
  3-4 end-crystal anchors appear and everybody is pulled toward the boss (capped, horizontal, `strength` 0.12-0.16) until they
  are destroyed (boss dazed 8 s, +25 % damage) or `seconds` run out (they overload and hit everyone). Reuses the crystal tracking
  of the shield, never both at once.
- **Bastion**: `depths_bastion` (palette `abyss`, key `leviathan_scale` renamed Abyss Bastion Key).

## Not verified
Written without Paper or Docker (no compile, no test run, nothing seen in game). Pure rules were compiled and unit-tested with
plain javac/JUnit; everything touching Bukkit (world build, ticks, events, anchors, module) is unrun. `Material.IRON_CHAIN` is looked
up by name because the block was renamed in recent versions. The Pressure Pearl sprite is a procedural placeholder (the ElevenLabs
image could not be downloaded here); the Tide Module reuses it. No sounds, no Sky Gate colour check, Hell Gao/modules of other Realms untouched.
