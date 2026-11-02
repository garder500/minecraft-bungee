# Putrid Marshes (Realm 5, GDD 3.1, 3.2, 7.5, 24.3; issue #43)

Package `marsh/` (feature `marsh`, listed after `ashes`). Written without a Paper build: nothing here was compiled or run.

- **World** `sb_marsh` (shared, created by `MarshRealm` with `WorldSetup`, midnight, border 140), built once from `MarshPlan`
  (marker `realms/marsh-world.json`, bump `MarshPlan.VERSION` to rebuild): sunken bog island, five pools, four islets on
  causeways (Vat Works, Landfill, Weeping Mangrove, Overflow Pit), three **Rot Vats** (water cauldrons), landing at the Gate.
- **Gate**: `SkyGateApi.setDestination(Realm.MARSHES, ...)`; lit by defeating the boss in setting `island.marsh.gate-boss`
  (default `sylvan_matriarch`, the previous Guardian: a guess, edit in the panel).
- **Rot Resin** (`rot_resin`, `RealmItems`, seed `realm.sql`): same pity shape as the other local resources (6 % / 12 / 36, a
  proposal), from muddy mangrove roots, slime blocks, sculk, slimes, witches, drowned. The name was "to validate" in the GDD.
  Vat salvage rolls use the pity kind `vat`.
- **Miasma**: Weakness I (no damage) until the **Filtered** affinity. **Purification** (`MarshProgress`, `realms/marsh.json`):
  points from salvaging, 40 = Filtered, 200 = **Reclaimer** (a Vat needs 6 points per roll instead of 8, `Salvage`).
- **Rot Vats (expired item recovery, recycling)**: right-click a Vat with a stack of rotten flesh, spider eyes, poisonous
  potatoes, bones, slime balls (1 point), pufferfish, fermented eyes (2), suspicious stew (3). Leftover points are kept (`carry`).
- **Creuset**: the Crucible (station feature) gives back 15 % more (`station.crucible.marsh-bonus`) when used in the Marsh;
  Bog Ichor (resonance component of the Marsh Bastion) is a Crucible component (100 % back, duplicates become Shards).
- **Module**: Recycler Module (`recycler_module`, forged from 3 Rot Resin + a Miner): speed x0.9, Flux x0.7, 15 % of cycles give
  a second copy of what was mined (`quarry.machines.module.recycler.*`).
- **Bastion** `marsh_bastion` (palette `marsh`; the palette CHECK was already opened by the Heights migration V021) and **Guardian** `mire_abomination`
  (Abomination Fangeuse, tier 3, 28 080 HP, three phases: poison and wither clouds, slams, acid zones, Rotlings). Key
  `mire_cyst` (forged: 24 Bronzite, 4 Rot Resin, 16 slime balls, 32 rotten flesh), hard-pity component `mire_core`, resonance
  component `mire_ichor`, first clear and kill give Rot Resin like the other Guardians.
- **Quests**: category `marsh` (`marsh_01`..`marsh_07`, `scripts/gen_quests.py`), events `marsh_enter`, `marsh_salvage`,
  `marsh_purify:1`, `marsh_purify:2`.
- **Not done / assumptions**: "industrial processing chains" are only the Vat -> Resin -> Recycler/key forge chain; no
  Sidegrade armor for the Marsh (not in the issue); item sprites are procedural placeholders (`resource-pack/art/marsh_items.py`,
  the ElevenLabs sheet could not be downloaded in the sandbox); the Guardian numbers are a first pass; the boss rows use
  list positions 14 in `boss.guardian.ids`, `resonance.bastions` and `station.crucible.components` to avoid clashing with the
  other Realm threads.
