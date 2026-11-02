package win.vampyuyu.survival.island;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frost affinity (GDD 9.2): points earned by cutting ice in the Frozen Heights, per player, saved in
 * {@code island/frost.json}. The tiers give functional bonuses, never raw power:
 * tier 1 "Warmth" (the cold no longer bites), tier 2 "Stasis" (sneaking in the air slows your fall).
 */
public final class FrostAffinity {
    public static final String FILE = "island/frost.json";
    /** Points needed for tier 1, 2. */
    public static final int[] THRESHOLDS = {40, 200};
    public static final String[] NAMES = {"None", "Warmth", "Stasis"};

    /** Persisted document. */
    public static final class Doc {
        public int version = 1;
        public Map<String, Integer> points = new HashMap<>();
    }

    private final Map<UUID, Integer> points = new HashMap<>();

    public static FrostAffinity fromDoc(Doc doc) {
        FrostAffinity out = new FrostAffinity();
        if (doc != null && doc.points != null) {
            doc.points.forEach((id, value) -> {
                try {
                    out.points.put(UUID.fromString(id), Math.max(0, value == null ? 0 : value));
                } catch (IllegalArgumentException e) {
                    // skip a damaged row
                }
            });
        }
        return out;
    }

    public Doc toDoc() {
        Doc doc = new Doc();
        points.forEach((id, value) -> doc.points.put(id.toString(), value));
        return doc;
    }

    public int points(UUID player) {
        return points.getOrDefault(player, 0);
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

    public void set(UUID player, int value) {
        points.put(player, Math.max(0, value));
    }
}
