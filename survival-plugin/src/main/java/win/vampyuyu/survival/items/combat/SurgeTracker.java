package win.vampyuyu.survival.items.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Surge: every Nth fully charged hit of an attacker on the same target is a Surge. Predictable on purpose: no dice,
 * the count only restarts when the attacker switches target (a weak, half-charged swing does not count and does not
 * reset it either).
 */
public final class SurgeTracker {
    private record Run(UUID target, int hits) {}

    private final Map<UUID, Run> runs = new HashMap<>();

    /**
     * Registers a hit.
     *
     * @param every the Surge comes on every Nth counted hit (2 or more)
     * @param counted false for a swing that was not charged enough: it changes nothing
     * @return true when this hit is a Surge
     */
    public synchronized boolean hit(UUID attacker, UUID target, boolean counted, int every) {
        if (!counted) {
            return false;
        }
        if (runs.size() > 4000) {
            runs.clear();
        }
        Run run = runs.get(attacker);
        int hits = run != null && run.target().equals(target) ? run.hits() + 1 : 1;
        if (hits >= Math.max(2, every)) {
            runs.put(attacker, new Run(target, 0));
            return true;
        }
        runs.put(attacker, new Run(target, hits));
        return false;
    }

    /** Hits counted so far towards the next Surge on {@code target} (0 after a Surge or on another target). */
    public synchronized int progress(UUID attacker, UUID target) {
        Run run = runs.get(attacker);
        return run != null && run.target().equals(target) ? run.hits() : 0;
    }

    public synchronized void forget(UUID attacker) {
        runs.remove(attacker);
    }
}
