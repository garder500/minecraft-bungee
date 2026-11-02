package win.vampyuyu.survival.realm;

/**
 * Pure rules of the Tempestuous Archipelago (GDD 3.1, 12.5): how unstable the energy of a Lightning Rod is, and when the
 * gusts of wind blow. No Bukkit here so the numbers are unit tested.
 */
public final class StormMath {
    /** Output factors of the Lightning Rod and the share of seconds that gives each one (the shares add up to 1). */
    static final double[] SURGE_FACTOR = {0.0, 0.5, 1.0, 2.0, 4.0};
    static final double[] SURGE_SHARE = {0.10, 0.25, 0.35, 0.20, 0.10};
    /** Average factor: 1.275, so a rod at a base of 30 Flux/s averages about 38 Flux/s (a coal generator makes 10). */
    public static final double SURGE_MEAN = mean();

    /** Gust cycle: calm, a 3 s warning, a 2 s blow. */
    public static final long GUST_CYCLE_TICKS = 600;
    public static final long GUST_WARN_AT = 440;
    public static final long GUST_BLOW_AT = 500;
    public static final long GUST_END_AT = 540;
    /** Added to the horizontal velocity of an exposed player each tick of the blow (blocks per tick). */
    public static final double GUST_PUSH = 0.11;
    /** A gust never pushes a player faster than this (blocks per tick), so a fall is never a sure death. */
    public static final double GUST_MAX_SPEED = 0.9;

    private StormMath() {}

    private static double mean() {
        double sum = 0;
        for (int i = 0; i < SURGE_FACTOR.length; i++) {
            sum += SURGE_FACTOR[i] * SURGE_SHARE[i];
        }
        return sum;
    }

    /** SplitMix64 finaliser: the same input always gives the same output, and nearby inputs look unrelated. */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number in [0, 1) from a seed. */
    static double unit(long seed) {
        return (mix(seed) >>> 11) * 0x1.0p-53;
    }

    /**
     * The factor a Lightning Rod's output is multiplied by during one second. {@code seed} tells the rods apart (use
     * their position), {@code second} is any increasing count of seconds. Deterministic.
     */
    public static double surge(long seed, long second) {
        double u = unit(mix(seed) ^ second * 0x2545F4914F6CDD1DL);
        double acc = 0;
        for (int i = 0; i < SURGE_FACTOR.length; i++) {
            acc += SURGE_SHARE[i];
            if (u < acc) {
                return SURGE_FACTOR[i];
            }
        }
        return SURGE_FACTOR[SURGE_FACTOR.length - 1];
    }

    /** Seed of a rod from its block position. */
    public static long seedOf(int x, int y, int z) {
        return ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);
    }

    /** What the wind is doing. */
    public enum Gust {
        CALM, WARNING, BLOWING
    }

    public static Gust gust(long worldTicks) {
        long p = Math.floorMod(worldTicks, GUST_CYCLE_TICKS);
        if (p >= GUST_BLOW_AT && p < GUST_END_AT) {
            return Gust.BLOWING;
        }
        return p >= GUST_WARN_AT && p < GUST_BLOW_AT ? Gust.WARNING : Gust.CALM;
    }

    /** Direction of the gust of the cycle this tick belongs to, as {x, z} of unit length. A whole cycle blows one way. */
    public static double[] gustDirection(long worldTicks) {
        long cycle = Math.floorDiv(worldTicks, GUST_CYCLE_TICKS);
        double angle = unit(cycle * 31L + 7L) * Math.PI * 2;
        return new double[] {Math.cos(angle), Math.sin(angle)};
    }

    /** The horizontal velocity after one tick of the blow, capped at {@link #GUST_MAX_SPEED}. */
    public static double[] pushed(double vx, double vz, double[] dir) {
        double nx = vx + dir[0] * GUST_PUSH;
        double nz = vz + dir[1] * GUST_PUSH;
        double speed = Math.hypot(nx, nz);
        if (speed > GUST_MAX_SPEED) {
            nx *= GUST_MAX_SPEED / speed;
            nz *= GUST_MAX_SPEED / speed;
        }
        return new double[] {nx, nz};
    }
}
