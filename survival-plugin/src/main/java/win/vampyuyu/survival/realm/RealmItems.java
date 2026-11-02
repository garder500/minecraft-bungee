package win.vampyuyu.survival.realm;

import org.bukkit.Material;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;

/** The local Realm resources. Pack models: {@code vampyuyu:ore/cinderheart}, {@code vampyuyu:ore/heartwood}. */
final class RealmItems {
    static final String CINDERHEART = "cinderheart";
    static final String HEARTWOOD = "heartwood";

    private RealmItems() {}

    static void register(Items items) {
        items.register(CustomItemDef.builder(CINDERHEART, "Cinderheart", Rarity.EPIC, Material.MAGMA_CREAM)
            .itemModel("vampyuyu:ore/cinderheart").type("GEM").glint(true).pdc("realm_resource", 1L)
            .lore("<gray>A heart of cooled obsidian that still burns.", "<gray>Found only in the <gold>Cracked Ashes<gray>.",
                "<dark_gray>Sidegrades, modules and runes will use it.").build());
        items.register(CustomItemDef.builder(HEARTWOOD, "Heartwood", Rarity.EPIC, Material.DARK_OAK_LOG)
            .itemModel("vampyuyu:ore/heartwood").type("GEM").glint(true).pdc("realm_resource", 1L)
            .lore("<gray>Living wood that murmurs when held.", "<gray>Found only in the <green>Whispering Grove<gray>.",
                "<dark_gray>Sidegrades, modules and runes will use it.").build());
    }
}
