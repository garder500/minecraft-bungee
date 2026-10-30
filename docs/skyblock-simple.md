# Simple Skyblock

The live Survival plugin loads only utility, islands, the Wings and network mode routing. The former economy, custom combat/items, skills, bosses, minions, dungeons, quests and fairy souls do not start. Their old data files and implementation remain available for compatibility; no migration deletes player data. `LegacyFeatureFixture` loads retired modules only inside their isolated compatibility tests.

A Skyblock selection goes directly to the player's personal island, creating one if needed. There is no generated hub or Skyblock End. Existing hub/home handoff aliases land on the personal island. `/sbmenu` opens the island menu; invitations, visits and public warps remain. Joining another island retains one's own island. Expansion remains usable without the retired coin service.

Only `sb_world` and `sb_nether` are created for Skyblock. The existing Nether platforms are retained. Vanilla Survival keeps its overworld, Nether and End. All ordinary teleports (including operator commands, homes/back/TPA) and physical portals must remain in their current mode. Explicit mode entry goes through `ModeTravel`; local network requests require the network plugin's temporary metadata nonce. Remote selections need their explicit Redis handoff; if unavailable, selection stays put instead of connecting to an ambiguous mode.

The `mode_respawns` section in each existing player JSON profile stores separate Skyblock and Survival points, the selected mode, and a one-time import of the old vanilla point into its matching world. Point updates never replace the other mode. Native Minecraft's single respawn slot is restored on mode changes without changing the stored point kind, preserving native bed/anchor behavior. Death routing validates the matching point and access, regardless of vanilla's initial event destination. Invalid or obstructed points fall back to the personal island or Survival spawn. A safe landing is found nearby or added in empty space above terrain; existing player blocks are not cleared. Missing mode worlds/services fail closed.

The old world names remain classified as Skyblock for isolation, even though the worlds are no longer generated or valid destinations. Old world directories and old profile sections are not removed. Routine reconnects keep their saved mode; a late handoff cannot override a more recent explicit selection.

## Verification

`make plugin-test PLUGIN=survival` and `make plugin-test PLUGIN=network` run the unit and compatibility suites. `SimpleSkyblockTest` covers personal ownership plus membership, world setup, portal events in both directions, independent beds and profile persistence, native respawn restoration, safe fallbacks and explicit mode entry.

`python3 scripts/e2e_client.py --scenario skyblock --keep` runs the real offline client on an isolated Paper instance and captures screenshots. `scripts/e2e_skyblock_roundtrip.py` extends this disposable fixture with two real clients, physical portals, deaths in each dimension and graceful server restarts. These scripts bind the test server to loopback and do not use the active stack or real player data. The test fixtures live under ignored `.e2e/`.
