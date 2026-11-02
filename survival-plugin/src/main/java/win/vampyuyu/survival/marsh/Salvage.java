package win.vampyuyu.survival.marsh;

import java.util.Map;
import org.bukkit.Material;

/** The pure rules of the Rot Vats (no Bukkit state): what counts as an expired item and how many Resin rolls it makes. */
public final class Salvage {
    /** Salvage points for 100 spoiled units, ordered by how rotten they are. */
    private static final Map<Material, Integer> POINTS = Map.of(
        Material.ROTTEN_FLESH, 1,
        Material.SPIDER_EYE, 1,
        Material.POISONOUS_POTATO, 1,
        Material.BONE, 1,
        Material.SLIME_BALL, 1,
        Material.PUFFERFISH, 2,
        Material.FERMENTED_SPIDER_EYE, 2,
        Material.SUSPICIOUS_STEW, 3);

    /** Points one unit of this material is worth in a Vat; 0 when the Vat refuses it. */
    public static int points(Material material) {
        return POINTS.getOrDefault(material, 0);
    }

    /** Points per Resin roll, by purification tier: the Reclaimer (tier 2) needs fewer. */
    public static int rollCost(int tier) {
        return tier >= 2 ? 6 : 8;
    }

    /** How many rolls a stock of points makes. */
    public static int rolls(int points, int tier) {
        return Math.max(0, points) / rollCost(tier);
    }

    /** What is left after the rolls were paid. */
    public static int carry(int points, int tier) {
        return Math.max(0, points) % rollCost(tier);
    }

    private Salvage() {}
}
