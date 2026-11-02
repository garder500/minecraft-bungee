package win.vampyuyu.survival.depths;

/**
 * The rules of the Abyssal Depths (GDD 3.1, 12.5) as pure functions: the pressure of the water, the breath it costs,
 * the irregular gravity and what a fishing rod brings besides the local resource. Nothing here kills on its own: the
 * pressure never takes the player under {@link #FLOOR}.
 */
public final class DepthsMath {
    /** Water deeper than this (blocks under the surface) is pressure tier 1. */
    public static final int SHALLOW = 6;
    /** Water deeper than this is pressure tier 2. */
    public static final int MID = 11;
    /** Pressure resistance needed per tier. A Respiration III helmet gives 30, a Conduit Power effect ignores it all. */
    public static final int[] REQUIRED = {0, 20, 45};
    /** Resistance gained for good once the three pumps are running (Pressure Attunement). */
    public static final int ATTUNEMENT = 20;
    /** Resistance of one level of Respiration on the helmet. */
    public static final int RESPIRATION_LEVEL = 10;
    /** PDC key (double) on a custom item: pressure resistance it adds while worn (Frostium and Abyss gear sets it). */
    public static final String ITEM_KEY = "pressure_resist";
    /** Health a pressure tick never takes the player below (half hearts). */
    public static final double FLOOR = 2.0;
    /** Seconds between two pulses of a drift zone, and how long one lifts (GDD 12.5: a telegraphed, controllable lift). */
    public static final int DRIFT_PERIOD_SECONDS = 8;
    public static final int DRIFT_LIFT_SECONDS = 2;

    private DepthsMath() {}

    /** Pressure tier for a depth under the surface; a running pump of the dome takes one tier off. */
    public static int tier(int depth, boolean pumped) {
        int base = depth <= SHALLOW ? 0 : depth <= MID ? 1 : 2;
        return pumped ? Math.max(0, base - 1) : base;
    }

    public static int required(int tier) {
        return REQUIRED[Math.max(0, Math.min(2, tier))];
    }

    /** Resistance of a worn helmet from its material alone (the turtle shell is made for the sea). */
    public static int helmetPoints(String material) {
        return "TURTLE_HELMET".equals(material) ? 10 : 0;
    }

    /** Resistance of the Respiration enchantment on the helmet. */
    public static int respirationPoints(int level) {
        return Math.max(0, level) * RESPIRATION_LEVEL;
    }

    public static boolean hurts(int tier, double resistance) {
        return required(tier) > resistance;
    }

    /** Health to take this tick (0 when resistant enough). Grows with the missing resistance, at most 6. */
    public static double damage(int tier, double resistance) {
        double missing = required(tier) - resistance;
        if (missing <= 0) {
            return 0;
        }
        return Math.min(6.0, 1.0 + missing / 15.0);
    }

    /** Health left after the damage, never under {@link #FLOOR}; unchanged when already below it. */
    public static double healthAfter(double health, double damage) {
        if (health <= FLOOR) {
            return health;
        }
        return Math.max(FLOOR, health - damage);
    }

    /** Air ticks the depth takes away besides vanilla's own use, per pressure pulse (every 2 s). Respiration spares it. */
    public static int airDrain(int tier, int respirationLevel) {
        return Math.max(0, 30 * tier - 15 * Math.max(0, respirationLevel));
    }

    /** True while a drift zone lifts: the first seconds of every period. */
    public static boolean drifting(long ticks) {
        return (ticks / 20) % DRIFT_PERIOD_SECONDS < DRIFT_LIFT_SECONDS;
    }

    /**
     * What a catch brings besides the fish, from a roll in [0, 1): a Material name, or null for nothing. Odds are
     * guesses to measure in play; the Pressure Pearl has its own pity curve and is not part of this table.
     */
    public static String extraCatch(double roll) {
        if (roll < 0.15) {
            return "PRISMARINE_SHARD";
        }
        if (roll < 0.25) {
            return "PRISMARINE_CRYSTALS";
        }
        if (roll < 0.29) {
            return "NAUTILUS_SHELL";
        }
        if (roll < 0.30) {
            return "HEART_OF_THE_SEA";
        }
        return null;
    }
}
