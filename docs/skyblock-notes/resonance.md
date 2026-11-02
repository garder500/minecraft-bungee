# Memory Shards and Resonance (survival-plugin, `resonance` feature, issue #34)

GDD 12.4 (ranks), 15.2 (pity), 15.3-15.5 (Shards). Code: `survival/resonance/`, `api/Resonance*.java`. Data: `db/seed/resonance.sql`
(`resonance.*` settings, edited in the panel). Profile section `resonance` (`ResonanceData`). The feature is listed after `boss`.

## Resonance ranks (Bastions are replayable at five ranks)
- Boss health x1 / 1.5 / 2 / 2.5 / 3, damage x1 / 1.2 / 1.4 / 1.6 / 1.8 (`api/Resonance`). No player stats.
- `/resonance rank <1-5>` picks the rank. A boss runs at `min(chosen, best cleared + 1)` (`ResonanceMath.effectiveRank`): clearing
  rank N opens N+1 on that boss. Summon items read it through `ResonanceApi.effectiveRank` and pass it in `BossSession.SpawnOptions.resonance`
  (0 = no Resonance, base stats). `BossKillEvent.getResonance()` carries it.

## Memory Shards
- Source: each Bastion clear pays `baseShards` (per boss, `resonance.bastions` list row `bossId|realmId|component|baseShards`) x
  `(1 + 0.25 x (rank-1))`, x2 on the first clear of the UTC day (no permanent streak). Needs `shards.min-share` (5%) of the damage.
- Failure (GDD 15.4): `ResonanceRules.defeat` pays `defeat-fraction` (20%) of a clear scaled by progress. **Not wired yet**: nothing
  calls it, the boss feature has no "late defeat" signal for the summon fights.
- Use: `/resonance forge <boss>` costs 100 Shards for one component of a boss whose component the player already got once
  (`found`), 5 forges per ISO week (guess).
- Guarantee (GDD 15.2): `BossLootHook` (3-argument `roll` sees what the boss already dropped). The component is handed out on the 4th
  run without one, on the 3rd from rank III. A natural drop resets the count and marks it found. Online participants only.

## Display
`/resonance` (alias `/shards`): chosen rank, Shards, per Bastion (Realm title, `Echo of the Rift` for the Voidwyrm) a five-square bar
of cleared ranks, clears, found state, runs since the last component, forges left. Admin: `/resonance give <n>` (`vampyuyu.sb.resonance.admin`).
No Sky Gate or Quai integration yet.

## Values to measure in play
Base Shards (25-50), rank bonus, forge cost and the weekly limit are guesses. Tests: `ResonanceRulesTest`.
