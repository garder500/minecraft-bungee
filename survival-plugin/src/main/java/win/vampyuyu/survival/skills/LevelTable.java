package win.vampyuyu.survival.skills;

import java.util.List;

/**
 * Experience needed per level. {@code cost(n)} is the experience to go from level {@code n-1} to level {@code n}
 * (levels start at 0). Built either from an explicit list or from {@code base * growth^(n-1)} rounded to a few
 * significant digits, which gives a smooth Hypixel-like curve.
 */
public final class LevelTable {
    private final double[] cost;        // cost[n] for n in 1..max, cost[0] unused
    private final double[] cumulative;  // cumulative[n] = xp needed to REACH level n

    public LevelTable(double[] costs) {
        int max = costs.length;
        this.cost = new double[max + 1];
        this.cumulative = new double[max + 1];
        for (int level = 1; level <= max; level++) {
            double c = costs[level - 1];
            if (!(c > 0) || Double.isInfinite(c)) {
                throw new IllegalArgumentException("level " + level + " cost must be a positive number, got " + c);
            }
            cost[level] = c;
            cumulative[level] = cumulative[level - 1] + c;
        }
    }

    /** Explicit table when {@code explicit} covers {@code max} levels, the formula otherwise. */
    public static LevelTable build(List<Double> explicit, int max, double base, double growth, int sigDigits) {
        double[] costs = new double[max];
        if (explicit != null && explicit.size() >= max) {
            for (int i = 0; i < max; i++) {
                costs[i] = explicit.get(i);
            }
        } else {
            for (int i = 0; i < max; i++) {
                costs[i] = roundSignificant(base * Math.pow(growth, i), sigDigits);
            }
        }
        return new LevelTable(costs);
    }

    static double roundSignificant(double value, int digits) {
        if (value == 0 || digits <= 0) {
            return value;
        }
        double scale = Math.pow(10, digits - 1 - Math.floor(Math.log10(Math.abs(value))));
        return Math.max(1, Math.round(value * scale) / scale);
    }

    public int maxLevel() {
        return cost.length - 1;
    }

    /** Experience from level {@code level-1} to {@code level} (1-based); 0 outside the table. */
    public double cost(int level) {
        return level >= 1 && level < cost.length ? cost[level] : 0;
    }

    /** Total experience needed to reach {@code level}. */
    public double xpFor(int level) {
        if (level <= 0) {
            return 0;
        }
        return cumulative[Math.min(level, maxLevel())];
    }

    /** Level reached with {@code xp}, never above {@code cap}. */
    public int levelOf(double xp, int cap) {
        int top = Math.min(cap, maxLevel());
        int lo = 0;
        int hi = top;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (cumulative[mid] <= xp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** Level plus the fraction of the next level already earned (max level gives a whole number). */
    public double fractionalLevel(double xp, int cap) {
        int level = levelOf(xp, cap);
        if (level >= Math.min(cap, maxLevel())) {
            return level;
        }
        return level + (xp - cumulative[level]) / cost[level + 1];
    }
}
