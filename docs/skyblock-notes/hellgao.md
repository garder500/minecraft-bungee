# Hell Gao, the Anomaly (GDD 19.4, issue #53)

The secret post-game superboss. It is **a Bastion** (`hell_gao_bastion`, palette `rift`, 2 to 5 players, 2 lives each) whose
Guardian is the boss row `hell_gao`; `hellgao/` only guards the entry and pays the rewards. Nothing in it is a stat.

## Defaults chosen by the implementation (the owner has not defined them: change them in the data)

- **Appearance**: a rigged Blockbench model (`resource-pack/art/blockbench/bosses/hell_gao.bbmodel`, 31 bones, 22 clips: idle,
  walk, attack, hurt, ability, phase, death and one per ability), a legless lich over a rift star: a skull split in two that peels
  open on a magenta eye (phase, laser_beam, blizzard, death), visible shoulder, elbow, wrist and knuckle joints, a talon arm and a bone-scythe arm, two mantis arms over a beating
  heart, and the eight Realm shards as dark blades in its back (the Marshes one snapped). On a hidden 2.4x Enderman (its eyes
  glow through invisibility), display scale 3.4 (`BossSkinService.LOOKS`), purple boss bar. Blockbench does not save
  `light_emission` in the Generic format: re-save the project with the cube-shading flag of the format on during
  `Codecs.project.compile`, or the 72 glowing cubes lose their glow.
- **Six phases**, each borrowing a rule from a Guardian or the Devourer and bending it (`db/seed/boss.sql`):
  Between Pulses (teleport behind + void zones), Borrowed Chains (Leviathan anchors + Judge's bone cage),
  Inverted Storm (Thunder Dragon crystals and lightning + dash), Frozen Hour (blizzard + ice spikes + fire ring),
  Devoured Gravity (vacuum + whirlpool + tidal wave), The Pulse Stops (everything at once, laser, soul drain).
- **Window between two pulses**: with the Celestial Breath (`BreathApi.millisToNextPulse`, added for it), the last
  `hellgao.pulse.window-seconds` (300) before the next pulsation while no event runs. Without the Breath, the last 300 s of every
  `hellgao.pulse.period-seconds` (3600) of the server clock. `/hellgao admin window open [minutes]` forces it for tests.
- **Key**: the Breath owns the Interpulsation Fragments and assembles Breath keys (`BreathApi`). `/hellgao claim` exchanges one
  Breath key (`consumeKey`) for the **Interpulse Key** item, which is the Bastion key the party leader spends.
- **Paragon**: every party member must be a Paragon (`ParagonApi.isParagon`, the `paragon` feature of #52). An admin makes a
  tester a Paragon with the Paragon commands.
- **Ascension**: 0 = plain fight. 1 to 5 = Guardian health x3.5 rising to x5, damage x2 rising to x2.6 (`HellGaoMath`), on top
  of the Resonance rank (always I here). Needs `hellgao.ascension.resonance-min` (3) Guardians cleared at Resonance V; rank N
  opens once N-1 is cleared. The leader's choice (`/hellgao ascension <0-5>`) applies to the party.
- **Rewards**: titles (`hellgao.titles`), an interpulse particle aura (`/hellgao aura`, separate from the rank 9 aura of `/paragon`), one lore fragment per clear
  (`/hellgao lore`), Paragon points (`hellgao.paragon-points.*`, through `ParagonApi.addPoints`). No coins, no XP, no
  drops, no Memory Shards.
- **Secret**: `/bastion list` hides it from players without a key or a clear; `/hellgao` tells a stranger "You feel nothing".

## Extension points added for it

- `api/BastionHook` (refuse an entry, hide a Bastion, scale the Guardian, react to open/clear), `BastionService.addHook`.
- `BossApi.spawnFight(..., hpMultiplier, damageMultiplier, onEnd)` and the two extra `BossSession.SpawnOptions` fields.
- Bastion palette `rift`.

## Not done / to define

- **Reaction to the classes present** (GDD 19.4) is not implemented: needs a phase hook in the boss engine and a design.
- The "no hit above 35% of the reference player's HP" cap is not enforced in code, only kept in mind in the ability multipliers.
- Never run: no Paper in the build threads. `make plugin-test PLUGIN=survival TEST=HellGaoMathTest,HellGaoConfigTest,HellGaoBossTest`
  and a real fight (`/hellgao admin give <you> key`, `window open`, a Paragon account, two players) are still to do.
