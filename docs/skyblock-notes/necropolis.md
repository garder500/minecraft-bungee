# Ethereal Necropolis (Realm 8, GDD 3.1, 3.2, 12.2, 15.3; issue #46)

A complete Realm: zone + local resource + system + Bastion + Guardian.

- **Zone**: world `sb_necropolis` (void world, `island_world` seed row `necropolis`), built once by `NecropolisRealm` from
  `NecropolisPlan` (main island of deepslate and soul soil, six islets: ossuary, soul brazier, grave field, blackstone bridges,
  dais arch of the Judge, soul sand field, landing). Marker `island/necropolis-build.json`; bump `NecropolisPlan.VERSION` to rebuild.
- **Gate**: `SkyGateApi.setDestination(Realm.NECROPOLIS, ...)`. Lights for the players who fought the boss of the setting
  `island.necropolis.gate-boss` (default `abyssal_leviathan`, a guess: the Guardian of the Realm before it). `/gateadmin light <player> necropolis` works.
- **Mechanics**: the fading of the memories (Darkness pulses, no damage) until affinity tier 1 *Lucidity*; affinity points =
  undead killed in the Necropolis (40 -> Lucidity, 200 -> *Recall*: one Memory Shard every `island.necropolis.recall-kills` = 10 undead kills,
  via `ResonanceApi.grantShards`). Void rescue to the landing. Quest events: `necropolis_enter`, `necropolis_affinity:1|2` (no quest uses them yet).
- **Soul Ash** (GDD 3.2, name still to validate): `realm_resource` row `soul_ash` (6 % / 12 / 36 like the other two), from soul sand, soul soil, bone
  blocks, skeletons, wither skeletons and zombies, only in `sb_necropolis`. Sold by the Adventurer at 60 (never traded on the market).
- **Memory Module** (Miner module, `MachineModule.MEMORY`): speed x0.7, Flux x1.3, 3 % of cycles distil one Memory Shard for the owner.
  Forge: 3 Soul Ash + Miner I + 8 soul sand + 8 redstone (`quarry.machines.module.memory.*`).
- **Bastion**: `necropolis_bastion` (migration V020 rebuilds the `bastion` table to allow the `necro` palette), Guardian `crypt_lord`
  (technical id kept, now named *Judge of Lost Souls*), key `judge_seal` (forge: 4 Soul Ash + 8 Starcore + 8 bone blocks). The Guardian has
  its Guardian rows (spoken lines, Soul Ash 2-4 per kill, 6 more on the first clear, component `judge_core` with the hard pity). The Resonance
  row `crypt_lord|necropolis|lord_bone|25` already existed. The dungeon's own Crypt Lord (MobRoster) is unchanged.
- **Sprites**: `judge_core` and `judge_seal` come from `art/boss_items.py`. `soul_ash` is a hand-drawn placeholder (the ElevenLabs image could not
  be downloaded from this sandbox): redo it from a concept sheet with `import_textures.py` when possible.

## Not verified
Written without Paper or Docker (no compile, no tests run, nothing seen in game). To run: `make plugin-test PLUGIN=survival
TEST=NecropolisTest,GuardianTest,BastionConfigTest,EnergyNetworkTest` then the whole suite; check migration V020 on a database that already has panel-edited Bastions.
Not done: quests for the Realm, a Necropolis Starcore sidegrade, Memory Shard processing beyond the module, "boss memories" and the story content, end-game difficulty tuning.
