package win.vampyuyu.survival.movement;

/**
 * Pure maths of the movement toolkit (GDD 12.5): every speed is capped, every pull has a dead zone and a counter-measure,
 * and no movement effect can take the last health point. Velocities are blocks per tick, like Bukkit's
 * {@code setVelocity}; the caps match the propulsion pads ({@code PadMath}).
 */
public final class MovementMath {
    /** Fastest horizontal speed a movement effect may give, blocks per tick. */
    public static final double MAX_HORIZONTAL = 1.8;
    /** Fastest upward speed, blocks per tick. */
    public static final double MAX_UP = 1.2;
    /** Fastest downward speed: a pull never slams a player into the ground. */
    public static final double MAX_DOWN = 0.9;
    /** Strongest knockback strength a boss hit may ask for. */
    public static final double MAX_KNOCKBACK = 1.6;
    /** Highest Levitation amplifier (0 based) and longest Levitation, in ticks. */
    public static final int MAX_LEVITATION_AMP = 2;
    public static final int MAX_LEVITATION_TICKS = 60;
    /** Highest Jump Boost amplifier (0 based): above it, cheats and falls into the void. */
    public static final int MAX_JUMP_AMP = 3;
    /** A pull stops this close to its centre, so nobody jitters on the singularity. */
    public static final double PULL_DEAD_ZONE = 0.75;

    private MovementMath() {}

    /** A velocity, blocks per tick. */
    public record Vec(double x, double y, double z) {
        public static final Vec ZERO = new Vec(0, 0, 0);

        public double horizontal() {
            return Math.hypot(x, z);
        }

        public Vec plus(Vec o) {
            return new Vec(x + o.x, y + o.y, z + o.z);
        }
    }

    /** Caps the horizontal speed and each vertical direction. NaN or infinite input gives a zero velocity. */
    public static Vec cap(Vec v) {
        if (!Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)) {
            return Vec.ZERO;
        }
        double h = v.horizontal();
        double k = h > MAX_HORIZONTAL ? MAX_HORIZONTAL / h : 1;
        return new Vec(v.x * k, Math.max(-MAX_DOWN, Math.min(MAX_UP, v.y)), v.z * k);
    }

    /**
     * Knockback away from a source: the horizontal direction source to victim, {@code strength} capped at
     * {@link #MAX_KNOCKBACK}, plus an upward {@code lift} (at most 0.8). A victim on the
     * source gets pushed along +x so the result is never a silent no-op.
     */
    public static Vec knockback(double sx, double sz, double vx, double vz, double strength, double lift) {
        double s = Math.max(0, Math.min(MAX_KNOCKBACK, strength));
        double dx = vx - sx;
        double dz = vz - sz;
        double d = Math.hypot(dx, dz);
        if (d < 1e-6) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        return cap(new Vec(dx / d * s, Math.max(0, Math.min(0.8, lift)), dz / d * s));
    }

    /**
     * Velocity to add each tick to pull a body at (px, py, pz) to a centre. Nothing beyond {@code radius}; the pull
     * is weakest at the rim and strongest near the centre, then nothing inside the dead zone. {@code resist} in 0..1
     * is the counter-measure (the share of the pull the player cancels) and {@code anchorsLeft/anchorsTotal} scales
     * the whole pull: destroying every anchor switches it off.
     */
    public static Vec pull(double px, double py, double pz, double cx, double cy, double cz,
                           double strength, double radius, double resist, int anchorsLeft, int anchorsTotal) {
        double dx = cx - px;
        double dy = cy - py;
        double dz = cz - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > radius || d < PULL_DEAD_ZONE || radius <= 0) {
            return Vec.ZERO;
        }
        double falloff = 0.4 + 0.6 * (1 - d / radius);
        double r = Math.max(0, Math.min(1, resist));
        double anchors = anchorFactor(anchorsLeft, anchorsTotal);
        double s = Math.max(0, strength) * falloff * (1 - r) * anchors;
        return new Vec(dx / d * s, dy / d * s, dz / d * s);
    }

    /** Share of the pull that remains once anchors are destroyed (1 with none destroyed, 0 with all). */
    public static double anchorFactor(int left, int total) {
        if (total <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, left / (double) total));
    }

    /** Velocity after adding a pull to the current one, capped. */
    public static Vec applyPull(Vec current, Vec delta) {
        return cap(current.plus(delta));
    }

    /** Damage a movement effect may deal: never the last half-heart, so nothing kills outright. */
    public static double safeDamage(double health, double damage) {
        return Math.max(0, Math.min(damage, health - 1));
    }

    public static int levitationAmp(int wanted) {
        return Math.max(0, Math.min(MAX_LEVITATION_AMP, wanted));
    }

    public static int levitationTicks(int wanted) {
        return Math.max(1, Math.min(MAX_LEVITATION_TICKS, wanted));
    }

    public static int jumpAmp(int wanted) {
        return Math.max(0, Math.min(MAX_JUMP_AMP, wanted));
    }

    /** True when (x, z) is inside a vertical column (levitation zone) of radius {@code r}, from y0 up to y0 + height. */
    public static boolean inColumn(double x, double y, double z, double cx, double y0, double cz, double r, double height) {
        return y >= y0 && y <= y0 + height && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r;
    }

    /** True when a position is a place to come back to: solid ground under the feet, not too close to the world floor. */
    public static boolean isSafeSpot(boolean onGround, boolean feetPassable, boolean inLiquid, double y, int minHeight) {
        return onGround && feetPassable && !inLiquid && y > minHeight + 4;
    }
}
