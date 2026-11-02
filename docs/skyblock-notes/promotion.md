# Class promotions (Adept, Master)

GDD 11.5, issue #40. Package `survival-plugin/.../promotion`, API `api/PromotionApi`, migration `V018__promotion`, seed
`db/seed/promotion.sql` (domain `promotion`, editable in the panel, `/promotion reload`).

## Flow

1. The Master NPC of the class (`/promotion npc add <class>` as OP, in the Hub area; `npc remove` / `npc list`) or `/promotion`
   shows where the player stands.
2. Unlock: Adept needs the Echo of the Rift defeated (`promotion.adept.echo-boss`) and `promotion.adept.bastion-clears` Bastion
   clears; Master needs `promotion.master.guardians` different Guardians cleared (from the `resonance` profile section).
   The Master default is 2 because only two Realms exist: raise it to 4 (half of the 8 Realms) when they all do.
3. The Master reads the profile (counters kept from the first day: kills, crafts, class actives, teamwork kills, Realm
   entries, solo / group bosses; `PromotionRules.score`) and offers the two best-fitting trials (Combat, Craft, Exploration,
   Teamwork, Boss hunting). The player picks one: `promotion_chain` rows, 3 to 5 `promotion_step` rows each.
4. Adept: at the end of the chain the player chooses one of the two `promotion_spec` of the class (never imposed).
   Master: the chain gives the third capacity of the class (`promotion_ability`), cast with `/class master`.
5. A class change (`ClassService.set`) puts the rank back to Novice.

## Effects

- Specialization: added to the class bonus, always inside the GDD 8 meta caps (`ClassBudget.with`); percents change the active's
  cooldown, cost and power (Bulwark duration, Rift Step distance, Burst radius, Volley arrow damage).
- Master capacities: Vanguard Rallying Cry (Resistance + Absorption around), Duelist Afterimage (next hits are Surges),
  Channeler Rift Nova (big burst), Ranger Hail of Arrows (9 arrows).
- Nothing requires a rank (GDD 11.4).

## Known limits

- The Master chain gives one fixed capacity per class; the "choose one of two" is the Adept specialization.
- No hotkey for `/class master`; kills are not checked against spawner farms.
- Step numbers and ability numbers are first guesses.
