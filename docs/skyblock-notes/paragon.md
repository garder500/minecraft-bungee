# Paragon (GDD 11.5 and 19.1, issue #52)

Feature `paragon` (`survival-plugin/.../paragon/`), data in the `paragon` domain (`db/seed/paragon.sql`, migration V023).

- **Becoming a Paragon.** `ParagonApi.markAstralCleared(uuid)` is called by whatever ends the Astral Interior raid (not in this
  branch: the raid is another issue). It sets the flag and pays `paragon.astral-clear-points` (1). Staff can do the same with
  `/paragon astral <player>`.
- **Transcendence ranks.** Ten ranks in `paragon_rank`, reached with Paragon points (`ParagonApi.addPoints`: the raid, Hell Gao,
  future challenges). Each rank gives one reward: TITLE, AURA (cosmetic particles, `ether`, `starlight`, `interpulse`), UTILITY
  (`island_members`: extra co-op slots on the island of the owner, wired in `IslandService.memberSlots`) or LORE. No stat (GDD 9.2).
  `ParagonApi.perk(uuid, key)` sums the UTILITY rewards; other systems can read more keys.
- **Ultimate.** A Paragon who is a Master of their class has `/class ultimate` (`paragon_ultimate`, one per class): the same shapes
  as the Master capacity (Vanguard rally with Resistance II and 8 Absorption hearts, Duelist 8 Surges, Channeler 400% blast,
  Ranger 15 arrows) with a 300 s cooldown and 80-100 Aether. Numbers are first guesses.
- **Commands.** `/paragon` (status), `/paragon aura <id|on|off>`, `/paragon title <title>`; admin (`vampyuyu.sb.paragon.admin`):
  `grant <player> <points>`, `astral <player>`, `reset <player>`, `reload`.
- **Titles** are exposed by `ParagonApi.title(uuid)`; nothing displays them in chat or tab yet.
- **Hell Gao** (GDD 19.4) checks `ParagonApi.isParagon`.
