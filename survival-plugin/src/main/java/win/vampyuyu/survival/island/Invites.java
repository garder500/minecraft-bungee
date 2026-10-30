package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Pending co-op invitations with expiry. Main thread only, in memory (an invite never outlives a restart). */
public final class Invites {
    /** One invitation. */
    public record Invite(UUID island, UUID inviter, long expiresAt) {}

    /** What happened when an invite was added. */
    public enum Added { SENT, REFRESHED, TOO_MANY }

    private final LongSupplier clock;
    private final Map<UUID, List<Invite>> byTarget = new HashMap<>();

    public Invites() {
        this(System::currentTimeMillis);
    }

    public Invites(LongSupplier clock) {
        this.clock = clock;
    }

    private List<Invite> live(UUID target) {
        List<Invite> list = byTarget.get(target);
        if (list == null) {
            return new ArrayList<>();
        }
        long now = clock.getAsLong();
        list.removeIf(invite -> invite.expiresAt() <= now);
        if (list.isEmpty()) {
            byTarget.remove(target);
        }
        return list;
    }

    /** Adds (or refreshes) an invite from an island to a player. */
    public Added add(UUID target, UUID island, UUID inviter, long ttlMillis, int maxPending) {
        List<Invite> list = live(target);
        boolean existed = list.removeIf(invite -> invite.island().equals(island));
        if (!existed && list.size() >= maxPending) {
            byTarget.put(target, list);
            return Added.TOO_MANY;
        }
        list.add(new Invite(island, inviter, clock.getAsLong() + ttlMillis));
        byTarget.put(target, list);
        return existed ? Added.REFRESHED : Added.SENT;
    }

    /** Live invites of a player, oldest first. */
    public List<Invite> pending(UUID target) {
        return List.copyOf(live(target));
    }

    /** Removes and returns the invite from an island (or the newest one when {@code island} is null). */
    public Optional<Invite> take(UUID target, UUID island) {
        List<Invite> list = live(target);
        Invite found = null;
        for (Invite invite : list) {
            if (island == null || invite.island().equals(island)) {
                found = invite;
                if (island != null) {
                    break;
                }
            }
        }
        if (found != null) {
            list.remove(found);
            if (list.isEmpty()) {
                byTarget.remove(target);
            }
        }
        return Optional.ofNullable(found);
    }

    /** Drops every invite from an island (it was deleted, or the player was banned). */
    public void dropIsland(UUID island) {
        byTarget.values().forEach(list -> list.removeIf(invite -> invite.island().equals(island)));
        byTarget.values().removeIf(List::isEmpty);
    }

    public void dropTarget(UUID target) {
        byTarget.remove(target);
    }
}
