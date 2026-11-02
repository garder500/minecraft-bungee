package win.vampyuyu.survival.island;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grove affinity (GDD 9.2): points earned by harvesting grown crops in the Whispering Grove, per player, saved in
 * {@code island/grove.json}. The tiers give functional bonuses, never raw power:
 * tier 1 "Respiration" (the spore haze no longer touches you), tier 2 "Growth" (bone meal grows a crop one more stage).
 */
public final class GroveAffinity {
    public static final String FILE = "island/grove.json";
    /** Points needed for tier 1, 2. */
    public static final int[] THRESHOLDS = {40, 200};
    public static final String[] NAMES = {"None", "Respiration", "Growth"};

    /** Persisted document. */
    public static final class Doc {
        public int version = 1;
        public Map<String, Integer> points = new HashMap<>();
    }

    private final Map<UUID, Integer> points = new HashMap<>();

    public static GroveAffinity fromDoc(Doc doc) {
        GroveAffinity out = new GroveAffinity();
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
