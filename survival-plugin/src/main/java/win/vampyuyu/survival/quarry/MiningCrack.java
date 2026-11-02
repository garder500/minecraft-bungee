package win.vampyuyu.survival.quarry;

/**
 * Pure maths of the visible break (GDD 7.2): a Miner's cycle progress (0 to 1) becomes one of the 10 vanilla crack
 * stages, and between two machine ticks (1 s) the progress is extrapolated so the crack grows smoothly.
 */
final class MiningCrack {
    /** Vanilla has 10 destroy stages. */
    static final int STAGES = 10;

    private MiningCrack() {}

    /** The crack stage, 0 when there is none yet and 1 to 10 for the ten textures. */
    static int stage(double progress) {
        if (progress <= 0) {
            return 0;
        }
        return Math.min(STAGES, (int) Math.floor(progress * STAGES) + 1);
    }

    /** The value for {@code Player#sendBlockDamage} that shows the given stage (0 clears the crack). */
    static float damage(int stage) {
        if (stage <= 0) {
            return 0f;
        }
        return Math.min(STAGES, stage) / (float) STAGES - 0.01f; // 0.09 ... 0.99: the same texture whether it floors or rounds up
    }

    /** The progress a Miner has {@code elapsedMillis} after its last tick, if it gains {@code rate} cycles per second. */
    static double extrapolate(double base, double rate, long elapsedMillis) {
        if (rate <= 0) {
            return Math.min(0.999, Math.max(0, base));
        }
        double p = base + rate * Math.min(1000, Math.max(0, elapsedMillis)) / 1000.0;
        return Math.min(0.999, Math.max(0, p)); // never shows the end of the cycle: the break itself does
    }
}
