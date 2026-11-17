package win.vampyuyu.survival.wings;

/**
 * The worn Wings look as a small state machine: which model variant to show (folded or spread), whether the wings
 * flap, and how the body is posed. Pure logic, no Bukkit types, so it is unit tested.
 */
public final class WingsPose {
    private WingsPose() { }

    /** Model variants, named like the pack models {@code worn_tierN_<name>}. */
    public enum Variant {
        FOLDED, SPREAD;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The two wings: each one is its own display and model ({@code worn_tierN_<variant>_<side>}). */
    public enum Side {
        LEFT(1), RIGHT(-1);

        private final int sign;

        Side(int sign) {
            this.sign = sign;
        }

        /** +1 for the wearer's left (display frame +x), -1 for the right. */
        public int sign() {
            return sign;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Body posture, it decides where the display is anchored on the player. */
    public enum Stance { STAND, SNEAK, LEANED }

    /** What the player is doing this tick. */
    public record Input(boolean gliding, boolean flying, boolean swimming, boolean sneaking) { }

    /** The decided look. {@code flap} only while gliding. */
    public record Look(Variant variant, boolean flap, Stance stance) { }

    public static Look decide(Input in) {
        if (in.gliding()) {
            return new Look(Variant.SPREAD, true, Stance.LEANED);
        }
        if (in.swimming()) {
            return new Look(Variant.FOLDED, false, Stance.LEANED);
        }
        if (in.flying()) {
            return new Look(Variant.SPREAD, false, in.sneaking() ? Stance.SNEAK : Stance.STAND);
        }
        return new Look(Variant.FOLDED, false, in.sneaking() ? Stance.SNEAK : Stance.STAND);
    }

    /** Slow flap clock: the phase flips every {@link #HALF_PERIOD} ticks (a full flap takes twice as long). */
    public static final class FlapCycle {
        public static final int HALF_PERIOD = 12;

        private FlapCycle() { }

        /** 0 (wings open) or 1 (wings drawn in) for a world tick. */
        public static int phase(long tick) {
            return (int) ((tick / HALF_PERIOD) & 1L);
        }
    }
}
