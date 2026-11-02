package win.vampyuyu.survival.classes;

import java.util.function.DoublePredicate;

/** Numbers and small pure rules of the Novice abilities (GDD 11.2). */
public final class ClassRules {
    /** Bulwark: damage multiplier and duration. */
    public static final double BULWARK_MULTIPLIER = 0.70;
    public static final long BULWARK_MILLIS = 6000;
    /** Vanguard passive: damage multiplier near an ally, and what near means. */
    public static final double ALLY_MULTIPLIER = 0.95;
    public static final double ALLY_RADIUS = 8;
    /** Rift Step: distance of the dash and how long its Surge waits for a hit. */
    public static final double RIFT_BLOCKS = 6;
    public static final long RIFT_SURGE_MILLIS = 10_000;
    /** Duelist passive: a Surge every this many hits (5 for everyone else). */
    public static final int DUELIST_SURGE_EVERY = 4;
    /** Elemental Burst: radius and share of the weapon damage. */
    public static final double BURST_RADIUS = 5;
    public static final double BURST_SHARE = 1.0;
    /** Channeler passive: elemental weakness bonus (Attunement points, 10 = +10%). */
    public static final double CHANNELER_ATTUNEMENT = 10;
    /** Gale Volley: arrows, share of the bow damage of each one, and the spread between them in degrees. */
    public static final int VOLLEY_ARROWS = 3;
    public static final double VOLLEY_SHARE = 0.40;
    public static final double VOLLEY_SPREAD_DEGREES = 6;
    /** Ranger passive: speed bonus in combat, how long it lasts, and the arrow speed factor (about +5 blocks of range). */
    public static final double RANGER_SPEED = 0.10;
    public static final long RANGER_COMBAT_MILLIS = 5000;
    public static final double RANGER_ARROW_SPEED = 1.06;

    private ClassRules() {}

    /** Damage left after Bulwark and the ally passive (they multiply, both can apply at once). */
    public static double damageTaken(double damage, boolean bulwark, boolean nearAlly) {
        double result = damage;
        if (bulwark) {
            result *= BULWARK_MULTIPLIER;
        }
        if (nearAlly) {
            result *= ALLY_MULTIPLIER;
        }
        return result;
    }

    /**
     * How far Rift Step carries: the longest distance up to {@code max} blocks, in half block steps, that is free
     * all the way (the first obstruction stops the dash in front of it). {@code freeAt} tells whether the player fits
     * at a distance along the look direction.
     */
    public static double riftDistance(double max, DoublePredicate freeAt) {
        double best = 0;
        for (double d = 0.5; d <= max + 1e-9; d += 0.5) {
            if (!freeAt.test(d)) {
                break;
            }
            best = d;
        }
        return best;
    }
}
