package win.vampyuyu.survival.realm;

/**
 * Pure maths of the moving platforms (GDD 12.5): a platform goes back and forth on a straight line between two block
 * positions, a block at a time, pausing at each end so a player can step on or off. Its position is a function of the
 * world time alone, so every platform of the Realm stays in step and nothing has to be saved.
 */
public final class PlatformMath {
    private PlatformMath() {}

    /** A line from one platform centre to another (block coordinates, relative to the build origin). */
    public record Track(int x0, int y0, int z0, int x1, int y1, int z1) {
        /** Number of one-block steps between the ends: the largest distance on an axis. */
        public int steps() {
            return Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        }

        /** The centre after {@code index} steps (0 = start, {@link #steps()} = end). */
        public int[] at(int index) {
            int n = steps();
            if (n == 0) {
                return new int[] {x0, y0, z0};
            }
            int i = Math.max(0, Math.min(n, index));
            return new int[] {lerp(x0, x1, i, n), lerp(y0, y1, i, n), lerp(z0, z1, i, n)};
        }

        private static int lerp(int a, int b, int i, int n) {
            return a + (int) Math.round((b - a) * (double) i / n);
        }
    }

    /**
     * Step index (0..steps) a platform is at, {@code tick} ticks into the world's life. It waits {@code dwell} ticks at
     * the start, travels for as long as {@code steps} at {@code stepsPerSecond} takes, waits at the end and comes back.
     * Between two consecutive ticks the index changes by at most one when {@code stepsPerSecond} is 20 or less.
     */
    public static int index(long tick, int steps, double stepsPerSecond, int dwell) {
        if (steps <= 0) {
            return 0;
        }
        long travel = Math.max(1, (long) Math.ceil(steps * 20.0 / Math.max(0.1, stepsPerSecond)));
        long cycle = 2 * (travel + dwell);
        long p = Math.floorMod(tick, cycle);
        if (p < dwell) {
            return 0;
        }
        p -= dwell;
        if (p < travel) {
            return (int) (steps * (p + 1) / travel);
        }
        p -= travel;
        if (p < dwell) {
            return steps;
        }
        p -= dwell;
        return steps - (int) (steps * (p + 1) / travel);
    }

    /** True when the 5x5 footprint centred on (cx, cz) holds the point, with a margin of a quarter block. */
    public static boolean onFootprint(int cx, int cz, int half, double x, double z) {
        return x >= cx - half - 0.25 && x <= cx + half + 1.25 && z >= cz - half - 0.25 && z <= cz + half + 1.25;
    }
}
