package win.vampyuyu.survival.realm;

import org.bukkit.Material;

/**
 * The heat of the Cracked Ashes (GDD 3.1: heat resistance). Four tiers: 0 on the cooled landing, 1 on the islets and
 * bridges, 2 at the Ember Tarn, 3 on the caldera. A tier asks for a heat resistance; with less, the player takes
 * slow damage that never kills on its own. A Fire Resistance effect ignores the heat entirely. Pure functions.
 */
public final class HeatMath {
    /** Heat resistance needed per tier. A full netherite set gives 48: tier 2 is fine, tier 3 needs more. */
    public static final int[] REQUIRED = {0, 20, 45, 75};
    /** Resistance gained for good once the three vents are stabilised (Ashen Attunement). */
    public static final int ATTUNEMENT = 15;
    /** PDC key (double) on a custom item: heat resistance it adds while worn. Sunstone gear sets it. */
    public static final String ITEM_KEY = "heat_resist";
    /** Health a heat tick never takes the player below (half hearts). */
    public static final double FLOOR = 2.0;

    private HeatMath() {}

    /** Tier at a spot: the islet tier, one higher beside lava or magma (never on the cooled landing). */
    public static int tier(int baseTier, boolean besideLava) {
        if (baseTier <= 0) {
            return 0;
        }
        return Math.min(3, baseTier + (besideLava ? 1 : 0));
    }

    public static int required(int tier) {
        return REQUIRED[Math.max(0, Math.min(3, tier))];
    }

    /** Resistance of a worn armor piece from its material alone (custom items add {@link #ITEM_KEY} on top). */
    public static int armorPoints(Material material) {
        if (material == null) {
            return 0;
        }
        String name = material.name();
        if (!(name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"))) {
            return 0;
        }
        if (name.startsWith("NETHERITE_")) {
            return 12;
        }
        if (name.startsWith("GOLDEN_")) {
            return 8;
        }
        if (name.startsWith("DIAMOND_") || name.startsWith("COPPER_")) {
            return 8;
        }
        if (name.startsWith("IRON_")) {
            return 6;
        }
        if (name.startsWith("CHAINMAIL_")) {
            return 4;
        }
        if (name.startsWith("LEATHER_")) {
            return 2;
        }
        return 0;
    }

    /** Health to take this tick (0 when resistant enough). Grows with the missing resistance, at most 6. */
    public static double damage(int tier, double resistance) {
        double missing = required(tier) - resistance;
        if (missing <= 0) {
            return 0;
        }
        return Math.min(6.0, 1.0 + missing / 15.0);
    }

    /** True when the heat of this tier hurts at all with this resistance. */
    public static boolean hurts(int tier, double resistance) {
        return required(tier) > resistance;
    }

    /** Health left after the damage, never under {@link #FLOOR}; unchanged when already below it. */
    public static double healthAfter(double health, double damage) {
        if (health <= FLOOR) {
            return health;
        }
        return Math.max(FLOOR, health - damage);
    }
}
