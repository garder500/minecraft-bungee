package win.vampyuyu.survival.quarry;

import java.util.List;
import org.bukkit.Material;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.Rarity;

/** The custom items of the Quarry. Pack models live under {@code vampyuyu:ore/*}, {@code forge/*}, {@code tool/*}. */
final class QuarryItems {
    static final String BRONZITE = "bronzite";
    static final String BRONZE_CORE = "bronze_core";
    static final String FORGE = "forge";
    static final String BRONZE_CLEAVER = "bronze_cleaver";
    static final String BRONZE_PICK = "bronze_pick";
    static final String FROSTIUM = "frostium";
    static final String SUNSTONE = "sunstone";
    static final String EMBER_CORE = "ember_core";
    static final String RIMEBLADE = "rimeblade";
    static final String RIME_PICK = "rime_pick";
    static final String VOID_LENS = "void_lens";
    static final String VOIDGLASS = "voidglass";
    static final String STARCORE = "starcore";
    /** PDC key (long) read from the held pickaxe: mining luck. */
    static final String MINING_LUCK = "mining_luck";

    private QuarryItems() {}

    private static CustomItemDef skinToken(Skin skin) {
        Rarity rarity = switch (skin.rarity) {
            case "Common" -> Rarity.COMMON;
            case "Rare" -> Rarity.RARE;
            case "Epic" -> Rarity.EPIC;
            default -> Rarity.MYTHIC;
        };
        return CustomItemDef.builder(skin.tokenId(), skin.title + " Skin", rarity, Material.PAPER)
            .itemModel(skin.model()).type("SKIN").glint(true).pdc("untradable", 1L)
            .lore("<gray>Right-click to unlock this sword skin", "<gray>for your account. Bound to you.").build();
    }

    static List<String> register(Items items) {
        List<CustomItemDef> defs = List.of(
            CustomItemDef.builder(BRONZITE, "Bronzite", Rarity.UNCOMMON, Material.RAW_COPPER)
                .itemModel("vampyuyu:ore/bronzite").type("ORE")
                .lore("<gray>A warm, brassy ore hidden in", "<gray>cobble from your generator.").build(),
            CustomItemDef.builder(BRONZE_CORE, "Bronze Core", Rarity.UNCOMMON, Material.AMETHYST_SHARD)
                .itemModel("vampyuyu:ore/bronze_core").type("UPGRADE").glint(true)
                .lore("<gray>Right-click on your island to", "<gray>upgrade your cobble generator", "<gray>to tier II (Vein).").build(),
            CustomItemDef.builder(FORGE, "Forge", Rarity.UNCOMMON, Material.BLAST_FURNACE)
                .itemModel("vampyuyu:forge/forge").type("MACHINE")
                .lore("<gray>Place it on your island and", "<gray>right-click it to forge gear.").build(),
            CustomItemDef.builder(BRONZE_CLEAVER, "Bronze Cleaver", Rarity.UNCOMMON, Material.IRON_SWORD)
                .itemModel("vampyuyu:weapon/bronze_cleaver").type("SWORD")
                .lore("<gray>Rend: <white>hits make the target", "<gray>bleed for 3 seconds.").build(),
            CustomItemDef.builder(BRONZE_PICK, "Bronze Pick", Rarity.UNCOMMON, Material.IRON_PICKAXE)
                .itemModel("vampyuyu:tool/bronze_pick").type("PICKAXE")
                .pdc(MINING_LUCK, 10L)
                .lore("<gray>Mining Luck: <green>+10", "<gray>Better odds in your generator.").build(),
            CustomItemDef.builder(FROSTIUM, "Frostium", Rarity.RARE, Material.PRISMARINE_CRYSTALS)
                .itemModel("vampyuyu:ore/frostium").type("ORE")
                .lore("<gray>A pale blue ore, cold to the touch.", "<gray>Found in tier II generators.").build(),
            CustomItemDef.builder(SUNSTONE, "Sunstone", Rarity.EPIC, Material.GLOWSTONE_DUST)
                .itemModel("vampyuyu:ore/sunstone").type("GEM")
                .lore("<gray>A gem that holds the light of noon.", "<gray>Found in tier III generators.").build(),
            CustomItemDef.builder(EMBER_CORE, "Ember Core", Rarity.RARE, Material.FIRE_CHARGE)
                .itemModel("vampyuyu:ore/ember_core").type("UPGRADE").glint(true)
                .lore("<gray>Right-click on your island to", "<gray>upgrade your cobble generator", "<gray>to tier III (Ember).").build(),
            CustomItemDef.builder(RIMEBLADE, "Rimeblade", Rarity.RARE, Material.IRON_SWORD)
                .itemModel("vampyuyu:weapon/rimeblade").type("SWORD")
                .lore("<gray>Frostbite: <white>hits slow the target", "<gray>for 2 seconds.").build(),
            CustomItemDef.builder(RIME_PICK, "Rime Pick", Rarity.RARE, Material.IRON_PICKAXE)
                .itemModel("vampyuyu:tool/rime_pick").type("PICKAXE")
                .pdc(MINING_LUCK, 25L)
                .lore("<gray>Mining Luck: <green>+25", "<gray>Better odds in your generator.").build(),
            skinToken(Skin.KITSUNE), skinToken(Skin.TITAN_EDGE), skinToken(Skin.CRYSTAL_SABER),
            skinToken(Skin.FLAME_BRAND), skinToken(Skin.FROST_FANG), skinToken(Skin.WYRMFANG),
            CustomItemDef.builder(VOIDGLASS, "Voidglass", Rarity.LEGENDARY, Material.ECHO_SHARD)
                .itemModel("vampyuyu:ore/voidglass").type("GEM")
                .lore("<gray>Glass that remembers the dark.", "<gray>Found in tier IV generators.").build(),
            CustomItemDef.builder(STARCORE, "Starcore Shard", Rarity.MYTHIC, Material.NETHER_STAR)
                .itemModel("vampyuyu:ore/starcore").type("GEM").glint(true)
                .lore("<gray>A fragment of a fallen star.", "<gray>Only geodes of tier IV hold one.").build(),
            CustomItemDef.builder(VOID_LENS, "Void Lens", Rarity.EPIC, Material.ENDER_EYE)
                .itemModel("vampyuyu:ore/void_lens").type("KEY").glint(true)
                .lore("<gray>Needs a tier III generator.", "<gray>Right-click on your island to open", "<gray>your <light_purple>End <gray>and the tier IV generator.").build(),
            CustomItemDef.builder("cable", "Copper Cable", Rarity.COMMON, Material.LIGHTNING_ROD)
                .itemModel("vampyuyu:machine/cable").type("MACHINE")
                .lore("<gray>Touching machines share Flux.", "<gray>Place it between them.").build(),
            CustomItemDef.builder("solar_panel", "Solar Panel", Rarity.RARE, Material.DAYLIGHT_DETECTOR)
                .itemModel("vampyuyu:machine/solar_panel").type("MACHINE")
                .lore("<gray>Makes <white>6 Flux/s <gray>in daylight,", "<gray>with the sky open above it.").build(),
            CustomItemDef.builder("battery", "Battery", Rarity.UNCOMMON, Material.WAXED_COPPER_BLOCK)
                .itemModel("vampyuyu:machine/battery").type("MACHINE")
                .lore("<gray>Stores Flux. Capacity: <white>10,000").build(),
            CustomItemDef.builder("coal_generator", "Coal Generator", Rarity.UNCOMMON, Material.STONECUTTER)
                .itemModel("vampyuyu:machine/coal_generator").type("MACHINE")
                .lore("<gray>Right-click it holding coal.", "<gray>Makes <white>10 Flux/s <gray>while it burns.").build(),
            CustomItemDef.builder("miner_1", "Miner I", Rarity.UNCOMMON, Material.LODESTONE)
                .itemModel("vampyuyu:machine/miner_1").type("MACHINE")
                .lore("<gray>Breaks the generator cobble next to it", "<gray>and fills the chest next to it.",
                    "<gray>Uses <white>8 Flux/s<gray>, <white>1 block per 2 s<gray>.").build(),
            CustomItemDef.builder("miner_2", "Miner II", Rarity.RARE, Material.LODESTONE)
                .itemModel("vampyuyu:machine/miner_2").type("MACHINE").glint(true)
                .lore("<gray>Breaks the generator cobble next to it", "<gray>and fills the chest next to it.",
                    "<gray>Uses <white>20 Flux/s<gray>, <white>1 block per second<gray>.").build());
        defs.forEach(items::register);
        return defs.stream().map(CustomItemDef::id).toList();
    }
}
