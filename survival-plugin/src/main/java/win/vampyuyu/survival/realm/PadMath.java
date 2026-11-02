package win.vampyuyu.survival.realm;

/**
 * Pure maths of the propulsion pads (GDD 12.5): the velocity that sends a player from a pad to a point on another island,
 * using the ground-less movement of Minecraft (horizontal speed x0.91 each tick, vertical speed (v - 0.08) x 0.98).
 * Speeds are capped so a pad never throws anyone faster than a Wing glide, and the flight is long enough to be readable.
 */
public final class PadMath {
    public static final double AIR_DRAG = 0.91;
    public static final double GRAVITY = 0.08;
    public static final double VERTICAL_DRAG = 0.98;
    /** Fastest horizontal speed at take-off, blocks per tick (36 blocks per second). */
    public static final double MAX_HORIZONTAL = 1.8;
    /** Fastest upward speed at take-off, blocks per tick. */
    public static final double MAX_VERTICAL = 2.2;
    public static final int MIN_TICKS = 24;
    public static final int MAX_TICKS = 80;

    private PadMath() {}

    /** A launch: the velocity to set and how many ticks the flight lasts. */
    public record Launch(double vx, double vy, double vz, int ticks) {}

    /** Sum of the horizontal drag over {@code ticks} ticks: the distance covered per block-per-tick of take-off speed. */
    static double horizontalReach(int ticks) {
        return (1 - Math.pow(AIR_DRAG, ticks)) / (1 - AIR_DRAG);
    }

    /** Height gained after {@code ticks} ticks for a take-off vertical speed. */
    static double rise(double vy0, int ticks) {
        double y = 0;
        double v = vy0;
        for (int i = 0; i < ticks; i++) {
            y += v;
            v = (v - GRAVITY) * VERTICAL_DRAG;
        }
        return y;
    }

    /** Highest point reached during the flight, relative to the start. */
    static double apex(double vy0, int ticks) {
        double y = 0;
        double best = 0;
        double v = vy0;
        for (int i = 0; i < ticks; i++) {
            y += v;
            best = Math.max(best, y);
            v = (v - GRAVITY) * VERTICAL_DRAG;
        }
        return best;
    }

    /**
     * The launch that lands {@code (dx, dy, dz)} blocks away, or null when no flight of at most {@link #MAX_TICKS} ticks
     * stays inside the speed caps (the pad is too far from its target).
     */
    public static Launch launch(double dx, double dy, double dz) {
        double flat = Math.hypot(dx, dz);
        for (int ticks = MIN_TICKS; ticks <= MAX_TICKS; ticks++) {
            double speed = flat / horizontalReach(ticks);
            if (speed > MAX_HORIZONTAL) {
                continue;
            }
            double low = rise(0, ticks);
            double high = rise(1, ticks);
            double vy0 = (dy - low) / (high - low);
            if (vy0 > MAX_VERTICAL) {
                continue;
            }
            double ux = flat == 0 ? 0 : dx / flat;
            double uz = flat == 0 ? 0 : dz / flat;
            return new Launch(ux * speed, vy0, uz * speed, ticks);
        }
        return null;
    }

    /** Where a launch ends up after its flight, relative to the start: {x, y, z}. */
    public static double[] landing(Launch launch) {
        double reach = horizontalReach(launch.ticks());
        return new double[] {launch.vx() * reach, rise(launch.vy(), launch.ticks()), launch.vz() * reach};
    }

    /** Height of the highest point of the flight above the start. */
    public static double apexOf(Launch launch) {
        return apex(launch.vy(), launch.ticks());
    }
}
