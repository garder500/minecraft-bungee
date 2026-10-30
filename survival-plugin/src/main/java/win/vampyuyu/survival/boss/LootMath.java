package win.vampyuyu.survival.boss;

import java.util.List;
import java.util.Random;

/** Pure reward formulas: damage shares, rank bonuses, Magic Find, pity. */
public final class LootMath {
    private LootMath() {}

    /**
     * Coins or xp for one participant: the {@code floor} part of the pool is split EQUALLY between the eligible
     * participants, the rest by damage share: {@code pool * (floor / n + (1 - floor) * share)}. The sum over all
     * eligible participants never exceeds the pool, so extra accounts cannot multiply the payout.
     */
    public static double share(double pool, double share, double floor, int eligible) {
        double f = clamp(floor, 0, 1);
        int n = Math.max(1, eligible);
        return Math.max(0, pool * (f / n + (1 - f) * clamp(share, 0, 1)));
    }

    /** Chance multiplier for a 1-based rank (1.0 beyond the configured list or for rank 0). */
    public static double rankMultiplier(List<Double> bonus, int rank) {
        if (rank < 1 || rank > bonus.size()) {
            return 1.0;
        }
        return Math.max(0, bonus.get(rank - 1));
    }

    /** Pity bonus after {@code dry} kills without the drop, capped. */
    public static double pityBonus(int dry, double perKill, double cap) {
        return Math.min(Math.max(0, cap), Math.max(0, dry) * Math.max(0, perKill));
    }

    /**
     * Final drop chance. A guaranteed entry (base 1) stays guaranteed. Magic Find is a percentage:
     * 50 means +50% chance.
     */
    public static double chance(double base, double pity, double rankMult, double magicFind, boolean useMagicFind) {
        if (base >= 1.0) {
            return 1.0;
        }
        double mf = useMagicFind ? 1 + Math.max(0, magicFind) / 100.0 : 1.0;
        return clamp((base + pity) * rankMult * mf, 0, 1);
    }

    /** Whether a participant may roll the entry at all. */
    public static boolean eligible(BossDefinition.LootEntry entry, double share, int rank) {
        if (share < entry.minShare()) {
            return false;
        }
        return entry.maxRank() <= 0 || (rank >= 1 && rank <= entry.maxRank());
    }

    /** Rolls an amount in {@code [min, max]}. */
    public static int amount(BossDefinition.LootEntry entry, Random random) {
        return entry.max() <= entry.min() ? entry.min() : entry.min() + random.nextInt(entry.max() - entry.min() + 1);
    }

    public static boolean roll(double chance, Random random) {
        return chance >= 1.0 || random.nextDouble() < chance;
    }

    /**
     * Payout multiplier of a repeated kill: {@code 1 - decay * recentKills}, never below {@code floor}. A boss
     * that can be summoned again and again with a crafted item must not be an endless coin faucet.
     */
    public static double repeatMultiplier(int recentKills, double decay, double floor) {
        double mult = 1.0 - Math.max(0, decay) * Math.max(0, recentKills);
        return clamp(mult, clamp(floor, 0, 1), 1.0);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
