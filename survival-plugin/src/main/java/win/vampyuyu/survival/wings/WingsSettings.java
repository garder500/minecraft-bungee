package win.vampyuyu.survival.wings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Immutable view of {@code wings.yml}. Built by {@link #from}; pure formulas live here so they can be unit tested. */
public final class WingsSettings {
    /** Why the Wings stopped working mid-air. */
    public enum Reason { ENERGY, UNEQUIP, WORLD, COMBAT, ZONE, DEATH }

    /** One configured no-fly box. */
    public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(String worldName, double x, double y, double z) {
            return world.equals(worldName) && x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
        }
    }

    /** One level of the trail table. */
    public record Trail(Particle particle, int count, double spread) {}

    /** Cost of one upgrade step. */
    public record Upgrade(int coreTier, int amount, double coins) {}

    public final Set<String> allowedWorlds;
    public final List<String> deniedPrefixes;
    public final String noFlyTag;
    public final List<Region> regions;
    public final int maxLevel;
    public final double[] capacityBonus;
    public final double[] regenMultiplier;
    public final double[] drainMultiplier;
    public final float[] flySpeed;
    public final Trail[] trail;
    public final double drainFly;
    public final double drainSprint;
    public final double drainHover;
    public final double islandDrainFactor;
    public final double regenGround;
    public final double regenIslandMultiplier;
    public final int regenDelayTicks;
    public final double resumeFraction;
    public final double lowFraction;
    public final int barHideSeconds;
    public final int combatSeconds;
    public final boolean combatFromPlayers;
    public final boolean combatFromBosses;
    public final boolean combatFromMobs;
    public final boolean combatTagAttackers;
    public final String bossTag;
    public final double bossMinHealth;
    public final Set<Reason> emergencyReasons;
    public final int emergencyMaxSeconds;
    public final boolean sounds;
    public final int beatIntervalTicks;
    public final boolean trailEnabled;
    public final boolean prefTrail;
    public final boolean prefBar;
    public final boolean prefDoubleJump;
    public final boolean prefSounds;
    public final Map<Integer, Upgrade> upgrades;
    public final double claimCoins;
    public final long claimCooldownMillis;
    public final List<String> coreNames;
    public final int noFlyBossRadius;
    public final double drainGlide;
    public final double fireworkCost;
    public final double diveRegen;
    public final boolean voidRescue;
    public final boolean dashEnabled;
    public final int dashLevel;
    public final double dashCost;
    public final long dashCooldownMillis;
    public final double dashPower;
    public final int diveLevel;
    public final int landingLevel;
    public final long landingCooldownMillis;
    /** Extra stats granted by the Wings through the stat source "wings", one value per level. */
    public final Map<Stat, double[]> statBonus;

    private WingsSettings(ConfigurationSection c) {
        allowedWorlds = Collections.unmodifiableSet(new HashSet<>(
            c.getStringList("worlds.allowed").isEmpty() ? List.of(SkyblockWorlds.HUB, SkyblockWorlds.WORLD, SkyblockWorlds.NETHER, SkyblockWorlds.END, "world")
                : c.getStringList("worlds.allowed")));
        List<String> denied = c.contains("worlds.denied-prefixes") ? c.getStringList("worlds.denied-prefixes")
            : List.of(SkyblockWorlds.DUNGEON_PREFIX);
        deniedPrefixes = List.copyOf(denied);
        noFlyTag = c.getString("no-fly.tag", "sb_no_fly");
        regions = parseRegions(c.getMapList("no-fly.regions"));
        maxLevel = Math.max(1, Math.min(50, c.getInt("max-level", 10)));
        capacityBonus = table(c, "levels.capacity-bonus", maxLevel, 40, 80, 120, 160, 200, 250, 300, 360, 430, 500);
        regenMultiplier = table(c, "levels.regen-multiplier", maxLevel, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.65, 1.8, 1.95, 2.2);
        drainMultiplier = table(c, "levels.drain-multiplier", maxLevel, 1.0, 0.95, 0.9, 0.85, 0.8, 0.75, 0.7, 0.65, 0.6, 0.55);
        double[] speeds = table(c, "levels.fly-speed", maxLevel, 0.10, 0.11, 0.12, 0.13, 0.14, 0.15, 0.16, 0.18, 0.20, 0.22);
        flySpeed = new float[maxLevel];
        for (int i = 0; i < maxLevel; i++) {
            flySpeed[i] = (float) Math.max(0.02, Math.min(1.0, speeds[i]));
        }
        trail = parseTrail(c.getStringList("levels.trail"), maxLevel);
        drainFly = Math.max(0, c.getDouble("energy.drain-fly", 4.0));
        drainSprint = Math.max(0, c.getDouble("energy.drain-sprint", 10.0));
        drainHover = Math.max(0, c.getDouble("energy.drain-hover", 2.0));
        islandDrainFactor = clamp(c.getDouble("energy.island-drain-factor", 0.5), 0, 1);
        regenGround = Math.max(0, c.getDouble("energy.regen-ground", 10.0));
        regenIslandMultiplier = Math.max(1, c.getDouble("energy.regen-island-multiplier", 2.0));
        regenDelayTicks = Math.max(0, c.getInt("energy.regen-delay-ticks", 20));
        resumeFraction = clamp(c.getDouble("energy.resume-fraction", 0.15), 0, 1);
        lowFraction = clamp(c.getDouble("energy.low-fraction", 0.2), 0, 1);
        barHideSeconds = Math.max(0, c.getInt("energy.bar-hide-seconds", 4));
        combatSeconds = Math.max(0, c.getInt("combat.seconds", 8));
        combatFromPlayers = c.getBoolean("combat.from-players", true);
        combatFromBosses = c.getBoolean("combat.from-bosses", true);
        combatFromMobs = c.getBoolean("combat.from-mobs", false);
        combatTagAttackers = c.getBoolean("combat.tag-attackers", true);
        bossTag = c.getString("combat.boss-tag", "sb_boss");
        bossMinHealth = c.getDouble("combat.boss-min-health", 300);
        Set<Reason> reasons = new HashSet<>();
        List<String> raw = c.contains("emergency.reasons") ? c.getStringList("emergency.reasons")
            : List.of("ENERGY", "UNEQUIP", "WORLD", "COMBAT", "ZONE");
        for (String name : raw) {
            try {
                reasons.add(Reason.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // unknown reason names are skipped
            }
        }
        emergencyReasons = Collections.unmodifiableSet(reasons);
        emergencyMaxSeconds = Math.max(5, c.getInt("emergency.max-seconds", 120));
        sounds = c.getBoolean("effects.sounds", true);
        beatIntervalTicks = Math.max(4, c.getInt("effects.beat-interval-ticks", 14));
        trailEnabled = c.getBoolean("effects.trail", true);
        prefTrail = c.getBoolean("preferences.trail", true);
        prefBar = c.getBoolean("preferences.bar", true);
        prefDoubleJump = c.getBoolean("preferences.double-jump", true);
        prefSounds = c.getBoolean("preferences.sounds", true);
        upgrades = parseUpgrades(c.getConfigurationSection("upgrades"), maxLevel);
        claimCoins = Math.max(0, c.getDouble("claim.coins", 25000));
        claimCooldownMillis = (long) (Math.max(0, c.getDouble("claim.cooldown-hours", 24)) * 3_600_000L);
        List<String> names = c.getStringList("cores.names");
        coreNames = names.size() >= 5 ? List.copyOf(names)
            : List.of("Feather Core", "Gilded Core", "Radiant Core", "Celestial Core", "Voidwyrm Core");
        noFlyBossRadius = Math.max(0, Math.min(128, c.getInt("no-fly.boss-radius", 40)));
        drainGlide = Math.max(0, c.getDouble("energy.drain-glide", 1.0));
        fireworkCost = Math.max(0, c.getDouble("energy.firework-cost", 12.0));
        diveRegen = Math.max(0, c.getDouble("energy.dive-regen", 3.0));
        voidRescue = c.getBoolean("emergency.void-rescue", true);
        dashEnabled = c.getBoolean("abilities.dash.enabled", true);
        dashLevel = Math.max(1, c.getInt("abilities.dash.level", 4));
        dashCost = Math.max(0, c.getDouble("abilities.dash.cost", 25.0));
        dashCooldownMillis = (long) (Math.max(0, c.getDouble("abilities.dash.cooldown-seconds", 4.0)) * 1000L);
        dashPower = clamp(c.getDouble("abilities.dash.power", 1.6), 0.2, 4.0);
        diveLevel = Math.max(1, c.getInt("abilities.dive-recovery.level", 6));
        landingLevel = Math.max(1, c.getInt("abilities.landing-burst.level", 8));
        landingCooldownMillis = (long) (Math.max(0, c.getDouble("abilities.landing-burst.cooldown-seconds", 8.0)) * 1000L);
        statBonus = parseStatBonus(c.getConfigurationSection("levels.stats"), maxLevel);
    }

    /** Builds the settings from any section. Missing keys use the shipped defaults. */
    public static WingsSettings from(ConfigurationSection section) {
        return new WingsSettings(section == null ? new YamlConfiguration() : section);
    }

    // ---------------------------------------------------------------- formulas

    /** Clamps a level into 1..maxLevel. */
    public int clampLevel(int level) {
        return Math.max(1, Math.min(maxLevel, level));
    }

    /** Wings tier (1-5, picks the worn texture) of a level: two levels per tier. */
    public static int tierOf(int level) {
        return Math.max(1, Math.min(5, (Math.max(1, level) + 1) / 2));
    }

    /** Capacity added to the Flight Energy stat. */
    public double capacityBonus(int level) {
        return capacityBonus[clampLevel(level) - 1];
    }

    public double regenMultiplier(int level) {
        return regenMultiplier[clampLevel(level) - 1];
    }

    public double drainMultiplier(int level) {
        return drainMultiplier[clampLevel(level) - 1];
    }

    public float flySpeed(int level) {
        return flySpeed[clampLevel(level) - 1];
    }

    public Trail trail(int level) {
        return trail[clampLevel(level) - 1];
    }

    /** Energy units lost per second while flying. */
    public double drainPerSecond(int level, boolean sprinting, boolean hovering, boolean overIsland) {
        double base = sprinting ? drainSprint : (hovering ? drainHover : drainFly);
        double drain = base * drainMultiplier(level);
        return overIsland ? drain * islandDrainFactor : drain;
    }

    /** Extra stat values of a level (the stat source "wings"): only stats with a non zero value. */
    public Map<Stat, Double> statBonuses(int level) {
        Map<Stat, Double> out = new java.util.EnumMap<>(Stat.class);
        for (Map.Entry<Stat, double[]> e : statBonus.entrySet()) {
            double v = e.getValue()[clampLevel(level) - 1];
            if (v != 0) {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    /** Energy units gained per second while resting. */
    public double regenPerSecond(int level, boolean onIsland) {
        return regenGround * regenMultiplier(level) * (onIsland ? regenIslandMultiplier : 1.0);
    }

    /** Seconds of normal flight a full bar lasts (for lore and GUI). */
    public double flightSeconds(double capacity, int level) {
        double perSecond = drainPerSecond(level, false, false, false);
        return perSecond <= 0 ? Double.POSITIVE_INFINITY : capacity / perSecond;
    }

    /** The energy needed to take off again after running dry. */
    public double resumeThreshold(double capacity) {
        return Math.max(5, capacity * resumeFraction);
    }

    /** True when the world name allows flight: listed, and not under a denied prefix. */
    public boolean worldAllowed(String worldName) {
        if (worldName == null) {
            return false;
        }
        for (String prefix : deniedPrefixes) {
            if (worldName.startsWith(prefix)) {
                return false;
            }
        }
        return allowedWorlds.contains(worldName);
    }

    public boolean inRegion(String worldName, double x, double y, double z) {
        for (Region region : regions) {
            if (region.contains(worldName, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /** Step cost from {@code level} to level+1, or null at the top level. */
    public Upgrade upgradeFrom(int level) {
        return upgrades.get(level);
    }

    // ---------------------------------------------------------------- parsing

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double[] table(ConfigurationSection c, String path, int size, double... fallback) {
        List<Double> values = new ArrayList<>();
        for (Object o : c.getList(path, List.of())) {
            if (o instanceof Number n) {
                values.add(n.doubleValue());
            }
        }
        if (values.isEmpty()) {
            for (double d : fallback) {
                values.add(d);
            }
        }
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = values.get(Math.min(i, values.size() - 1));
        }
        return out;
    }

    private static final Map<String, double[]> DEFAULT_STAT_BONUS = Map.of(
        "DEFENSE", new double[] {2, 4, 6, 8, 10, 13, 16, 20, 25, 30},
        "HEALTH", new double[] {0, 0, 5, 10, 15, 20, 25, 30, 40, 50},
        "SPEED", new double[] {0, 0, 0, 2, 4, 6, 8, 10, 12, 15});

    private static Map<Stat, double[]> parseStatBonus(ConfigurationSection section, int maxLevel) {
        Map<Stat, double[]> out = new java.util.EnumMap<>(Stat.class);
        if (section == null) {
            DEFAULT_STAT_BONUS.forEach((name, values) -> out.put(Stat.valueOf(name), fit(values, maxLevel)));
            return Collections.unmodifiableMap(out);
        }
        for (String key : section.getKeys(false)) {
            Stat stat;
            try {
                stat = Stat.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                continue;  // unknown stat names are skipped
            }
            List<Double> values = new ArrayList<>();
            for (Object o : section.getList(key, List.of())) {
                if (o instanceof Number n) {
                    values.add(n.doubleValue());
                }
            }
            if (!values.isEmpty()) {
                out.put(stat, fit(values.stream().mapToDouble(Double::doubleValue).toArray(), maxLevel));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static double[] fit(double[] values, int size) {
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = values[Math.min(i, values.length - 1)];
        }
        return out;
    }

    private static List<Region> parseRegions(List<Map<?, ?>> raw) {
        List<Region> out = new ArrayList<>();
        for (Map<?, ?> map : raw) {
            Object world = map.get("world");
            int[] min = triple(map.get("min"));
            int[] max = triple(map.get("max"));
            if (world == null || min == null || max == null) {
                continue;
            }
            out.add(new Region(world.toString(), Math.min(min[0], max[0]), Math.min(min[1], max[1]),
                Math.min(min[2], max[2]), Math.max(min[0], max[0]), Math.max(min[1], max[1]),
                Math.max(min[2], max[2])));
        }
        return List.copyOf(out);
    }

    private static int[] triple(Object o) {
        if (o instanceof List<?> list && list.size() == 3 && list.stream().allMatch(v -> v instanceof Number)) {
            return new int[] {((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue(),
                ((Number) list.get(2)).intValue()};
        }
        return null;
    }

    private static final String[] DEFAULT_TRAIL = {"CLOUD:1", "CLOUD:2", "END_ROD:1", "END_ROD:2", "ELECTRIC_SPARK:2",
        "SNOWFLAKE:2", "WAX_ON:2", "SOUL_FIRE_FLAME:2", "TOTEM_OF_UNDYING:2", "FIREWORK:3"};

    static Trail[] parseTrail(List<String> raw, int levels) {
        List<String> lines = raw.isEmpty() ? List.of(DEFAULT_TRAIL) : raw;
        Trail[] out = new Trail[levels];
        for (int i = 0; i < levels; i++) {
            out[i] = parseTrailLine(lines.get(Math.min(i, lines.size() - 1)));
        }
        return out;
    }

    static Trail parseTrailLine(String line) {
        String[] parts = line.trim().split(":");
        Particle particle = Particle.CLOUD;
        try {
            Particle parsed = Particle.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            if (parsed.getDataType() == Void.class) {
                particle = parsed;
            }
        } catch (IllegalArgumentException ignored) {
            // unknown or data-carrying particle: keep the cloud
        }
        int count = 1;
        double spread = 0.15;
        try {
            if (parts.length > 1) {
                count = Math.max(0, Math.min(20, Integer.parseInt(parts[1].trim())));
            }
            if (parts.length > 2) {
                spread = Math.max(0, Math.min(2, Double.parseDouble(parts[2].trim())));
            }
        } catch (NumberFormatException ignored) {
            // keep the defaults
        }
        return new Trail(particle, count, spread);
    }

    /** Same as the upgrades block of wings.yml. */
    private static final Upgrade[] DEFAULT_UPGRADES = {
        new Upgrade(1, 1, 0), new Upgrade(1, 3, 5000), new Upgrade(2, 1, 15000), new Upgrade(2, 3, 40000),
        new Upgrade(3, 1, 100000), new Upgrade(3, 3, 250000), new Upgrade(4, 1, 600000), new Upgrade(4, 2, 1500000),
        new Upgrade(5, 1, 4000000)};

    private static Map<Integer, Upgrade> parseUpgrades(ConfigurationSection section, int maxLevel) {
        Map<Integer, Upgrade> out = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection row = section.getConfigurationSection(key);
                int level;
                try {
                    level = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (row == null || level < 1 || level >= maxLevel) {
                    continue;
                }
                out.put(level, new Upgrade(Math.max(1, Math.min(5, row.getInt("core", tierOf(level)))),
                    Math.max(1, row.getInt("amount", 1)), Math.max(0, row.getDouble("coins", 0))));
            }
        }
        for (int level = 1; level < maxLevel; level++) {
            out.putIfAbsent(level, level <= DEFAULT_UPGRADES.length ? DEFAULT_UPGRADES[level - 1]
                : new Upgrade(5, 1 + (level % 2), 0));
        }
        return Collections.unmodifiableMap(out);
    }
}
