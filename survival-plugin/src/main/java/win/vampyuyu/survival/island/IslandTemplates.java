package win.vampyuyu.survival.island;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Material;
import win.vampyuyu.survival.island.IslandConfig.ItemSpec;

/**
 * Code-generated overworld islands, the Nether platform and the End platform. All coordinates are relative to the
 * island centre: y = 0 is the top layer of the island (players stand at y = 1) and the spawn anchor is (0, 1, 0).
 *
 * <p>Layout shared by every overworld template: spawn at the centre, the sealed End gate to the north (z = -6), the
 * starter chest to the west/east. The outline radius is {@link #RADIUS} with a wobble of at least 84 percent, so
 * everything is placed within 9.5 blocks of the centre.
 */
public final class IslandTemplates {
    public static final double RADIUS = 10;
    public static final int DEPTH = 5;
    /** Radius of the Nether and End platforms. */
    public static final double PLATFORM_RADIUS = 15;
    public static final int GATE_Z = -6;
    /** Fixed spots (relative to the island centre) shared with the portal code and the notes. */
    public static final int[] NETHER_SPAWN = {-4, 1, 5};
    public static final int[] NETHER_PORTAL = {-4, 1, 2};
    public static final int[] END_SPAWN = {0, 1, 4};
    public static final int[] END_ALTAR = {0, 1, 0};
    public static final int[] END_EXIT = {0, 1, -8};

    /** Generates one template into a plan. */
    @FunctionalInterface
    interface Builder {
        void build(BlockPlan plan, Random rnd, List<ItemSpec> chest, String biome, boolean gateLit);
    }

    private static final Map<String, Builder> BUILDERS = Map.of(
        "classic", IslandTemplates::classic,
        "desert", IslandTemplates::desert,
        "frozen", IslandTemplates::frozen,
        "jungle", IslandTemplates::jungle,
        "mushroom", IslandTemplates::mushroom);

    private IslandTemplates() {}

    public static boolean exists(String id) {
        return BUILDERS.containsKey(id);
    }

    public static java.util.Set<String> ids() {
        return BUILDERS.keySet();
    }

    /** Deterministic random source for an island so a re-generation gives the same shape. */
    public static Random random(UUID islandId) {
        return new Random(islandId.getMostSignificantBits() ^ islandId.getLeastSignificantBits());
    }

    /**
     * Deterministic random source for one build step of an island: the same (island, generation, salt) always gives the
     * same shape (so a retried step repeats exactly), a reset (new generation) gives a slightly different island.
     * Salts: 0 overworld, 1 Nether platform, 2 End platform.
     */
    public static Random random(UUID islandId, int generation, int salt) {
        long seed = islandId.getMostSignificantBits() ^ islandId.getLeastSignificantBits();
        seed ^= generation * 0x9E3779B97F4A7C15L;
        seed ^= (salt + 1) * 0xC2B2AE3D27D4EB4FL;
        return new Random(seed);
    }

    /**
     * Builds the overworld island.
     *
     * @param chest contents of the starter chest
     * @param biome biome key such as {@code plains}, empty to leave the biome
     * @param gateLit whether the End gate starts activated
     */
    public static BlockPlan overworld(String templateId, Random rnd, List<ItemSpec> chest, String biome, boolean gateLit) {
        Builder builder = BUILDERS.get(templateId);
        if (builder == null) {
            throw new IllegalArgumentException("Unknown island template: " + templateId);
        }
        BlockPlan plan = new BlockPlan();
        builder.build(plan, rnd, chest, biome, gateLit);
        plan.anchor("spawn", 0, 1, 0);
        plan.anchor("gate", 0, 1, GATE_Z);
        int r = (int) RADIUS + 3;
        plan.biome(-r, -DEPTH - 2, -r, r, 12, r, biome);
        return plan;
    }

    // ---------------------------------------------------------------- shared pieces

    private static void base(BlockPlan plan, Random rnd, java.util.function.IntFunction<Material> layers) {
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, RADIUS, DEPTH, layers);
        plan.set(0, -DEPTH, 0, Material.BEDROCK);
    }

    /** Sealed gate: 12 End portal frames around a 3x3 portal. */
    private static void endGate(BlockPlan plan, boolean lit) {
        int cx = 0;
        int cz = GATE_Z;
        String eye = lit ? "true" : "false";
        for (int d = -1; d <= 1; d++) {
            plan.set(cx + d, 0, cz - 2, Material.END_PORTAL_FRAME, "eye=" + eye + ",facing=south");
            plan.set(cx + d, 0, cz + 2, Material.END_PORTAL_FRAME, "eye=" + eye + ",facing=north");
            plan.set(cx - 2, 0, cz + d, Material.END_PORTAL_FRAME, "eye=" + eye + ",facing=east");
            plan.set(cx + 2, 0, cz + d, Material.END_PORTAL_FRAME, "eye=" + eye + ",facing=west");
        }
        if (lit) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    plan.set(cx + x, 0, cz + z, Material.END_PORTAL);
                }
            }
        }
        plan.anchor("end_gate", cx, 1, cz);
    }

    private static void decorate(BlockPlan plan, Random rnd, Material ground, Material[] plants, int count) {
        for (int i = 0; i < count * 6 && count > 0; i++) {
            int x = rnd.nextInt(19) - 9;
            int z = rnd.nextInt(19) - 9;
            if (plan.get(x, 0, z) == ground && plan.get(x, 1, z) == null && !nearGate(x, z) && Math.abs(x) + Math.abs(z) > 2) {
                plan.set(x, 1, z, plants[rnd.nextInt(plants.length)]);
                if (--count == 0) {
                    break;
                }
            }
        }
    }

    static boolean nearGate(int x, int z) {
        return Math.abs(x) <= 3 && z >= GATE_Z - 3 && z <= GATE_Z + 3;
    }

    private static void oakTree(BlockPlan plan, int tx, int tz, Material log, Material leaves, int height) {
        ShapeKit.column(plan, tx, 1, height, tz, log);
        for (int y = height - 2; y <= height - 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                        continue;
                    }
                    if (x != 0 || z != 0) {
                        plan.set(tx + x, y, tz + z, leaves, "persistent=true");
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) + Math.abs(z) <= 1) {
                    plan.set(tx + x, height, tz + z, leaves, "persistent=true");
                }
            }
        }
        plan.set(tx, height + 1, tz, leaves, "persistent=true");
        plan.set(tx, height, tz, log);
    }

    // ---------------------------------------------------------------- templates

    private static void classic(BlockPlan plan, Random rnd, List<ItemSpec> chest, String biome, boolean lit) {
        base(plan, rnd, d -> d == 0 ? Material.GRASS_BLOCK : d <= 2 ? Material.DIRT : Material.STONE);
        oakTree(plan, 4, 3, Material.OAK_LOG, Material.OAK_LEAVES, 5);
        decorate(plan, rnd, Material.GRASS_BLOCK, new Material[] {Material.SHORT_GRASS, Material.SHORT_GRASS,
            Material.POPPY, Material.DANDELION}, 10);
        plan.chest(-5, 1, 2, "east", chest);
        plan.anchor("chest", -5, 1, 2);
    }

    private static void desert(BlockPlan plan, Random rnd, List<ItemSpec> chest, String biome, boolean lit) {
        base(plan, rnd, d -> d == 0 ? Material.SAND : d <= 2 ? Material.SANDSTONE : Material.STONE);
        ShapeKit.fixSupport(plan, Material.SAND, Material.SANDSTONE);
        // cacti stand alone on sand: nothing solid on their sides
        int[][] cacti = {{5, 2, 3}, {-7, -1, 2}, {7, -2, 2}};
        for (int[] c : cacti) {
            if (plan.get(c[0], 0, c[1]) == Material.SAND) {
                ShapeKit.column(plan, c[0], 1, c[2], c[1], Material.CACTUS);
            }
        }
        decorate(plan, rnd, Material.SAND, new Material[] {Material.DEAD_BUSH}, 5);
        // pyramid fragment: 5x5, 3x3, 1x1 with a hollow core and a door
        int px = -4;
        int pz = 4;
        for (int layer = 0; layer < 3; layer++) {
            int half = 2 - layer;
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    Material m = (Math.abs(x) == half || Math.abs(z) == half) ? Material.CUT_SANDSTONE : Material.SANDSTONE;
                    if (layer == 2) {
                        m = Material.CHISELED_SANDSTONE;
                    }
                    if (layer > 0 && rnd.nextInt(6) == 0 && Math.abs(x) + Math.abs(z) > 0) {
                        continue; // ruined: a few missing blocks
                    }
                    plan.set(px + x, 1 + layer, pz + z, m);
                }
            }
        }
        // hollow core and a door on the east side (two blocks high everywhere)
        plan.air(px, 1, pz);
        plan.air(px, 2, pz);
        plan.air(px + 1, 1, pz);
        plan.air(px + 1, 2, pz);
        plan.air(px + 2, 1, pz);
        plan.air(px + 2, 2, pz);
        plan.set(px, 0, pz, Material.SANDSTONE);
        plan.chest(6, 1, 0, "west", chest);
        plan.anchor("chest", 6, 1, 0);
    }

    private static void frozen(BlockPlan plan, Random rnd, List<ItemSpec> chest, String biome, boolean lit) {
        base(plan, rnd, d -> d == 0 ? Material.SNOW_BLOCK : d == 1 ? Material.DIRT : d == 2 ? Material.PACKED_ICE
            : Material.STONE);
        // spruce
        int tx = 4;
        int tz = 3;
        ShapeKit.column(plan, tx, 1, 7, tz, Material.SPRUCE_LOG);
        for (int y = 2; y <= 7; y++) {
            int r = y <= 3 ? 2 : y <= 5 ? 1 : y == 6 ? 1 : 0;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if ((x != 0 || z != 0) && Math.abs(x) + Math.abs(z) <= (r == 2 ? 3 : 1)) {
                        plan.set(tx + x, y, tz + z, Material.SPRUCE_LEAVES, "persistent=true");
                    }
                }
            }
        }
        plan.set(tx, 8, tz, Material.SPRUCE_LEAVES, "persistent=true");
        // ice patch and snow layers
        for (int i = 0; i < 6; i++) {
            int x = -9 + rnd.nextInt(5);
            int z = rnd.nextInt(7) - 3;
            if (plan.get(x, 0, z) == Material.SNOW_BLOCK) {
                plan.set(x, 0, z, Material.ICE);
            }
        }
        for (int i = 0; i < 30; i++) {
            int x = rnd.nextInt(19) - 9;
            int z = rnd.nextInt(19) - 9;
            if (plan.get(x, 0, z) == Material.SNOW_BLOCK && plan.get(x, 1, z) == null && !nearGate(x, z)
                    && !(x == 0 && z == 0)) {
                plan.set(x, 1, z, Material.SNOW, "layers=" + (1 + rnd.nextInt(2)));
            }
        }
        plan.chest(-5, 1, 2, "east", chest);
        plan.anchor("chest", -5, 1, 2);
    }

    private static void jungle(BlockPlan plan, Random rnd, List<ItemSpec> chest, String biome, boolean lit) {
        base(plan, rnd, d -> d == 0 ? Material.GRASS_BLOCK : d <= 2 ? Material.DIRT : Material.STONE);
        for (int i = 0; i < 14; i++) {
            int x = rnd.nextInt(19) - 9;
            int z = rnd.nextInt(19) - 9;
            if (plan.get(x, 0, z) == Material.GRASS_BLOCK && !nearGate(x, z)) {
                plan.set(x, 0, z, Material.PODZOL);
            }
        }
        // jungle tree with cocoa on two sides
        int tx = 4;
        int tz = 3;
        ShapeKit.column(plan, tx, 1, 7, tz, Material.JUNGLE_LOG);
        ShapeKit.leaves(plan, tx, 7, tz, 2.6, Material.JUNGLE_LEAVES);
        ShapeKit.leaves(plan, tx, 5, tz, 1.9, Material.JUNGLE_LEAVES);
        plan.set(tx, 5, tz, Material.JUNGLE_LOG);
        plan.set(tx, 7, tz, Material.JUNGLE_LOG);
        plan.set(tx + 1, 3, tz, Material.COCOA, "age=2,facing=west");
        plan.set(tx - 1, 2, tz, Material.COCOA, "age=1,facing=east");
        plan.set(tx, 3, tz + 1, Material.COCOA, "age=2,facing=north");
        // bamboo grove
        int[][] bamboo = {{-5, 4, 4}, {-6, 5, 5}, {-4, 6, 3}, {-6, 3, 4}, {-5, 5, 3}};
        for (int[] b : bamboo) {
            if (plan.get(b[0], 0, b[1]) != null && plan.get(b[0], 0, b[1]).isSolid()) {
                plan.set(b[0], 0, b[1], Material.PODZOL);
                for (int y = 1; y <= b[2]; y++) {
                    plan.set(b[0], y, b[1], Material.BAMBOO, y == b[2] ? "leaves=large" : "leaves=small");
                }
            }
        }
        decorate(plan, rnd, Material.GRASS_BLOCK, new Material[] {Material.FERN, Material.SHORT_GRASS, Material.SHORT_GRASS}, 8);
        plan.chest(6, 1, -1, "west", chest);
        plan.anchor("chest", 6, 1, -1);
    }

    private static void mushroom(BlockPlan plan, Random rnd, List<ItemSpec> chest, String biome, boolean lit) {
        base(plan, rnd, d -> d == 0 ? Material.MYCELIUM : d <= 2 ? Material.DIRT : Material.STONE);
        // giant red mushroom
        int tx = 4;
        int tz = 3;
        ShapeKit.column(plan, tx, 1, 4, tz, Material.MUSHROOM_STEM);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 && Math.abs(z) == 2) {
                    continue;
                }
                plan.set(tx + x, 5, tz + z, Material.RED_MUSHROOM_BLOCK);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                plan.set(tx + x, 6, tz + z, Material.RED_MUSHROOM_BLOCK);
            }
        }
        // small brown mushroom tree
        ShapeKit.column(plan, -5, 1, 2, -3, Material.MUSHROOM_STEM);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) + Math.abs(z) <= 3) {
                    plan.set(-5 + x, 3, -3 + z, Material.BROWN_MUSHROOM_BLOCK);
                }
            }
        }
        decorate(plan, rnd, Material.MYCELIUM, new Material[] {Material.RED_MUSHROOM, Material.BROWN_MUSHROOM}, 10);
        plan.chest(-5, 1, 3, "east", chest);
        plan.anchor("chest", -5, 1, 3);
    }

    // ---------------------------------------------------------------- Nether platform

    /** The island's own Nether platform. Anchors: spawn, portal, chest, altar-free. */
    public static BlockPlan nether(Random rnd, List<ItemSpec> loot) {
        BlockPlan plan = new BlockPlan();
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, PLATFORM_RADIUS, 7,
            d -> d == 0 ? Material.NETHERRACK : (d > 3 && rnd.nextInt(5) == 0) ? Material.BLACKSTONE : Material.NETHERRACK);
        // ore and decoration inside the rock
        for (int i = 0; i < 40 && plan.count(Material.NETHER_QUARTZ_ORE) < 8; i++) {
            int x = rnd.nextInt(21) - 10;
            int z = rnd.nextInt(21) - 10;
            int y = -1 - rnd.nextInt(3);
            if (plan.get(x, y, z) == Material.NETHERRACK && plan.solidAt(x, y - 1, z)) {
                plan.set(x, y, z, Material.NETHER_QUARTZ_ORE);
            }
        }
        for (int i = 0; i < 20 && plan.count(Material.NETHER_GOLD_ORE) < 3; i++) {
            int x = rnd.nextInt(21) - 10;
            int z = rnd.nextInt(21) - 10;
            if (plan.get(x, -2, z) == Material.NETHERRACK && plan.solidAt(x, -3, z)) {
                plan.set(x, -2, z, Material.NETHER_GOLD_ORE);
            }
        }
        for (int i = 0; i < 30 && plan.count(Material.MAGMA_BLOCK) < 3; i++) {
            int x = rnd.nextInt(21) - 10;
            int z = rnd.nextInt(21) - 10;
            if (plan.get(x, 0, z) == Material.NETHERRACK && Math.abs(x) + Math.abs(z) > 6) {
                plan.set(x, 0, z, Material.MAGMA_BLOCK);
            }
        }
        // glowstone clusters standing on the rock
        int[][] glow = {{9, -2}, {-8, -6}, {2, 9}};
        for (int[] g : glow) {
            if (plan.get(g[0], 0, g[1]) != null) {
                plan.set(g[0], 1, g[1], Material.GLOWSTONE);
                plan.set(g[0] + 1, 1, g[1], Material.GLOWSTONE);
                plan.set(g[0], 2, g[1], Material.GLOWSTONE);
            }
        }
        // lava pool
        for (int x = 7; x <= 9; x++) {
            for (int z = 4; z <= 6; z++) {
                if (plan.get(x, 0, z) != null) {
                    plan.set(x, 0, z, Material.LAVA);
                }
            }
        }
        // soul sand patch with nether wart
        for (int x = -9; x <= -6; x++) {
            for (int z = -3; z <= -1; z++) {
                if (plan.get(x, 0, z) != null) {
                    plan.set(x, 0, z, Material.SOUL_SAND);
                    plan.set(x, 1, z, Material.NETHER_WART, "age=3");
                }
            }
        }
        // portal frame (axis x) with the portal blocks inside
        for (int x = -6; x <= -3; x++) {
            plan.set(x, 0, 2, Material.OBSIDIAN);
            plan.set(x, 4, 2, Material.OBSIDIAN);
        }
        for (int y = 1; y <= 3; y++) {
            plan.set(-6, y, 2, Material.OBSIDIAN);
            plan.set(-3, y, 2, Material.OBSIDIAN);
            plan.set(-5, y, 2, Material.NETHER_PORTAL, "axis=x");
            plan.set(-4, y, 2, Material.NETHER_PORTAL, "axis=x");
        }
        plan.anchor("portal", NETHER_PORTAL[0], NETHER_PORTAL[1], NETHER_PORTAL[2]);
        // ruined fortress hut with a chest (no spawner)
        int fx = 6;
        int fz = -7;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                plan.set(fx + x, 0, fz + z, Material.NETHER_BRICKS);
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (edge) {
                    boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                    int h = corner ? 4 : 2;
                    for (int y = 1; y <= h; y++) {
                        if (!corner && rnd.nextInt(4) == 0) {
                            continue;
                        }
                        plan.set(fx + x, y, fz + z, corner || y == 1 ? Material.NETHER_BRICKS : Material.NETHER_BRICK_FENCE);
                    }
                }
            }
        }
        plan.air(fx, 1, fz + 2);
        plan.air(fx, 2, fz + 2);
        for (int x = -2; x <= 2; x++) {
            if (rnd.nextInt(3) != 0) {
                plan.set(fx + x, 5, fz - 2, Material.NETHER_BRICK_SLAB);
            }
        }
        plan.chest(fx, 1, fz, "south", loot);
        plan.anchor("chest", fx, 1, fz);
        plan.anchor("spawn", NETHER_SPAWN[0], NETHER_SPAWN[1], NETHER_SPAWN[2]);
        return plan;
    }

    // ---------------------------------------------------------------- End platform

    /**
     * The island's own End platform. Anchors: spawn (in front of the return gate), altar (centre, the spot for the
     * dragon fight: block y = 1 above the obsidian slab), exit (return gate), chest.
     */
    public static BlockPlan end(Random rnd, List<ItemSpec> loot) {
        BlockPlan plan = new BlockPlan();
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, PLATFORM_RADIUS, 6, d -> Material.END_STONE);
        // altar: 5x5 obsidian slab with a crying obsidian heart and four purpur pillars
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                plan.set(x, 0, z, Material.OBSIDIAN);
            }
        }
        plan.set(0, 0, 0, Material.CRYING_OBSIDIAN);
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            ShapeKit.column(plan, c[0], 1, 3, c[1], Material.PURPUR_PILLAR);
            plan.set(c[0], 4, c[1], Material.END_ROD, "facing=up");
        }
        plan.anchor("altar", END_ALTAR[0], END_ALTAR[1], END_ALTAR[2]);
        // six obsidian pillars on a ring
        for (int i = 0; i < 6; i++) {
            int px = ShapeKit.polarX(10, i * 60);
            int pz = ShapeKit.polarZ(10, i * 60);
            int height = 5 + (i * 3) % 7;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    ShapeKit.column(plan, px + x, 1, height, pz + z, Material.OBSIDIAN);
                }
            }
            plan.set(px, height + 1, pz, Material.END_ROD, "facing=up");
        }
        // outpost north-east of the altar
        int ox = 0;
        int oz = 8;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                plan.set(ox + x, 0, oz + z, Material.PURPUR_BLOCK);
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                for (int y = 1; y <= 3; y++) {
                    plan.set(ox + x, y, oz + z, edge ? Material.END_STONE_BRICKS : Material.AIR);
                }
                plan.set(ox + x, 4, oz + z, Material.PURPUR_SLAB);
            }
        }
        plan.air(ox, 1, oz - 2);
        plan.air(ox, 2, oz - 2);
        plan.set(ox + 1, 3, oz, Material.END_ROD, "facing=down");
        plan.chest(ox, 1, oz + 1, "north", loot);
        plan.anchor("chest", ox, 1, oz + 1);
        // return gate: 3x3 exit portal inside an obsidian ring, south of the altar
        for (int x = -2; x <= 2; x++) {
            for (int z = -10; z <= -6; z++) {
                plan.set(x, 0, z, Material.OBSIDIAN);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -9; z <= -7; z++) {
                plan.set(x, 0, z, Material.END_PORTAL);
            }
        }
        plan.anchor("exit", END_EXIT[0], END_EXIT[1], END_EXIT[2]);
        plan.anchor("spawn", END_SPAWN[0], END_SPAWN[1], END_SPAWN[2]);
        return plan;
    }
}
