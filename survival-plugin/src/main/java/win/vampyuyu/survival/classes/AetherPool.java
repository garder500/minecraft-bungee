package win.vampyuyu.survival.classes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The Aether reserve of each player, refilled over time (computed on demand, nothing ticks). */
public final class AetherPool {
    public static final double BASE = 100;
    /** Aether per second. */
    public static final double REGEN = 2.0;

    private record Level(double value, long at) {}

    private final Map<UUID, Level> levels = new HashMap<>();

    /** Current Aether of the player, between 0 and {@code max}. A new player starts full. */
    public synchronized double current(UUID player, double max, long now) {
        Level level = levels.get(player);
        if (level == null) {
            return max;
        }
        return Math.min(max, level.value() + REGEN * Math.max(0, now - level.at()) / 1000.0);
    }

    /** Spends {@code cost} if the reserve holds it. */
    public synchronized boolean spend(UUID player, double cost, double max, long now) {
        double have = current(player, max, now);
        if (have + 1e-9 < cost) {
            return false;
        }
        levels.put(player, new Level(have - cost, now));
        return true;
    }

    /** Milliseconds until {@code cost} is available. */
    public synchronized long waitFor(UUID player, double cost, double max, long now) {
        double missing = cost - current(player, max, now);
        return missing <= 0 ? 0 : (long) Math.ceil(missing / REGEN * 1000.0);
    }

    public synchronized void forget(UUID player) {
        levels.remove(player);
    }
}
