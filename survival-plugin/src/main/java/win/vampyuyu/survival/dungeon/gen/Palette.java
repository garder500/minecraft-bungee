package win.vampyuyu.survival.dungeon.gen;

import java.util.Locale;

/** Block set of a floor theme. Every field is a block data string. */
public record Palette(String name, String floor, String floorAlt, String wall, String wallAlt, String pillar,
        String ceiling, String slab, String fence, String wallTorch, String lantern, String accent) {

    public static Palette byName(String name) {
        return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "mossy" -> new Palette("mossy", "mossy_stone_bricks", "mossy_cobblestone", "mossy_stone_bricks",
                "stone_bricks", "chiseled_stone_bricks", "mossy_stone_bricks", "mossy_stone_brick_slab",
                "mossy_stone_brick_wall", "wall_torch", "lantern", "dark_oak_planks");
            case "deepslate" -> new Palette("deepslate", "deepslate_tiles", "cracked_deepslate_tiles", "deepslate_bricks",
                "cracked_deepslate_bricks", "chiseled_deepslate", "deepslate_bricks", "deepslate_brick_slab",
                "deepslate_brick_wall", "soul_wall_torch", "soul_lantern", "polished_deepslate");
            case "blackstone" -> new Palette("blackstone", "polished_blackstone_bricks",
                "cracked_polished_blackstone_bricks", "polished_blackstone_bricks", "gilded_blackstone",
                "chiseled_polished_blackstone", "blackstone", "polished_blackstone_brick_slab",
                "polished_blackstone_brick_wall", "soul_wall_torch", "soul_lantern", "polished_blackstone");
            case "nether" -> new Palette("nether", "nether_bricks", "cracked_nether_bricks", "nether_bricks",
                "red_nether_bricks", "chiseled_nether_bricks", "nether_bricks", "nether_brick_slab",
                "nether_brick_wall", "wall_torch", "lantern", "magma_block");
            case "crimson" -> new Palette("crimson", "crimson_planks", "stripped_crimson_stem", "red_nether_bricks",
                "crimson_planks", "stripped_crimson_hyphae", "red_nether_bricks", "red_nether_brick_slab",
                "red_nether_brick_wall", "soul_wall_torch", "soul_lantern", "crimson_stem");
            case "end" -> new Palette("end", "purpur_block", "end_stone_bricks", "end_stone_bricks", "purpur_block",
                "purpur_pillar", "end_stone_bricks", "purpur_slab", "end_stone_brick_wall", "soul_wall_torch",
                "soul_lantern", "crying_obsidian");
            default -> new Palette("stone_brick", "stone_bricks", "cracked_stone_bricks", "stone_bricks",
                "mossy_stone_bricks", "chiseled_stone_bricks", "stone_bricks", "stone_brick_slab", "stone_brick_wall",
                "wall_torch", "lantern", "polished_andesite");
        };
    }
}
