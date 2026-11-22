# Realm resources: Cinderheart, Heartwood and Thunder Quartz (GDD 3.2, 15.1)

Feature `realm` (`survival-plugin/.../realm/`), API `api/RealmResourceApi`, schema `V016__realm_resources.sql`, seed `db/seed/realm.sql` (domain `realm`).

- **Items**: `cinderheart` (Cracked Ashes), `heartwood` (Whispering Grove), pack models `vampyuyu:ore/*`.
- **Sources**: table `realm_resource_source` (block break or mob kill) and `realm_world` (world name -> Realm). Drops only happen in a world listed there (seed: `sb_ashes`, `sb_grove`: guessed names, add the real ones when the zones exist).
- **Pity** (GDD 15.1 curve, `RealmPity`): chance p0 until `soft` misses, linear to 1 at `dur`. Counter per player, resource and kind, in the profile section `realm`. Seed values 6 % / 12 / 36 are proposals (the GDD has no row for these two).
- **Pressure Pearl** (Abyssal Depths, #45): fished, see `depths.md`.
- **First use**: sold to the adventurer (60 coins), not tradable on the market. Real uses are phase 3: runes are done (`runes.md`: Scorch from Cinderheart, Verdant from Heartwood), sidegrades and modules are not.
- Bastions and Guardians can call `RealmResourceApi.roll(player, resourceId, kind)` to feed a resource.
- Sprites: Cinderheart, Heartwood and Stasis Crystal are imported (`art/concepts/realm.png`); Soul Ash and Pressure Pearl are 32x32 procedural drawings (`art/realm32.py`), the Marsh items too (`art/marsh_items.py`).
- **Thunder Quartz** (`thunder_quartz`, Tempestuous Archipelago, `storm.md`): nodes of the zone, pity rows for `sb_storm`, fuel of the Lightning Rod and key of the Tempest Bastion.
