package win.vampyuyu.survival.dungeon;

import java.util.List;
import org.bukkit.Material;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;

/** The custom items of the dungeon: crafting drops from the reward chests and two consumables. */
public final class DungeonItems {
    public static final String BONE_FRAGMENT = "dungeon_bone_fragment";
    public static final String CRYPT_SHARD = "dungeon_crypt_shard";
    public static final String WITHER_ESSENCE = "dungeon_wither_essence";
    public static final String SPIRIT_DUST = "dungeon_spirit_dust";
    public static final String RELIC = "dungeon_relic";
    public static final String DARK_SCROLL = "dungeon_dark_scroll";
    public static final String SPIRIT_LEAF = "dungeon_spirit_leaf";
    public static final String HEALING_FLASK = "dungeon_healing_flask";

    private DungeonItems() {}

    /** Ids of every item this feature registers (the pack icons carry the same names without the prefix). */
    public static List<String> ids() {
        return List.of(BONE_FRAGMENT, CRYPT_SHARD, WITHER_ESSENCE, SPIRIT_DUST, RELIC, DARK_SCROLL, SPIRIT_LEAF, HEALING_FLASK);
    }

    private static String model(String id) {
        return "vampyuyu:dungeon/" + id.substring("dungeon_".length());
    }

    /** Registers the items; ones that already exist (a second enable in the same JVM) are left alone. */
    public static void register(Core core) {
        reg(core, CustomItemDef.builder(BONE_FRAGMENT, "Bone Fragment", Rarity.COMMON, Material.BONE)
            .itemModel(model(BONE_FRAGMENT)).type("MATERIAL")
            .lore("<gray>Splintered bone from the crypt.", "<dark_gray>Dropped by dungeon chests.").build());
        reg(core, CustomItemDef.builder(CRYPT_SHARD, "Crypt Shard", Rarity.UNCOMMON, Material.PRISMARINE_SHARD)
            .itemModel(model(CRYPT_SHARD)).type("MATERIAL")
            .lore("<gray>A cold shard that hums with", "<gray>the memory of the dead.").build());
        reg(core, CustomItemDef.builder(WITHER_ESSENCE, "Wither Essence", Rarity.RARE, Material.BLACK_DYE)
            .itemModel(model(WITHER_ESSENCE)).type("MATERIAL")
            .lore("<gray>Condensed wither magic.", "<dark_gray>Found from floor 2 on.").build());
        reg(core, CustomItemDef.builder(SPIRIT_DUST, "Spirit Dust", Rarity.EPIC, Material.GLOWSTONE_DUST)
            .itemModel(model(SPIRIT_DUST)).type("MATERIAL")
            .lore("<gray>Dust of a restless ghost.", "<dark_gray>Found from floor 4 on, rank A or better.").build());
        reg(core, CustomItemDef.builder(RELIC, "Ancient Relic", Rarity.LEGENDARY, Material.ECHO_SHARD)
            .itemModel(model(RELIC)).type("MATERIAL").glint(true)
            .lore("<gray>A relic of the first crypt kings.", "<dark_gray>Found from floor 5 on, rank S or better.").build());
        reg(core, CustomItemDef.builder(DARK_SCROLL, "Dark Scroll", Rarity.MYTHIC, Material.PAPER)
            .itemModel(model(DARK_SCROLL)).type("MATERIAL").glint(true)
            .lore("<gray>Written in a language no one", "<gray>alive can read.", "<dark_gray>Floor 7, rank S+.").build());
        reg(core, CustomItemDef.builder(SPIRIT_LEAF, "Spirit Leaf", Rarity.RARE, Material.FERN)
            .itemModel(model(SPIRIT_LEAF)).type("CONSUMABLE")
            .lore("<gray>Right-click in a dungeon to", "<green>instantly revive a ghost<gray> of your party.",
                "<dark_gray>Sold by the Crypt Merchant.").build());
        reg(core, CustomItemDef.builder(HEALING_FLASK, "Healing Flask", Rarity.UNCOMMON, Material.GLASS_BOTTLE)
            .itemModel(model(HEALING_FLASK)).type("CONSUMABLE")
            .lore("<gray>Right-click to restore a large part", "<gray>of your health.",
                "<dark_gray>Sold by the Crypt Merchant.").build());
    }

    private static void reg(Core core, CustomItemDef def) {
        if (core.items().def(def.id()).isEmpty()) {
            core.items().register(def);
        }
    }
}
