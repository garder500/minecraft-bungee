package win.vampyuyu.survival.quarry;

import java.util.Map;
import org.bukkit.Material;

/** The Quarry's content, read from the content database by {@link QuarryRepo}. */
public final class QuarryConfig {
    /** The cobble draw of the island generator. */
    public final OreRoller roller;
    /** The ores of the ring generators' blocks (Frost, Ember). */
    public final OreRoller outputs;
    public final int tiers;
    public final Map<String, ForgeRecipe> recipes;
    public final Map<String, Integer> forgeBlockRecipe;
    public final int forgeSlots;
    final MachineSettings machines;

    QuarryConfig(OreRoller roller, OreRoller outputs, int tiers, Map<String, ForgeRecipe> recipes,
            Map<String, Integer> forgeBlockRecipe, int forgeSlots, MachineSettings machines) {
        this.roller = roller;
        this.outputs = outputs;
        this.tiers = tiers;
        this.recipes = recipes;
        this.forgeBlockRecipe = forgeBlockRecipe;
        this.forgeSlots = forgeSlots;
        this.machines = machines;
    }

    /** A vanilla material name or custom:<id>. Custom ids are checked when used (they register later). */
    static boolean validItem(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return key.startsWith("custom:") ? key.length() > 7 : Material.matchMaterial(key) != null;
    }
}
