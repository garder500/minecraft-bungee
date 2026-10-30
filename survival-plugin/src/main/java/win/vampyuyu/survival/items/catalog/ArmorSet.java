package win.vampyuyu.survival.items.catalog;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import win.vampyuyu.survival.api.Stat;

/**
 * One armor set: four pieces in three tiers, plus a full-set bonus (needs four pieces of the same set; tiers may be
 * mixed, the bonus uses the lowest tier worn).
 *
 * @param id lower case id used in item ids ({@code <id>_helmet_t1})
 * @param name display name of the set ("Miner")
 * @param material material prefix of the pieces (IRON, LEATHER...)
 * @param pieceStats base stats of a chestplate at tier 1; other slots scale with {@link #slotWeight}
 * @param bonusName name of the full set bonus
 * @param bonusLines MiniMessage description of the bonus
 * @param bonusStats stats granted by the bonus at tier 1 (multiplied by the tier factor)
 */
public record ArmorSet(String id, String name, String material, Map<Stat, Double> pieceStats, String bonusName,
        List<String> bonusLines, Map<Stat, Double> bonusStats) {

    /** Stat multiplier per tier (1-based). */
    public static double tierFactor(int tier) {
        return switch (tier) {
            case 1 -> 1.0;
            case 2 -> 2.1;
            default -> 3.6;
        };
    }

    /** Share of a chestplate's stats a slot gets. */
    public static double slotWeight(String slot) {
        return switch (slot) {
            case "helmet" -> 0.8;
            case "chestplate" -> 1.2;
            case "leggings" -> 1.0;
            default -> 0.7;
        };
    }

    /** Vanilla material of a piece, e.g. IRON + helmet gives IRON_HELMET. */
    public Material pieceMaterial(String slot) {
        return Material.valueOf(material + "_" + slot.toUpperCase(java.util.Locale.ROOT));
    }
}
