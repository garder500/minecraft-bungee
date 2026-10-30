package win.vampyuyu.survival.api;

import org.bukkit.Material;

/** The eight skills of the Skyblock. */
public enum Skill {
    COMBAT("Combat", Material.STONE_SWORD),
    MINING("Mining", Material.STONE_PICKAXE),
    FARMING("Farming", Material.WOODEN_HOE),
    FORAGING("Foraging", Material.JUNGLE_SAPLING),
    FISHING("Fishing", Material.FISHING_ROD),
    ENCHANTING("Enchanting", Material.ENCHANTING_TABLE),
    ALCHEMY("Alchemy", Material.BREWING_STAND),
    CARPENTRY("Carpentry", Material.CRAFTING_TABLE);

    private final String displayName;
    private final Material icon;

    Skill(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    /** Human readable English name. */
    public String displayName() {
        return displayName;
    }

    /** Material used as the skill's icon in menus. */
    public Material icon() {
        return icon;
    }

    /** Lower case key used in data files, e.g. "mining". */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
