# Boss feature (`win.vampyuyu.survival.boss`)

Boss framework, six contract bosses, summon items, Slayer quests, scheduled world bosses. Data-driven: everything
tunable is in `plugins/VampyuyuSurvival/boss.yml` (defaults in `survival-plugin/src/main/resources/boss.yml`).
Icons: `resource-pack/art/boss_items.py` (stdlib, deterministic) writes 30 textures + models + item definitions
under `vampyuyu:boss/<id>`.

## Player description

* **Bosses** are fought in free space. Each has phases (health thresholds with a title), telegraphed abilities that
  reward dodging, a boss bar for everyone within `bar-range`, an enrage timer and a leash to its spawn point.
  Damage is credited per player; shares decide coins, combat XP and drop chances. Loot goes straight to the
  inventory (overflow drops as an item reserved for its owner).
* **Summon items** (right-click): Void Sigil (End altar, `voidwyrm`), Magma Core (`magma_warden`, Nether),
  Frost Heart (`frost_revenant`), Matriarch Egg (`sylvan_matriarch`), Leviathan Scale (`abyssal_leviathan`, must stand in
  water). Dropped by vanilla mobs (`mob-drops`) or crafted (`recipes`, expensive, switchable). The item is consumed, given
  back when the boss despawns without a kill (`refund-on-despawn`), and subject to per player and global cooldowns.
* **Voidwyrm**: every participant with a damage share of at least 5 percent (`loot.wings-min-share`) gets
  `WingsApi.grantOnce(player, "boss:voidwyrm")` (online players only), a Wing Core, and the kill is announced.
* **Slayers** (`/slayer`): Revenant Horror (zombies), Tarantula Broodfather (spiders), Sven Packmaster (wolves),
  Voidgloom Seraph (endermen, End only), Demonlord Inferno (blazes, Nether only), tiers I-V. Pay coins, kill matching
  mobs to fill the quest bar, then the personal boss spawns near you (only the owner can see and hurt it, one per owner).
  Death of the owner or the time limit fails the quest (no refund). Wins give Slayer XP, levels 1-9 with permanent stat
  perks (through `StatsApi` source `"slayer"`), combat XP and drops with pity. Tier unlocks need a slayer level.
* **World bosses** (`/worldboss`): Ancient Colossus or Storm Roc, every `interval-minutes` of server time when at least
  `min-players` are in Skyblock worlds; announcements at 300/60/10 s, title on spawn, leaderboard in chat, shared loot.

## Commands and permissions

| Command | Permission (default) | Notes |
|---|---|---|
| `/boss list`, `/boss info <id>` | `vampyuyu.sb.boss.info` (TRUE) | |
| `/boss spawn <id>`, `kill [id\|all]`, `active`, `reload` | `vampyuyu.sb.boss.admin` (OP) | admin also bypasses arena build/teleport locks |
| `/slayer [gui\|start <slayer> <tier>\|spawn\|cancel\|status\|help]` | `vampyuyu.sb.boss.slayer` (TRUE) | |
| `/slayer admin setxp\|addxp\|reset ...` | `vampyuyu.sb.boss.admin` | |
| `/worldboss [status]` | `vampyuyu.sb.boss.worldboss` (TRUE) | |
| `/worldboss spawn [id]`, `cancel`, `schedule <minutes>` | `vampyuyu.sb.boss.admin` | |
| using a summon item | `vampyuyu.sb.boss.summon` (TRUE) | |

All tab completed. `/sbgive <id>` (core) gives any boss item.

## Config keys (`boss.yml`)

`settings` (hp-scale, damage-scale, incoming-damage-scale, tick-period, bar-range, visual-health, hit-iframes,
magic-find-cap, purge-orphans), `bosses.<id>` (name, description, entity, health, damage, scale, speed,
knockback-resistance, bar, arena{radius, leash, projectile-range, no-fly, no-build, no-teleport, min-players,
scale-per-player, scale-cap, enrage-seconds, enrage-damage, enrage-growth, idle-despawn-seconds, time-limit-seconds,
max-minions}, phases[name, at, title, subtitle, damage, speed, enter[], abilities[]], equipment, options, loot, summon,
kind), `world-bosses`, `slayer-settings`, `slayers.<id>` (five-value lists per tier: costs, required-xp, health, damage,
slayer-xp, time-limit, coins, combat-xp, speed, scale, armor, unlock-level; level-xp (9 values), perks, kill-xp,
abilities with `from-tier`, frenzy, loot), `mob-drops`, `recipes`, `items` (name/lore overrides). The file header lists
every ability id (23 abilities) and its keys. A broken entry is dropped and logged as `boss.yml: <path>: <problem>`;
`/boss reload` re-reads it (running fights keep their definition; item names need a restart).

Loot math: `coins_i = pool * (floor / n + (1 - floor) * share_i)` over the `n` participants above `min-share`, so
the pool is never exceeded however many accounts join (the earlier "floor per participant" formula was an alt-account
exploit). Drop chance = `(base + pity) * rankBonus * (1 + MagicFind / 100)`, capped at 1 (Magic Find capped by
`magic-find-cap`; a guaranteed drop stays guaranteed). Health scales with group size
(`1 + scale-per-player * (n - min-players)`, capped by `scale-cap`); a newcomer's pool growth is applied BEFORE their hit.

## Persistence choice

Boss entities are **not persistent** (`setPersistent(false)`, tagged with PDC `vampyuyu:boss_id`, `boss_session`,
`boss_minion`). A restart or chunk unload therefore removes them; `purge-orphans` also removes any tagged leftover when
a chunk loads and once at startup. Sessions end with `WORLD_UNLOAD`, `OWNER_LEFT`, `TIMEOUT`... and a summon item is
refunded on a non-kill end. Rationale: a half-dead boss with a lost damage ledger and cooldowns is worse than a clean
despawn. The Slayer quest itself (state, progress, paid) lives in the profile and survives restarts; only the boss
fight is lost (the quest returns to READY).

## Data layout

* Profile section `boss` (`BossData`): `slayerXp`, `slayerKills`, `kills`, `dry` (pity), `seenLevel`, `quest`
  (`slayer`, `tier`, `progress`, `required`, `state` collecting|ready|fighting, `paid`, `startedAt`), `pending`
  (base64 items for players who were offline at the kill).
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
* The End altar position and the hub arena positions in `boss.yml` are guesses: the `island` feature owns the real
  coordinates. Change `bosses.voidwyrm.summon.altar` and `world-bosses.locations`.
* Nothing in `core` or `api` had to change.

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
* Offline participants get coins and queued items but no Wings, no combat XP and no pity bookkeeping (the profile
  read would block).
* Slayer progress and summon-item mob drops ignore farmable sources (`SpawnSources`: spawners, spawn eggs, breeding,
  built golems/snowmen/withers, infection, cure, eggs, slime splits); mob drops never use snow golems, iron golems or
  withers. Not verified in game that Paper reports these reasons for every farm design.
* Summon items of the Frost Revenant, Matriarch and Leviathan work only in `sb_world` (islands), not the hub, so nobody can
  lock pearls and building in the hub; edit `summon.worlds` to change it.
* No custom sounds or translated names (pack rules): vanilla sounds only.
