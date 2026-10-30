package win.vampyuyu.survival.utility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Pending /tpa and /tpahere requests. Pure state (injectable clock), main thread only.
 * Rules: one open request per sender (a new one replaces the old), a cooldown between two requests of the same
 * sender, a cap of open requests per target, expiry, and {@link #take} removes a request so it can only be
 * answered once.
 */
public final class TpaManager {
    public enum Type {
        /** The sender goes to the target. */
        TO_TARGET,
        /** The target goes to the sender. */
        HERE
    }

    public record Request(UUID sender, UUID target, Type type, long expiresAt) {}

    public sealed interface SendResult {
        record Sent(Request request, Request replaced) implements SendResult {}

        record Self() implements SendResult {}

        record Cooldown(long remainingMs) implements SendResult {}

        record TooMany() implements SendResult {}
    }

    private final LongSupplier clock;
    private volatile long expireMs;
    private volatile long sendCooldownMs;
    private volatile int maxPerTarget;
    /** sender to their single open request. */
    private final Map<UUID, Request> bySender = new LinkedHashMap<>();
    /** sender to the time they may send again. */
    private final Map<UUID, Long> nextSend = new LinkedHashMap<>();

    public TpaManager(LongSupplier clock, long expireMs, long sendCooldownMs, int maxPerTarget) {
        this.clock = clock;
        this.expireMs = expireMs;
        this.sendCooldownMs = sendCooldownMs;
        this.maxPerTarget = maxPerTarget;
    }

    /** Applies new limits (config reload) to the requests made from now on. */
    public void configure(long expireMs, long sendCooldownMs, int maxPerTarget) {
        this.expireMs = expireMs;
        this.sendCooldownMs = sendCooldownMs;
        this.maxPerTarget = maxPerTarget;
    }

    public SendResult send(UUID sender, UUID target, Type type, boolean bypassCooldown) {
        if (sender.equals(target)) {
            return new SendResult.Self();
        }
        long now = clock.getAsLong();
        if (!bypassCooldown) {
            Long next = nextSend.get(sender);
            if (next != null && next > now) {
                return new SendResult.Cooldown(next - now);
            }
        }
        Request old = bySender.get(sender);
        if (old != null && old.expiresAt() <= now) {
            bySender.remove(sender);
            old = null;
        }
        // A replacement of the request to the same target does not count against the cap.
        if (countFor(target, now) >= maxPerTarget && (old == null || !old.target().equals(target))) {
            return new SendResult.TooMany();
        }
        Request request = new Request(sender, target, type, now + expireMs);
        bySender.put(sender, request);
        nextSend.put(sender, now + sendCooldownMs);
        return new SendResult.Sent(request, old);
    }

    private int countFor(UUID target, long now) {
        int n = 0;
        for (Request r : bySender.values()) {
            if (r.target().equals(target) && r.expiresAt() > now) {
                n++;
            }
        }
        return n;
    }

    /** Open requests for a target, oldest first. */
    public List<Request> pendingFor(UUID target) {
        long now = clock.getAsLong();
        List<Request> list = new ArrayList<>();
        for (Request r : bySender.values()) {
            if (r.target().equals(target) && r.expiresAt() > now) {
                list.add(r);
            }
        }
        return list;
    }

    /**
     * Removes and returns a request for {@code target}: the one from {@code sender}, or the newest one when
     * {@code sender} is null. Expired requests are never returned.
     */
    public Optional<Request> take(UUID target, UUID sender) {
        List<Request> open = pendingFor(target);
        Request chosen = null;
        for (Request r : open) {
            if (sender == null || r.sender().equals(sender)) {
                chosen = r;
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        bySender.remove(chosen.sender(), chosen);
        return Optional.of(chosen);
    }

    /** The sender cancels their own open request. */
    public Optional<Request> cancel(UUID sender) {
        Request r = bySender.remove(sender);
        if (r == null || r.expiresAt() <= clock.getAsLong()) {
            return Optional.empty();
        }
        return Optional.of(r);
    }

    /** Removes and returns the requests that expired (so the senders can be told). */
    public List<Request> purgeExpired() {
        long now = clock.getAsLong();
        List<Request> expired = new ArrayList<>();
        bySender.values().removeIf(r -> {
            if (r.expiresAt() <= now) {
                expired.add(r);
                return true;
            }
            return false;
        });
        nextSend.values().removeIf(t -> t <= now);
        return expired;
    }

    /** Forgets everything about a player who left. Returns the removed requests they sent or received. */
    public List<Request> forget(UUID player) {
        List<Request> removed = new ArrayList<>();
        bySender.values().removeIf(r -> {
            if (r.sender().equals(player) || r.target().equals(player)) {
                removed.add(r);
                return true;
            }
            return false;
        });
        // nextSend stays: leaving and rejoining must not reset the anti-spam cooldown.
        return removed;
    }

    public int size() {
        return bySender.size();
    }
}
