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

    /** Tags the player for {@code millis} (extends a running tag, never shortens it). */
    public void tag(UUID player, long millis) {
        long end = clock.getAsLong() + millis;
        until.merge(player, end, Math::max);
    }

    /** Milliseconds of tag left, 0 when free. */
    public long remaining(UUID player) {
        Long end = until.get(player);
        if (end == null) {
            return 0;
        }
        long left = end - clock.getAsLong();
        if (left <= 0) {
            until.remove(player, end);
            return 0;
        }
        return left;
    }

    public boolean tagged(UUID player) {
        return remaining(player) > 0;
    }

    public void clear(UUID player) {
        until.remove(player);
    }
}
