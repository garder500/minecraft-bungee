# Classes (Novice rank)

GDD 11. Package `survival-plugin/.../classes`, API in `api/ClassApi`, `api/PlayerClass`, `api/LadderApi`.

- Choice: `/class` (clickable list at first join), `/class choose <vanguard|duelist|channeler|ranger>`, once. The class-change
  quests call `ClassApi.set`. `/class reset <player>` (OP). Stored in the profile section `classes` (`vocation`, `rank`).
- Active: sneak + swap hands (F) or `/class cast`. Cost in Aether (reserve 100 + class bonus, +2/s, in memory) and a cooldown.
- Bonuses sit INSIDE the GDD 8 meta caps (`ClassBudget`: 100 PV, 25 Might, 25 Dexterity). Health = PV / 5, like the ladder armor.
- Combat goes through `LadderCombat` (Might, Attunement, Surge every N hits, Surge power 2.0 + Dex/200); abilities that
  deal weapon damage use `LadderApi` (Elemental Burst, Gale Volley arrows at 40%).
- Rule 11.4: nothing asks for a class; with none chosen every modifier is neutral.
- Adept and Master ranks: see `promotion.md`. Not done here: Paragon, trades (Artisan, Warden), a menu and HUD for Aether.
