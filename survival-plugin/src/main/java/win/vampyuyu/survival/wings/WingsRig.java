package win.vampyuyu.survival.wings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Bookkeeping of the worn Wings displays: exactly one pair (left and right wing) per wearer, created when the wearer needs one, updated only when
 * what it shows has changed, removed on every exit path. The entity work is behind {@link Backend} so the logic is
 * testable without a server.
 */
public final class WingsRig {
    /** What a display should show now. Rotations are quantised by the caller so equal looks compare equal. */
    public record Desired(int tier, WingsPose.Variant variant, int flapPhase, boolean flap, WingsPose.Stance stance,
                          int yaw, int lean, boolean hideFromOwner) {
        boolean sameLook(Desired o) {
            return tier == o.tier && variant == o.variant && stance == o.stance;
        }
    }

    /** The entity side: every wearer has a pair of displays, one per {@link WingsPose.Side}. */
    public interface Backend {
        /** Creates and mounts the display of one wing. False when it could not be created. */
        boolean spawn(UUID owner, WingsPose.Side side, Desired desired);

        /** Applies the difference between two looks to one wing. {@code ticks} is the interpolation length. */
        void update(UUID owner, WingsPose.Side side, Desired previous, Desired next, int ticks);

        /** Removes the display of one wing (also when it is already gone). */
        void remove(UUID owner, WingsPose.Side side);

        /** False when the entity died, was unloaded or got dismounted. */
        boolean alive(UUID owner, WingsPose.Side side);
    }

    public static final int FAST = 2;
    public static final int SLOW = 10;

    private final Backend backend;
    private final Map<UUID, Desired> shown = new HashMap<>();
    private int spawns;
    private int updates;

    public WingsRig(Backend backend) {
        this.backend = backend;
    }

    /** Brings the display of {@code owner} to {@code want}; null means no display. Returns true if packets were needed. */
    public boolean sync(UUID owner, @Nullable Desired want) {
        if (want == null) {
            return detach(owner);
        }
        Desired now = shown.get(owner);
        if (now != null && !pairAlive(owner)) {
            removePair(owner);  // one wing lost: both are replaced, never a lone wing
            shown.remove(owner);
            now = null;
        }
        if (now == null) {
            for (WingsPose.Side side : WingsPose.Side.values()) {
                if (!backend.spawn(owner, side, want)) {
                    removePair(owner);  // nothing half attached
                    return false;
                }
            }
            shown.put(owner, want);
            spawns++;
            return true;
        }
        if (now.equals(want)) {
            return false;  // standing still: nothing is sent
        }
        boolean flapOnly = now.sameLook(want) && now.yaw() == want.yaw() && now.lean() == want.lean()
            && now.flapPhase() != want.flapPhase();
        for (WingsPose.Side side : WingsPose.Side.values()) {
            backend.update(owner, side, now, want, flapOnly ? SLOW : FAST);
        }
        shown.put(owner, want);
        updates++;
        return true;
    }

    /** Removes the display of one player. True if there was one. */
    public boolean detach(UUID owner) {
        if (shown.remove(owner) == null) {
            return false;
        }
        removePair(owner);
        return true;
    }

    private boolean pairAlive(UUID owner) {
        for (WingsPose.Side side : WingsPose.Side.values()) {
            if (!backend.alive(owner, side)) {
                return false;
            }
        }
        return true;
    }

    private void removePair(UUID owner) {
        for (WingsPose.Side side : WingsPose.Side.values()) {
            backend.remove(owner, side);
        }
    }

    /** Removes every display (plugin disable). */
    public void detachAll() {
        for (UUID id : new HashSet<>(shown.keySet())) {
            detach(id);
        }
    }

    public boolean attached(UUID owner) {
        return shown.containsKey(owner);
    }

    public Set<UUID> owners() {
        return Set.copyOf(shown.keySet());
    }

    public @Nullable Desired current(UUID owner) {
        return shown.get(owner);
    }

    public int spawns() {
        return spawns;
    }

    public int updates() {
        return updates;
    }
}
