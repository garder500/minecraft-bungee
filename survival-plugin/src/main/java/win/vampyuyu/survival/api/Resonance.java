package win.vampyuyu.survival.api;

/**
 * Resonance ranks of the Bastions (GDD 12.4): five replayable difficulty ranks. Rank 0 means "no Resonance" (a boss
 * summoned outside a Bastion run) and behaves like rank I. Pure constants, shared by the boss and resonance features.
 * The ranks never give the player any stat: they scale the boss and unlock better rewards.
 */
public final class Resonance {
    public static final int MAX_RANK = 5;
    private static final double[] HP = {1, 1.5, 2, 2.5, 3};
    private static final double[] DAMAGE = {1, 1.2, 1.4, 1.6, 1.8};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    private Resonance() {}

    /** Keeps a rank in 1..5 (0 and below become I). */
    public static int clamp(int rank) {
        return Math.max(1, Math.min(MAX_RANK, rank));
    }

    /** Boss health multiplier of a rank: x1 / x1.5 / x2 / x2.5 / x3. */
    public static double hpMultiplier(int rank) {
        return HP[clamp(rank) - 1];
    }

    /** Boss damage multiplier of a rank: x1 / x1.2 / x1.4 / x1.6 / x1.8. */
    public static double damageMultiplier(int rank) {
        return DAMAGE[clamp(rank) - 1];
    }

    public static String roman(int rank) {
        return ROMAN[clamp(rank) - 1];
    }
}
