# Raid of the Devourer (GDD 4.6, 8.7, 12.5, issue #50)

The final boss of the main story. It is a **Bastion** (`devourer_raid`, `win.vampyuyu.survival.bastion`) with three extra rules, so it
reuses the instance, party, key, lives, time limit and reward plumbing of `docs/skyblock-notes/bastion.md`. The boss is `devourer`
(`boss.sql`), spawned by `BossApi.spawnFight` only (no summon rule).

## Player flow
* Entry: `/bastion enter devourer_raid` (or `BastionApi.enter`). The leader holds an **Event Horizon Key** (`devourer_sigil`), forged
  from **one core of each of the eight Guardians** (`quarry.sql`, 2 h). Every member must have **cleared the eight Bastions**
  (`bastion.raid.requires`, profile section `bastion`); the refusal names the member and the missing Bastions.
* Arena: `sb_bastion_<n>`, palette `singularity` (radius 40, up to 5 players, 3 lives each, 25 min). Same void rescue as every Bastion.
* The key is spent when the Devourer wakes, returned on a server fault, like every Bastion key.

## The fight (health about 52 000 at tier 5, x(1 + 0.5 (p - 1)) capped x3, enrage after 9 min, phase damage x1.0 / x1.2 / x1.4)
1. **Event Horizon** (100 %): manipulation of time and distance. `time_echo` marks where each target stands (cyan: the past) and where
   they are walking (violet: the future); both strike after the telegraph. `vacuum` pulls everyone in, then detonates. `ground_slam`.
2. **Metamorphic Assimilation** (66 %): it wears the eight Guardians' mechanics, one form each: fire ring (Ashes), webs (Grove), bone
   cage (Necropolis), ice spikes (Heights), blink behind (Citadel), poison clouds (Marsh), tidal wave (Depths), lightning (Tempest), plus `time_echo`.
3. **Naked Singularity** (33 %): the arena falls ring by ring (`bastion.raid.collapse-seconds`, 3 s) down to a core of radius 9
   (`RaidRules`, `BastionArena.collapseRing`); `weightless` (Slow Falling, short Levitation under 9 blocks of ceiling, a capped
   current around the boss), `chain_anchors` (attraction toward the boss; break the anchors to cut it), `vacuum`, `laser_beam`, `time_echo`.
   Players who slip off are put back on the core (`RaidRules.safeSpot`), nothing kills outright.

New boss abilities: `time_echo` and `weightless` (`boss/Gravity.java`, `AbilityType`). They reuse the caps of the movement prototype
(`MovementMath`, #47). `BossApi.Fight.phase()` tells the Bastion service which phase the boss is in.

## Rewards (GDD 16.2: no extra stats)
* Boss coins 1 000 by damage share + **4 000 coins the first time** a member clears it (`bastion.raid.first-clear-coins`, 5 000 in all at most).
* Drops: **Singularity Relic** (trophy, every participant with 5 % of the damage), **Horizon Lens** (cosmetic, 8 %). Neither has stats.
* No Memory Shards, no component: the Raid is not a Guardian. Profile `bastion` records runs, clears and best time like any Bastion.

## Data
`bastion` row `devourer_raid` and `bastion.raid.*` settings (`id`, `requires`, `first-clear-coins`, `collapse-phase`, `collapse-seconds`) in
`bastion.sql`; `devourer` rows in `boss.sql`; `devourer_sigil` recipe in `quarry.sql`; items in `BossItems`. No migration.

## Not verified
Nothing was compiled or run (no Paper/Docker in the thread; only a syntax-only `javac` pass). To do on the dev server: `make plugin-test
PLUGIN=survival TEST=RaidRulesTest,RaidBossTest,BastionConfigTest,QuarrySeedTest,ShippedConfigTest`, then a run with `/sbgive devourer_sigil` and a profile
that has cleared the eight Bastions. Guessed values to measure: health and damage (12 base) against the tier-5 kit, the 31 rings at 3 s,
the key cost (eight cores), the 4 000 first-clear coins, the `time_echo` lead of 7 blocks, and the feel of the current at 100-200 ms latency.
There is no Intériorité Astrale yet (the three regions of § 4.5): the Raid opens from the Bastion menu/command for now.
