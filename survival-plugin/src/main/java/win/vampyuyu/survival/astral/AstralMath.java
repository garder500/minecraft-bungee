package win.vampyuyu.survival.astral;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import win.vampyuyu.survival.api.Realm;

/**
 * The numbers of the Astral Interiority, pure and testable (GDD 4.5 and 12.5). Every force is bounded and telegraphed: the
 * pull is an impulse, never damage; whoever the singularity wins is carried back to the last shelter without a scratch.
 */
public final class AstralMath {
    /** Default pull of the singularity at its edge of reach, per application, in blocks per tick (setting {@code realm.astral.pull-max}). */
    public static final double DEFAULT_PULL_MAX = 0.22;
    /** The pull never exceeds this, whatever the settings say. */
    public static final double PULL_CEILING = 0.35;
    /** Surge multiplier. */
    public static final double SURGE_FACTOR = 2.5;
    /** Ticks between two surges and how long one lasts. */
    public static final int SURGE_PERIOD = 300;
    public static final int SURGE_LENGTH = 60;
    /** Ticks of warning before a surge (sound and action bar), so the player can reach a shelter or crouch. */
    public static final int SURGE_WARNING = 40;
    /** Crouching halves the pull. */
    public static final double CROUCH_FACTOR = 0.5;
    /** Speed of the guided lift of a synapse, blocks per tick, and the longest it can last. */
    public static final double LIFT_SPEED = 0.55;
    public static final int LIFT_MAX_TICKS = 60;
    /** Push of the memory current of the sea, blocks per tick. */
    public static final double CURRENT = 0.05;

    private AstralMath() {}

    /** The Realms whose Guardian this player did not defeat yet, in Realm order. Empty means the Interiority is open to them. */
    public static List<Realm> missing(Set<Realm> defeated) {
        List<Realm> out = new ArrayList<>();
        for (Realm realm : Realm.values()) {
            if (!defeated.contains(realm)) {
                out.add(realm);
            }
        }
        return out;
    }

    /**
     * Pull of the singularity on a player at {@code dist} blocks of its centre (impulse in blocks per tick toward the
     * centre): zero out of reach, growing linearly to {@code max} at the edge of the horizon, times {@link #SURGE_FACTOR}
     * during a surge, halved when crouching, never above {@link #PULL_CEILING}.
     */
    public static double pull(double dist, boolean surge, boolean crouching, double max) {
        if (dist >= AstralLayout.PULL_RADIUS || dist <= 0) {
            return 0;
        }
        double base = Math.min(max, PULL_CEILING) * (1 - dist / AstralLayout.PULL_RADIUS);
        if (surge) {
            base *= SURGE_FACTOR;
        }
        if (crouching) {
            base *= CROUCH_FACTOR;
        }
        return Math.min(base, PULL_CEILING);
    }

    /** True while a surge is running at this tick of the realm clock. */
    public static boolean surging(long tick) {
        return Math.floorMod(tick, (long) SURGE_PERIOD) >= SURGE_PERIOD - SURGE_LENGTH;
    }

    /** True in the {@link #SURGE_WARNING} ticks before a surge. */
    public static boolean warning(long tick) {
        long phase = Math.floorMod(tick, (long) SURGE_PERIOD);
        return phase >= SURGE_PERIOD - SURGE_LENGTH - SURGE_WARNING && phase < SURGE_PERIOD - SURGE_LENGTH;
    }

    /** The lit anchor whose shelter covers this point, or null. */
    public static AstralLayout.Anchor shelter(double x, double y, double z, Predicate<String> lit) {
        for (AstralLayout.Anchor anchor : AstralLayout.ANCHORS) {
            if (lit.test(anchor.id()) && Math.hypot(x - anchor.x(), z - anchor.z()) <= AstralLayout.ANCHOR_SHELTER
                    && Math.abs(y - anchor.y()) <= 6) {
                return anchor;
            }
        }
        return null;
    }

    /** The lit anchor to bring a player back to: the one closest to the Nexus end of the causeway that is lit, else the Nexus. */
    public static int[] checkpoint(Predicate<String> lit) {
        AstralLayout.Anchor best = null;
        for (AstralLayout.Anchor anchor : AstralLayout.ANCHORS) {
            if (lit.test(anchor.id())) {
                best = anchor;
            }
        }
        return best == null ? new int[] {AstralLayout.NEXUS[0], AstralLayout.NEXUS[1] + 1, AstralLayout.NEXUS[2]}
            : new int[] {best.x(), best.y(), best.z()};
    }

    /** Unit-less step of the guided lift: the velocity (blocks per tick) from {@code from} toward {@code to}. */
    public static double[] lift(double[] from, double[] to, double speed) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) {
            return new double[] {0, 0, 0};
        }
        double k = Math.min(speed, len) / len;
        return new double[] {dx * k, dy * k, dz * k};
    }

    /** True when the lift is over: close enough to the target. */
    public static boolean arrived(double[] from, double[] to) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }

    /** The synapse pad under (or beside) this point (relative), or null. */
    public static AstralLayout.Synapse synapseAt(double x, double y, double z) {
        for (AstralLayout.Synapse synapse : AstralLayout.SYNAPSES) {
            if (Math.hypot(x - synapse.x() - 0.5, z - synapse.z() - 0.5) <= AstralLayout.SYNAPSE_TRIGGER + 0.5
                    && y >= synapse.y() - 1.2 && y <= synapse.y() + 0.6) {
                return synapse;
            }
        }
        return null;
    }
}
