# Whispering Grove (Realm 2, GDD 3.1, issue #30)

- World `sb_grove` (void world, created by `IslandFeature.setupWorlds`, rules in `island_world` seed row `grove`).
  Built once by `GroveRealm` from `GrovePlan` (pure data: main island, six islets with root bridges, farm terrace,
  mushroom, heartwood tree, spore pool, arch of the Mycelial Queen, landing). Marker `island/grove-build.json`
  stores the plan version; bump `GrovePlan.VERSION` to rebuild.
- Gate: `GroveRealm` registers the destination with `SkyGateApi.setDestination(Realm.GROVE, ...)`. The Gate lights
  for the players who fought the boss in the setting `island.grove.gate-boss` (default `magma_warden`, Realm 1
  guardian); `/gateadmin light <player> grove` also works.
- Mechanics: spore haze (Hunger I, no damage) until Respiration; affinity points = fully grown crops harvested in
  the Grove (`island/grove.json`): 40 -> tier 1 Respiration (no haze), 200 -> tier 2 Growth (bone meal grows a crop
  one more stage, any world). Void rescue back to the landing below y-70.
- Quests: category `grove` (main chain, ids `grove_01`..`grove_09`, unlocked by the Echo of the Rift kill), events
  `grove_enter`, `grove_affinity:1`, `grove_affinity:2`.
- Not done: the Heartwood item and its uses (needs the items catalogue), the Sylvan Matriarch arena (boss still
  summoned with its item), textures/sounds. Not compiled or run when this was written (no Paper repo access).
