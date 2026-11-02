# Open design decisions (GDD 24, issue #54)

Answers to the open questions of GDD v3.1 section 24. Items 1-4 are decided and match what is already merged; item 5 is a
**draft proposal**, the GDD says the owner defines Hell Gao, so it is not canon until Garder confirms.

## 1. Order of the Realms: semi-free
* Cracked Ashes then Whispering Grove are the **obligatory opening** (tutorial, balance, first Realm resources, first two
  Bastions). They are the only Realms the roadmap and the quest plan assume in order.
* After that the other six are **free**. Nothing locks a Realm: a Bastion opens with its key (`bastion.md`), so the soft
  gate is power, not a flag. Each Realm shows a recommended Resonance rank; going early is allowed and punished by the numbers.
* Why: replay, coop (a friend can be at a different Realm) and specialisation, with no per-Realm unlock state to maintain.
* Consequence: quests and tutorials of Realms 3-8 must not assume a previous Realm item, only the Starcore line and Resonance.

## 2. Shared world
* **Shared**: the Hub, the sky geography of the Realm gates, server events (Celestial Breath cycle, invasions), the market.
* **Personal or island**: narrative progress (lore fragments, Guardian clears, promotion chains) belongs to the player; coop
  quests stay shared per island (#19).
* **Instanced**: Bastions, dungeons and Hell Gao (already how Bastions and the Catacombs work, 1 to 5 players).
* Realm zones stay open worlds like today; there is no shared "main story" state that one player can spoil for another.

## 3. Local resources of the last five Realms: validated
Chrono Sand, Rot Resin, Thunder Quartz, Pressure Pearl and Soul Ash are the names already in code (`realm/RealmItems`) and in
the pack (`vampyuyu:ore/*`). No rename.

## 4. Second trade: at the Master rank
* A player starts with one trade (Artisan or Warden). A **second slot unlocks at the class Master rank** (`promotion.md`),
  matching the GDD proposal. Trades stay independent of the class and never mandatory (GDD 11.4).
* The trade feature is not built yet (`classes.md`); when it is, add the check `promotion rank == MASTER` before picking the
  second trade, and keep the two levels separate (1-5 each).

## 5. Hell Gao: draft for the owner (not canon)
* **Look**: a shape that is never the same twice, a rift in the sky that borrows parts of the Guardians and of the
  Devourer (a Colossus arm, a Matriarch crown, a Dragon wing), drawn in the Devourer's violet and the Breath's gold.
* **Phases**: 6. Each reuses one earlier mechanic bent out of shape: (1) Colossus lava floor that moves, (2) Matriarch spores
  that heal it unless cleansed, (3) Frost Giant freeze that spreads between players, (4) Thunder Dragon chain lightning with
  wind pads, (5) Leviathan anchors that pull toward the void, (6) Devourer siphon: the arena shrinks and the Reality Pillars of
  the party's islands light up as safe spots.
* It reads the classes present (Vanguard shield windows, Duelist parry windows, Channeler cleanse, Ranger long range marks)
  without requiring any of them (GDD 11.4). Ascension ranks follow GDD 19.4.
* Open for Garder: final look, phase order, and whether phase 6 should tie in with the player's own base.

Nothing here changes code. Not tested (no change to run).
