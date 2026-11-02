# Bastions (GDD 12.1, 12.3, 14.4, 15.4) — survival-plugin, package `win.vampyuyu.survival.bastion`

The Bastion is the fortress of a Realm's Guardian: an **instanced** fight for a party of 1 to 5, opened with a **key**.
Phase 2 ships two: the **Ashes Bastion** (Lava Colossus = boss `magma_warden`, key `magma_core`) and the **Grove Bastion**
(Mycelial Queen = boss `sylvan_matriarch`, key `matriarch_egg`). Technical ids are unchanged; the visible names are the canon.

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
  `time_limit_seconds` (20 min). The Guardian's group scaling (0.5 per player, GDD 8.7) is the boss engine's.

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

## Not verified / assumptions
* Written without a Paper build available (the sandbox could not reach repo.papermc.io): compile and run `make plugin-test
  PLUGIN=survival TEST=Bastion*`, then try `/bastion enter ashes_bastion` with a key (`/sbgive magma_core`) on the local stack.
* The arena is a plain platform; the Guardian fights are the existing boss rows.
* Realm zones and Sky Gates (#29-#31, #33) only need to call `BastionApi.enter` or send players to `/bastion`.
