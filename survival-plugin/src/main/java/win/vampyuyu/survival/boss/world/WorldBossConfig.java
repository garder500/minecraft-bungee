package win.vampyuyu.survival.boss.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.boss.BossDefinition;
import win.vampyuyu.survival.data.Settings;

/**
 * The {@code boss.world.*} settings and the world boss spot and pool tables.
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

    /**
     * Validates the {@code boss.world.*} settings and the spot and pool rows; pool entries must exist in {@code bosses}.
     * {@code announce} are the warning times as read (sorted here, non-positive ones dropped).
     */
    public static WorldBossConfig parse(Settings s, List<Integer> announce, List<Spot> spots, List<Entry> rawPool,
            Map<String, BossDefinition> bosses, List<String> errors) {
        int interval = s.getInt("world.interval-minutes", 120);
        int first = s.getInt("world.first-spawn-delay-minutes", 30);
        int minPlayers = s.getInt("world.min-players", 1);
        int duration = s.getInt("world.duration-minutes", 15);
        int retry = s.getInt("world.retry-minutes", 5);
        if (interval < 1 || first < 0 || minPlayers < 1 || duration < 1 || retry < 1) {
            errors.add("world-bosses: interval-minutes, duration-minutes, retry-minutes, min-players must be >= 1");
        }
        List<Integer> warnings = new ArrayList<>(announce.stream().filter(n -> n > 0).toList());
        warnings.sort((a, b) -> b - a);
        List<Entry> pool = new ArrayList<>();
        for (Entry e : rawPool) {
            if (!bosses.containsKey(e.boss())) {
                errors.add("world-bosses.pool: unknown boss '" + e.boss() + "'");
            } else if (e.weight() <= 0) {
                errors.add("world-bosses.pool: weight of " + e.boss() + " must be positive");
            } else {
                pool.add(e);
            }
        }
        boolean enabled = s.getBoolean("world.enabled", false);
        if (enabled && (spots.isEmpty() || pool.isEmpty())) {
            errors.add("world-bosses: enabled but no valid locations or pool; events are off");
            enabled = false;
        }
        return new WorldBossConfig(enabled, interval, first, minPlayers, warnings, duration, retry, spots, pool,
            Math.max(1, s.getInt("world.leaderboard-size", 5)), Math.max(1, s.getInt("world.history-size", 10)));
    }
}
