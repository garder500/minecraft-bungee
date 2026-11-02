# Frozen Heights (Realm 3, GDD 3.1, issue #41)

Realm complete = zone + local resource + system + Bastion + Guardian (GDD 26). Everything follows the Grove / Ashes
pattern; nothing here was compiled or run (no Paper repo in the thread that wrote it).

- **Zone**: world `sb_heights` (created by `IslandFeature.setupWorlds`, rules in the `island_world` row `frost`), built once
  by `FrostRealm` from `FrostPlan` (main island of snow and ice, six peaks joined by ice bridges, crystal spire, frozen
  city fragment, frozen pool, cooling yard, Watcher arch, landing). Marker `island/frost-build.json`; bump
  `FrostPlan.VERSION` to rebuild. Sky Gate: lit by the setting `island.frost.gate-boss` (default `sylvan_matriarch`).
- **Cold** (GDD 7.5, 3.1): in the zone `freezeTicks` builds up (never below 3 hearts) until the player has the Warmth
  affinity or wears the full Frost Starcore. **Low gravity** (GDD 12.5): Jump Boost II refreshed every second, fall
  damage cancelled in the zone; Slow Falling when sneaking in the air with the Stasis affinity. No higher Jump Boost
  level (cheating and void falls); the void rescue is the old one (below y-70, back to the landing).
- **Affinity** (`island/frost.json`): ice blocks cut in the zone, 40 -> Warmth, 200 -> Stasis. Events `heights_enter`,
  `heights_affinity:1|2`. Quests: category `heights` (`heights_01`..`heights_09`, unlocked by `grove_queen`).
- **Stasis Crystal**: custom item `stasis_crystal`, drops from blue/packed ice, amethyst clusters, strays and polar bears
  in `sb_heights` (seed `realm.sql`, same pity curve as the other two; a proposal, tune in the panel). Texture is a
  procedural 32x32 sprite (the generated image could not be downloaded in the thread).
- **Guardian**: `frost_revenant`, shown as the **Boreal Watcher** (technical id kept, GDD 12.2). HP unchanged: tier 3 on
  the 8.7 curve (`hp_auto`, 25 920). Component `frost_core` (Watcher Core, hard pity 4 / 3), resource 2-4 Stasis
  Crystals, 6 on the first clear, spoken lines: `boss.guardian.frost_revenant.*`.
- **Bastion**: `heights_bastion`, key `frost_heart` (renamed Heights Bastion Key, no longer summons in the open),
  palette `frost` (`BastionArena`).
- **Set variant**: `frost_starcore_*` (`scripts/gen_sidegrades.py items frost_starcore`): Might x0.85, Ward x0.95, the
  rest in Vitality, cold resistance on every piece (a stat key for the later cold gauge), full set "Glacial Poise": freezing
  damage is cancelled and falls hurt half as much. Forged from the Starcore piece + Stasis Crystals.
- **Module**: Cryo Module (`cryo_module`, 3 Stasis Crystal + Miner I + 8 blue ice + 8 redstone), Miner speed x1.3, Flux
  use x1.3 (settings `quarry.machines.module.cryo.*`), 30 % of cycles also give a packed ice.
- **Realm rune**: Stasis (weapon, `stasis_crystal`): a Surge slows (Slowness III) and freezes the target
  (`runes.stasis.slow-ticks`). The Runecraft menu now puts Realm runes in columns 5-6 (up to 6 stat + 6 Realm runes).
- **Migration V021** rebuilds `island_world` (its CHECK only allowed hub/world/nether/end, so the `grove` row of #30 could
  not be inserted) and `bastion` (palette CHECK was `cinder|grove`) with open CHECKs.
- Not done: advanced cooling as a standalone machine (only the module carries it for now), the cold gauge as a HUD,
  puzzles of stasis, sounds, the Watcher arena beyond the Bastion floor.
