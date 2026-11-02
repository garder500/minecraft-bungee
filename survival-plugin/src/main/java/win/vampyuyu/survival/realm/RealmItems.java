package win.vampyuyu.survival.realm;

import org.bukkit.Material;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;

/** The local Realm resources. Pack models: {@code vampyuyu:ore/cinderheart}, {@code vampyuyu:ore/heartwood},
 * {@code vampyuyu:ore/soul_ash}, {@code vampyuyu:ore/stasis_crystal}, {@code vampyuyu:ore/rot_resin}. */
final class RealmItems {
    static final String CINDERHEART = "cinderheart";
    static final String HEARTWOOD = "heartwood";
    static final String SOUL_ASH = "soul_ash";
    static final String ROT_RESIN = "rot_resin";
    static final String STASIS_CRYSTAL = "stasis_crystal";

    private RealmItems() {}

    static void register(Items items) {
        items.register(CustomItemDef.builder(CINDERHEART, "Cinderheart", Rarity.EPIC, Material.MAGMA_CREAM)
            .itemModel("vampyuyu:ore/cinderheart").type("GEM").glint(true).pdc("realm_resource", 1L)
            .lore("<gray>A heart of cooled obsidian that still burns.", "<gray>Found only in the <gold>Cracked Ashes<gray>.",
                "<dark_gray>Runecraft: makes the Scorch rune.").build());
        items.register(CustomItemDef.builder(HEARTWOOD, "Heartwood", Rarity.EPIC, Material.DARK_OAK_LOG)
            .itemModel("vampyuyu:ore/heartwood").type("GEM").glint(true).pdc("realm_resource", 1L)
            .lore("<gray>Living wood that murmurs when held.", "<gray>Found only in the <green>Whispering Grove<gray>.",
                "<dark_gray>Runecraft: makes the Verdant rune.").build());
        items.register(CustomItemDef.builder(SOUL_ASH, "Soul Ash", Rarity.EPIC, Material.SOUL_SAND)
            .itemModel("vampyuyu:ore/soul_ash").type("GEM").glint(true).pdc("realm_resource", 1L)
            .lore("<gray>What is left of a memory once it is let go.", "<gray>Found only in the <dark_purple>Ethereal Necropolis<gray>.",
                "<dark_gray>Forge: makes the Memory Module.").build());
        items.register(CustomItemDef.builder(STASIS_CRYSTAL, "Stasis Crystal", Rarity.EPIC, Material.AMETHYST_SHARD)
            .itemModel("vampyuyu:ore/stasis_crystal").type("GEM").glint(true).pdc("realm_resource", 1L)
            .lore("<gray>A crystal that holds a moment still.", "<gray>Found only in the <aqua>Frozen Heights<gray>.",
                "<dark_gray>Runecraft: makes the Stasis rune.").build());
        items.register(CustomItemDef.builder(ROT_RESIN, "Rot Resin", Rarity.EPIC, Material.HONEYCOMB)
            .itemModel("vampyuyu:ore/rot_resin").type("GEM").glint(true).pdc("realm_resource", 1L)
            .lore("<gray>Sap of a tree the Devourer left to rot.", "<gray>Found only in the <dark_green>Putrid Marshes<gray>.",
                "<dark_gray>Forge: Recycler Module.").build());
    }
}
