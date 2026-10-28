# Skyblock: binding design contract

Module: `survival-plugin/` (Java 25, paper-api 26.3, package `win.vampyuyu.survival`).
This document is the contract between the foundation (`core/`, `api/`, `SurvivalPlugin`) and the ten feature agents.
If this file and a feature's wishes disagree, this file wins. Need a change? Do not edit shared files: describe the
needed change (new `Stat`, new API method, new event) in your final report and the foundation owner applies it.

## 1. Rules (read first)

1. **Every user-facing string is English** (chat, menus, lore, item names, titles, boss bars, errors). Code comments
   and docs are English too. The user talks to the assistant in French; the game never does.
2. **You only ADD new files, and only inside your own package** (`win.vampyuyu.survival.<feature>`) plus your own test
   package (`src/test/java/win/vampyuyu/survival/<feature>/`). You never edit an existing file. The shared files are:
   `core/**`, `api/**`, `SurvivalPlugin.java`, `back/**`, `src/main/resources/plugin.yml`, `config.yml`, `pom.xml`,
   `docs/skyblock-design.md`, `Makefile`, `scripts/**`, and every other feature's files.
3. **No plugin.yml edits.** Commands go through `core.commands()`, permissions through `core.permissions()` (a command
   spec with `.permission(...)` registers its permission for you), listeners through `core.listen(...)`.
4. **Features never import each other's packages.** Cross-feature calls use `api/` interfaces looked up with
   `core.service(X.class)` (an `Optional`, because the other feature may be disabled or failed) and `api/event/` events.
5. **Resource pack:** you may only add NEW files whose path contains your feature name; never edit a shared pack file
   (see section 8).
6. **Config defaults** go in `survival-plugin/src/main/resources/<feature>.yml` (section 7).
7. **Permissions** are named `vampyuyu.sb.<feature>.<action>` (section 9).
8. **World names** come from `core.SkyblockWorlds`; never write `"sb_world"` in your own code.
9. Never `git add -A` (the repo has `.claude/worktrees/`); `git add` your own paths only. Work in your own git
   worktree. Commit only when asked.

## 2. Package ownership

| Package | Feature id | Owns | API implemented (`api/`) | Profile section keys |
|---|---|---|---|---|
| `utility` (+ `back`) | `utility` | `/back` (unchanged) and small QoL commands | none | `utility` |
| `island` | `island` | island grid, protection, co-op, home, `sb_hub`/`sb_world`/`sb_nether`/`sb_end` creation | `IslandApi` | `island` |
| `economy` | `economy` | coins, bank, shops, NPC merchants | `EconomyApi` | `economy` |
| `items` | `items` | stats engine, enchants, reforges, pets, accessories, damage formula | `StatsApi` | `items`, `pets`, `accessories` |
| `skills` | `skills` | 8 skills, levels, stat rewards | `SkillsApi` | `skills` |
| `wings` | `wings` | the Wings item, flight, flight energy, once-per-player grant | `WingsApi` | `wings` |
| `boss` | `boss` | bosses, arenas, summon items, loot | `BossApi` | `boss` |
| `minions` | `minions` | placeable minions | `MinionApi` | `minions` |
| `menu` | `menu` | `/menu` Skyblock menu, quests, fairy souls, hub navigation, cross-server handoff | none | `menu`, `quests`, `fairy_souls` |
| `dungeon` | `dungeon` | dungeon runs in `sb_dungeon_*` worlds | `DungeonApi` | `dungeon` |
| `core` | (foundation) | `Feature`, `Core`, services | none | none |
| `api` | (foundation) | contracts, events | none | none |

Foundation stubs already exist: `<Feature>Feature` (id, `enable()` logs a debug line) and `package-info.java` in each
package. You replace the body of `enable()` (and may add `disable()`); the class stays `public final class
<Name>Feature implements Feature` with the same constructor-less shape because `SurvivalPlugin.featureList()` does
`new XxxFeature()`.

### Enable order and lookups

`utility, island, economy, items, skills, wings, boss, minions, menu, dungeon`. Features enable in that order and
disable in reverse. A service is available once its feature's `enable()` returned. Therefore:

* Never `require()` a service of a LATER feature inside `enable()`. Look it up lazily, when you use it:
  `core.service(StatsApi.class).ifPresent(...)`.
* A disabled (`features.<id>.enabled: false`) or failed feature simply has no service: always cope with `Optional.empty()`.
* If your `enable()` throws, the feature is logged and skipped; the others still load. Everything you registered
  through the Core during that `enable()` is rolled back (services, `core.listen` listeners, commands, permissions), so
  other features never see a half-built service. Custom items, scheduled tasks and open GUIs are NOT rolled back.
  Listeners registered straight through `Bukkit.getPluginManager()` are not rolled back either: always use `core.listen`.
* Publish your API implementation with `core.register(IslandApi.class, impl)` once it is fully initialised (last
  step of `enable()`), not before.

## 3. Core reference (`win.vampyuyu.survival.core`)

```java
interface Feature { String id(); void enable(Core core) throws Exception; default void disable() {} }

final class Core {
  JavaPlugin getPlugin();  File getDataFolder();  FileConfiguration config();
  YamlConfiguration featureConfig(String id);          // copies <id>.yml out of the jar once; empty config if none
  void debug(String line);                              // logs when debug: true
  void listen(Listener l);                              // registerEvents for the plugin
  <T> void register(Class<T> type, T impl);             // IllegalStateException if already registered
  <T> Optional<T> service(Class<T> type);
  <T> T require(Class<T> type);                         // IllegalStateException if absent
  PlayerProfiles profiles();  Items items();  Commands commands();  Permissions permissions();
  Guis guis();  Scheduler scheduler();  Msg msg();  JsonStore store();
}
```

### Messages (`Msg`, `core.msg()`)
`info/ok/warn/error(CommandSender, String plainText)` prefix the message with "Skyblock »" and colour it gray / green /
yellow / red. The text is PLAIN (never parsed), so player names cannot inject formatting. `mini(sender, "<aqua>..")`
sends MiniMessage you wrote. Static formatters: `Msg.number(1234) -> "1.2k"` (truncated, `k M B T Q`),
`Msg.coins(1234567.8) -> "1,234,567 coins"`, `Msg.coinsShort(2500) -> "2.5k coins"`, `Msg.duration(ms) -> "1h 2m"`,
`Msg.component(mini)`, `Msg.plain(text, color)`.

### JSON files (`JsonStore`, `core.store()`)
Root = the plugin data folder. `read(rel, Class, Supplier defaults)`, `write(rel, obj)`, `exists`, `delete`. Writes go to
`<file>.tmp` then `ATOMIC_MOVE`. A corrupt file is logged, copied to `<file>.corrupt`, and the defaults are returned.
`JsonStore.GSON` is the shared Gson. Paths may not escape the data folder.

### Player data (`PlayerProfiles`, `core.profiles()`)
One document per player: `plugins/VampyuyuSurvival/players/<uuid>.json`
(`{version, name, lastSeen, sections: {<key>: {...}}}`). Each feature owns the SECTION KEYS of section 2 and stores
a plain Gson class of its own in them.

```java
public final class EconomyData { public double coins; }     // yours, in your package
var data = profiles.section(uuid, "economy", EconomyData.class, EconomyData::new);
data.coins += 5;  profiles.markDirty(uuid);
profiles.edit(uuid, "economy", EconomyData.class, EconomyData::new, d -> d.coins += 5);  // any thread, offline OK
```
* Loaded at pre-login and on join; offline UUIDs are loaded lazily (blocking disk read: do that off the main thread).
* Saved every 5 minutes, on quit and on disable. Writes run on one ordered IO thread; a quit followed by a quick
  rejoin sees the latest data. Only dirty profiles are written (join and quit mark the profile dirty to refresh `name`
  and `lastSeen`; a lookup of an offline player that changes nothing writes nothing).
* A section object is shared and mutable: mutate it on the main thread, or use `edit(...)` from other threads.
  A key must always be requested with the same class (otherwise `IllegalStateException`). A section that fails to
  parse is kept under `<key>.corrupt` and replaced by your defaults.
* Gson tips: public fields or records; give new fields a default value so old documents still load.
* Data that is not per player (island registry, boss spawn points...) lives in `core.store()` files under
  `<dataFolder>/<feature>/...`.

### Items (`Items`, `core.items()`)
```java
CustomItemDef def = CustomItemDef.builder("wings", "Wings", Rarity.MYTHIC, Material.ELYTRA)
    .itemModel("vampyuyu:wings/wings")                 // optional, resource pack item model
    .equippable(EquipmentSlot.CHEST, "vampyuyu:wings_default") // optional, worn asset: equipment/wings_default.json
    .type("CHESTPLATE").lore("<gray>Lets you fly.")
    .stat(Stat.FLIGHT_ENERGY, 100).pdc("tier", 1L).unbreakable(true).build();
core.items().register(def);                  // IllegalStateException on a duplicate id
ItemStack s = core.items().create("wings");  // IllegalArgumentException on an unknown id
```
* `Rarity`: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, DIVINE, SPECIAL (each with a colour and an upper case label).
* Ids match `[a-z0-9_]+`. Prefix yours with your feature when it could clash (`minion_cobblestone_1`, `boss_grave_key`).
* PDC namespace `vampyuyu`: `item_id` (the id) and `bonus_<stat>` (per-item bonus) are reserved; extra values you
  declare with `.pdc(key, value)` use `[a-z0-9_./-]+` keys, read with `items.getString/getLong/getDouble(stack, key)`,
  written with `items.set(stack, key, value)`.
* Queries: `idOf(stack) -> Optional<String>`, `isCustom(stack)`, `is(stack, id)`, `statsOf(stack)` (definition stats
  + per-item bonuses), `bonuses(stack)`, `setBonus(stack, stat, amount)`, then `refresh(stack)` to redraw the lore.
* Lore is rendered by `ItemRenderer` (stable): stat lines in `Stat` order (`Strength: +50 (+10)`), blank line,
  your description lines (MiniMessage), blank line, bold `RARITY TYPE` (e.g. `LEGENDARY CHESTPLATE`). Do not write
  rarity or stat lines yourself.
* Admin hook registered by the core: `/sbgive <id> [amount] [player]`, permission `vampyuyu.sb.admin` (OP).
* Stat contribution of held/worn items is the `items` feature's job (through `StatsApi`), not the core's.

### Commands and permissions
```java
core.commands().command("island").aliases("is").description("Manage your island")
    .usage("/island [home|invite <player>]")
    .permission("vampyuyu.sb.island.use", PermissionDefault.TRUE)
    .executor(myExecutor).tab(myCompleter).register();
core.permissions().register("vampyuyu.sb.island.admin", "Island administration", PermissionDefault.OP);
```
Commands are registered in the server `CommandMap` (no plugin.yml). A name already taken by another plugin is logged
and the command stays reachable as `/vampyuyusurvival:name`. Everything is removed on disable. `/back` keeps its
plugin.yml entry on purpose.

### GUIs (`Guis`, `core.guis()`)
```java
Gui gui = core.guis().create(3, "<gold>Title");               // 1-6 rows, MiniMessage or Component title
gui.border(Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
gui.set(13, Guis.icon(Material.DIAMOND, "<aqua>Name", "<gray>Lore line"), click -> {
    if (click.shift()) ...; click.player(); click.close();     // close() runs next tick
});
gui.paginate(list, gui.innerSlots(), item -> icon(item), (click, item) -> ..., prevSlot, nextSlot);
gui.onClose(player -> ...);  gui.open(player);
```
One global listener cancels EVERY click and drag while the player's top inventory is one of our menus (also
shift-clicks from the player's own inventory, number keys, double click). Nothing can be moved in or out. Menu text
is tooltip text only (no text drawn over pack art). Handler exceptions are caught and logged.

### Scheduler and cooldowns
`core.scheduler()`: `sync` (now if already on the main thread), `nextTick`, `async`, `later(ticks, r)`,
`laterAsync`, `repeating(delay, period, r)`, `repeatingAsync`; each returns a `Scheduler.Handle` with `cancel()`.
All tasks are cancelled on disable. `new Cooldowns()` (`tryUse(uuid, key, millis)`, `remaining`, `ready`, `set`,
`clear`) is an in-memory helper; create your own instance, it is not a shared service.

### Config
* `config.yml` (foundation): `debug` and `features.<id>.enabled` (default true).
* `core.featureConfig("<id>")`: per-feature YAML at `plugins/VampyuyuSurvival/<id>.yml`, copied from the jar the
  first time; keys missing from the user's file fall back to the jar's copy.

## 4. API reference (`win.vampyuyu.survival.api`)

All interfaces are documented with Javadoc in the source; this is the summary. Implementer = owner in section 2.

| Interface | Methods |
|---|---|
| `StatsApi` | `double stat(Player, Stat)`; `addModifier(UUID, String source, Stat, double)` (replaces the same source+stat); `removeModifiers(UUID, String source)`; `recompute(Player)` (main thread) |
| `EconomyApi` | `double coins(UUID)`; `has(UUID, double)`; `boolean take(UUID, double, String reason)`; `add(UUID, double, String reason)`; `addTransactionListener(Consumer<Transaction>)`; thread safe |
| `SkillsApi` | `addXp(Player, Skill, double, String source)`; `int level(UUID, Skill)`; `double xp(UUID, Skill)`; `int maxLevel(Skill)` |
| `IslandApi` | `Optional<IslandInfo> islandAt(Location)`; `islandOf(UUID)`; `isSkyblockWorld(World)`; `canBuild(Player, Location)`; `teleportHome(Player)`; `Collection<IslandInfo> all()` |
| `WingsApi` | `ItemStack create()`; `isWings(ItemStack)`; `isWearing(Player)`; `boolean grantOnce(Player, String source)`; `hasReceived(UUID)` |
| `BossApi` | `boolean spawn(String bossId, Location, @Nullable Player)`; `Collection<String> bossIds()`; `registerLootHook(BossLootHook)` |
| `MinionApi` | `int slotsFor(UUID)`; `int placedCount(UUID)` |
| `DungeonApi` | `boolean inDungeon(Player)` |

Records and enums: `IslandInfo(UUID id, UUID owner, Set<UUID> members, String world, int cellX, int cellZ, Location
center, int radius)`; `EconomyApi.Transaction(player, delta, balanceAfter, reason, timeMillis)`; `BossLootHook.roll(
BossKillEvent, Player recipient) -> List<ItemStack>`.

* `Stat` (20): HEALTH, DEFENSE, TRUE_DEFENSE, STRENGTH, CRIT_CHANCE, CRIT_DAMAGE, ATTACK_SPEED, FEROCITY,
  ABILITY_DAMAGE, INTELLIGENCE, SPEED, MINING_SPEED, MINING_FORTUNE, FARMING_FORTUNE, FORAGING_FORTUNE, FISHING_SPEED,
  SEA_CREATURE_CHANCE, MAGIC_FIND, PET_LUCK, FLIGHT_ENERGY. Each has `displayName()`, `color()`, `symbol()`, `base()`,
  `percent()`, `key()`.
* `Skill` (8): COMBAT, MINING, FARMING, FORAGING, FISHING, ENCHANTING, ALCHEMY, CARPENTRY (`displayName()`, `icon()`, `key()`).
* Stat modifier sources are namespaced strings: `"wings"`, `"skill:mining"`, `"pet"`, `"armor"`, `"accessory"`,
  `"reforge"`. A feature only replaces or removes its own sources.
* Events (`api/event/`, all `final`, with `HandlerList`): `CoinsChangeEvent` (async when fired off-thread; check
  `isAsynchronous()`), `SkillXpEvent` (cancellable, amount mutable), `SkillLevelUpEvent`, `IslandEnterEvent`,
  `IslandLeaveEvent`, `StatsRecalculateEvent` (mutable `totals()`), `BossKillEvent` (boss id, killer, top damagers,
  location). Every event except `CoinsChangeEvent` is fired on the main thread. The implementing feature fires them.

## 5. Worlds (`core.SkyblockWorlds`)

`HUB = "sb_hub"`, `WORLD = "sb_world"`, `NETHER = "sb_nether"`, `END = "sb_end"`, `DUNGEON_PREFIX = "sb_dungeon_"` with
`dungeon(runId)`, `isDungeon(name)`, `isSkyblock(name)`. The `island` feature creates and owns hub/world/nether/end;
the `dungeon` feature creates and deletes `sb_dungeon_<run>` worlds.

## 6. Data files

| Path under `plugins/VampyuyuSurvival/` | Owner | Content |
|---|---|---|
| `players/<uuid>.json` | core | per-player sections (section 2 for keys) |
| `back.json` | utility | `/back` state (original format, do not move) |
| `config.yml` | core | `debug`, `features.<id>.enabled` |
| `<feature>.yml` | that feature | defaults shipped in `src/main/resources/<feature>.yml` |
| `<feature>/**` | that feature | any non-player data, written through `core.store()` |

Section contents are private to the owner. If another feature needs the data, it goes through the `api/` interface.

## 7. Per-feature config files

Create `survival-plugin/src/main/resources/<feature>.yml` (a NEW file named after your feature id) and read it with
`core.featureConfig("<feature>")`. Document each key with a comment. Do not touch `config.yml`.

## 8. Resource pack rules

Pack sources: `resource-pack/pack/assets/vampyuyu/...` (built by `make pack`, which zips the whole tree). You may only
ADD files whose path contains your feature name:

* item definitions: `assets/vampyuyu/items/<feature>/<name>.json` (used as item model `vampyuyu:<feature>/<name>`)
* models: `assets/vampyuyu/models/item/<feature>/...`
* textures: `assets/vampyuyu/textures/item/<feature>/...`
* worn equipment: `assets/vampyuyu/equipment/<feature>_<name>.json` (asset id `vampyuyu:<feature>_<name>`) and
  its layer textures `assets/vampyuyu/textures/entity/equipment/**/<feature>_<name>.png`
* new generator scripts: `resource-pack/art/<feature>_*.py`, not wired into any shared script.

Never edit `pack.mcmeta`, shaders, fonts, lang files, `blank.json` or another feature's files. Check with
`make test-pack`. Remember servers read the pack at start.

## 9. Permissions

Format `vampyuyu.sb.<feature>.<action>`: `vampyuyu.sb.island.use`, `vampyuyu.sb.economy.pay`,
`vampyuyu.sb.boss.summon`, `vampyuyu.sb.dungeon.enter`... Players' defaults: `PermissionDefault.TRUE`; staff and
destructive actions: `OP`. The foundation owns `vampyuyu.sb.admin` (OP, used by `/sbgive`); admin sub-commands of your
feature use `vampyuyu.sb.<feature>.admin`. `vampyuyu.back` and `vampyuyu.back.bypass` stay in plugin.yml.

## 10. Tests

* JUnit 4 + MockBukkit (`org.mockbukkit.mockbukkit.MockBukkit`), tests in `src/test/java/win/vampyuyu/survival/<feature>/`.
* Run: `make plugin-test PLUGIN=survival` (silent when green; count the files in
  `survival-plugin/target/surefire-reports/` to be sure tests ran) or `TEST='win.vampyuyu.survival.<feature>.*Test'`.
* Build the jar: `make plugin-build PLUGIN=survival`. The foundation test `SurvivalPluginTest` boots the whole plugin;
  it must stay green with your feature enabled.
* MockBukkit limits found while building the core: `InventoryView.getSlotType` is not implemented, and the command
  map is a forwarding view (remove entries by key). Anything visible in game still needs a real check (CLAUDE.md,
  step 3); a MockBukkit pass is not proof.

## 11. Investigation: the network currency

Read in `network-plugin/.../CommunityFeatures.java`, `ManagementApi.java` and `internal/players/economy.go`:

* The network currency (default name **Shards**, env `NETWORK_CURRENCY_NAME`) is a per-player integer wallet in the
  panel's SQLite database (`wallets(uuid, balance >= 0)`), NOT on any Minecraft server. Game servers keep no balance
  cache ("read-through profiles").
* It is earned by arena events: `POST /bridge/events` (`RecordEvent`, idempotent per event id, gain up to 200000 per
  entry, servers `creative|survival|lobby`). `/profil` shows balance and paged history through `POST /bridge/profile`.
  `/moneyadmin give|take|set <player|uuid> <amount>` (permission `vampyuyu.money.admin`, also from console) goes through
  `POST /bridge/money` with an idempotency id, logged in `money_operations`. All calls are plain HTTP to
  `ROLE_BRIDGE_URL` (`http://panel:8083`) authenticated with the `role_bridge_token` secret.
* Amounts are `int64`, capped at 1,000,000,000 per operation, and every call is a network round trip.

**Decision: Skyblock coins are a separate currency, NOT bridged.** Per-player `coins` (double) stored in the profile
section `economy` of `players/<uuid>.json`, owned by the `economy` feature, exposed only through `EconomyApi`. Reasons:
Skyblock needs thousands of transactions per minute (minion sales, bazaar-style trades, fractional prices) which the
panel bridge (HTTP round trip, integer amounts, 1e9 cap, panel SQLite) cannot absorb; a bridge would also couple the
Skyblock economy to arena rewards and let creative/lobby grind inflate it. Coins are called "coins" in every message;
Shards keep their name. A future optional exchange (Shards to coins) would be its own feature behind the bridge and is out
of scope.

## 12. Investigation: Redis and the cross-server destination handoff

What exists (read, not guessed):

* Redis 8.2 runs as the `redis` service in `compose.local.yaml` and `compose.prod.yaml` on the `game` network, with
  `requirepass` taken from the secret `arena_redis_password`, `maxmemory 64mb`, `maxmemory-policy noeviction`,
  `appendonly yes`. **Every key must have a TTL.**
* The `survival` service uses the `*game` anchor (`networks: [game]`), lists `arena_redis_password` in its secrets
  and inherits `ARENA_REDIS_HOST: redis` and `ARENA_REDIS_PASSWORD_FILE: /run/secrets/arena_redis_password` from the shared
  paper environment. So the survival container CAN reach Redis (also in prod). Port 6379.
* The arena plugin (`arena-plugin/.../RedisManager.java`, Jedis 6.2.0) reads the password file (it requires at least
  32 characters), builds a `JedisPool`, and uses `nexus:transfer:<uuid>` for arena handoffs (`SETEX` 30 s by
  `CrossServerBridge.join`, `GETDEL` by `CrossServerBridge.arrived` on `PlayerJoinEvent`). The arena plugin is
  deployed on lobby, creative AND survival (`scripts/plugins.sh`: `arena` goes to all three), so its `arrived()` consumes
  anything under that exact key. **Skyblock must therefore not reuse `nexus:transfer:*`.**
* Players change server with the `BungeeCord` plugin channel `Connect` message (`LobbySelector`, `WorldSelector`,
  `CrossServerBridge` all do it; `infra/velocity.toml` has `bungee-plugin-message-channel = true`).
* The survival plugin did not have Jedis. The foundation `pom.xml` now depends on `redis.clients:jedis:6.2.0` (Gson and
  SLF4J excluded, Paper provides them) and shades it relocated to `win.vampyuyu.survival.lib.redis.jedis.*`,
  `lib.pool.*` and `lib.json.*`. In code write `import redis.clients.jedis.*;` as the arena plugin does: relocation only happens at package time.

Mechanism the `menu` feature must use:

1. Key `sb:transfer:<uuid>`, value JSON `{"dest":"hub|island|dungeon|...","arg":"...","ts":<epoch millis>}`, written
   with `SETEX` and a TTL of 30 seconds (same as arena).
2. On the sending side (a player on lobby who clicks "Skyblock"): write the key, then send the `BungeeCord` `Connect`
   message for server `survival` (register the outgoing `BungeeCord` channel first:
   `getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord")`).
3. On the survival side, in `PlayerJoinEvent`: `GETDEL sb:transfer:<uuid>` off the main thread (use `core.scheduler().async`),
   parse, then teleport (the island feature's `IslandApi.teleportHome` or `sb_hub` spawn) on the main thread. A missing
   key means a normal join: send the player to `sb_hub`. Ignore a payload older than 30 s (`ts`).
4. Connection settings: host from `System.getenv("ARENA_REDIS_HOST")` (default `redis`), port 6379, password from the file
   named by `ARENA_REDIS_PASSWORD_FILE` (default `/run/secrets/arena_redis_password`), connection timeout 2 s, socket
   timeout 3 s, pool of at most 4. If Redis is unreachable, log once and fall back to a plain `sb_hub` join (never fail
   the join). Key prefix `sb:` is reserved for Skyblock.
5. Who WRITES the key from the lobby side: the lobby server runs `lobby-selector` (`lobby/`, `LobbySelector.connect`)
   and `network-plugin` (`WorldSelector`), NOT survival-plugin (`plugins.sh`: `survival` is installed on the survival server
   only). Feature agents cannot edit those plugins. Until the foundation owner adds the writer there, a player
   arriving from lobby simply has no key and lands on `sb_hub`: the survival-side half (steps 3 and 4) is therefore enough to
   ship, and everything inside the survival server (hub to island, island to dungeon) needs no Redis at all
   (teleport directly).

Not verified end to end: the Redis path was read from the compose files and plugin code but not exercised from the
survival container.
