# Tempestuous Archipelago / Archipel Tempétueux (Realm 6, GDD 3.1, 3.2, 12.5, issue #44)

A complete Realm: zone + local resource + system + Bastion + Guardian. Package `realm/` (feature `storm`, listed after
`ashes`), world `sb_storm` (normal environment, void generator, always raining, border 700), built once by `StormRealm`
from `StormPlan` (marker `realms/storm-world.json`, bump `StormPlan.VERSION` to rebuild). One shared world, like the Ashes.

## Zone (`StormPlan`)
Seven islands in a chain, joined by movement mechanics instead of bridges: **Gale Landing** (cooled, no building) ->
moving platform -> **Weathervane Isle** -> wind pad -> **Rod Field** -> moving platform -> **Cirrus Shelf** -> wind pad ->
**Maelstrom Heart** -> moving platform -> **Roc Roost** -> wind pad -> **Dragon's Eyrie** (the Sarcophagus seal).
Every island has its own height. Landmarks give a title and a lore line the first time a player stands on them.
Gate 6 lights for whoever defeats the boss of the setting `island.storm.gate-boss` (default `sylvan_matriarch`, the
Mycelial Queen: the Realms are opened in the order of the GDD); `/gateadmin light <player> archipelago` also works, and
`/storm go force` (admin) skips the Gate. Back: `/quai`, `/hub`.

## Movement prototype (GDD 12.5)
* **Moving platforms** (`PlatformMath`, `StormRealm.Mover`): a 5x5 copper slab, two blocks thick, real blocks moved one
  step at a time along a straight track between two islands (3 platforms, 16 to 19 steps, 4 blocks per second, 6 s pause at
  each end). The position is a function of the world time alone, so the three stay in step and nothing is saved. The
  players standing on it are carried by the same offset. If a block is in the way it waits. Taken down on a clean stop; a
  crash leaves blocks in the air, swept at the next start (only the two platform materials, only along the tracks).
  Nobody can break or build on a platform's blocks.
* **Wind pads** (`PadMath`): a 3x3 floor with light blue terracotta in the middle (6 pads, one each way between the pad
  islands). Standing on one for 0.6 s (sound and sparks: the telegraph) throws the player to the next island with the
  ground-less Minecraft movement (x0.91 horizontal, gravity 0.08, x0.98 vertical), Slow Falling for 12 s, no fall damage.
  Counter-measure: sneak to stay put. Speeds are capped (1.8 blocks per tick horizontal, 2.2 vertical) and a unit test
  checks that every pad lands inside the island it names, with an apex under 14 blocks.
* **Gusts**: every 30 s, a 3 s warning ("A gust is coming. Sneak to brace."), then 2 s of wind in one direction (changes each
  cycle). Sneaking, the Landing and creative/spectator ignore it. The push is capped at 0.9 blocks per tick.
* **Falling**: below the Realm the player goes back to the last island they stood on (never the void, never lethal: it
  costs 2 hearts but never below 1). Lightning in the sky is a harmless effect, not a strike.
* Not done: crumbling platforms, updraft columns (Levitation), Wings-specific wind. The platforms are real blocks, not
  display entities: see "Not verified".

## Local resource: Thunder Quartz (GDD 3.2, name still to validate by the owner)
Item `thunder_quartz` (`RealmItems`, pack model `vampyuyu:ore/thunder_quartz`). Fourteen chiseled quartz **nodes** on the Rod
Field, the Maelstrom and the Roost: cut with a pickaxe, 1 to 2 for sure (40 % for the second), they grow back after 90 s.
Other sources are the generic Realm pity (`realm.sql`: 6 % / 12 / 36 like the other two): breeze and phantom kills, the
decorative rods. The Guardian pays 2 to 4 per kill and 6 more the first time. Uses: it burns in the **Lightning Rod**, it is
in the **Tempest Bastion key** and in the Rod's own recipe. Per GDD 3.2 it is no tier and raises no cap.

## System: unstable energy (`StormMath`, `EnergyNetwork`)
* **Lightning Rod** (machine `lightning_rod`, Forge: 2 Thunder Quartz, a lightning rod, 6 copper, 4 redstone): joins the Flux
  networks like any generator. It runs when the sky is open above it **in a thunderstorm**, or while it **burns Thunder
  Quartz** (right-click; 180 s each, setting `quarry.machines.lightning-rod.quartz-seconds`).
* **Very strong but unstable**: base 30 Flux/s (`quarry.machines.lightning-rod.output`) times a **surge factor** drawn every
  second per rod from a fixed hash (deterministic, so tests can check it): 0 (10 % of the seconds), x0.5 (25 %), x1 (35 %),
  x2 (20 %), x4 (10 %). Mean x1.275, about 38 Flux/s, nearly 4 coal generators; a Miner II uses 20.
* **Batteries are the answer**: the surplus of a good second is kept, so a Miner II on a rod and a battery works 99 % of the
  time, on the rod alone 65 % (`LightningRodTest`). The info line of the rod says so.
* Note: the island worlds clear the weather, so on a player island a rod normally runs on Thunder Quartz only.

## Bastion and Guardian
* **Primordial Thunder Dragon** (`thunder_dragon`, tier 4 curve, ender dragon model at scale 0.8, boss bar yellow). Three
  phases: *Circling* (lightning strikes, dive bombs), *Stormfront* at 65 % (lightning rods as shield crystals, wisps),
  *Eye of the Storm* at 30 % (more strikes, a laser beam, faster dives). Readability rules of GDD 8.7 are checked by
  `GuardianTest`. Spoken lines, resource and component follow the other Guardians (`boss.guardian.thunder_dragon.*`).
* **Component** `thunder_core` (Dragon Core): 35 % drop with the hard pity of the Guardians (4th run, 3rd from Resonance III).
  Also a lightning rod stack and breeze rods.
* **Key** `tempest_key` (Tempest Bastion Key, Forge 1 h: 4 Thunder Quartz, 8 Voidglass, 4 breeze rods).
* **Tempest Bastion** (`tempest_bastion`, palette `storm`, arena radius 36): right-click the seal (a lodestone in front of the
  crying obsidian wall on the Eyrie) or use `/bastion enter tempest_bastion`. Resonance row `thunder_dragon|archipelago|thunder_core|40`.

## Not verified (no Paper, no Docker in the thread that wrote this)
Nothing here was compiled or run against Paper. What was checked: the pure classes (`StormMath`, `PlatformMath`, `PadMath`,
`StormPlan`, `LightningRodTest`) were compiled with javac against stubs and their tests run with JUnit; the icons were drawn and looked at. To do on the local stack: `make plugin-test PLUGIN=survival TEST=Storm*,LightningRod*,Guardian*,Bastion*,QuarrySeed*,ResonanceRules*,SurvivalPlugin*`,
then `/storm go force`, walk the three platforms (do the riders stay on? any rubber banding?), step on each pad (landing
spot, latency), wait for a gust, `/sbgive tempest_key`, right-click the seal, check the fight and its lightning on the real client
(`e2e_client.py` screenshots). The Lightning Rod has a flat sprite only (no 3D block model yet); no sounds or
voices for the Dragon (use the ElevenLabs connector); no quests of its own (`gen_quests.py`).
