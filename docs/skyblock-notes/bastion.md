# Bastions (GDD 12.1, 12.3, 14.4, 15.4) — survival-plugin, package `win.vampyuyu.survival.bastion`

The Bastion is the fortress of a Realm's Guardian: an **instanced** fight for a party of 1 to 5, opened with a **key**.
Phase 2 ships two: the **Ashes Bastion** (Lava Colossus = boss `magma_warden`, key `magma_core`) and the **Grove Bastion**
(Mycelial Queen = boss `sylvan_matriarch`, key `matriarch_egg`). Technical ids are unchanged; the visible names are the canon.

* **Rift of the Echo** (`echo_rift`, boss `voidwyrm`, key `echo_key`, palette `rift`): dungeon 0. It is opened by right-clicking
  the Echo Key at the Rift altar of the Hub (the altar of the `voidwyrm` summon row; any Bastion key whose summon row is in
  `altar` mode does the same at its altar) or with `/bastion enter echo_rift`. The Echo used to wake in the shared Hub, where its
  attacks hit every player around. Not in `bastion.raid.requires`; no Resonance row, so no Memory Shards.

## Player flow
* `/bastion` (alias `/bst`) lists the Bastions (key held or missing), `/bastion info <id>`, `/bastion enter <id>`, `/bastion leave`.
  Another feature (a Realm gate, a menu) opens one with `BastionApi.enter(player, id)`.
* The party leader enters (`/party` of the dungeon feature; with no party the player goes alone). Everybody is moved to
  `sb_bastion_<n>`, a void world with a round platform built from code (`BastionArena`, palette `cinder` or `grove`), and a
  countdown runs (`bastion.countdown-seconds`). When it ends the leader's key is **spent** and the Guardian wakes through
  `BossApi.spawnFight`. A leader without the key at that moment, or a refused spawn, closes the Bastion and nothing is spent.
* Keys (GDD 12.3): the former summon items (`magma_core`, `matriarch_egg`, renamed). Spent on entry, **returned on a server
  fault** (shutdown, world unload, error: the boss feature's own `refundItem`), cannot be placed or eaten (`BossItemGuard`), and
  no longer summon anything in the open world (`SummonListener` refuses a Bastion key). They still drop and are forged as before.
* Rules inside: no building, no pearls/chorus/teleport commands, only `/bastion /party /pc /msg ...`; 3 lives per player
  (`deaths_allowed`), then spectator; a fall into the void brings you back; all eliminated or gone = defeat; time limit
  `time_limit_seconds` (20 min). The Guardian's health is fixed: it does not grow with the party size (2026-11-23).

## Rewards and coop (GDD 14.4, 15.4)
Nothing is paid twice: a clear is an ordinary boss kill, so the boss engine pays coins, XP and drops by damage share and the
**resonance feature** (#34) pays Memory Shards, the first clear of the day bonus and the component guarantee, for players with
at least 5 % of the damage. The Bastion adds:
* **Resonance rank**: the fight runs at the leader's `ResonanceApi.effectiveRank` (the same boss stats for the whole party).
* **Useful failure** (the hook #34 left unwired): a lost fight calls `ResonanceApi.payDefeat(player, boss, rank, progress, share)`
  for each member who fought, `progress` = the share of the Guardian's health taken. A member who left forfeits nothing the
  boss engine already credited, but gets no defeat Shards.
* Profile section `bastion` (`BastionData`): runs, clears, defeats and best time per Bastion.

## Data
Table `bastion` (migration V017, seed `bastion.sql`, domain `ContentDomains.BASTION`) and settings `bastion.*` (`enabled`,
`max-concurrent`, `countdown-seconds`, `exit-delay-seconds`). `/bastion admin list|abort [world|all]|reload`, permission
`vampyuyu.sb.bastion.admin`.

## Seals: where a Realm's exploration ends (`bastion/SealDoors.java`)
* **Gate first**: a Realm's Bastion (the one its seal opens, `BastionConfig.realmOf`) refuses `/bastion enter` (and the seal)
  until every party member has lit that Realm's Gate (`SkyGateApi.isLit`), so the command cannot skip the Gate chain. Bastions no
  seal opens (the Echo's Rift, the Interpulse Rift, the Raid) keep their own rules.
Every Realm ends at a sealed door next to its Guardian. Right-clicking the **seal block** (main hand, any item or none) calls
`BastionApi.enter` exactly like `/bastion enter`: the key, the party and every refusal are the Bastion's own (a missing key
is refused by its display name: "You need a Ashes Bastion Key to open the Ashes Bastion..."). One listener for the eight
Realms; Storm's own handler is gone (same lodestone, same coordinates).
* **Mapping (data)**: settings `bastion.seal.<realm id>` = Bastion id in `db/seed/bastion.sql` (`ashes`, `grove`, `heights`,
  `citadel`, `marshes`, `archipelago`, `depths`, `necropolis`), read at each click so `/bastion admin reload` and the panel
  apply. A value that is not a Bastion is listed in `BastionConfig.problems()` and that seal does nothing.
* **Positions (plans)**: `SealDoors.sites()` reads each plan (`AshesPlan.sealBlock()`, `GrovePlan.SEAL`, `FrostPlan.SEAL`,
  `CitadelPlan.SEAL`, `MarshPlan.sealBlock()`, `StormPlan.sealBlock()`, `DepthsPlan.sealBlock()`, `NecropolisPlan.SEAL`) plus the
  world's origin height. `/bastion admin seals` lists them (Realm, world, x y z, the side players stand on, the Bastion).
* **Structure**: `island/SealDoor.java` builds the shared door (5 wide frame, 3x3 opening filled in the Realm's palette, the
  seal block in the middle at eye height, a wall sign "Sealed / Guardian / <Realm> Key / Right-click", the box in front cleared).
  Ashes (the Caldera wall, replacing the respawn-anchor placeholder), Grove, Heights, Necropolis (north of the central arch,
  z -12), Citadel (closes the pyramid corridor, z -8) and Marsh (south of the Overflow Pit) use it. Storm keeps its lodestone
  before the crying obsidian wall and the Depths keep their chained Sarcophagus (a sea lantern knotted into the middle chain,
  sign reworded). Plan versions bumped (Ashes, Grove, Heights, Citadel, Marsh, Necropolis, Depths: 3), so an existing Realm
  is rebuilt on its next start.
* Known weakness: the Marsh pit islet ends 2 blocks in front of its door (only 2 cells of floor; 3 blocks out the Marsh edge rule carries the player back to the landing).
* A Bastion key held in the hand does not trigger the "take it to a Bastion gate" refusal of `SummonListener` on a seal
  (`BastionApi.isSeal`).
* Tests: `SealDoorsTest` (mapping, click handling, Storm coordinates), `SealDoorPlanTest` (the seal block is in each plan
  built with the production cap, nothing in front), `SealSignKeyTest` (the sign's key word matches the key item's name).
* Real client: `python3 scripts/e2e_client.py --scenario realms` with `E2E_SEAL=all|ashes,grove` (default ashes,grove): the
  bot right-clicks the seal without the key (refusal in the client chat), then with it (`sb_bastion_` world), then
  `bastion admin abort all`. Shots `rl-6-seal-<realm>-door|close|refused|opened.png`.

## Not verified / assumptions
* Written without a Paper build available (the sandbox could not reach repo.papermc.io): compile and run `make plugin-test
  PLUGIN=survival TEST=Bastion*`, then try `/bastion enter ashes_bastion` with a key (`/sbgive magma_core`) on the local stack.
* The arena is a plain platform; the Guardian fights are the existing boss rows.
* Realm zones and Sky Gates (#29-#31, #33) only need to call `BastionApi.enter` or send players to `/bastion`; the Realms' seals do it (see Seals).
