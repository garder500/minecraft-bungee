package win.vampyuyu.survival.utility;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Remembers who was hit recently. Thread safe, in memory, pure (injectable clock). */
public final class CombatTag {
    private final LongSupplier clock;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();

    public CombatTag(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Tags the player for {@code millis} (extends a running tag, never shortens it). True when the player was free
     * before, so the caller can announce that the tag started.
     */
    public boolean tag(UUID player, long millis) {
        long now = clock.getAsLong();
        long end = now + millis;
        boolean[] fresh = {false};
        until.compute(player, (id, old) -> {
            fresh[0] = old == null || old <= now;
            return old == null ? end : Math.max(old, end);
        });
        return fresh[0];
    }

    /** Players whose tag ended since the last call (removed, so each one is returned once). */
    public java.util.List<UUID> expired() {
        long now = clock.getAsLong();
        java.util.List<UUID> done = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Long> entry : until.entrySet()) {
            if (entry.getValue() <= now && until.remove(entry.getKey(), entry.getValue())) {
                done.add(entry.getKey());
            }
        }
        return done;
    }

    /** Milliseconds of tag left, 0 when free. */
    public long remaining(UUID player) {
        Long end = until.get(player);
        if (end == null) {
            return 0;
        }
        // An ended tag stays until expired() hands it out (or clear/tag replaces it).
        return Math.max(0, end - clock.getAsLong());
    }

    public boolean tagged(UUID player) {
        return remaining(player) > 0;
    }

    public void clear(UUID player) {
        until.remove(player);
    }
}
