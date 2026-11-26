package win.vampyuyu.survival.bastion;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The fighting ground of a Bastion: a round platform in the void with rune rings, a low rim, eight pylons, a crown of
 * colossal ruined towers floating round it, a keel, and a rune ring turning overhead. Built once per instance from code
 * (no schematic). The top of the floor is at {@link #FLOOR_Y}; everybody stands at {@link #FLOOR_Y} + 1.
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
            case "rift" -> new Material[] {Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.PURPUR_BLOCK,
                Material.AMETHYST_BLOCK};
            case "storm" -> new Material[] {Material.WAXED_OXIDIZED_CUT_COPPER, Material.LIGHT_GRAY_CONCRETE, Material.ANDESITE,
                Material.WAXED_WEATHERED_CUT_COPPER};
            case "singularity" -> new Material[] {Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.DEEPSLATE_TILES,
                Material.PURPUR_BLOCK};
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
            case "singularity" -> Material.PURPUR_PILLAR;
            case "rift" -> Material.PURPUR_PILLAR;
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
            case "singularity" -> Material.CRYING_OBSIDIAN;
            case "rift" -> Material.SEA_LANTERN;
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
            case "singularity" -> Material.END_STONE_BRICK_WALL;
            case "rift" -> Material.END_ROD;
            default -> Material.BLACKSTONE_WALL;
        };
    }

    /** The glow of a palette: inlaid in the floor's rune rings and on the towers. */
    static Material glow(String palette) {
        return switch (palette) {
            case "grove" -> Material.SHROOMLIGHT;
            case "marsh" -> Material.OCHRE_FROGLIGHT;
            case "necro" -> Material.VERDANT_FROGLIGHT;
            case "frost", "abyss" -> Material.SEA_LANTERN;
            case "citadel" -> Material.GLOWSTONE;
            case "storm" -> Material.SEA_LANTERN;
            case "singularity", "rift" -> Material.CRYING_OBSIDIAN;
            default -> Material.MAGMA_BLOCK;
        };
    }

    /** Tint of the rune ring turning over the arena, by palette. */
    static int ringColor(String palette) {
        return switch (palette) {
            case "grove" -> 0x8CFF5A;
            case "marsh" -> 0xC0A040;
            case "necro" -> 0x4FF0D6;
            case "frost" -> 0x8FE3FF;
            case "citadel" -> 0xFFD84A;
            case "abyss" -> 0x4F7DFF;
            case "storm" -> 0xC27BFF;
            case "singularity", "rift" -> 0xD060FF;
            default -> 0xFF8A2A;
        };
    }

    /** Height of the ring over the floor. */
    static final int RING_Y = 30;
    /** How far out of the floor's edge the towers float, and how many. */
    static final int TOWER_GAP = 9;
    static final int TOWERS = 8;
    /** Radius of the keel under the floor: never more than the Raid's core, so nothing floats when the rings fall. */
    static final int KEEL_RADIUS = RaidRules.CORE_RADIUS;

    /**
     * Places the whole arena. Main thread. The fighting floor stays flat (bosses snap to it): concentric bands, two rings
     * of glowing runes and a sigil at the centre. Eight low pylons stand inside the rim. Out of reach round the floor,
     * eight colossal ruined towers float in a crown (each broken at its own height, a glow core, a keel), under the
     * floor a keel, and over it all a rune ring in the Realm's colour turns slowly (a display model, see {@link #models}).
     */
    public static void build(World w, String palette, int radius) {
        Material[] m = floor(palette);
        Material glow = glow(palette);
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
                // two rings of runes: a glowing stone every few blocks on a band of the edge material
                for (double ring : new double[] {radius * 0.33, radius * 0.66}) {
                    if (Math.abs(d - ring) < 0.5) {
                        double a = Math.toDegrees(Math.atan2(z, x));
                        top = ((int) Math.floor(a / 15) & 1) == 0 ? glow : m[3];
                    }
                }
                if (d <= 2.5) {
                    top = d <= 1 ? glow : m[3]; // the sigil where the Guardian stands
                } else if (d <= 5.5 && (Math.abs(x) == Math.abs(z) || x == 0 || z == 0)) {
                    top = m[3];
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
                set(w, px, FLOOR_Y + y, pz, y == 4 ? glow : pillar(palette));
            }
            set(w, px, FLOOR_Y + 7, pz, cap(palette));
        }
        keel(w, m, glow);
        for (int i = 0; i < TOWERS; i++) {
            tower(w, palette, m, glow, radius, i);
        }
        lights(w, radius);
    }

    /** A hollow inverted cone under the centre of the floor. */
    private static void keel(World w, Material[] m, Material glow) {
        int depth = 22;
        for (int k = 3; k <= depth; k++) {
            double r = KEEL_RADIUS * Math.pow(1 - (double) (k - 2) / (depth - 1), 0.9);
            int ri = (int) Math.ceil(r);
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    double d = Math.sqrt((double) x * x + (double) z * z);
                    if (d <= r && (d > r - 1.6 || r < 1.6)) {
                        set(w, x, FLOOR_Y - k, z, ((x * 7 + z * 13 + k) % 9 == 0) ? glow : k > 12 ? m[3] : m[2]);
                    }
                }
            }
        }
    }

    /** One of the colossal ruined towers floating round the arena. */
    private static void tower(World w, String palette, Material[] m, Material glow, int radius, int i) {
        double a = Math.PI * 2 * i / TOWERS;
        int cx = (int) Math.round(Math.cos(a) * (radius + TOWER_GAP));
        int cz = (int) Math.round(Math.sin(a) * (radius + TOWER_GAP));
        int base = FLOOR_Y - 6 - (i * 5) % 7;
        int height = 22 + (i * 11) % 15;
        double tr = 3.2;
        for (int y = 0; y <= height; y++) {
            // broken top: the ring of the tower thins out in its last blocks
            int cut = (i * 7 + y * 3) % 8;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    double d = Math.sqrt((double) x * x + (double) z * z);
                    if (d > tr || d < tr - 1.3) {
                        continue;
                    }
                    if (y > height - 5 && ((x * 3 + z * 5 + cut) & 3) == 0) {
                        continue;
                    }
                    Material mat = y % 7 == 0 ? m[3] : (y % 11 == 5 && ((x + z) & 1) == 0) ? glow : pillar(palette);
                    if (mat == Material.END_ROD || mat == Material.LIGHTNING_ROD) {
                        mat = m[3];
                    }
                    set(w, cx + x, base + y, cz + z, mat);
                }
            }
        }
        set(w, cx, base + height / 2, cz, glow);
        // the keel under the tower
        for (int k = 1; k <= 8; k++) {
            double r = tr * (1 - k / 9.0);
            int ri = (int) Math.ceil(r);
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    if (Math.sqrt((double) x * x + (double) z * z) <= r) {
                        set(w, cx + x, base - k, cz + z, m[2]);
                    }
                }
            }
        }
    }

    /** Invisible light over the floor, so the arena never fights in the dark. */
    private static void lights(World w, int radius) {
        for (int x = -radius; x <= radius; x += 8) {
            for (int z = -radius; z <= radius; z += 8) {
                if (Math.sqrt((double) x * x + (double) z * z) < radius - 2) {
                    set(w, x, FLOOR_Y + 9, z, Material.LIGHT);
                }
            }
        }
    }

    /** The rune ring turning over the arena: the Quai's lit halo, laid flat, in the Realm's colour. */
    static java.util.List<win.vampyuyu.survival.island.BlockPlan.Model> models(String palette, int radius) {
        float scale = Math.max(6f, radius * 0.42f);
        return java.util.List.of(win.vampyuyu.survival.island.BlockPlan.Model.of("structure/quai_halo_lit", 0.5,
            FLOOR_Y + RING_Y, 0.5, scale).tilt(90, 0, 0).spin(6, "z").color(ringColor(palette)));
    }

    private static final java.util.Map<String, win.vampyuyu.survival.island.StructureModels> RINGS = new java.util.HashMap<>();

    /** Keeps the ring of a Bastion instance turning while its world is up (called by the service's tick). */
    static void tickModels(org.bukkit.plugin.Plugin plugin, World w, String palette, int radius, int periodTicks) {
        RINGS.keySet().removeIf(name -> org.bukkit.Bukkit.getWorld(name) == null);
        RINGS.computeIfAbsent(w.getName(), k -> new win.vampyuyu.survival.island.StructureModels(plugin, "bastion_ring", w,
            0, 0, 0, models(palette, radius))).tick(periodTicks);
    }

    /** True when the column (x, z) belongs to the ring of the floor that falls when the arena shrinks to {@code radius}. */
    static boolean inRing(int x, int z, int radius) {
        return Math.round(Math.sqrt((double) x * x + (double) z * z)) == radius;
    }

    /**
     * Phase III of the Raid (GDD 4.6): the outermost ring of the platform falls away, with whatever stood on it (rim and
     * pillars included). Returns how many blocks were removed. Main thread.
     */
    public static int collapseRing(World w, int radius) {
        int removed = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!inRing(x, z, radius)) {
                    continue;
                }
                for (int y = FLOOR_Y - 2; y <= FLOOR_Y + 9; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    private static void set(World w, int x, int y, int z, Material material) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(material, false);
    }
}
