# Wings (feature `wings`)

Package `win.vampyuyu.survival.wings`, config `plugins/VampyuyuSurvival/wings.yml` (defaults in
`survival-plugin/src/main/resources/wings.yml`), profile section `wings`, API `WingsApi` (published last in `enable()`).

## What the player sees

* **The Wings** are an elytra-based chestplate-slot item (item model `vampyuyu:wings/wings`, tiers 2-5 use
  `vampyuyu:wings/wings_tier<N>`), worn with a custom equipment asset `vampyuyu:wings_tier<N>` (large feathered white-gold
  wings, a glow colour per tier: gold, ice blue, rose, violet, radiant gold/cyan). Two levels share one tier.
* Worn in an allowed world, the owner flies like in creative (double jump, or `/wings fly` when double jump is off).
  Allowed worlds: `sb_hub`, `sb_world`, `sb_nether`, `sb_end`, `world` (config). Never in `sb_dungeon_*`, never in a
  no-fly zone, never in creative/spectator (those are left alone), never for 8 s after taking player/boss damage.
* **Flight Energy**: a boss bar (never the action bar) drains while flying (normal 4/s, sprint 10/s, hovering 2/s, halved
  over an island, multiplied by the level), refills on the ground (10/s, x2 on an island, x level multiplier, after a 1 s
  delay). Capacity = Flight Energy stat (base 100, plus the wings' level bonus 40..500 through the stat source
  `"wings"`). Running dry ends the flight and applies Slow Falling until landing, with fall damage cancelled: nobody dies
  of an empty bar. After running dry you need 15 % of the bar back before taking off again. The bar and the lockout
  survive taking the Wings off (swap, quit, rejoin): the time away recharges it at the ground rate, a death refills it.
  Over the void the slow fall cannot kill: void damage during or right after a slow fall is cancelled and the player is
  teleported to the last solid ground they stood on in that world (else island home via `IslandApi`, else world spawn).
* Level 1-10 (levels raise capacity, recharge, drain efficiency, fly speed and the particle trail, and grant small
  Defense / Health / Speed bonuses through the stat source `"wings"` to offset the lost chestplate). Levels come from
  **Wing Cores** consumed in `/wings` (+ coins when the economy exists).
* **Abilities by level** (listed in the item lore and in `/wings`): level 4 **dash** (swap-hands key F in the air, 25
  energy, 4 s cooldown), level 6 **dive recovery** (steep dives while gliding refill energy), level 8 **landing burst**
  (a cushion of wind absorbs one fall every 8 s). All thresholds and costs are in `abilities.*`.
* The boss bar shows `~Ns left` from the current drain and `NO GROUND BELOW!` (red) when the bar is low and the void is
  under you.
* Cosmetics: particle trail per level, wing-beat sound, take-off sound, low-energy warning, `/wings toggle`.
* Wearing the Wings also lets you glide like an elytra (vanilla behaviour of the elytra base). Gliding costs energy
  (`energy.drain-glide`/s, times the level's drain multiplier) and each firework rocket costs `energy.firework-cost`.
  A glide that is already running is ended, with a slow fall, where flying is refused (dungeons, no-fly zones, combat)
  and when the bar runs dry; starting a glide or a rocket boost is refused there too (also while exhausted).

## Soulbound, not craftable, not duplicable

* No recipe exists; the guard cancels any crafting grid, crafter, anvil, grindstone, smithing and enchanting table result
  that has Wings as an input (Wing Cores too: they are amethyst shards, so no spyglass, tinted glass or trim), armor stand
  and dispenser equipping, and hoppers picking them up or moving them.
* Wings may only live in a player inventory or an ender chest: clicks, shift clicks, number-key and off-hand swaps and
  drags that would put them in any other container (chests, shulker box, hopper, menus) or a bundle are cancelled, and
  the once-a-second scan pulls Wings out of shulker boxes and bundles in the inventory and sends the valid copy home.
* Identity lives in the PDC (namespace `vampyuyu`): `item_id` = `wings_<level>`, `wings_owner` (owner UUID), `wings_uuid`
  (unique id of the item), and **`untradable` = long 1** (the flag the `economy` feature must check and refuse for auction
  house, bazaar, player trade and shops).
* The profile holds the ONE valid id (`activeId`) and the level (authoritative). Every second each online player's
  inventory is scanned: foreign wings go back to their owner (inventory, or `pendingReturn` for the next join), stale or
  duplicate copies turn to dust, a copy with an outdated level is rebuilt. Grants, `/wings claim` and returning after a
  death rotate the id, so every older copy becomes inert.
* A dropped Wings never despawns or burns, glows, and only its owner can pick it up (`Item#setOwner`); a stranger's pickup
  is cancelled and the item is sent home. Death never drops them: a fresh copy is handed back on respawn.
* An **unbound** item (`WingsApi.create()`, `/sbgive wings_3`) binds to the first player who holds it and has not received
  Wings yet (counts as received, source `bound`); a player who already owns Wings sees it crumble.
* `WingsApi.grantOnce(player, source)` gives Wings once per player in their lifetime (persisted), drops at the feet when
  the inventory is full, and is safe to call from any thread (it hops to the main thread). Offline players get the item on
  their next join.
* Lost Wings (void, destroyed item entity): `/wings claim` forges a new pair (default cost 25,000 coins, once per 24 h,
  refused while a valid pair is in the inventory or the ender chest).

## Commands and permissions

| Command | Permission | Notes |
|---|---|---|
| `/wings` | `vampyuyu.sb.wings.use` (default true) | menu: status, upgrade, recovery, toggles |
| `/wings fly` | use | start/stop flying without the double jump |
| `/wings toggle <trail\|bar\|doublejump\|sounds>` | use | personal preferences (stored in the profile) |
| `/wings claim [confirm]` | use | recover lost Wings |
| `/wings info` | use | own status; `/wings info <player>` needs admin |
| `/wings give <player> [level]` | `vampyuyu.sb.wings.admin` (OP) | gives or replaces (older copies turn inert) |
| `/wings revoke <player> [reset]` | admin | `reset` also allows `grantOnce` again |
| `/wings reload` | admin | re-reads `wings.yml`, rebuilds items and the online players' Wings |

## Config keys (wings.yml)

`worlds.allowed`, `worlds.denied-prefixes`, `no-fly.tag`, `no-fly.regions`, `max-level`, `no-fly.boss-radius`, `levels.*` (per level tables:
`capacity-bonus`, `regen-multiplier`, `drain-multiplier`, `fly-speed`, `stats` as `STAT: [..]`, `trail` as
`PARTICLE:count[:spread]`), `energy.*` (drain incl. glide and rockets, dive regen, regen, resume and warning fractions,
bar hide delay), `abilities.*` (`dash`, `dive-recovery`, `landing-burst`), `emergency.void-rescue`, `combat.*` (seconds, sources, boss detection), `emergency.*`
(reasons that give Slow Falling), `effects.*`, `preferences.*` (defaults for new players), `upgrades.<level>`
(`core`, `amount`, `coins`), `claim.*`, `cores.names`.

## No-fly flag for boss arenas (documented contract)

Any feature can forbid flight without importing this package, in three ways:

1. **Self healing, recommended**: set the player's PDC `NamespacedKey("vampyuyu", "no_fly_until")` (long) to a future epoch
   millis; refresh it every second while the player is in the arena (e.g. `now + 3000`). A crash cannot strand a player.
2. Add the scoreboard tag `sb_no_fly` to the player (remove it when they leave).
3. List a box in `no-fly.regions` of `wings.yml`.

Boss detection for the combat tag: vanilla `Boss` entities, mobs with the scoreboard tag `sb_boss`, or max health >= 300.

Self defence: even if nobody sets a flag, a vanilla `Boss` or a mob with the scoreboard tag `sb_boss` within
`no-fly.boss-radius` (40) blocks counts as a no-fly zone (checked once a second; high health alone does not count).

Combat choice: `emergency.reasons` includes COMBAT and `combat.tag-attackers` stays true. Getting hit (or hitting a
player) grounds the flyer, who then falls gently instead of dying, while the attacker cannot kite from the air.

## Wing Cores (for the boss feature)

Items `wings_core_1` .. `wings_core_5` (Feather, Gilded, Radiant, Celestial, Voidwyrm Core; rarity RARE..DIVINE).
Create them with `core.items().create("wings_core_3", amount)` (also `/sbgive wings_core_3`). Suggested: bosses of tier N
drop core N. Default upgrade table: 1->2 one core 1, 2->3 three core 1, 3->4 one core 2, ... 9->10 one core 5.

## Data layout

Profile section `wings` (`WingsData`): `received`, `source`, `grantedAt`, `activeId`, `level`, `lastClaim`, `claims`,
`pendingReturn`, `energy` (-1 = full or live), `exhausted` and `energySavedAt` (the bar and lockout while the Wings are
off), `coresUsed`, preferences `trail`, `bar`, `doubleJump`, `sounds`.
No other files. Combat tags and fall guards are in memory only.

## API and events

Publishes `WingsApi` (`create`, `isWings`, `isWearing`, `grantOnce`, `hasReceived`). Consumes `StatsApi` (modifier source
`"wings"`, stat `FLIGHT_ENERGY`; capacity falls back to 100 + bonus when absent), `IslandApi` (island regen and drain),
`EconomyApi` (upgrade and claim coins; costs are waived when the economy is absent). No events published or consumed.

## Resource pack

`resource-pack/art/wings_art.py` (stdlib only, deterministic) writes everything; `resource-pack/art/wings_check.py`
validates references, sizes and reproducibility; `make test-pack` passes with the files.
Files: `equipment/wings.json`, `equipment/wings_tier1..5.json` (layers `humanoid` + `wings`),
`textures/entity/equipment/wings/wings_tier1..5.png` (64x32, elytra UV: visible face x36..46 y2..22),
`textures/entity/equipment/humanoid/wings_torso.png`, item icons `textures/item/wings/{wings,wings_tier2..5,core_1..5}.png`,
`items/wings/*.json`, `models/item/wings/*.json`.

## NEEDS (outside my files) and integration checklist

Checked in the sibling worktrees at review time: neither is wired yet. Wings defend themselves where they can (see above)
but these tickets are required at integration:

* `economy`: refuse any stack whose PDC `vampyuyu:untradable` is set (long 1) in auction, bazaar, shops, trading, AND any
  shulker box or bundle that contains such a stack (nested contents). Today nothing in `economy` reads the flag.
* `boss`: its `arena.noFly()` only blocks hurting the boss; it should set the player PDC `no_fly_until` (or tag
  `sb_no_fly`) for players in the arena (the wings' boss-radius check is only a safety net), tag boss mobs `sb_boss`,
  drop `wings_core_N`, and call grantOnce. Call `core.service(WingsApi.class).ifPresent(w -> w.grantOnce(player, "boss:voidwyrm"))`; drop
  `core.items().create("wings_core_N")`; mark arena players with the no-fly flag above; tag boss mobs `sb_boss`.
* Optional (foundation): `WingsApi.energy(Player)` / `maxEnergy(Player)` if the HUD should show the bar (today the bar is
  a boss bar, nothing else writes the action bar).

## Known gaps / NOT verified in game

* Nothing was run on a real server or client: the wings render (equipment asset, the `wings` + `humanoid` layers, UV of the
  64x32 texture, the glider-component assumption of the wings layer), the item icons, the boss bar look, the flight feel and
  numbers, particle and sound mix are unverified. MockBukkit only proves the logic.
* MockBukkit cannot build anvil, grindstone, smithing or enchanting events, so those four guards are untested (the crafting
  grid guard is). Dropped item properties (`setOwner`, unlimited lifetime) are not implemented by MockBukkit and are only
  exercised for "does not throw".
* The Wings use the elytra as base: gliding and rocket boosts work while worn, cost energy and are refused in no-fly
  places or when exhausted. The wing rendering depends on the elytra glider component (unverified).
* The dash (swap-hands key while flying: server `setVelocity` on a creative-flying player), the dive-recovery pitch and
  speed thresholds (pitch > 25 degrees, vertical speed < -0.4) and the landing burst are only unit-tested, never felt.
* Void rescue, the `NO GROUND BELOW` check (ray trace) and the boss-radius scan are unit-tested only through MockBukkit
  (ray tracing is not implemented there, so that branch falls back to "ground below"); the island home fallback was not
  exercised without the island feature.
* The container guards: MockBukkit cannot build the real player inventory view, so the shift-click path is covered only
  with a shulker view, number-key/off-hand swaps and the drag guard are untested; bundles/shulkers are found by the scan.
* A dropped item that falls into the void or is `/kill`ed is lost; `/wings claim` is the recovery path.
* Energy refill uses the client's on-ground flag.
