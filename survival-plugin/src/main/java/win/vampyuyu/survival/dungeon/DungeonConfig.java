package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * Parsed and validated dungeon content: the {@code dungeon.*} settings and the {@code dungeon_*} tables. Never throws on bad values: they are clamped to a sane range and each
 * correction is recorded in {@link #problems()} so the feature can log them once.
 */
public final class DungeonConfig {
    /** One floor of the dungeon. */
    public record Floor(int number, String name, String palette, int grid, int rooms, int puzzles, int traps,
            int secretRooms, int miniBosses, boolean shop, boolean fairy, int witherDoors, int loops, double hp,
            double damage, int mobLevel, int parSeconds, int secretsNeededPercent, double reward,
            int minCombatLevel, int tier, String weakness, String resistance) {}

    /** One warned attack of a floor boss. */
    public record BossAttack(String kind, String warning, String hint) {}

    /** The boss of one floor. {@code phaseKind} is ADDS or ANCHORS; {@code phaseGoal} is what its shield asks for. */
    public record BossKit(String name, String entity, String weapon, String color, String tagline, String phaseKind,
            String phaseGoal, List<BossAttack> attacks) {}

    /**
     * One trade of the Crypt Forge. {@code cost} maps item ids (custom id or {@code mc:MATERIAL}) to amounts. Exactly one
     * outcome: {@code give} (+ {@code amount}), {@code cacheRolls} item rolls of a grade, or {@code coins}.
     */
    public record ForgeTrade(String id, String name, String icon, Map<String, Integer> cost, String give, int amount,
            int cacheRolls, Grade cacheGrade, double coins) {}

    /** What a grade pays. */
    public record Tier(double coins, double xp, int rolls, String chest) {}

    /** One line of the item reward table. {@code item} is a custom item id or {@code mc:MATERIAL}. */
    public record RewardEntry(String item, int weight, int min, int max, int minFloor, Grade minGrade) {}

    public static final List<String> PALETTES =
        List.of("stone_brick", "mossy", "deepslate", "blackstone", "nether", "end", "crimson");

    private final List<String> problems = new ArrayList<>();

    public int maxConcurrentRuns;
    public int closeDelaySeconds;
    public int runTimeoutMinutes;
    public int bossTimeoutMinutes;
    public int bossWarningTicks;
    public int bossPauseTicks;
    public int holdSeconds;
    public int eliteHealthPercent;
    /** From this floor on the boss feature's crypt_lord is used (0 = never, the scripted floor-scaled boss is used). */
    public int bossExternalFromFloor;
    public int readyCountdownSeconds;
    public boolean sidebar;
    public boolean darknessEffect;
    public boolean requirePreviousFloor;

    public int partyMaxSize;
    public int inviteExpireSeconds;
    public int readyCheckSeconds;
    public int warpCooldownSeconds;
    public boolean membersCanInvite;

    public int maxMsPerTick;
    public int baseY;
    public int blocksPerCheck;

    public boolean entranceEnabled;
    public String entranceWorld;
    public double entranceX;
    public double entranceY;
    public double entranceZ;
    public double entranceRadius;
    public int entranceHintCooldownSeconds;

    public int deathsAllowed;
    public int ghostSeconds;
    public int reviveSeconds;
    public int reviveHealthPercent;
    public double voidDamage;
    public List<String> allowedCommands = List.of();

    public int maxMobsPerRoom;
    public int maxMobsPerRun;
    public double partyHealthScale;
    public double partyDamageScale;
    /** Seconds a basic monster lives against the floor tier's sword (see {@link win.vampyuyu.survival.dungeon.run.DungeonHp}). */
    public double mobTargetSeconds;
    /** Base health that counts as one basic monster. */
    public double mobBaseHealth;
    public int watcherGuardsBase;
    public int watcherGuardsPerFloor;

    public double shopLeafPrice;
    public double shopFlaskPrice;
    public int flaskHealPercent;
    public int flaskCooldownSeconds;

    public double speedDecaySeconds;
    public double deathPenalty;
    public final Map<Grade, Integer> thresholds = new EnumMap<>(Grade.class);

    public long catacombsLevelBase;
    public int catacombsMaxLevel;
    public double catacombsXpShare;
    public double catacombsRewardPercentPerLevel;
    public final List<ForgeTrade> forgeTrades = new ArrayList<>();

    public final Map<Grade, Tier> tiers = new EnumMap<>(Grade.class);
    public final List<RewardEntry> rewardItems = new ArrayList<>();
    public final Map<Integer, Floor> floors = new LinkedHashMap<>();
    public final Map<Integer, BossKit> bosses = new LinkedHashMap<>();

    private DungeonConfig() {}

    /** Problems found while parsing (already corrected). */
    public List<String> problems() { return List.copyOf(problems); }

    public int floorCount() { return floors.size(); }

    public Floor floor(int number) { return floors.get(number); }

    /** The boss of a floor; a floor without a row meets a plain Crypt Lord. */
    public BossKit boss(int floor) {
        BossKit kit = bosses.get(floor);
        return kit != null ? kit : new BossKit("Crypt Lord", "WITHER_SKELETON", "NETHERITE_SWORD", "<dark_red>", "It stirs...", "ADDS",
            "kill his servants", List.of(new BossAttack("SLAM", "The Crypt Lord raises his sword!",
                "<red>Red circle around the boss: <white>walk out of it.")));
    }

    // ------------------------------------------------------------------ parsing

    /** Built-in configuration: the defaults of every value, the built-in floors, reward table and Forge. */
    public static DungeonConfig defaults() {
        DungeonConfig c = new DungeonConfig();
        c.read(Settings.empty("dungeon"), List.of(), List.of(), List.of(), List.of());
        return c;
    }

    /** Reads the {@code dungeon.*} settings and the {@code dungeon_*} tables; bad values are corrected and reported. */
    public static DungeonConfig load(Db db) {
        Settings settings = Settings.load(db, "dungeon");
        DungeonConfig c = new DungeonConfig();
        db.read(conn -> {
            List<Object[]> grades = Db.query(conn, "SELECT id, min_score, coins, xp, rolls, chest FROM dungeon_grade ORDER BY position",
                rs -> new Object[] {rs.getString(1), rs.getInt(2), rs.getDouble(3), rs.getDouble(4), rs.getInt(5), rs.getString(6)});
            List<Object[]> rewards = Db.query(conn, "SELECT item, weight, min_amount, max_amount, min_floor, min_grade FROM dungeon_reward "
                + "ORDER BY position", rs -> new Object[] {rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                    rs.getString(6)});
            Map<String, Map<String, Integer>> costs = new LinkedHashMap<>();
            Db.query(conn, "SELECT trade_id, item, amount FROM dungeon_trade_cost ORDER BY rowid", rs -> {
                costs.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>()).put(rs.getString(2), rs.getInt(3));
                return null;
            });
            List<Object[]> trades = Db.query(conn, "SELECT id, name, icon, give, amount, cache_rolls, cache_grade, coins FROM dungeon_trade "
                + "ORDER BY position", rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getInt(5), rs.getInt(6), rs.getString(7), rs.getDouble(8), costs.getOrDefault(rs.getString(1), Map.of())});
            List<Object[]> floors = Db.query(conn, "SELECT number, name, palette, grid, rooms, puzzles, traps, secret_rooms, mini_bosses, "
                + "shop, fairy, wither_doors, loops, hp, damage, mob_level, par_seconds, secrets_needed_percent, reward, "
                + "min_combat_level, tier, weakness, resistance FROM dungeon_floor ORDER BY number", rs -> {
                    Object[] row = new Object[23];
                    for (int i = 0; i < row.length; i++) {
                        row[i] = rs.getObject(i + 1);
                    }
                    return row;
                });
            Map<String, List<BossAttack>> attacks = new LinkedHashMap<>();
            Db.query(conn, "SELECT boss_id, kind, warning, hint FROM dungeon_boss_attack ORDER BY boss_id, position", rs -> {
                attacks.computeIfAbsent(rs.getString(1), k -> new java.util.ArrayList<>())
                    .add(new BossAttack(rs.getString(2), rs.getString(3), rs.getString(4)));
                return null;
            });
            Db.query(conn, "SELECT id, floor, name, entity, weapon, color, tagline, phase_kind, phase_goal FROM dungeon_boss", rs -> {
                List<BossAttack> list = attacks.get(rs.getString(1));
                if (list != null) {
                    c.bosses.put(rs.getInt(2), new BossKit(rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getString(9), List.copyOf(list)));
                }
                return null;
            });
            c.read(settings, grades, rewards, trades, floors);
            return null;
        });
        c.problems.addAll(settings.problems());
        return c;
    }

    private int num(Settings s, String path, int def, int min, int max) {
        int v = s.getInt(path, def);
        if (v < min || v > max) {
            problems.add(path + "=" + v + " is outside " + min + ".." + max + ", using " + Math.max(min, Math.min(max, v)));
            v = Math.max(min, Math.min(max, v));
        }
        return v;
    }

    private double dbl(Settings s, String path, double def, double min, double max) {
        double v = s.getDouble(path, def);
        if (Double.isNaN(v) || v < min || v > max) {
            double fixed = Double.isNaN(v) ? def : Math.max(min, Math.min(max, v));
            problems.add(path + "=" + v + " is outside " + min + ".." + max + ", using " + fixed);
            v = fixed;
        }
        return v;
    }

    private void read(Settings r, List<Object[]> grades, List<Object[]> rewards, List<Object[]> trades, List<Object[]> floorRows) {
        maxConcurrentRuns = num(r, "max-concurrent-runs", 3, 1, 32);
        closeDelaySeconds = num(r, "close-delay-seconds", 45, 5, 3600);
        runTimeoutMinutes = num(r, "run-timeout-minutes", 75, 5, 600);
        bossTimeoutMinutes = num(r, "boss-timeout-minutes", 12, 1, 120);
        bossWarningTicks = num(r, "boss.warning-ticks", 40, 10, 200);
        bossPauseTicks = num(r, "boss.pause-ticks", 100, 20, 600);
        holdSeconds = num(r, "rooms.hold-seconds", 20, 5, 120);
        eliteHealthPercent = num(r, "rooms.elite-health-percent", 500, 100, 5000);
        bossExternalFromFloor = num(r, "boss-external-from-floor", 0, 0, 99);
        readyCountdownSeconds = num(r, "ready-countdown-seconds", 8, 0, 120);
        sidebar = r.getBoolean("sidebar", true);
        darknessEffect = r.getBoolean("darkness-effect", false);
        requirePreviousFloor = r.getBoolean("require-previous-floor", true);

        partyMaxSize = num(r, "party.max-size", 5, 1, 5);
        inviteExpireSeconds = num(r, "party.invite-expire-seconds", 60, 5, 3600);
        readyCheckSeconds = num(r, "party.ready-check-seconds", 30, 5, 600);
        warpCooldownSeconds = num(r, "party.warp-cooldown-seconds", 15, 0, 3600);
        membersCanInvite = r.getBoolean("party.members-can-invite", false);

        maxMsPerTick = num(r, "generation.max-ms-per-tick", 6, 1, 40);
        baseY = num(r, "generation.base-y", 64, -32, 200);
        blocksPerCheck = num(r, "generation.blocks-per-check", 256, 16, 8192);

        entranceEnabled = r.getBoolean("entrance.enabled", true);
        entranceWorld = r.getString("entrance.world", "sb_hub");
        entranceX = r.getDouble("entrance.x", 33.5);
        entranceY = r.getDouble("entrance.y", 101.0);
        entranceZ = r.getDouble("entrance.z", 0.5);
        entranceRadius = dbl(r, "entrance.radius", 3.0, 1.0, 32.0);
        entranceHintCooldownSeconds = num(r, "entrance.hint-cooldown-seconds", 90, 5, 86400);

        deathsAllowed = num(r, "rules.deaths-allowed", 3, 0, 20);
        ghostSeconds = num(r, "rules.ghost-seconds", 40, 5, 600);
        reviveSeconds = num(r, "rules.revive-seconds", 3, 1, 60);
        reviveHealthPercent = num(r, "rules.revive-health-percent", 50, 10, 100);
        voidDamage = dbl(r, "rules.void-damage", 6.0, 0.0, 100.0);
        List<String> cmds = new ArrayList<>();
        List<String> configured = r.has("rules.allowed-commands") ? r.getList("rules.allowed-commands")
            : List.of("party", "pc", "dungeon", "dg", "msg", "tell", "w", "r", "help", "menu", "leave");
        for (String s : configured) {
            if (s != null && !s.isBlank()) {
                cmds.add(s.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
            }
        }
        allowedCommands = List.copyOf(cmds);

        maxMobsPerRoom = num(r, "mobs.max-per-room", 14, 1, 60);
        maxMobsPerRun = num(r, "mobs.max-per-run", 70, 1, 400);
        if (maxMobsPerRun < maxMobsPerRoom) {
            problems.add("mobs.max-per-run is below max-per-room, raised to " + maxMobsPerRoom);
            maxMobsPerRun = maxMobsPerRoom;
        }
        partyHealthScale = dbl(r, "mobs.party-health-scale", 0.30, 0.0, 3.0);
        partyDamageScale = dbl(r, "mobs.party-damage-scale", 0.10, 0.0, 3.0);
        mobTargetSeconds = dbl(r, "mobs.target-seconds", 2.5, 0.0, 600.0);
        mobBaseHealth = dbl(r, "mobs.base-health", 40.0, 1.0, 10000.0);
        watcherGuardsBase = num(r, "mobs.watcher-guards-base", 3, 0, 30);
        watcherGuardsPerFloor = num(r, "mobs.watcher-guards-per-floor", 1, 0, 10);

        shopLeafPrice = dbl(r, "shop.spirit-leaf-price", 1500, 0, 1e9);
        shopFlaskPrice = dbl(r, "shop.healing-flask-price", 400, 0, 1e9);
        flaskHealPercent = num(r, "shop.flask-heal-percent", 60, 1, 100);
        flaskCooldownSeconds = num(r, "shop.flask-cooldown-seconds", 15, 0, 3600);

        speedDecaySeconds = dbl(r, "scoring.speed-decay-seconds", 6.0, 0.5, 600.0);
        deathPenalty = dbl(r, "scoring.death-penalty", 2.0, 0.0, 50.0);
        readThresholds(grades);
        readTiers(grades);
        readRewardItems(rewards);
        catacombsLevelBase = num(r, "catacombs.level-base", 400, 1, 1_000_000);
        catacombsMaxLevel = num(r, "catacombs.max-level", 50, 1, 200);
        catacombsXpShare = dbl(r, "catacombs.xp-share", 0.5, 0.0, 10.0);
        catacombsRewardPercentPerLevel = dbl(r, "catacombs.reward-percent-per-level", 1.0, 0.0, 10.0);
        readForge(trades);
        readFloors(floorRows);
    }

    private static int defaultThreshold(Grade g) {
        return switch (g) {
            case S_PLUS -> 300;
            case S -> 270;
            case A -> 230;
            case B -> 160;
            case C -> 100;
            case D -> 0;
        };
    }

    private void readThresholds(List<Object[]> grades) {
        Map<String, Integer> stored = new LinkedHashMap<>();
        for (Object[] g : grades) {
            stored.put((String) g[0], (Integer) g[1]);
        }
        int previous = Integer.MAX_VALUE;
        Grade[] all = Grade.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Grade grade = all[i];
            int v = stored.getOrDefault(grade.label(), defaultThreshold(grade));
            if (grade == Grade.D) {
                v = 0;
            }
            if (v >= previous) {
                problems.add("scoring.thresholds." + grade.label() + " must be below the next grade, using " + (previous - 1));
                v = previous - 1;
            }
            if (v < 0) {
                v = 0;
            }
            thresholds.put(grade, v);
            previous = v;
        }
    }

    private static Tier defaultTier(Grade g) {
        return switch (g) {
            case D -> new Tier(400, 120, 1, "Wooden Chest");
            case C -> new Tier(900, 220, 2, "Wooden Chest");
            case B -> new Tier(1800, 380, 3, "Gold Chest");
            case A -> new Tier(3500, 650, 4, "Diamond Chest");
            case S -> new Tier(6500, 1100, 5, "Emerald Chest");
            case S_PLUS -> new Tier(12000, 1800, 6, "Obsidian Chest");
        };
    }

    private void readTiers(List<Object[]> grades) {
        Map<String, Object[]> stored = new LinkedHashMap<>();
        for (Object[] g : grades) {
            stored.put((String) g[0], g);
        }
        for (Grade grade : Grade.values()) {
            Tier d = defaultTier(grade);
            Object[] t = stored.get(grade.label());
            if (t == null) {
                tiers.put(grade, d);
                continue;
            }
            tiers.put(grade, new Tier(clampD("rewards.tiers." + grade.label() + ".coins", (Double) t[2], 0, 1e9),
                clampD("rewards.tiers." + grade.label() + ".xp", (Double) t[3], 0, 1e9),
                clampI("rewards.tiers." + grade.label() + ".rolls", (Integer) t[4], 0, 20), (String) t[5]));
        }
    }

    private int clampI(String path, int v, int min, int max) {
        if (v < min || v > max) {
            problems.add(path + "=" + v + " is outside " + min + ".." + max + ", using " + Math.max(min, Math.min(max, v)));
        }
        return Math.max(min, Math.min(max, v));
    }

    private double clampD(String path, double v, double min, double max) {
        if (Double.isNaN(v) || v < min || v > max) {
            double fixed = Double.isNaN(v) ? min : Math.max(min, Math.min(max, v));
            problems.add(path + "=" + v + " is outside " + min + ".." + max + ", using " + fixed);
            return fixed;
        }
        return v;
    }

    /** Built-in reward table, identical to the one seeded in the database (a test keeps them in step). */
    public static final List<RewardEntry> DEFAULT_REWARDS = List.of(
        new RewardEntry("dungeon_bone_fragment", 60, 3, 8, 1, Grade.D),
        new RewardEntry("dungeon_crypt_shard", 40, 1, 4, 1, Grade.C),
        new RewardEntry("mc:IRON_INGOT", 30, 4, 12, 1, Grade.D),
        new RewardEntry("mc:GOLD_INGOT", 20, 3, 9, 2, Grade.C),
        new RewardEntry("dungeon_wither_essence", 30, 2, 6, 2, Grade.B),
        new RewardEntry("mc:DIAMOND", 12, 1, 3, 3, Grade.B),
        new RewardEntry("dungeon_spirit_dust", 12, 1, 3, 4, Grade.A),
        new RewardEntry("mc:NETHERITE_SCRAP", 6, 1, 2, 5, Grade.A),
        new RewardEntry("skin_titan_edge", 4, 1, 1, 4, Grade.S),
        new RewardEntry("dungeon_relic", 3, 1, 1, 5, Grade.S),
        new RewardEntry("dungeon_dark_scroll", 1, 1, 1, 7, Grade.S_PLUS));

    private void readRewardItems(List<Object[]> rows) {
        if (rows.isEmpty()) {
            rewardItems.addAll(DEFAULT_REWARDS);
            return;
        }
        for (Object[] m : rows) {
            Object item = m[0];
            if (item == null || String.valueOf(item).isBlank()) {
                problems.add("a rewards.items entry has no item, skipped");
                continue;
            }
            int weight = (Integer) m[1];
            int min = (Integer) m[2];
            int max = (Integer) m[3];
            if (weight < 1 || min < 1 || max < min) {
                problems.add("rewards.items entry " + item + " has bad weight/min/max, skipped");
                continue;
            }
            Grade grade = Grade.parse(String.valueOf(m[5])).orElse(Grade.D);
            rewardItems.add(new RewardEntry(String.valueOf(item), weight, min, max, Math.max(1, (Integer) m[4]), grade));
        }
        if (rewardItems.isEmpty()) {
            problems.add("rewards.items has no valid entry, using the built-in table");
            rewardItems.addAll(DEFAULT_REWARDS);
        }
    }

    /** The built-in Crypt Forge (mirrored in the seed, a test keeps them equal). */
    public static final List<ForgeTrade> DEFAULT_TRADES = List.of(
        new ForgeTrade("spirit_leaf", "Spirit Leaf", "FERN", costOf("dungeon_bone_fragment", 16, "dungeon_crypt_shard", 4),
            "dungeon_spirit_leaf", 1, 0, Grade.D, 0),
        new ForgeTrade("healing_flasks", "Healing Flasks", "GLASS_BOTTLE", costOf("dungeon_bone_fragment", 12),
            "dungeon_healing_flask", 3, 0, Grade.D, 0),
        new ForgeTrade("crypt_cache", "Crypt Cache", "ENDER_CHEST",
            costOf("dungeon_crypt_shard", 12, "dungeon_wither_essence", 3), "", 0, 4, Grade.B, 0),
        new ForgeTrade("spirit_cache", "Spirit Cache", "SHULKER_BOX",
            costOf("dungeon_wither_essence", 12, "dungeon_spirit_dust", 4), "", 0, 5, Grade.S, 0),
        new ForgeTrade("dark_scroll", "Dark Scroll", "PAPER", costOf("dungeon_relic", 5, "dungeon_spirit_dust", 16),
            "dungeon_dark_scroll", 1, 0, Grade.D, 0),
        new ForgeTrade("sell_bones", "Bone Market", "BONE", costOf("dungeon_bone_fragment", 64), "", 0, 0, Grade.D, 600),
        new ForgeTrade("sell_essence", "Essence Broker", "BLACK_DYE", costOf("dungeon_wither_essence", 10), "", 0, 0,
            Grade.D, 1500));

    private static Map<String, Integer> costOf(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    @SuppressWarnings("unchecked")
    private void readForge(List<Object[]> rows) {
        if (rows.isEmpty()) {
            forgeTrades.addAll(DEFAULT_TRADES);
            return;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Object[] m : rows) {
            String id = String.valueOf(m[0]).trim().toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_]+") || !seen.add(id)) {
                problems.add("forge.trades entry '" + id + "' has a missing, bad or duplicate id, skipped");
                continue;
            }
            Map<String, Integer> cost = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : ((Map<String, Integer>) m[8]).entrySet()) {
                int amount = e.getValue();
                if (amount >= 1 && amount <= 100_000 && !e.getKey().isBlank()) {
                    cost.merge(e.getKey(), amount, Integer::sum);
                }
            }
            String give = m[3] == null ? "" : String.valueOf(m[3]).trim();
            int amount = Math.max(1, Math.min(2304, (Integer) m[4]));
            int rolls = Math.max(0, Math.min(20, (Integer) m[5]));
            double coins = Math.max(0, Math.min(1e9, (Double) m[7]));
            int outcomes = (give.isEmpty() ? 0 : 1) + (rolls > 0 ? 1 : 0) + (coins > 0 ? 1 : 0);
            if (cost.isEmpty() || outcomes != 1) {
                problems.add("forge.trades entry " + id + " needs a cost and exactly one of give, cache-rolls, coins, skipped");
                continue;
            }
            String name = m[1] == null ? id : String.valueOf(m[1]);
            String icon = m[2] == null ? "CHEST" : String.valueOf(m[2]).toUpperCase(Locale.ROOT);
            Grade grade = rolls > 0 ? Grade.parse(String.valueOf(m[6])).orElse(Grade.B) : Grade.D;
            forgeTrades.add(new ForgeTrade(id, name, icon, java.util.Collections.unmodifiableMap(cost), give, give.isEmpty() ? 0 : amount,
                rolls, grade, coins));
        }
    }

    /** Built-in floors, identical to the seeded floors. */
    private static final Object[][] FLOOR_TABLE = {
        // name, palette, grid, rooms, puzzles, traps, secretRooms, miniBosses, shop, fairy, witherDoors, loops, hp, dmg, level, par, secrets%, reward, minCombat
        // hp is a multiplier on the tier's curve health (see dungeon.run.DungeonHp), not the monster health itself
        {"The Entrance", "stone_brick", 3, 8, 1, 0, 1, 0, false, true, 0, 0, 1.0, 1.0, 3, 420, 40, 1.0, 0},
        {"The Ossuary", "mossy", 4, 13, 2, 1, 1, 1, true, true, 1, 1, 1.3, 1.4, 8, 600, 45, 1.5, 5},
        {"The Drowned Vault", "deepslate", 4, 15, 2, 1, 2, 1, true, true, 1, 1, 1.0, 1.9, 14, 720, 50, 2.2, 10},
        {"The Black Chapel", "blackstone", 5, 18, 3, 2, 2, 2, true, true, 2, 2, 1.3, 2.6, 21, 900, 55, 3.2, 15},
        {"The Ember Crypt", "nether", 5, 21, 3, 3, 3, 2, true, true, 2, 2, 1.0, 3.5, 29, 1080, 60, 4.6, 20},
        {"The Crimson Spire", "crimson", 6, 26, 4, 3, 3, 3, true, true, 3, 3, 1.0, 4.6, 38, 1320, 65, 6.5, 25},
        {"The Void Sanctum", "end", 7, 34, 5, 4, 4, 3, true, true, 3, 4, 1.0, 6.0, 50, 1680, 70, 9.0, 30}};

    /** Weakness and resistance of the built-in floors. */
    private static final String[][] FLOOR_ELEMENTS = {{"EARTH", "VOID"}, {"EARTH", "ASTRAL"}, {"FROST", "EMBER"}, {"FROST", "VOID"},
        {"EMBER", "EARTH"}, {"VOID", "FROST"}, {"ASTRAL", "EARTH"}};

    /** Built-in floor used when a floor is missing from the file. */
    public static Floor defaultFloor(int n) {
        Object[] r = FLOOR_TABLE[Math.max(1, Math.min(7, n)) - 1];
        double extra = Math.max(0, n - 7);
        return new Floor(n, n <= 7 ? (String) r[0] : "Floor " + n, (String) r[1], (int) r[2], (int) r[3], (int) r[4],
            (int) r[5], (int) r[6], (int) r[7], (boolean) r[8], (boolean) r[9], (int) r[10], (int) r[11],
            (double) r[12] * (1 + extra * 0.4), (double) r[13] * (1 + extra * 0.3), (int) r[14] + (int) extra * 8,
            (int) r[15] + (int) extra * 240, (int) r[16], (double) r[17] * (1 + extra * 0.4), (int) r[18] + (int) extra * 5, Math.min(5, new int[] {1, 1, 2, 2, 3, 4, 5}[Math.max(1, Math.min(7, n)) - 1] + (n > 7 ? 1 : 0)),
            n <= 7 ? FLOOR_ELEMENTS[n - 1][0] : "ASTRAL", n <= 7 ? FLOOR_ELEMENTS[n - 1][1] : "EARTH");
    }

    private void readFloors(List<Object[]> rows) {
        for (Object[] r : rows) {
            int n = (Integer) r[0];
            if (n < 1 || n > 20) {
                problems.add("floor " + n + " is outside 1..20, ignored");
                continue;
            }
            floors.put(n, readFloor(n, r));
        }
        if (floors.isEmpty()) {
            for (int n = 1; n <= 7; n++) {
                floors.put(n, validate(defaultFloor(n), false));
            }
            return;
        }
        // floors must be consecutive from 1 so "previous floor" makes sense
        int expect = 1;
        Map<Integer, Floor> fixed = new LinkedHashMap<>();
        for (Floor f : floors.values()) {
            if (f.number() != expect) {
                problems.add("floors are not consecutive, floor " + f.number() + " renumbered to " + expect);
                f = new Floor(expect, f.name(), f.palette(), f.grid(), f.rooms(), f.puzzles(), f.traps(), f.secretRooms(),
                    f.miniBosses(), f.shop(), f.fairy(), f.witherDoors(), f.loops(), f.hp(), f.damage(), f.mobLevel(),
                    f.parSeconds(), f.secretsNeededPercent(), f.reward(), f.minCombatLevel(), f.tier(), f.weakness(), f.resistance());
            }
            fixed.put(expect, f);
            expect++;
        }
        floors.clear();
        floors.putAll(fixed);
    }

    /** One {@code dungeon_floor} row (columns as selected by {@link #load}), corrected to the valid ranges. */
    private Floor readFloor(int n, Object[] f) {
        Floor d = defaultFloor(n);
        String prefix = "floors." + n + ".";
        String palette = String.valueOf(f[2]).toLowerCase(Locale.ROOT);
        if (!PALETTES.contains(palette)) {
            problems.add(prefix + "palette '" + palette + "' is unknown, using " + d.palette());
            palette = d.palette();
        }
        Floor floor = new Floor(n, String.valueOf(f[1]), palette,
            clampI(prefix + "grid", (Integer) f[3], 3, 7), clampI(prefix + "rooms", (Integer) f[4], 4, 49),
            clampI(prefix + "puzzles", (Integer) f[5], 0, 12), clampI(prefix + "traps", (Integer) f[6], 0, 12),
            clampI(prefix + "secret-rooms", (Integer) f[7], 0, 12), clampI(prefix + "mini-bosses", (Integer) f[8], 0, 8),
            (Integer) f[9] == 1, (Integer) f[10] == 1, clampI(prefix + "wither-doors", (Integer) f[11], 0, 8),
            clampI(prefix + "loops", (Integer) f[12], 0, 12), clampD(prefix + "hp", ((Number) f[13]).doubleValue(), 0.1, 1000),
            clampD(prefix + "damage", ((Number) f[14]).doubleValue(), 0.1, 1000), clampI(prefix + "mob-level", (Integer) f[15], 1, 999),
            clampI(prefix + "par-seconds", (Integer) f[16], 60, 36000),
            clampI(prefix + "secrets-needed-percent", (Integer) f[17], 0, 100),
            clampD(prefix + "reward", ((Number) f[18]).doubleValue(), 0.0, 1000),
            clampI(prefix + "min-combat-level", (Integer) f[19], 0, 100),
            clampI(prefix + "tier", f[20] == null ? d.tier() : (Integer) f[20], 1, 5),
            f[21] == null ? null : String.valueOf(f[21]), f[22] == null ? null : String.valueOf(f[22]));
        return validate(floor, true);
    }

    /** Clamps the room counts to what the grid can hold. */
    private Floor validate(Floor f, boolean report) {
        int cells = f.grid() * f.grid();
        int minRooms = f.grid() + 2; // a straight path from the bottom row to the top row plus blood and boss
        int rooms = Math.max(minRooms, Math.min(cells, f.rooms()));
        if (report && rooms != f.rooms()) {
            problems.add("floors." + f.number() + ".rooms=" + f.rooms() + " does not fit a " + f.grid() + "x" + f.grid()
                + " grid (" + minRooms + ".." + cells + "), using " + rooms);
        }
        // entrance, blood and boss are fixed; the remaining rooms hold everything else
        int free = rooms - 3;
        int specials = f.puzzles() + f.traps() + f.secretRooms() + f.miniBosses() + (f.shop() ? 1 : 0) + (f.fairy() ? 1 : 0);
        if (report && specials > free) {
            problems.add("floors." + f.number() + " asks for " + specials + " special rooms but only " + free
                + " fit, the extras become mob rooms");
        }
        return new Floor(f.number(), f.name(), f.palette(), f.grid(), rooms, f.puzzles(), f.traps(), f.secretRooms(),
            f.miniBosses(), f.shop(), f.fairy(), f.witherDoors(), f.loops(), f.hp(), f.damage(), f.mobLevel(),
            f.parSeconds(), f.secretsNeededPercent(), f.reward(), f.minCombatLevel(), f.tier(), f.weakness(), f.resistance());
    }
}
