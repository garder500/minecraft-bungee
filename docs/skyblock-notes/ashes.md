# Royaume des Cendres Fissurées / Cracked Ashes (GDD 3.1, issue #29)

Package `realm/` (feature `realms`, listed after `boss`). World `sb_ashes` (Nether environment, void generator, border 500),
built once by `AshesRealm` from `AshesPlan` (marker `realms/ashes-world.json`, bump `AshesPlan.VERSION` to rebuild).

- **World decision (GDD open question 2)**: one shared world, not an instance per island. Nothing in the Ashes belongs to an
  island yet and the nodes/vents are per-player in progress, not in the world. Bastions and the Guardian fight (own issues) may
  instance later: only `AshesRealm.spawn()` (the Gate destination) would change.
- **Opening**: killing boss `voidwyrm` (the Echo) lights Gate 1 for the killer and every damager (`BossKillEvent`).
  Travel is `SkyGateApi.travel`; `/ashes go` does the same, `/ashes go force` (admin) skips the Gate. Back: `/quai`, `/hub`.
- **Zone**: Ember Landing (cooled, no building), five islets joined by bridges (Needle, Slag Foundry, Cinder Fields, Ember Tarn,
  Rib of the Colossus), the Caldera with the sealed Sarcophagus door (placeholder for the Colossus fight), five contained seas of magma.
- **Heat** (`HeatMath`): tiers 0/1/2/3 (landing / islets+bridges / tarn / caldera, +1 beside lava or magma). Needed resistance 0/20/45/75.
  Resistance = worn armor (netherite 12 per piece, gold/diamond/copper 8, iron 6, chain 4, leather 2) + item PDC double
  `heat_resist` (for Sunstone gear) + 15 after Ashen Attunement. Fire Resistance = immune. Too little: slowness and damage every 2 s,
  never below 1 heart. Falling off the Realm brings the player back to the Landing (non lethal).
- **Quests** (`AshesProgress`, saved in `realms/ashes.json`, `/ashes` journal): exploration (5 landmarks, 60 coins each, +400),
  narrative (a lore line per landmark), progression (12 Cinderheart from the `gilded blackstone` nodes, 300 coins),
  world (stabilise 3 heat vents with 3 Cinderheart each, 150 coins each, +600, then the Attunement).
- **Cinderheart** (`cinderheart`, item of `RealmItems`, #31): the nodes give it for sure (pickaxe, back after 90 s); other Ashes blocks and mobs still roll the pity curve. The Miner +25 % per Block bonus is not wired yet.
- Not done here (own issues): the Colossus, Bastions, Sunstone gear with `heat_resist`, textures and sounds for the Realm.
