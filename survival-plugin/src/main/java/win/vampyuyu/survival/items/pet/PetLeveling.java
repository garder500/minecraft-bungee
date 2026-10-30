package win.vampyuyu.survival.items.pet;

import java.util.Map;
import win.vampyuyu.survival.core.Rarity;

/** Pet levels 1-100 from experience (pure). The XP needed for level L to L+1 is {@code base * rarity * growth^(L-1)}. */
public final class PetLeveling {
    public static final int MAX_LEVEL = 100;

    private final double base;
    private final double growth;
    private final Map<String, Double> rarityMultiplier;

    public PetLeveling(double base, double growth, Map<String, Double> rarityMultiplier) {
        this.base = base;
        this.growth = growth;
        this.rarityMultiplier = rarityMultiplier;
    }

    private double multiplier(Rarity rarity) {
        return rarityMultiplier.getOrDefault(rarity.name(), 1.0);
    }

    /** XP needed to go from {@code level} to the next one. */
    public double xpToNext(int level, Rarity rarity) {
        if (level >= MAX_LEVEL) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(1, Math.round(base * multiplier(rarity) * Math.pow(growth, Math.max(0, level - 1))));
    }

    /** Total XP of a pet that has just reached {@code level}. */
    public double totalForLevel(int level, Rarity rarity) {
        double total = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
            total += xpToNext(l, rarity);
        }
        return total;
    }

    /** Level reached with {@code xp} total experience (1-100). */
    public int levelFor(double xp, Rarity rarity) {
        double left = Math.max(0, xp);
        int level = 1;
        while (level < MAX_LEVEL) {
            double need = xpToNext(level, rarity);
            if (left < need) {
                break;
            }
            left -= need;
            level++;
        }
        return level;
    }

    /** XP collected inside the current level. */
    public double xpIntoLevel(double xp, Rarity rarity) {
        int level = levelFor(xp, rarity);
        return level >= MAX_LEVEL ? 0 : xp - totalForLevel(level, rarity);
    }

    /** "██████░░░░" style bar of 10 cells for a progress ratio. */
    public static String bar(double ratio) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 10);
        return "█".repeat(filled) + "░".repeat(10 - filled);
    }
}
