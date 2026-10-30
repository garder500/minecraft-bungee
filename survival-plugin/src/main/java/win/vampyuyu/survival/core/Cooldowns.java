package win.vampyuyu.survival.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** In-memory per player and key cooldowns, in milliseconds. Thread safe; not persisted. */
public final class Cooldowns {
    private final LongSupplier clock;
    private final Map<String, Long> until = new ConcurrentHashMap<>();

    public Cooldowns() {
        this(System::currentTimeMillis);
    }

    public Cooldowns(LongSupplier clock) {
        this.clock = clock;
    }

    private static String k(UUID player, String key) {
        return player + "|" + key;
    }

    /** Milliseconds left, 0 when ready. */
    public long remaining(UUID player, String key) {
        Long end = until.get(k(player, key));
        if (end == null) {
            return 0;
        }
        long left = end - clock.getAsLong();
        if (left <= 0) {
            until.remove(k(player, key), end);
            return 0;
        }
        return left;
    }

    public boolean ready(UUID player, String key) {
        return remaining(player, key) == 0;
    }

    /** Starts the cooldown if ready. Returns false (and changes nothing) while it is still running. */
    public boolean tryUse(UUID player, String key, long millis) {
        long now = clock.getAsLong();
        boolean[] granted = {false};
        until.compute(k(player, key), (name, end) -> {
            if (end == null || end <= now) {
                granted[0] = true;
                return now + millis;
            }
            return end;
        });
        return granted[0];
    }

    /** Forces a cooldown, replacing any running one. */
    public void set(UUID player, String key, long millis) {
        until.put(k(player, key), clock.getAsLong() + millis);
    }

    public void clear(UUID player, String key) {
        until.remove(k(player, key));
    }
}
