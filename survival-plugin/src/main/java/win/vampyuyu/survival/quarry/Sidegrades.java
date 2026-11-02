package win.vampyuyu.survival.quarry;

import java.util.Collection;
import java.util.List;
import win.vampyuyu.survival.api.Realm;

/**
 * The Starcore sidegrades (GDD 9.2): one variant of the tier 5 armor per opened Realm. Same total of stat points as
 * Starcore (the pieces are {@code item_def} rows, see {@code scripts/gen_sidegrades.py}), redistributed, plus an
 * affinity that is not a stat. Nothing here is a Tier 6. Pure functions; the effects are in {@link SidegradeListener}.
 */
public final class Sidegrades {
    /** Cracked Ashes variant: heat resistance on every piece, fire and lava hurt half as much with four pieces. */
    public static final String CINDER = "cinder_starcore";
    /** Whispering Grove variant: the helmet breathes under water, four pieces mend out of combat. */
    public static final String GROVE = "grove_starcore";

    /** Frozen Heights variant: cold resistance on every piece, four pieces make frost harmless and falls hurt half as much. */
    public static final String FROST = "frost_starcore";

    public static final List<String> SETS = List.of(CINDER, GROVE, FROST);
    /** Armor slots as they appear at the end of an item id. */
    public static final List<String> SLOTS = List.of("helmet", "chestplate", "leggings", "boots");

    private Sidegrades() {}

    /** The Realm that has to be open to forge a piece of this set, or null when the item is not a sidegrade. */
    public static Realm realmOf(String itemId) {
        String set = setOf(itemId);
        if (set == null) {
            return null;
        }
        return set.equals(CINDER) ? Realm.ASHES : set.equals(GROVE) ? Realm.GROVE : Realm.HEIGHTS;
    }

    /** The sidegrade set an item id belongs to ({@code cinder_starcore_helmet} gives {@code cinder_starcore}), or null. */
    public static String setOf(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (String set : SETS) {
            for (String slot : SLOTS) {
                if (itemId.equals(set + "_" + slot)) {
                    return set;
                }
            }
        }
        return null;
    }

    /** True when {@code itemId} is the helmet of the given set. */
    public static boolean isHelmet(String itemId, String set) {
        return itemId != null && itemId.equals(set + "_helmet");
    }

    /**
     * The set worn when these item ids (the four armor slots, null for an empty or a vanilla slot, any order) are the
     * four different pieces of one sidegrade set; null otherwise.
     */
    public static String fullSet(Collection<String> wornIds) {
        if (wornIds.size() != 4) {
            return null;
        }
        for (String set : SETS) {
            boolean all = true;
            for (String slot : SLOTS) {
                all &= wornIds.contains(set + "_" + slot);
            }
            if (all) {
                return set;
            }
        }
        return null;
    }

    /** Damage multiplier of the Ashen Mantle for a fire-type hit (1 when the set is not worn or the hit is another kind). */
    public static double fireFactor(String wornSet, org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (!CINDER.equals(wornSet)) {
            return 1.0;
        }
        return switch (cause) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> 0.5;
            default -> 1.0;
        };
    }

    /**
     * Glacial Poise: damage multiplier of the Frost set for a hit (0 for freezing, 0.5 for a fall, 1 when the set is not
     * worn or the hit is another kind).
     */
    public static double poiseFactor(String wornSet, org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (!FROST.equals(wornSet)) {
            return 1.0;
        }
        return switch (cause) {
            case FREEZE -> 0.0;
            case FALL -> 0.5;
            default -> 1.0;
        };
    }

    /** Verdant Mending: true when the set mends (every {@code period} seconds) and the last damage is old enough. */
    public static boolean mends(String wornSet, long lastHurtMillis, long nowMillis) {
        return GROVE.equals(wornSet) && nowMillis - lastHurtMillis >= 8_000L;
    }
}
