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
* **Slayers** (`/slayer`): The Unrotting (zombies), The Great Spinner (spiders), The Gaunt Alpha (wolves),
  The Hollow Walker (endermen, End only), The Furnace Tender (blazes, Nether only), tiers I-V. Pay coins, kill matching
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

## Sarcophagus Guardians (GDD 12.2, 15.2)

`magma_warden` (in game: **Lava Colossus**, Cracked Ashes) and `sylvan_matriarch` (**Mycelial Queen**, Whispering Grove)
are the Guardians of the two phase 2 Realms. The technical ids stay (quests, profiles); everything visible uses the canon.
Their extra rules live in `boss.guardian.*` setting rows of `db/seed/boss.sql`, read into `Guardians`:

* **Health** follows the GDD 8.7 curve like every boss (`hp_auto`, tier 2 / tier 3), fixed whatever the number of attackers (no group scaling since 2026-11-23),
  enrage 9 min. `GuardianTest` re-checks the readability rules (phase x1.4 at most, an ability takes at most 35 percent of the
  reference player).
* **Spoken lines** (`intro`, `phase1`, `phase2`, `death`, `first-clear`): sent in chat to everyone around the fight, a random
  line of the list, signed with the Guardian's `speaker`. Voices are not recorded.
* **Local resource** (`resource`, `resource-min/max`, `first-clear-resource`): every eligible participant gets 2 to 4
  Cinderheart (Lava Colossus) or Heartwood (Mycelial Queen), 6 more the first time they ever defeat it. The items are
  registered by their own feature: while the item id is unknown the reward is skipped, never an error.
* **Component** (`lava_core` Colossus Core, `mycelial_core` Queen Core): a normal drop (35 percent, 5 percent damage share)
  plus the **hard pity**: certain on the 4th run without it (`hard-pity`), on the 3rd from Resonance III
  (`hard-pity-resonance`, `resonance-rank`). The streak is the existing `BossData.dry` counter; `BossRewards.setResonance`
  is the hook for the Resonance system (it counts 0 until then).
* Not done here: the Bastion itself (instance, keys, first-clear Memory Shards x2) belongs to the Bastion framework.

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
  assume the island feature's hub (spawn pad at 0,14; the hub is now the Aethelgard fragment of `hub.md`, not a plaza) and use `y: -999` offsets from the world
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
* Hub world bosses stand on the hub plaza (spawn pad area): abilities hit every targetable player within radius + 8,
  including AFK players and new arrivals. Only the world boss events can do that now (`boss.world.enabled`, off by default):
  the Echo of the Rift no longer wakes in the Hub. Its Echo Key is a Bastion key, so at the Rift altar `SummonListener` opens
  the `echo_rift` Bastion (an instance for the party, see `bastion.md`) instead of summoning it in the shared world.
* Boss models were redrawn on 2026-11-20 (`resource-pack/art/boss_skins.py`, class `Painted`): every face has its own
  painted texture at 2 texels per model unit (plates with rivets, cracked rock with lava veins, scales, bone, cloth folds,
  faceted ice, bark, membranes), with a lit top, a dark underside, occlusion toward the bottom and bevelled edges, on
  muted palettes (they were 4x4-pixel flat tiles stretched over each box). New pieces: the Colossus's knee guards, belt
  and brow, the Watcher's fur mantle and helm band, the Queen's mushroom caps, the Leviathan's chains, the Judge's
  robe, hood and scales of judgement, the Echo's snout and brow. Second pass the same day, new silhouettes: a hunched
  Colossus with fists on the ground and a basalt crest, the Watcher with both hands on his planted greatsword, the Queen
  as a woman rising from a mushroom-grown spider with jointed legs, the Leviathan rearing out of its coil with open jaws
  and a broken collar chain, the Judge floating in a deep hood with an ornate sceptre, the Echo with a stepped neck, a
  crown of horns, open jaws and a bladed tail. `resource-pack/art/preview_boss_skins.py` renders them offline (12 s).
  Third pass after two reviews by a second model (Fable): softer bevel on large faces only and rounded scales (the hard
  bevel outlined every cuboid like a toy brick), a knuckle-walking Colossus with its heart as three fissures, the
  Watcher's real greatsword held at the pommel, the Queen's pale face and fanned legs, the Leviathan's S-curve, ringed
  coil and gaping jaw, the Judge floating with the scales held out, the Echo's raised neck and jointed wings. x-axis
  rotations follow Minecraft's convention (a positive angle tilts +y toward +z); several were the wrong way round.
* **Voiced lines (2026-11-23).** Every spoken boss line has a recorded voice: the 8 Guardians (`boss.guardian.<id>.<event>`)
  and the ultimate bosses, which now speak too without being Guardians (`boss.speech.ids` = voidwyrm, devourer, hell_gao;
  `boss.speech.<id>.speaker` and `.<event>` lists, read by `Guardians.parse` into a speech-only map, no Guardian loot).
  `Guardians.say` returns the chat line and its sound `vampyuyu:voice.<boss>.<event>.<n>` (n = position in the list);
  `BossSession.say` and the death / first-clear lines in `BossRewards` play it on the VOICE channel to the same players.
  Clips: ElevenLabs library voices, one per boss (`resource-pack/art/boss_voices.py` VOICES), generated from
  `resource-pack/voices/manifest.json` (`boss_voices.py manifest`), mono Ogg in `pack/assets/vampyuyu/sounds/voice/`,
  registered by `boss_voices.py sounds`. A line edited in the panel keeps its old clip until it is recorded again:
  `boss_voices.py check` and `test_pack` list such lines. The Devourer's lines follow the lore (a world-eater that wanted
  to be loved, not a villain with a speech).
* Skinned bosses show their name and health on a text display over the model (`BossSkinService.tags`, 2 x scale + 0.25
  above the feet): the mob's own name tag sat inside the model. The Queen's spider is now hidden like the Echo's dragon
  (a spider's eyes glow through invisibility); `HiddenBossMelee` hits a hidden plain mob on its hit box.
* Boss models faced backwards until 2026-11-20: `BossSkinService.Look.yawOffset` was 180 for the mob bosses and 0 for
  the Echo; measured in game, a mob's yaw is where it looks and the vanilla dragon's yaw is 180 degrees from its heading,
  so it is now 0 for the mobs and 180 for the Echo (`ShippedConfigTest.bossModelsFaceWhereTheBossLooks`).
* Tier-5 boss hits were sized for a player wearing four armor sets at once (`PowerCurve` summed Starcore and its three
  Realm sidegrades). Measured in game: the Echo took 55 % of a full-Starcore player's 96 health per contact hit. With one
  set per slot it took 2 %, like a tier-2 or tier-3 boss on a player of its tier (2 to 4 %).
* Hits too soft, fixed (2026-11-23, real client). The missing layer was the VANILLA ARMOR of the base materials: the items
  feature (which zeroes it) is not loaded in production, so a ladder player keeps chainmail 12, iron 15, diamond 20 / 8,
  netherite 20 / 12 and Starcore 25 / 16 (base material plus the `extra_armor` / `extra_toughness` pdc rows), measured
  with `attribute E2EBot minecraft:armor get`. `PowerCurve.bossHitDamage` now solves for the hit that loses
  `1 / hits-to-down` of the health AFTER Ward and vanilla armor (`afterVanillaArmor`, bisection: vanilla's formula
  depends on the damage). `boss.settings.hits-to-down` is now a seed row at 10 (was 8 in code, GDD says 8 to 12) and
  `boss.settings.damage-scale` 0.6 -> 1.0 (it was hiding the same gap the other way). Tier 5 uses the per-slot mean of
  Starcore and its sidegrades (armor 24.25), the wearer of plain Starcore (25 / 16) takes a bit less.
  Measured, a full tier set against its boss, one plain hit (mult 1.0), before -> after, % of max health:

  | tier | boss | player (max hp, armor/tough) | before | after |
  |---|---|---|---|---|
  | 1 | Crypt Lord | bronze (30, 12/0) | 3.4 | 10.0 |
  | 2 | Magma Warden | rime (40, 15/0) | 2 to 4 | 9.7 |
  | 3 | Frost Revenant | ember (54, 20/8) | 1 | 10.0 |
  | 5 | Echo of the Rift | starcore (96, 25/16) | 1.5 | 8.6 |

  Abilities with a lower multiplier hurt less (0.7 to 5 %), the Echo's x3.5 dive bomb about a third, as the seed
  intends (`PowerCurveTest` readability limit now 0.4). A bare or under-geared player takes far more
  (`PowerCurveTest.anUnderGearedPlayerIsPunishedHarderThanATierMatchedOne`). Tests: `aTierMatchedPlayerLosesAboutATenth
  OfItsHealthPerBossHit` pins 8 to 12 % for every tier. Not measured: tier 4 (the bot died before its run).
* The Echo's flight (2026-11-20, real client): the vanilla dragon ignores speed attributes and cruises at about 22
  blocks/s. `BossSession.slowFlight` caps its velocity each tick at `speed` x 1.1 blocks/tick (voidwyrm 0.45: about
  10 blocks/s measured); scaling the velocity instead compounds with the dragon's drag (0.55 gave 2.5 blocks/s). The
  `perch` ability (all three phases, 4-6 s) and the `stun` parameter of `dive_bomb` (3 s) pin it on the ground in a
  sitting phase (no wing knockback, no contact push, `[STUNNED]` on the bar, dazed bonus damage): the melee window.
  Its health uses `boss.settings.hp-factor.voidwyrm` 0.425 (85 % of the curve; `PowerCurveTest` reads it back as fewer
  landed hits). A landed vanilla dragon still shoves everyone near its wings every tick (only the wing damage stops when
  it sits): `BossListener.onPushed`/`onKnockback` cancel the pushes of a grounded boss. Checked in game: landing title,
  cruise speed, a player beside the landed Echo stays put (pushed 8 blocks a second before the fix) and a Starcore sword
  hit counts.
* Non-participants can still build or throw pearls inside a summoned boss's arena until their first hit (the arena rules
  bind the summoner and the players in the ledger). Two summons of the same boss cannot share an altar anchor.
* The ability-hit damage path is covered by a flag test; the end-to-end test through `Player.damage` is skipped when
  MockBukkit fires no damage event for it.
* A spawn that fails inside the session (an exception after the entity exists) does NOT refund: the caller keeps the item.
  Update-loop exceptions (`End.ERROR`) do refund.
* The crafting guard only touches vanilla recipes and this plugin's own; recipes of other plugins that use a boss item
  as an ingredient are left to their owner. The repeat damper does not apply to slayer bosses.
* Not verified against the real items feature (sb/items): the damage ordering is tested with a stand-in listener at HIGH.
* Slayer progress and summon-item mob drops ignore farmable sources (`SpawnSources`: spawners, spawn eggs, breeding,
  built golems/snowmen/withers, infection, cure, eggs, slime splits); mob drops never use snow golems, iron golems or
  withers. Not verified in game that Paper reports these reasons for every farm design.
* Summon items of the Boreal Watcher, Matriarch and Leviathan work only in `sb_world` (islands), not the hub, so nobody can
  lock pearls and building in the hub; edit `summon.worlds` to change it.
* No custom sounds or translated names (pack rules): vanilla sounds only.
* Slayers run only in island worlds (`sb_world`, `sb_nether`, `sb_end`); `sb_hub` was removed from `slayers.*.worlds` (design principle, docs/skyblock-design.md section 13). World boss spots stay on the hub plaza (special event).

* **Land-locked Guardian**: the Chained Leviathan is an Elder Guardian, and every Bastion arena (and the hub) is dry. Vanilla makes a Guardian out of water flop
  (a hop and a random sideways push at every landing), so it wandered off and fell from small platforms with its model. `BossSession.keepWaterMobOnLand` turns its AI
  off while it is not in water (abilities are the session's own and keep running), faces it toward the nearest player and gives the AI back in water.

## Custom VFX, statuses and veils (2026-11-25)

No boss ability shows a vanilla particle or lays a vanilla potion any more (owner's rule: "never the basic Minecraft effects").

* **Effects**: `BossVfx` spawns pack models on item displays at full brightness: 16 white primitives modelled in Blockbench
  (`resource-pack/art/blockbench/fx/`: ring, disc, sigil, beam, bolt, spike, pillar, orb, shard, vortex, wave, chain_link,
  cage_bar, cloud, slash, burst), converted by `art/fx_models.py`, tinted per element through `custom_model_data` colours.
  Each effect is a list of keyframes the client interpolates; helpers: `decal`, `decalOn` (follows a player), `wave`,
  `column`, `beam`, `sweep` (a beam pivoting on the boss), `bolt`, `burst`, `spin`, `missile` (moved every tick), `impact`,
  `attached` (ends with its entity), `arcWave` (an expanding ring with a gap) and `wall` (a marching wave). Displays are not
  persistent, tagged `vampyuyu:boss_vfx`, purged on world load; a failed spawn never breaks a fight. Telegraph decals are as
  wide as the damage.
* **Glow**: `core/item.fsh` (generated by `art/fx_shaders.py`) makes texels of alpha 251 pulse and burn white-hot; `boss_skins.py`
  marks the faces of glowing cubes, `fx_models.py` marks whole primitives. The big primitives get a `<name>_halo` twin (alpha 96,
  a breathing translucent haze, 30 % bigger): the stand-in for a bloom halo. `bbmodel.problems` rejects face UVs outside the
  texture: the client refuses a model with translucent texels whose UVs leave the image (Hell Gao's halo did).
* **Statuses**: `BossStatus` replaces Slowness, Blindness, Poison, Wither, Levitation, Slow Falling, freeze and fire ticks with
  transient attribute modifiers (movement speed, gravity, jump strength) and damage ticks, cleared on death, quit, world change,
  milk, fight end and plugin stop (`StatusKind`, `StatusMath`).
* **Veils**: `core/ScreenVeil` glyphs (font `vampyuyu:fx`, colour 78/92/id) stretched full screen by the text shader: 5 void,
  6 frost, 7 decay, 8 burn, 9 warped. The items HUD appends them to its line; when no HUD owns a player, `BossStatus` sends them.
* **Crystals and anchors** are invisible, silent, still slimes (melee and arrows land) under a glowing shard.
* **Checks**: `/boss cast <ability>`, `/boss status <status|clear> [s]`, `python3 scripts/e2e_client.py --scenario vfx`
  (`E2E_VFX_BOSS`, `E2E_VFX`: films `.e2e/vfx-videos/<boss>-<ability>.mp4`, then shoots each veil).
* **Sounds**: every boss fight sound is `Fx.sfx(world, at, "<cue>", volume, pitch)` playing `vampyuyu:fx.<cue>` on the hostile
  channel: 42 clips generated with ElevenLabs text to sound (`eleven_text_to_sound_v3`) from the prompts in
  `resource-pack/art/boss_sfx.py` (`CUES`), cut and faded, mono, 32 kHz Ogg Vorbis in `sounds/fx/`. `boss_sfx.py import <cue>
  <file>` converts a new take, `sounds` registers them, `check` (in `make test-pack`) fails when the plugin plays a cue without a
  clip. Subtitles: `subtitles.vampyuyu.fx.<cue>` in `lang/en_us.json`. The pack cap rose to 12 MB for the VFX and the sounds.
* **Not done**: the vanilla mobs under the bosses still make their own ambient, hurt and death sounds; the dungeon floor bosses
  (`dungeon/run/BossDirector`), slayers, the Breath events, the Astral realm and the Raid still use vanilla particles and sounds
  outside `boss/`.
