package win.vampyuyu.survival.bastion;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The fighting ground of a Bastion: a round platform in the void, a low rim and eight pillars. Built once per instance
 * from code (no schematic). The top of the floor is at {@link #FLOOR_Y}; everybody stands at {@link #FLOOR_Y} + 1.
 */
public final class BastionArena {
    public static final int FLOOR_Y = 99;
    /** Under this height a player has fallen off the platform. */
    public static final int VOID_Y = 80;

    private BastionArena() {}

    /** Where the Guardian stands. */
    public static Location centre(World w) {
        return new Location(w, 0.5, FLOOR_Y + 1, 0.5);
    }

    /** Where the party arrives: on the south edge, facing the Guardian. */
    public static Location arrival(World w, int radius) {
        return new Location(w, 0.5, FLOOR_Y + 1, radius - 6 + 0.5, 180f, 0f);
    }

    /** The blocks of the floor for a palette, as (centre ring, middle ring, outer ring, edge). */
    static Material[] floor(String palette) {
        return switch (palette) {
            case "grove" -> new Material[] {Material.MOSS_BLOCK, Material.GRASS_BLOCK, Material.PODZOL, Material.MUD_BRICKS};
            case "marsh" -> new Material[] {Material.SCULK, Material.MUD, Material.PACKED_MUD, Material.MUD_BRICKS};
            case "necro" -> new Material[] {Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.SOUL_SOIL,
                Material.CRACKED_DEEPSLATE_BRICKS};
            case "frost" -> new Material[] {Material.BLUE_ICE, Material.PACKED_ICE, Material.SNOW_BLOCK, Material.STONE_BRICKS};
            case "citadel" -> new Material[] {Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.SAND, Material.CHISELED_SANDSTONE};
            case "abyss" -> new Material[] {Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.DARK_PRISMARINE,
                Material.DARK_PRISMARINE};
            case "storm" -> new Material[] {Material.WAXED_OXIDIZED_CUT_COPPER, Material.LIGHT_GRAY_CONCRETE, Material.ANDESITE,
                Material.WAXED_WEATHERED_CUT_COPPER};
            default -> new Material[] {Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE, Material.BASALT,
                Material.CRACKED_POLISHED_BLACKSTONE_BRICKS};
        };
    }

    static Material pillar(String palette) {
        return switch (palette) {
            case "grove" -> Material.STRIPPED_DARK_OAK_LOG;
            case "marsh" -> Material.MANGROVE_LOG;
            case "necro" -> Material.BONE_BLOCK;
            case "frost" -> Material.PACKED_ICE;
            case "citadel" -> Material.CHISELED_SANDSTONE;
            case "abyss" -> Material.DARK_PRISMARINE;
            case "storm" -> Material.WAXED_COPPER_BLOCK;
            default -> Material.POLISHED_BASALT;
        };
    }

    static Material cap(String palette) {
        return switch (palette) {
            case "grove" -> Material.SHROOMLIGHT;
            case "marsh" -> Material.SOUL_LANTERN;
            case "necro" -> Material.SOUL_LANTERN;
            case "frost" -> Material.SEA_LANTERN;
            case "citadel" -> Material.GOLD_BLOCK;
            case "abyss" -> Material.SEA_LANTERN;
            case "storm" -> Material.LIGHTNING_ROD;
            default -> Material.GLOWSTONE;
        };
    }

    static Material rim(String palette) {
        return switch (palette) {
            case "grove" -> Material.MOSSY_STONE_BRICK_WALL;
            case "marsh" -> Material.MUD_BRICK_WALL;
            case "necro" -> Material.DEEPSLATE_BRICK_WALL;
            case "frost" -> Material.STONE_BRICK_WALL;
            case "citadel" -> Material.SANDSTONE_WALL;
            case "abyss" -> Material.PRISMARINE_WALL;
            case "storm" -> Material.IRON_BARS;
            default -> Material.BLACKSTONE_WALL;
        };
    }

    /** Places the whole arena. Main thread. */
    public static void build(World w, String palette, int radius) {
        Material[] m = floor(palette);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d > radius + 0.5) {
                    continue;
                }
                Material top = d > radius - 1.5 ? m[3] : d > radius * 0.66 ? m[2] : d > radius * 0.33 ? m[1] : m[0];
                if (((x * 31 + z * 17) & 7) == 0 && top != m[3]) {
                    top = m[1]; // a little variety
                }
                set(w, x, FLOOR_Y, z, top);
                set(w, x, FLOOR_Y - 1, z, m[1]);
                set(w, x, FLOOR_Y - 2, z, d < radius - 3 ? m[2] : Material.AIR);
                if (d > radius - 0.5 && d <= radius + 0.5 && ((x + z) & 1) == 0) {
                    set(w, x, FLOOR_Y + 1, z, rim(palette));
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(a) * (radius - 4));
            int pz = (int) Math.round(Math.sin(a) * (radius - 4));
            for (int y = 1; y <= 6; y++) {
                set(w, px, FLOOR_Y + y, pz, pillar(palette));
            }
            set(w, px, FLOOR_Y + 7, pz, cap(palette));
        }
    }

    private static void set(World w, int x, int y, int z, Material material) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(material, false);
    }
}
