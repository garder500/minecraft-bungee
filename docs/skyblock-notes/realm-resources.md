# Realm resources: Cinderheart and Heartwood (GDD 3.2, 15.1)

Feature `realm` (`survival-plugin/.../realm/`), API `api/RealmResourceApi`, schema `V016__realm_resources.sql`, seed `db/seed/realm.sql` (domain `realm`).

- **Items**: `cinderheart` (Cracked Ashes), `heartwood` (Whispering Grove), pack models `vampyuyu:ore/*`.
- **Sources**: table `realm_resource_source` (block break or mob kill) and `realm_world` (world name -> Realm). Drops only happen in a world listed there (seed: `sb_ashes`, `sb_grove`: guessed names, add the real ones when the zones exist).
- **Pity** (GDD 15.1 curve, `RealmPity`): chance p0 until `soft` misses, linear to 1 at `dur`. Counter per player, resource and kind, in the profile section `realm`. Seed values 6 % / 12 / 36 are proposals (the GDD has no row for these two).
- **First use**: sold to the adventurer (60 coins), not tradable on the market. Real uses (sidegrades, modules, runes) are phase 3.
- Bastions and Guardians can call `RealmResourceApi.roll(player, resourceId, kind)` to feed a resource.
- Sprites are placeholders (procedural hearts). The generated concept sheet is meant for `art/concepts/realm.png` (mapping already in `import_textures.py`); the sandbox could not download it.
