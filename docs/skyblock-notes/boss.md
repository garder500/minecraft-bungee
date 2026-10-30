# Boss feature (`win.vampyuyu.survival.boss`)

Boss framework, six contract bosses, summon items, Slayer quests, scheduled world bosses. Data-driven: everything
tunable is in `plugins/VampyuyuSurvival/boss.yml` (defaults in `survival-plugin/src/main/resources/boss.yml`).
Icons: `resource-pack/art/boss_items.py` (stdlib, deterministic) writes 30 textures + models + item definitions
under `vampyuyu:boss/<id>`.

## Player description

* **Bosses** are fought in free space. Each has phases (health thresholds with a title), telegraphed abilities that
  reward dodging, a boss bar for everyone within `bar-range`, an enrage timer and a leash to its spawn point.
  Damage is credited per player; shares decide coins, combat XP and drop chances. Loot goes straight to the
  inventory; what does not fit is queued in the profile (never dropped where the void or a despawn timer could eat
  it) and delivered at login or with `/boss claim`. Bosses show their own floating damage numbers and their health in
  the name tag (they take a token real hit, so generic damage indicators would read 0).
* **Summon items** (right-click): Void Sigil (End altar, `voidwyrm`), Magma Core (`magma_warden`, Nether),
  Frost Heart (`frost_revenant`), Matriarch Egg (`sylvan_matriarch`), Leviathan Scale (`abyssal_leviathan`, must stand in
  water). Dropped by vanilla mobs (`mob-drops`). The crafting `recipes` ship disabled (wither farms make Nether Stars without
  limit); turn them on with `enabled: true`. The item is consumed and given back when the fight ends through a server
  fault (shutdown, world unload, error) or when the boss vanishes (idle, chunk unloaded) before anybody hurt it
  (`refund-on-despawn`); once players dealt damage a vanished boss is a lost fight. Subject to per player and global
  cooldowns, `max-concurrent` per summoner and `global-max-concurrent` (default 6) per boss.
  Boss drops are inert in vanilla ways: they cannot be placed, eaten, thrown, dispensed, composted or used as a crafting
  ingredient (`BossItemGuard`).
* **End altar**: every island has its own altar in its End cell (`x = cellX * 400 + 0.5`, `y = island-y + 1`,
  `z = cellZ * 400 + 0.5`); the Void Sigil uses the altar closest to the player (`altar.cell-size`).
* **Arena rules** (no building, no pearls) only bind the summoner and players who hurt the boss, so a neighbour on the
  next island is never locked out; public world events bind everybody around them.
* **Voidwyrm**: every participant with a damage share of at least 5 percent (`loot.wings-min-share`) gets
  `WingsApi.grantOnce(player, "boss:voidwyrm")`, a Wing Core, and the kill is announced. A participant who is offline at
  the kill gets the Wings, the combat XP, the kill count and the pity update at their next login (`BossData.pending*`).
* **Slayers** (`/slayer`): Revenant Horror (zombies), Tarantula Broodfather (spiders), Sven Packmaster (wolves),
  Voidgloom Seraph (endermen, End only), Demonlord Inferno (blazes, Nether only), tiers I-V. Pay coins, kill matching
  mobs to fill the quest bar, then the personal boss spawns near you (only the owner can see and hurt it, one per owner).
  Death of the owner, leaving (quit, `/hub`), the time limit, or the boss vanishing (idle despawn, chunk unloaded)
  after the owner hurt it fails the quest (no refund). A boss that vanishes before any damage, a restart, a world unload
  or an internal error put the quest back to READY. A fight is never resumed: rejoining spawns a fresh boss only for a
  quest that was not lost. Wins give Slayer XP, levels 1-9 with permanent stat
  perks (through `StatsApi` source `"slayer"`), combat XP and drops with pity. Tier unlocks need a slayer level.
* **World bosses** (`/worldboss`): Ancient Colossus or Storm Roc, every `interval-minutes` of server time when at least
  `min-players` are in Skyblock worlds; announcements at 300/60/10 s, title on spawn, a clickable teleport, leaderboard
  in chat, shared loot. The boss is placed on the highest solid block of its spot with 10 free blocks above; a spot with
  no ground is skipped and the event retried. The fight keeps its chunks loaded (plugin chunk tickets) and a world event
  has no idle despawn, only its duration. Rewards of repeated kills of the same boss shrink for a while
  (`settings.repeat-*`).

## Commands and permissions

| Command | Permission (default) | Notes |
|---|---|---|
| `/boss list`, `/boss info <id>`, `/boss claim` | `vampyuyu.sb.boss.info` (TRUE) | `claim` delivers loot that did not fit |
| `/boss spawn <id>`, `kill [id\|all]`, `active`, `reload` | `vampyuyu.sb.boss.admin` (OP) | admin also bypasses arena build/teleport locks |
| `/slayer [gui\|start <slayer> <tier>\|spawn\|cancel\|status\|help]` | `vampyuyu.sb.boss.slayer` (TRUE) | |
| `/slayer admin setxp\|addxp\|reset ...` | `vampyuyu.sb.boss.admin` | |
| `/worldboss [status]`, `/worldboss warp` | `vampyuyu.sb.boss.worldboss` (TRUE) | `warp` lands next to the running event (not in a dungeon) |
| `/worldboss spawn [id]`, `cancel`, `schedule <minutes>`, `addspot <name>`, `removespot <name>` | `vampyuyu.sb.boss.admin` | spots saved in game go to `boss/worldboss.json` and are used next to the yml ones |
| using a summon item | `vampyuyu.sb.boss.summon` (TRUE) | |

All tab completed. `/sbgive <id>` (core) gives any boss item.

## Config keys (`boss.yml`)

`settings` (hp-scale, damage-scale, incoming-damage-scale, tick-period, bar-range, visual-health, hit-iframes,
magic-find-cap, purge-orphans, scale-min-share, repeat-window-minutes, repeat-decay, repeat-floor, damage-numbers), `bosses.<id>` (name, description, entity, health, damage, scale, speed,
knockback-resistance, bar, arena{radius, leash, projectile-range, no-fly, no-build, no-teleport, min-players,
scale-per-player, scale-cap, enrage-seconds, enrage-damage, enrage-growth, idle-despawn-seconds, time-limit-seconds,
max-minions}, `summon.altar.cell-size` and `summon.global-max-concurrent`, phases[name, at, title, subtitle, damage, speed, enter[], abilities[]], equipment, options, loot, summon,
kind), `world-bosses`, `slayer-settings`, `slayers.<id>` (five-value lists per tier: costs, required-xp, health, damage,
slayer-xp, time-limit, coins, combat-xp, speed, scale, armor, unlock-level; level-xp (9 values), perks, kill-xp,
abilities with `from-tier`, frenzy, loot), `mob-drops`, `recipes`, `items` (name/lore overrides). The file header lists
every ability id (23 abilities) and its keys. A broken entry is dropped and logged as `boss.yml: <path>: <problem>`;
`/boss reload` re-reads it (running fights keep their definition; item names need a restart).

Loot math: `coins_i = pool * (floor / n + (1 - floor) * share_i)` over the `n` participants above `min-share`, so
the pool is never exceeded however many accounts join (the earlier "floor per participant" formula was an alt-account
exploit). Drop chance = `(base + pity) * rankBonus * (1 + MagicFind / 100)`, capped at 1 (Magic Find capped by
`magic-find-cap`; a guaranteed drop stays guaranteed). Health scales with group size
(`1 + scale-per-player * (n - min-players)`, capped by `scale-cap`), counting only players whose credited damage reached
`scale-min-share` of the base health (a one-hit alt account does not grow the boss); the growth is applied BEFORE the hit
that crosses the threshold. Coins and combat XP of the n-th kill of the same boss inside `repeat-window-minutes` are
multiplied by `max(repeat-floor, 1 - repeat-decay * earlier kills)` (online players; offline participants are not damped).

Damage to players: boss hits are set at `EventPriority.LOW`, before the items feature applies scale, Defense and shields
at HIGH, so melee, projectiles and ability hits (`BossSession.hit`, which keeps its multiplier) are all mitigated the
same way. The boss's own damage handler (`bossHurt`) stays at HIGHEST because it reads the items formula's output.

## Persistence choice

Boss entities are **not persistent** (`setPersistent(false)`, tagged with PDC `vampyuyu:boss_id`, `boss_session`,
`boss_minion`). A restart or chunk unload therefore removes them; `purge-orphans` also removes any tagged leftover when
a chunk loads and once at startup. Sessions end with `WORLD_UNLOAD`, `OWNER_LEFT`, `TIMEOUT`... and a summon item is
refunded on a non-kill end. Rationale: a half-dead boss with a lost damage ledger and cooldowns is worse than a clean
despawn. The Slayer quest itself (state, progress, paid) lives in the profile and survives restarts; only the boss
fight is lost (see the Slayers rules above for when the quest survives). While a fight runs it holds plugin chunk
tickets on the chunks around its spawn point and around the boss, released when it ends, so an unloaded chunk cannot
take the boss away while players walk to it.

## Data layout

* Profile section `boss` (`BossData`): `slayerXp`, `slayerKills`, `kills`, `dry` (pity), `seenLevel`, `quest`
  (`slayer`, `tier`, `progress`, `required`, `state` collecting|ready|fighting, `paid`, `startedAt`), `pending`
  (base64 items waiting for room or for a login, cap 1000), `pendingWings`, `pendingXp`, `pendingKills`, `pendingDry`
  (what an offline participant earned, applied at login), `recentKills` (timestamps for the repeat damper).
* `plugins/VampyuyuSurvival/boss/worldboss.json` (next spawn time, history), written through `core.store()`.
* Custom items registered: `void_sigil magma_core frost_heart matriarch_egg leviathan_scale wing_core voidwyrm_scale
  void_fang warden_heart ember_blade frost_shard glacial_edge matriarch_silk venom_dagger leviathan_tooth tidal_trident
  lord_bone necro_scepter colossus_core roc_feather revenant_flesh revenant_catalyst tarantula_web tarantula_charm
  ravager_tooth ravager_pelt null_sphere void_conduit demon_ember demon_core`.

## API and events

* Publishes `BossApi` (`spawn(id, loc, summoner)`, `bossIds()`, `registerLootHook`). `spawn` ignores summon rules and
  works in any world, e.g. `BossApi.spawn("crypt_lord", location, null)` from the dungeon feature (no summon item).
  Boss ids are a contract: `voidwyrm magma_warden frost_revenant sylvan_matriarch abyssal_leviathan crypt_lord`
  (also `ancient_colossus`, `storm_roc`, and generated `slayer_<slayer>_<tier>`).
* Fires `BossKillEvent` (boss id, killer, ranking, location) before rewards; loot hooks run per online recipient.
* Consumes lazily (all optional): `EconomyApi` (slayer cost, coin rewards), `SkillsApi` (combat XP), `StatsApi`
  (Magic Find, slayer perks), `WingsApi.grantOnce`, `IslandApi` (summon needs island membership when
  `require-island-member`). Slayer XP popups are the items feature's job (it listens to `SkillXpEvent`); this feature
  never writes the action bar.

## NEEDS (contract changes I did not make)

* `StatsApi` should keep (or accept) modifiers set with `addModifier(uuid, "slayer", ...)` for players who are online; perks are re-applied on join and after each slayer level up.
* The altar follows the island feature's documented convention (`cellX * cell-size + 0.5`, `island-y + 1`); the hub spots
  assume the island feature's hub (spawn pad at 0,14, plaza radius 26 around 0,0) and use `y: -999` offsets from the world
  spawn. An `IslandApi.dragonAltar(...)` / hub pad accessor in `api/` would replace both conventions.
* A failure callback for dungeons: only a kill is observable (`BossKillEvent`). A `BossEndEvent(bossId, reason, ledger)`
  in `api/event` would let the dungeon feature tell a wipe from a hang. Until then `crypt_lord` has radius 48, idle
  despawn 300 s and a 900 s limit (players further than the radius cannot hurt it); `BossApi.spawn` has no options argument.
* Economy sink: kill payouts are minted coins. The repeat damper and the disabled recipes limit the faucet, but a coin sink
  (and a per-player daily cap, or recipes that need a rare ingredient instead of a Nether Star) is the economy feature's call.
* The items feature should skip floating damage numbers for victims tagged by `Tags.isOurs` (the boss draws its own).
* Nothing in `core` or `api` was changed.

## Known gaps / NOT verified in game

* **Nothing was run on a real Paper server or with the e2e client.** Unit and MockBukkit tests pass (formulas, config
  validation of the shipped `boss.yml`, a real session: spawn, damage ledger, personal boss, kill, Wings grant, coins,
  loot hook, no double payout, shutdown cleanup). MockBukkit does not simulate AI, attributes of every mob, bars,
  particles or the ender dragon phases.
* Visuals (particles, telegraphs, ring and wave geometry, the dragon dive, the laser, crystal shield) and the bosses'
  real difficulty and dodge windows are untested; every number is a first guess. Screenshots with
  `scripts/e2e_client.py` are still to do. Pack icons were checked on a contact sheet only, not in a game client.
* Dragon fights: Paper's `EnderDragon` is steered with phases and a podium; the dive is simulated with velocity and
  may look rough. The vanilla dragon fight in `sb_end` (if the island feature keeps one) could interfere.
* `incoming-damage-scale` and boss `damage` must be calibrated against the items feature's damage formula and player
  health once that exists (boss damage is vanilla HP before `damage-scale`).
* Offline participants: coins and items are paid at the kill; Wings, combat XP, the kill count and pity are queued and
  applied at login (tested with a fake offline UUID, not with a real disconnect). The repeat damper does not apply to them.
* Chunk tickets are not implemented by MockBukkit: the code path is exercised, the ticket effect is not. Not verified that
  a boss survives with nobody nearby on a real server.
* The hub world-boss spots and the per-cell altar were verified only against the island feature's notes, not in game.
  The boss placed at a hub plaza spot stands over a void world; leashing and falling off the plaza are untested.
* Floating damage numbers (`TextDisplay`) and the name tag mirror were not seen in a client.
* Conversions (drowned, stray) are excluded through their spawn reason; a mob that was farmed first and converted later
  keeps only the conversion reason, which is now excluded too, but the origin of a conversion is not tracked.
* Quitting during a slayer fight fails the quest (no grace period for a crash); resuming a fight with saved boss health
  was not built.
* The summon item cooldown is not re-armed when a boss vanishes after damage (the item is not refunded, which is the
  deterrent).
* Not verified against the real items feature (sb/items): the damage ordering is tested with a stand-in listener at HIGH.
* Slayer progress and summon-item mob drops ignore farmable sources (`SpawnSources`: spawners, spawn eggs, breeding,
  built golems/snowmen/withers, infection, cure, eggs, slime splits); mob drops never use snow golems, iron golems or
  withers. Not verified in game that Paper reports these reasons for every farm design.
* Summon items of the Frost Revenant, Matriarch and Leviathan work only in `sb_world` (islands), not the hub, so nobody can
  lock pearls and building in the hub; edit `summon.worlds` to change it.
* No custom sounds or translated names (pack rules): vanilla sounds only.
