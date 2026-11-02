package win.vampyuyu.survival.marsh;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Purification (the Marsh affinity), per player, saved in {@code realms/marsh.json}: points come from salvaging expired
 * items in a Rot Vat. Tier 1 "Filtered" (the miasma no longer weakens you), tier 2 "Reclaimer" (a Vat needs fewer
 * items per Rot Resin roll). Functional bonuses, never raw power. {@code carry} keeps the points a Vat could not turn
 * into a whole roll yet.
 */
public final class MarshProgress {
    public static final String FILE = "realms/marsh.json";
    public static final int[] THRESHOLDS = {40, 200};
    public static final String[] NAMES = {"None", "Filtered", "Reclaimer"};

    /** Persisted document. */
    public static final class Doc {
        public int version = 1;
        public Map<String, Integer> points = new HashMap<>();
        public Map<String, Integer> carry = new HashMap<>();
    }

    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Integer> carry = new HashMap<>();

    public static MarshProgress fromDoc(Doc doc) {
        MarshProgress out = new MarshProgress();
        if (doc != null) {
            read(doc.points, out.points);
            read(doc.carry, out.carry);
        }
        return out;
    }

    private static void read(Map<String, Integer> from, Map<UUID, Integer> to) {
        if (from == null) {
            return;
        }
        from.forEach((id, value) -> {
            try {
                to.put(UUID.fromString(id), Math.max(0, value == null ? 0 : value));
            } catch (IllegalArgumentException e) {
                // skip a damaged row
            }
        });
    }

    public Doc toDoc() {
        Doc doc = new Doc();
        points.forEach((id, value) -> doc.points.put(id.toString(), value));
        carry.forEach((id, value) -> doc.carry.put(id.toString(), value));
        return doc;
    }

    public int points(UUID player) {
        return points.getOrDefault(player, 0);
    }

    public int carry(UUID player) {
        return carry.getOrDefault(player, 0);
    }

    public void setCarry(UUID player, int value) {
        carry.put(player, Math.max(0, value));
    }

    public int tier(UUID player) {
        return tierOf(points(player));
    }

    public static int tierOf(int points) {
        int tier = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (points >= THRESHOLDS[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    /** Adds points; returns the new tier when this crossed a threshold, otherwise -1. */
    public int add(UUID player, int amount) {
        int before = tier(player);
        points.merge(player, Math.max(0, amount), Integer::sum);
        int after = tier(player);
        return after > before ? after : -1;
    }
}
