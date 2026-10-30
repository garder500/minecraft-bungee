package win.vampyuyu.survival.boss.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.boss.BossDefinition;

/**
 * The {@code world-bosses:} section of boss.yml.
 *
 * @param enabled master switch
 * @param intervalMinutes minutes between two events
 * @param firstDelayMinutes delay before the very first event after a fresh install
 * @param minPlayers players online in Skyblock worlds needed to start an event
 * @param announceBeforeSeconds warnings before the spawn, seconds, descending
 * @param durationMinutes the boss leaves after this long
 * @param retryMinutes when too few players are online, check again after this long
 * @param locations where events happen
 * @param pool bosses to pick from (weighted)
 * @param leaderboardSize rows shown by /worldboss status and after the kill
 * @param historySize number of past events kept
 */
public record WorldBossConfig(boolean enabled, int intervalMinutes, int firstDelayMinutes, int minPlayers,
        List<Integer> announceBeforeSeconds, int durationMinutes, int retryMinutes, List<Spot> locations,
        List<Entry> pool, int leaderboardSize, int historySize) {

    /** A spawn spot. */
    public record Spot(String world, double x, double y, double z, String name) {}

    /** A boss of the pool. */
    public record Entry(String boss, double weight) {}

    public WorldBossConfig {
        announceBeforeSeconds = List.copyOf(announceBeforeSeconds);
        locations = List.copyOf(locations);
        pool = List.copyOf(pool);
    }

    public static WorldBossConfig disabled() {
        return new WorldBossConfig(false, 120, 30, 1, List.of(), 15, 5, List.of(), List.of(), 5, 10);
    }

    /** Parses and validates; pool entries must exist in {@code bosses}. */
    public static WorldBossConfig parse(@Nullable ConfigurationSection s, Map<String, BossDefinition> bosses,
            List<String> errors) {
        if (s == null) {
            return disabled();
        }
        int interval = s.getInt("interval-minutes", 120);
        int first = s.getInt("first-spawn-delay-minutes", 30);
        int minPlayers = s.getInt("min-players", 1);
        int duration = s.getInt("duration-minutes", 15);
        int retry = s.getInt("retry-minutes", 5);
        if (interval < 1 || first < 0 || minPlayers < 1 || duration < 1 || retry < 1) {
            errors.add("world-bosses: interval-minutes, duration-minutes, retry-minutes, min-players must be >= 1");
        }
        List<Integer> announce = new ArrayList<>();
        for (Object o : s.getList("announce-before-seconds", List.of(300, 60, 10))) {
            if (o instanceof Number n && n.intValue() > 0) {
                announce.add(n.intValue());
            }
        }
        announce.sort((a, b) -> b - a);
        List<Spot> spots = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("locations")) {
            Object world = m.get("world");
            if (world == null || !(m.get("x") instanceof Number x) || !(m.get("y") instanceof Number y)
                || !(m.get("z") instanceof Number z)) {
                errors.add("world-bosses.locations: each entry needs world, x, y, z");
                continue;
            }
            Object name = m.get("name");
            spots.add(new Spot(String.valueOf(world), x.doubleValue(), y.doubleValue(), z.doubleValue(),
                name == null ? String.valueOf(world) : String.valueOf(name)));
        }
        List<Entry> pool = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("pool")) {
            String id = String.valueOf(m.get("boss"));
            Object w = m.get("weight");
            double weight = w instanceof Number n ? n.doubleValue() : 1;
            if (!bosses.containsKey(id)) {
                errors.add("world-bosses.pool: unknown boss '" + id + "'");
            } else if (weight <= 0) {
                errors.add("world-bosses.pool: weight of " + id + " must be positive");
            } else {
                pool.add(new Entry(id, weight));
            }
        }
        boolean enabled = s.getBoolean("enabled", false);
        if (enabled && (spots.isEmpty() || pool.isEmpty())) {
            errors.add("world-bosses: enabled but no valid locations or pool; events are off");
            enabled = false;
        }
        return new WorldBossConfig(enabled, interval, first, minPlayers, announce, duration, retry, spots, pool,
            Math.max(1, s.getInt("leaderboard-size", 5)), Math.max(1, s.getInt("history-size", 10)));
    }
}
