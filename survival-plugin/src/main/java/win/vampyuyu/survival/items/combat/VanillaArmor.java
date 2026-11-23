package win.vampyuyu.survival.items.combat;

import java.util.Locale;

/** The vanilla armor points and toughness of an armor material name (facts of the game, not tuning). */
final class VanillaArmor {
    private VanillaArmor() {
    }

    /** Armor points of one piece, from names like {@code IRON_CHESTPLATE}; 0 for anything else. */
    static double points(String material) {
        String m = material == null ? "" : material.toUpperCase(Locale.ROOT);
        int piece = m.endsWith("_HELMET") ? 0 : m.endsWith("_CHESTPLATE") ? 1 : m.endsWith("_LEGGINGS") ? 2
            : m.endsWith("_BOOTS") ? 3 : -1;
        if (piece < 0) {
            return 0;
        }
        int[] set = m.startsWith("LEATHER") ? new int[] {1, 3, 2, 1}
            : m.startsWith("CHAINMAIL") ? new int[] {2, 5, 4, 1}
            : m.startsWith("GOLDEN") ? new int[] {2, 5, 3, 1}
            : m.startsWith("IRON") ? new int[] {2, 6, 5, 2}
            : m.startsWith("DIAMOND") || m.startsWith("NETHERITE") ? new int[] {3, 8, 6, 3}
            : new int[] {0, 0, 0, 0};
        return set[piece];
    }

    /** Armor toughness of one piece: 2 for diamond, 3 for netherite, none for the rest. */
    static double toughness(String material) {
        String m = material == null ? "" : material.toUpperCase(Locale.ROOT);
        if (points(m) == 0) {
            return 0;
        }
        return m.startsWith("DIAMOND") ? 2 : m.startsWith("NETHERITE") ? 3 : 0;
    }
}
