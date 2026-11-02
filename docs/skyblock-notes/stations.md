# Stations: Alembic, Crucible, Ranch and contracts (GDD 10.1, 17, 22; issue #38)

Feature `stations` (`survival-plugin/.../station/`), data domain `stations` (migration `V019__stations.sql`, seed
`db/seed/stations.sql`, panel-editable). Everything numeric is a guess to measure in play (GDD 23).

## Stations
Craftable items placed on the island (vanilla blocks marked in the chunk PDC, like the Forge): Alembic (brewing stand),
Crucible (cauldron), Ranch (composter). Right-click opens the menu, `/alembic`, `/crucible`, `/ranch` work on the island too.
Recipes are shapeless with vanilla ingredients (see `StationFeature.addRecipes`).

- **Alembic**: a queue per player (`station.alembic.slots` run at once, `station.alembic.queue` fit). Brews finish while the
  player is away, then are collected as flasks (honey bottle with the recipe id in the PDC). Families: tonic (potion
  effect), oil (temporary element on every weapon: `StationApi.oilElement`, read by `LadderCombat.weaponElement`), draught
  (potion effect). `alembic_recipe.min_tier` is the generator tier needed.
- **Crucible**: melts the held item back into 60% of its Forge ingredients (`station.crucible.return-rate`); boss components
  (`station.crucible.components`) come back whole. Duplicate components (all but one copy, and only for a component the
  player already found) turn into Memory Shards (`station.crucible.duplicate-shards` each, via `ResonanceApi.grantShards`).
  Only items made by the Forge (`forge_recipe`) can be melted.
- **Ranch**: one clock per player (starts when the first Ranch is placed). Output per product (`ranch_product`) accrues while
  away up to `station.ranch.storage-hours`, multiplied by `1 + tier-bonus * (generator tier - 1)`, and a product needs its
  `min_tier`. Marrow (`custom:marrow`) feeds the oils and some draughts.
- **Contracts**: the Hub's Contract Hunter runs `/contracts`. Each UTC day a player gets `station.contracts.per-day`
  contracts drawn from `contract` within their generator tier (same draw all day). Kinds: kill (mob type), deliver (items
  taken at turn-in), boss (any `BossKillEvent` the player damaged). Rewards: coins (capped per day), Memory Shards, an item.

## Warden trade
The Warden trade (TradeApi) is wired: +20% Ranch output, a chance of one more flask per brew, and trade XP for each collection.

## Not done
- Tonics use vanilla potion effects, not the elemental resistance of `LadderDefense`; no "Aether" draught (no Aether API).
- No custom models/icons: stations and flasks use vanilla ones.
