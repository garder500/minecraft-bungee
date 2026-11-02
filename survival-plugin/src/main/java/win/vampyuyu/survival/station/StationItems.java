package win.vampyuyu.survival.station;

import org.bukkit.Material;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;

/** The custom items of the stations. They use vanilla models until the pack has art for them. */
final class StationItems {
    static final String ALEMBIC = "alembic";
    static final String CRUCIBLE = "crucible";
    static final String RANCH = "ranch";
    static final String MARROW = "marrow";
    /** The bottle every Alembic brew comes in; the recipe is in its PDC. */
    static final String FLASK = "alembic_flask";
    /** PDC key (string) of a flask: the Alembic recipe id. */
    static final String RECIPE_KEY = "alembic_recipe";

    private StationItems() {}

    static void register(Items items) {
        items.register(CustomItemDef.builder(ALEMBIC, "Alembic", Rarity.UNCOMMON, Material.BREWING_STAND).type("STATION")
            .lore("<gray>Brews tonics, oils and draughts", "<gray>while you do something else.", "<dark_gray>Place it on your island, right-click to use.").build());
        items.register(CustomItemDef.builder(CRUCIBLE, "Crucible", Rarity.UNCOMMON, Material.CAULDRON).type("STATION")
            .lore("<gray>Melts down outdated gear: 60% of the", "<gray>materials, boss components whole.", "<dark_gray>Place it on your island, right-click to use.").build());
        items.register(CustomItemDef.builder(RANCH, "Ranch", Rarity.UNCOMMON, Material.COMPOSTER).type("STATION")
            .lore("<gray>Wool, leather and marrow, even while", "<gray>you are away. Grows with your generator.", "<dark_gray>Place it on your island, right-click to use.").build());
        items.register(CustomItemDef.builder(MARROW, "Marrow", Rarity.UNCOMMON, Material.BONE_MEAL).type("MATERIAL")
            .lore("<gray>Rich marrow from the Ranch.", "<gray>Oils and draughts need it.").build());
        items.register(CustomItemDef.builder(FLASK, "Alembic Flask", Rarity.COMMON, Material.HONEY_BOTTLE).type("CONSUMABLE").build());
    }
}
