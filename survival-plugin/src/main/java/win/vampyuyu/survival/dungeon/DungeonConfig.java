package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Parsed and validated {@code dungeon.yml}. Never throws on bad values: they are clamped to a sane range and each
 * correction is recorded in {@link #problems()} so the feature can log them once.
 */
public final class DungeonConfig {
    /** One floor of the dungeon. */
    public record Floor(int number, String name, String palette, int grid, int rooms, int puzzles, int traps,
            int secretRooms, int miniBosses, boolean shop, boolean fairy, int witherDoors, int loops, double hp,
            double damage, int mobLevel, int parSeconds, int secretsNeededPercent, double reward,
            int minCombatLevel) {}

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
    public int watcherGuardsBase;
    public int watcherGuardsPerFloor;

    public double speedDecaySeconds;
    public double deathPenalty;
    public final Map<Grade, Integer> thresholds = new EnumMap<>(Grade.class);

    public final Map<Grade, Tier> tiers = new EnumMap<>(Grade.class);
    public final List<RewardEntry> rewardItems = new ArrayList<>();
    public final Map<Integer, Floor> floors = new LinkedHashMap<>();

    private DungeonConfig() {}

    /** Problems found while parsing (already corrected). */
    public List<String> problems() { return List.copyOf(problems); }

    public int floorCount() { return floors.size(); }

    public Floor floor(int number) { return floors.get(number); }

    // ------------------------------------------------------------------ parsing

    /** Parses {@code root}; a null or empty section yields the built-in defaults. */
    public static DungeonConfig parse(ConfigurationSection root) {
        DungeonConfig c = new DungeonConfig();
        c.read(root);
        return c;
    }

    private int num(ConfigurationSection s, String path, int def, int min, int max) {
        int v = s == null ? def : s.getInt(path, def);
        if (v < min || v > max) {
            problems.add(path + "=" + v + " is outside " + min + ".." + max + ", using " + Math.max(min, Math.min(max, v)));
            v = Math.max(min, Math.min(max, v));
        }
        return v;
    }

    private double dbl(ConfigurationSection s, String path, double def, double min, double max) {
        double v = s == null ? def : s.getDouble(path, def);
        if (Double.isNaN(v) || v < min || v > max) {
            double fixed = Double.isNaN(v) ? def : Math.max(min, Math.min(max, v));
            problems.add(path + "=" + v + " is outside " + min + ".." + max + ", using " + fixed);
            v = fixed;
        }
        return v;
    }

    private void read(ConfigurationSection root) {
        ConfigurationSection r = root;
        maxConcurrentRuns = num(r, "max-concurrent-runs", 3, 1, 32);
        closeDelaySeconds = num(r, "close-delay-seconds", 45, 5, 3600);
        runTimeoutMinutes = num(r, "run-timeout-minutes", 75, 5, 600);
        bossTimeoutMinutes = num(r, "boss-timeout-minutes", 12, 1, 120);
        readyCountdownSeconds = num(r, "ready-countdown-seconds", 8, 0, 120);
        sidebar = r == null || r.getBoolean("sidebar", true);
        darknessEffect = r != null && r.getBoolean("darkness-effect", false);
        requirePreviousFloor = r == null || r.getBoolean("require-previous-floor", true);

        ConfigurationSection p = r == null ? null : r.getConfigurationSection("party");
        partyMaxSize = num(p, "max-size", 5, 1, 5);
        inviteExpireSeconds = num(p, "invite-expire-seconds", 60, 5, 3600);
        readyCheckSeconds = num(p, "ready-check-seconds", 30, 5, 600);
        warpCooldownSeconds = num(p, "warp-cooldown-seconds", 15, 0, 3600);
        membersCanInvite = p != null && p.getBoolean("members-can-invite", false);

        ConfigurationSection g = r == null ? null : r.getConfigurationSection("generation");
        maxMsPerTick = num(g, "max-ms-per-tick", 6, 1, 40);
        baseY = num(g, "base-y", 64, -32, 200);
        blocksPerCheck = num(g, "blocks-per-check", 256, 16, 8192);

        ConfigurationSection e = r == null ? null : r.getConfigurationSection("entrance");
        entranceEnabled = e == null || e.getBoolean("enabled", true);
        entranceWorld = e == null ? "sb_hub" : e.getString("world", "sb_hub");
        entranceX = e == null ? 0.5 : e.getDouble("x", 0.5);
        entranceY = e == null ? 80.0 : e.getDouble("y", 80.0);
        entranceZ = e == null ? 24.5 : e.getDouble("z", 24.5);
        entranceRadius = dbl(e, "radius", 3.0, 1.0, 32.0);
        entranceHintCooldownSeconds = num(e, "hint-cooldown-seconds", 90, 5, 86400);

        ConfigurationSection rules = r == null ? null : r.getConfigurationSection("rules");
        deathsAllowed = num(rules, "deaths-allowed", 3, 0, 20);
        ghostSeconds = num(rules, "ghost-seconds", 40, 5, 600);
        reviveSeconds = num(rules, "revive-seconds", 3, 1, 60);
        reviveHealthPercent = num(rules, "revive-health-percent", 50, 10, 100);
        voidDamage = dbl(rules, "void-damage", 6.0, 0.0, 100.0);
        List<String> cmds = new ArrayList<>();
        List<String> configured = rules == null ? List.of() : rules.getStringList("allowed-commands");
        if (rules == null || !rules.isSet("allowed-commands")) {
            configured = List.of("party", "pc", "dungeon", "dg", "msg", "tell", "w", "r", "help", "menu", "leave");
        }
        for (String s : configured) {
            if (s != null && !s.isBlank()) {
                cmds.add(s.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
            }
        }
        allowedCommands = List.copyOf(cmds);

        ConfigurationSection mobs = r == null ? null : r.getConfigurationSection("mobs");
        maxMobsPerRoom = num(mobs, "max-per-room", 14, 1, 60);
        maxMobsPerRun = num(mobs, "max-per-run", 70, 1, 400);
        if (maxMobsPerRun < maxMobsPerRoom) {
            problems.add("mobs.max-per-run is below max-per-room, raised to " + maxMobsPerRoom);
            maxMobsPerRun = maxMobsPerRoom;
        }
        partyHealthScale = dbl(mobs, "party-health-scale", 0.30, 0.0, 3.0);
        partyDamageScale = dbl(mobs, "party-damage-scale", 0.10, 0.0, 3.0);
        watcherGuardsBase = num(mobs, "watcher-guards-base", 3, 0, 30);
        watcherGuardsPerFloor = num(mobs, "watcher-guards-per-floor", 1, 0, 10);

        ConfigurationSection score = r == null ? null : r.getConfigurationSection("scoring");
        speedDecaySeconds = dbl(score, "speed-decay-seconds", 6.0, 0.5, 600.0);
        deathPenalty = dbl(score, "death-penalty", 2.0, 0.0, 50.0);
        readThresholds(score == null ? null : score.getConfigurationSection("thresholds"));

        ConfigurationSection rewards = r == null ? null : r.getConfigurationSection("rewards");
        readTiers(rewards == null ? null : rewards.getConfigurationSection("tiers"));
        readRewardItems(rewards);
        readFloors(r == null ? null : r.getConfigurationSection("floors"));
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

    private void readThresholds(ConfigurationSection s) {
        int previous = Integer.MAX_VALUE;
        Grade[] grades = Grade.values();
        for (int i = grades.length - 1; i >= 0; i--) {
            Grade grade = grades[i];
            int v = s == null ? defaultThreshold(grade) : s.getInt(grade.label(), defaultThreshold(grade));
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

    private void readTiers(ConfigurationSection s) {
        for (Grade grade : Grade.values()) {
            Tier d = defaultTier(grade);
            ConfigurationSection t = s == null ? null : s.getConfigurationSection(grade.label());
            if (t == null) {
                tiers.put(grade, d);
                continue;
            }
            tiers.put(grade, new Tier(dbl(t, "coins", d.coins(), 0, 1e9), dbl(t, "xp", d.xp(), 0, 1e9),
                num(t, "rolls", d.rolls(), 0, 20), t.getString("chest", d.chest())));
        }
    }

    /** Built-in reward table, identical to the one shipped in dungeon.yml (a test keeps them in step). */
    public static final List<RewardEntry> DEFAULT_REWARDS = List.of(
        new RewardEntry("dungeon_bone_fragment", 60, 3, 8, 1, Grade.D),
        new RewardEntry("dungeon_crypt_shard", 40, 1, 4, 1, Grade.C),
        new RewardEntry("mc:IRON_INGOT", 30, 4, 12, 1, Grade.D),
        new RewardEntry("mc:GOLD_INGOT", 20, 3, 9, 2, Grade.C),
        new RewardEntry("dungeon_wither_essence", 30, 2, 6, 2, Grade.B),
        new RewardEntry("mc:DIAMOND", 12, 1, 3, 3, Grade.B),
        new RewardEntry("dungeon_spirit_dust", 12, 1, 3, 4, Grade.A),
        new RewardEntry("mc:NETHERITE_SCRAP", 6, 1, 2, 5, Grade.A),
        new RewardEntry("dungeon_relic", 3, 1, 1, 5, Grade.S),
        new RewardEntry("dungeon_dark_scroll", 1, 1, 1, 7, Grade.S_PLUS));

    private void readRewardItems(ConfigurationSection rewards) {
        if (rewards == null || !rewards.isList("items")) {
            rewardItems.addAll(DEFAULT_REWARDS);
            return;
        }
        List<Map<?, ?>> list = rewards.getMapList("items");
        for (Map<?, ?> m : list) {
            Object item = m.get("item");
            if (item == null || String.valueOf(item).isBlank()) {
                problems.add("a rewards.items entry has no item, skipped");
                continue;
            }
            int weight = intOf(m.get("weight"), 1);
            int min = intOf(m.get("min"), 1);
            int max = intOf(m.get("max"), min);
            if (weight < 1 || min < 1 || max < min) {
                problems.add("rewards.items entry " + item + " has bad weight/min/max, skipped");
                continue;
            }
            Grade grade = Grade.parse(String.valueOf(m.get("min-grade"))).orElse(Grade.D);
            rewardItems.add(new RewardEntry(String.valueOf(item), weight, min, max, Math.max(1, intOf(m.get("min-floor"), 1)), grade));
        }
        if (rewardItems.isEmpty()) {
            problems.add("rewards.items has no valid entry, using the built-in table");
            rewardItems.addAll(DEFAULT_REWARDS);
        }
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    /** Built-in floors, identical to dungeon.yml. */
    private static final Object[][] FLOOR_TABLE = {
        // name, palette, grid, rooms, puzzles, traps, secretRooms, miniBosses, shop, fairy, witherDoors, loops, hp, dmg, level, par, secrets%, reward, minCombat
        {"The Entrance", "stone_brick", 3, 8, 1, 0, 1, 0, false, true, 0, 0, 1.0, 1.0, 3, 420, 40, 1.0, 0},
        {"The Ossuary", "mossy", 4, 13, 2, 1, 1, 1, true, true, 1, 1, 1.6, 1.4, 8, 600, 45, 1.5, 5},
        {"The Drowned Vault", "deepslate", 4, 15, 2, 1, 2, 1, true, true, 1, 1, 2.4, 1.9, 14, 720, 50, 2.2, 10},
        {"The Black Chapel", "blackstone", 5, 18, 3, 2, 2, 2, true, true, 2, 2, 3.6, 2.6, 21, 900, 55, 3.2, 15},
        {"The Ember Crypt", "nether", 5, 21, 3, 3, 3, 2, true, true, 2, 2, 5.2, 3.5, 29, 1080, 60, 4.6, 20},
        {"The Crimson Spire", "crimson", 6, 26, 4, 3, 3, 3, true, true, 3, 3, 7.5, 4.6, 38, 1320, 65, 6.5, 25},
        {"The Void Sanctum", "end", 7, 34, 5, 4, 4, 3, true, true, 3, 4, 11.0, 6.0, 50, 1680, 70, 9.0, 30}};

    /** Built-in floor used when a floor is missing from the file. */
    public static Floor defaultFloor(int n) {
        Object[] r = FLOOR_TABLE[Math.max(1, Math.min(7, n)) - 1];
        double extra = Math.max(0, n - 7);
        return new Floor(n, n <= 7 ? (String) r[0] : "Floor " + n, (String) r[1], (int) r[2], (int) r[3], (int) r[4],
            (int) r[5], (int) r[6], (int) r[7], (boolean) r[8], (boolean) r[9], (int) r[10], (int) r[11],
            (double) r[12] * (1 + extra * 0.4), (double) r[13] * (1 + extra * 0.3), (int) r[14] + (int) extra * 8,
            (int) r[15] + (int) extra * 240, (int) r[16], (double) r[17] * (1 + extra * 0.4), (int) r[18] + (int) extra * 5);
    }

    private void readFloors(ConfigurationSection s) {
        if (s != null) {
            List<Integer> numbers = new ArrayList<>();
            for (String key : s.getKeys(false)) {
                try {
                    int n = Integer.parseInt(key.trim());
                    if (n >= 1 && n <= 20) {
                        numbers.add(n);
                    } else {
                        problems.add("floor " + key + " is outside 1..20, ignored");
                    }
                } catch (NumberFormatException ex) {
                    problems.add("floor key '" + key + "' is not a number, ignored");
                }
            }
            numbers.sort(Integer::compare);
            for (int n : numbers) {
                floors.put(n, readFloor(n, s.getConfigurationSection(Integer.toString(n))));
            }
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
                    f.parSeconds(), f.secretsNeededPercent(), f.reward(), f.minCombatLevel());
            }
            fixed.put(expect, f);
            expect++;
        }
        floors.clear();
        floors.putAll(fixed);
    }

    private Floor readFloor(int n, ConfigurationSection f) {
        Floor d = defaultFloor(n);
        if (f == null) {
            problems.add("floor " + n + " is not a section, using defaults");
            return validate(d, true);
        }
        String prefix = "floors." + n + ".";
        String palette = f.getString("palette", d.palette()).toLowerCase(Locale.ROOT);
        if (!PALETTES.contains(palette)) {
            problems.add(prefix + "palette '" + palette + "' is unknown, using " + d.palette());
            palette = d.palette();
        }
        Floor floor = new Floor(n, f.getString("name", d.name()), palette,
            num(f, "grid", d.grid(), 3, 7), num(f, "rooms", d.rooms(), 4, 49), num(f, "puzzles", d.puzzles(), 0, 12),
            num(f, "traps", d.traps(), 0, 12), num(f, "secret-rooms", d.secretRooms(), 0, 12),
            num(f, "mini-bosses", d.miniBosses(), 0, 8), f.getBoolean("shop", d.shop()), f.getBoolean("fairy", d.fairy()),
            num(f, "wither-doors", d.witherDoors(), 0, 8), num(f, "loops", d.loops(), 0, 12),
            dbl(f, "hp", d.hp(), 0.1, 1000), dbl(f, "damage", d.damage(), 0.1, 1000),
            num(f, "mob-level", d.mobLevel(), 1, 999), num(f, "par-seconds", d.parSeconds(), 60, 36000),
            num(f, "secrets-needed-percent", d.secretsNeededPercent(), 0, 100), dbl(f, "reward", d.reward(), 0.0, 1000),
            num(f, "min-combat-level", d.minCombatLevel(), 0, 100));
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
            f.parSeconds(), f.secretsNeededPercent(), f.reward(), f.minCombatLevel());
    }
}
