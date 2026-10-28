package win.vampyuyu.survival.menu;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.menu.SoulLogic.SoulDef;
import win.vampyuyu.survival.menu.SoulLogic.StatStep;

/** Typed, immutable snapshot of {@code menu.yml}. Bad values fall back to defaults and are listed in {@link #problems}. */
public final class MenuConfig {
    public final JoinRouter.NoKey noKey;
    public final long joinMaxAgeMs;
    public final String createIslandCommand;
    public final boolean redisEnabled;
    public final String redisHost;
    public final int redisPort;
    public final String redisPasswordFile;
    public final int redisConnectTimeoutMs;
    public final int redisSocketTimeoutMs;
    public final long redisBackoffMs;
    public final String respawnRoute;

    public final boolean menuItemEnabled;
    public final int menuItemSlot;
    public final long menuClickCooldownMs;
    public final Map<String, List<String>> commands;

    public final int warpWarmupSeconds;
    public final int warpCooldownSeconds;
    public final ConfigurationSection netherWarp;
    public final ConfigurationSection endWarp;
    public final String bossHint;

    public final boolean sidebarEnabled;
    public final String sidebarTitle;
    public final String sidebarFooter;
    public final int sidebarUpdateTicks;
    public final Map<String, String> defaultZones;
    public final Zones zones;

    public final boolean damageEnabled;
    public final int damageLifetimeTicks;
    public final int damageMaxPerSecond;

    public final Map<String, String> sounds;

    public final boolean fairyEnabled;
    public final double fairyViewDistance;
    public final double fairyCollectRadius;
    public final int fairyEvery;
    public final double fairyCoinsPerMilestone;
    public final List<StatStep> fairyCycle;
    public final String fairyGlow;
    public final List<SoulDef> fairyFixed;
    public final List<SoulDef> fairyIsland;

    public final boolean questsEnabled;
    public final QuestCatalog catalog;
    public final DailyClock dailyClock;
    public final int dailyCount;
    public final double dailyBonusCoins;
    public final double dailyStreakCoins;
    public final int dailyStreakCap;
    public final double dailyBonusXp;
    public final List<String> ignoreCoinReasons;
    public final int placedMemory;

    private final List<String> problems = new ArrayList<>();

    public MenuConfig(ConfigurationSection c) {
        String noKeyText = c.getString("join.no-key", "stay").toLowerCase(Locale.ROOT);
        noKey = noKeyText.equals("hub") ? JoinRouter.NoKey.HUB : JoinRouter.NoKey.STAY;
        if (!noKeyText.equals("hub") && !noKeyText.equals("stay")) {
            problems.add("join.no-key must be stay or hub, got '" + noKeyText + "'; using stay");
        }
        joinMaxAgeMs = Math.max(1, c.getLong("join.max-age-seconds", 30)) * 1000;
        createIslandCommand = clean(c.getString("join.create-island-command", "island create"));
        redisEnabled = c.getBoolean("join.redis.enabled", true);
        redisHost = c.getString("join.redis.host", "");
        redisPort = clamp(c.getInt("join.redis.port", 6379), 1, 65535);
        redisPasswordFile = c.getString("join.redis.password-file", "");
        redisConnectTimeoutMs = clamp(c.getInt("join.redis.connect-timeout-ms", 1500), 100, 10000);
        redisSocketTimeoutMs = clamp(c.getInt("join.redis.socket-timeout-ms", 1500), 100, 10000);
        redisBackoffMs = Math.max(1, c.getLong("join.redis.backoff-seconds", 30)) * 1000;
        String route = c.getString("respawn.route", "island").toLowerCase(Locale.ROOT);
        respawnRoute = List.of("island", "hub", "none").contains(route) ? route : "island";

        menuItemEnabled = c.getBoolean("menu-item.enabled", true);
        menuItemSlot = clamp(c.getInt("menu-item.slot", 8), 0, 8);
        menuClickCooldownMs = Math.max(0, c.getLong("menu-item.click-cooldown-ms", 250));
        Map<String, List<String>> cmds = new LinkedHashMap<>();
        ConfigurationSection cs = c.getConfigurationSection("commands");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                cmds.put(key.toLowerCase(Locale.ROOT), cs.getStringList(key).stream().map(MenuConfig::clean).toList());
            }
        }
        commands = Map.copyOf(cmds);

        warpWarmupSeconds = clamp(c.getInt("warps.warmup-seconds", 3), 0, 30);
        warpCooldownSeconds = clamp(c.getInt("warps.cooldown-seconds", 5), 0, 600);
        netherWarp = c.getConfigurationSection("warps.nether");
        endWarp = c.getConfigurationSection("warps.end");
        bossHint = c.getString("warps.boss-hint", "");

        sidebarEnabled = c.getBoolean("sidebar.enabled", true);
        sidebarTitle = c.getString("sidebar.title", "<bold>SKYBLOCK");
        sidebarFooter = c.getString("sidebar.footer", "");
        sidebarUpdateTicks = clamp(c.getInt("sidebar.update-ticks", 20), 5, 200);
        Map<String, String> dz = new LinkedHashMap<>();
        ConfigurationSection dzs = c.getConfigurationSection("sidebar.default-zones");
        if (dzs != null) {
            for (String key : dzs.getKeys(false)) {
                dz.put(key.toLowerCase(Locale.ROOT), dzs.getString(key, key));
            }
        }
        defaultZones = Map.copyOf(dz);
        zones = Zones.parse(c.getMapList("sidebar.zones"), MenuConfig::worldName, problems);

        damageEnabled = c.getBoolean("damage-indicators.enabled", true);
        damageLifetimeTicks = clamp(c.getInt("damage-indicators.lifetime-ticks", 24), 5, 100);
        damageMaxPerSecond = clamp(c.getInt("damage-indicators.max-per-second", 12), 1, 100);

        Map<String, String> snd = new LinkedHashMap<>();
        ConfigurationSection ss = c.getConfigurationSection("sounds");
        if (ss != null) {
            for (String key : ss.getKeys(false)) {
                snd.put(key, ss.getString(key, ""));
            }
        }
        sounds = Map.copyOf(snd);

        fairyEnabled = c.getBoolean("fairy-souls.enabled", true);
        fairyViewDistance = Math.max(8, c.getDouble("fairy-souls.view-distance", 40));
        fairyCollectRadius = Math.max(0.5, c.getDouble("fairy-souls.collect-radius", 1.6));
        fairyEvery = Math.max(0, c.getInt("fairy-souls.every", 5));
        fairyCoinsPerMilestone = Math.max(0, c.getDouble("fairy-souls.coins-per-milestone", 250));
        fairyCycle = List.copyOf(SoulLogic.parseCycle(c.getMapList("fairy-souls.stat-cycle"), problems));
        fairyGlow = c.getString("fairy-souls.glow-color", "#ff7ad9");
        fairyFixed = List.copyOf(SoulLogic.parse(c.getMapList("fairy-souls.fixed"), false, problems));
        fairyIsland = List.copyOf(SoulLogic.parse(c.getMapList("fairy-souls.island"), true, problems));

        questsEnabled = c.getBoolean("quests.enabled", true);
        ConfigurationSection quests = c.getConfigurationSection("quests");
        catalog = questsEnabled ? QuestCatalog.parse(quests) : QuestCatalog.empty();
        problems.addAll(catalog.problems());
        DailyClock clock;
        try {
            clock = new DailyClock(DailyClock.parseZone(c.getString("quests.daily.time-zone", "UTC")),
                DailyClock.parseReset(c.getString("quests.daily.reset-time", "04:00")));
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage() + "; using 04:00 UTC");
            clock = new DailyClock(ZoneId.of("UTC"), LocalTime.of(4, 0));
        }
        dailyClock = clock;
        dailyCount = clamp(c.getInt("quests.daily.count", 3), 0, 9);
        dailyBonusCoins = Math.max(0, c.getDouble("quests.daily.bonus-coins", 150));
        dailyStreakCoins = Math.max(0, c.getDouble("quests.daily.streak-bonus-coins", 50));
        dailyStreakCap = Math.max(0, c.getInt("quests.daily.streak-cap", 10));
        dailyBonusXp = Math.max(0, c.getDouble("quests.daily.bonus-skill-xp", 40));
        ignoreCoinReasons = c.getStringList("quests.daily.ignore-coin-reasons").stream()
            .map(s -> s.toLowerCase(Locale.ROOT)).toList();
        placedMemory = clamp(c.getInt("quests.daily.placed-memory", 2048), 64, 65536);
    }

    private static String clean(String command) {
        String text = command == null ? "" : command.trim();
        return text.startsWith("/") ? text.substring(1) : text;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Config world token (hub/world/nether/end) to the real world name; other values are returned as written. */
    public static String worldName(String token) {
        return switch (token == null ? "" : token.toLowerCase(Locale.ROOT)) {
            case "hub" -> SkyblockWorlds.HUB;
            case "world" -> SkyblockWorlds.WORLD;
            case "nether" -> SkyblockWorlds.NETHER;
            case "end" -> SkyblockWorlds.END;
            default -> token == null ? "" : token;
        };
    }

    /** The world token of a world name, or the name itself (used for default zone names and VISIT_WORLD). */
    public static String worldToken(String worldName) {
        return switch (worldName) {
            case SkyblockWorlds.HUB -> "hub";
            case SkyblockWorlds.WORLD -> "world";
            case SkyblockWorlds.NETHER -> "nether";
            case SkyblockWorlds.END -> "end";
            default -> worldName;
        };
    }

    /** The configured warp location for a world, or its spawn when x/y/z are not set. */
    public static Location warpLocation(ConfigurationSection section, World world) {
        if (section != null && section.contains("x") && section.contains("y") && section.contains("z")) {
            return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw", 0), (float) section.getDouble("pitch", 0));
        }
        return world.getSpawnLocation();
    }

    /** Load problems, empty when the file is clean. */
    public List<String> problems() {
        return List.copyOf(problems);
    }
}
