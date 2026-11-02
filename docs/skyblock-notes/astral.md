# Astral Interiority / Intériorité Astrale (Act III, GDD 4.4, 4.5, 26 final, issue #49)

The inside of the Devourer, reserved to the players who defeated the Guardian of **every** Realm. Package `astral/`
(feature `astral`, listed after `stations`), world `sb_astral` (void generator, midnight, border 800), built once by
`AstralRealm` from `AstralPlan` (marker `realms/astral-world.json`, bump `AstralLayout.VERSION` to rebuild).
Progress in `realms/astral.json` (`AstralProgress`). No SQL table, no migration.

## Access
`/astral go` checks `BossApi.kills` of each Guardian of `boss.guardian.*` (each one names its Realm) and lists the Realms
that are missing. `/astral go force` (permission `vampyuyu.sb.astral.admin`) skips the check, and the setting
`realm.astral.require-guardians` (false) opens it to everyone. There is no Sky Gate: the Gates are the eight Realms.
The way in is the command for now (a mouth in the Quai after the Great Disillusion, GDD 4.4, is a later step).
`AstralApi.canEnter` / `survived` are published for the Raid. Sitting on the Throne calls `ParagonApi.markAstralCleared` (the Paragon feature, #52).

## Regions (`AstralLayout`, west to east, relative to y = 100)
1. **Rift Landing** then the **Sea of Devoured Ideas**: a sea of liquid memory (water over deepslate), five ruined islets
   (Drowned Library, Amphitheatre of Echoes, Unlit Lighthouse, Cradle of Names, Shore) joined by glass threads. Swimmers get
   Water Breathing and a current pushing back toward the Landing.
2. **Nervous Tree**: trunk of hyphae, nine platforms on a spiral (100 degrees, 10 blocks higher each), the Crown at y = 100.
   **Synapses** (sea lantern with light blue terracotta pad) are guided lifts: stepping on one carries the player at
   0.55 blocks/tick straight to the next platform (or the Crown). A lift lands 3 blocks from the next pad, crouching cancels
   it, it ends after 60 ticks at most.
3. **Gravitational Throne**: the Nexus, a causeway (half ring of radius 22) around a singularity (black sphere and accretion
   ring), five **Anchors** (lodestone) and the Throne (quartz stairs on a dais).

## Movement rules (GDD 12.5)
* Slow Falling is renewed every second everywhere: no fall damage.
* A player who falls 30 blocks under the last place they stood on, or under y = -40, goes back to it (last grounded place
  outside the pull; on the Throne side, the last lit Anchor, else the Nexus).
* **Pull** (`AstralMath.pull`): an impulse toward the centre, linear from 0 at 45 blocks to `realm.astral.pull-max` (0.22) at
  the centre, ceiling 0.35. **Telegraph**: every 15 s a surge of 3 s (x2.5), announced 2 s before (action bar and sound).
  **Counters**: crouching halves the pull, a lit Anchor cancels it within 4 blocks.
* Inside 11.5 blocks of the centre the singularity "lets go": the player is carried back to the checkpoint, no damage.
* Nothing can be built or broken (bypass permission excepted).

## Quests (13.3 pattern, paid once, coins)
Exploration (9 places, lore on discovery, bonus for all), progression (10 synapses fired, bonus), world (5 Anchors lit by
right-click, bonus), narrative (the lore lines and the entry text). **The Throne**: right-click it after reaching the Crown
and lighting every Anchor: the player is a *survivor* (`AstralApi.survived`), the condition of the Paragon (issue #52).
`/astral` shows the journal; `/astral build` rebuilds; `/astral reset <player>` resets one player.

## Not done / not verified
No Paper here: nothing was compiled or run in the game. The tunable numbers (pull, lift speed, current, coins) are
proposals, never playtested; the guided lift and the pull are the first things to feel on `make dev-server`. No textures,
sounds or voices were generated. The Raid (issue #50) is not part of this issue.
