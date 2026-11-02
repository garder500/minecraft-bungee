package win.vampyuyu.survival.island;

import java.util.Random;
import org.bukkit.Material;

/**
 * The Whispering Grove (GDD 3.1), relative coordinates, surface at y = 0: a living island of moss and grass held up
 * by giant roots, six smaller islands around it joined by root bridges, bioluminescent spores (shroomlight, glow
 * lichen, glow berries, spore blossoms), a farmland terrace for the Ranch and a landing platform at the Sky Gate.
 * The Mycelial Queen sleeps under the central arch. Pure data, like {@link QuaiPlan}.
 */
public final class GrovePlan {
    /** Bump when the layout changes so an existing Grove is rebuilt (the marker file stores it). */
    public static final int VERSION = 1;
    public static final int MAIN_RADIUS = 24;
    public static final int ISLET_COUNT = 6;
    public static final int ISLET_ORBIT = 44;
    public static final int ISLET_RADIUS = 9;
    public static final int BORDER = 120;
    private static final long SEED = 0x6120_0030L;

    private GrovePlan() {}

    /** Where players arrive from the Sky Gate, relative to the centre: the landing platform on the south side. */
    public static int[] spawn() {
        return new int[] {0, 1, MAIN_RADIUS - 6};
    }

    /** Centre of an islet (x, z), on a circle around the main island. */
    public static int[] isletCenter(int index) {
        double angle = Math.toRadians(30 + 60.0 * index);
        return new int[] {(int) Math.round(ISLET_ORBIT * Math.cos(angle)), (int) Math.round(ISLET_ORBIT * Math.sin(angle))};
    }

    public static BlockPlan build(int maxBlocks) {
        BlockPlan plan = new BlockPlan(maxBlocks);
        Random rnd = new Random(SEED);

        // main island: grass and moss on top, dirt, then the great roots hanging below
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, MAIN_RADIUS, 12, d -> d == 0 ? (rnd.nextInt(4) == 0 ? Material.MOSS_BLOCK
            : Material.GRASS_BLOCK) : d < 4 ? Material.DIRT : d < 7 ? Material.ROOTED_DIRT : Material.MUD);
        for (int i = 0; i < 7; i++) {
            root(plan, rnd, ShapeKit.polarX(MAIN_RADIUS * 0.6, 51.4 * i), ShapeKit.polarZ(MAIN_RADIUS * 0.6, 51.4 * i), 14 + rnd.nextInt(8));
        }

        // islets and the root bridges to the main island
        for (int i = 0; i < ISLET_COUNT; i++) {
            int[] c = isletCenter(i);
            islet(plan, new Random(SEED + i), c[0], c[1], i);
            bridge(plan, c[0], c[1]);
        }

        // central arch of living roots, where the Queen sleeps
        arch(plan);

        // ranch terrace: farmland with water, on the east side of the main island
        farm(plan, 10, -8);

        // spore garden on the main island
        for (int i = 0; i < 90; i++) {
            int x = rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS;
            int z = rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS;
            decorate(plan, rnd, x, z);
        }

        // landing platform at the Sky Gate
        int[] s = spawn();
        ShapeKit.disc(plan, s[0], 0, s[2], 4.5, Material.MOSS_BLOCK);
        ShapeKit.ring(plan, s[0], 0, s[2], 3.2, 4.4, Material.MUD_BRICKS);
        plan.set(s[0], 0, s[2], Material.SHROOMLIGHT);
        plan.sign(s[0] + 2, 1, s[2] + 1, Material.MANGROVE_SIGN, "rotation=0", "Whispering Grove", "/hub to leave", "/island to go home", "");
        plan.anchor("spawn", s[0], s[1], s[2]);
        plan.anchor("queen", 0, 1, -6);
        plan.biome(-MAIN_RADIUS - 4, -20, -MAIN_RADIUS - 4, MAIN_RADIUS + 4, 12, MAIN_RADIUS + 4, "minecraft:lush_caves");
        return plan;
    }

    /** True when (x, z) lies on the main island (used to keep the structures inside the grass). */
    public static boolean onMain(int x, int z) {
        return ShapeKit.inside(null, MAIN_RADIUS * ShapeKit.Outline.minFactor(), x, z);
    }

    private static void root(BlockPlan plan, Random rnd, int x, int z, int length) {
        int cx = x;
        int cz = z;
        for (int d = 6; d <= 6 + length; d++) {
            plan.set(cx, -d, cz, Material.MANGROVE_ROOTS, "waterlogged=false");
            if (d % 3 == 0) {
                plan.set(cx + 1, -d, cz, Material.MUDDY_MANGROVE_ROOTS);
                cx += rnd.nextInt(3) - 1;
                cz += rnd.nextInt(3) - 1;
            }
        }
        plan.set(cx, -7 - length, cz, Material.GLOW_LICHEN, "down=true");
    }

    private static void islet(BlockPlan plan, Random rnd, int cx, int cz, int index) {
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        BlockPlan local = new BlockPlan();
        ShapeKit.floatingIsland(local, outline, ISLET_RADIUS, 6, d -> d == 0 ? Material.GRASS_BLOCK : d < 3 ? Material.DIRT : Material.MUD);
        local.blocks().forEach((key, blk) -> plan.set(cx + BlockPlan.keyX(key), BlockPlan.keyY(key), cz + BlockPlan.keyZ(key), blk));
        // each islet has its own bioluminescent feature
        switch (index % 3) {
            case 0 -> {
                // giant mushroom
                ShapeKit.column(plan, cx, 1, 6, cz, Material.MUSHROOM_STEM);
                ShapeKit.disc(plan, cx, 7, cz, 4.5, Material.RED_MUSHROOM_BLOCK);
                ShapeKit.disc(plan, cx, 8, cz, 2.5, Material.RED_MUSHROOM_BLOCK);
                plan.set(cx, 6, cz, Material.SHROOMLIGHT);
            }
            case 1 -> {
                // heartwood tree: the local resource grows here
                ShapeKit.column(plan, cx, 1, 9, cz, Material.MANGROVE_LOG);
                ShapeKit.leaves(plan, cx, 10, cz, 4.2, Material.AZALEA_LEAVES);
                plan.set(cx + 1, 8, cz, Material.SHROOMLIGHT);
                plan.set(cx - 1, 7, cz, Material.SHROOMLIGHT);
            }
            default -> {
                // spore pool
                ShapeKit.disc(plan, cx, 0, cz, 3.5, Material.CLAY);
                ShapeKit.disc(plan, cx, 0, cz, 2.5, Material.WATER);
                plan.set(cx, 1, cz, Material.SPORE_BLOSSOM);
            }
        }
        plan.set(cx + 4, 1, cz, Material.SHROOMLIGHT);
    }

    private static void bridge(BlockPlan plan, int tx, int tz) {
        double len = Math.hypot(tx, tz);
        int steps = (int) len;
        for (int i = (int) (MAIN_RADIUS * 0.9); i < steps - ISLET_RADIUS + 2; i++) {
            double f = i / len;
            int x = (int) Math.round(tx * f);
            int z = (int) Math.round(tz * f);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    plan.set(x + dx, 0, z + dz, Material.MANGROVE_PLANKS);
                }
            }
            if (i % 4 == 0) {
                plan.set(x, 1, z, Material.LANTERN, "hanging=false");
                plan.set(x, -1, z, Material.MANGROVE_ROOTS, "waterlogged=false");
            }
        }
    }

    private static void arch(BlockPlan plan) {
        for (int side : new int[] {-3, 3}) {
            ShapeKit.column(plan, side, 1, 7, -8, Material.MANGROVE_LOG);
            ShapeKit.column(plan, side, 1, 7, -4, Material.MANGROVE_LOG);
        }
        for (int x = -4; x <= 4; x++) {
            plan.set(x, 8, -8, Material.MANGROVE_LOG);
            plan.set(x, 8, -4, Material.MANGROVE_LOG);
            for (int z = -8; z <= -4; z++) {
                plan.set(x, 9, z, Material.MOSS_BLOCK);
            }
        }
        for (int x = -2; x <= 2; x++) {
            plan.set(x, 8, -6, Material.HANGING_ROOTS, "waterlogged=false");
        }
        ShapeKit.disc(plan, 0, 0, -6, 2.4, Material.MYCELIUM);
        plan.set(0, 1, -6, Material.RED_MUSHROOM);
        plan.set(1, 1, -6, Material.BROWN_MUSHROOM);
        plan.set(-1, 1, -6, Material.BROWN_MUSHROOM);
    }

    private static void farm(BlockPlan plan, int x0, int z0) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 7; z++) {
                boolean water = x == 4 && z == 3;
                plan.set(x0 + x, 0, z0 + z, water ? Material.WATER : Material.FARMLAND, water ? null : "moisture=7");
                if (!water) {
                    plan.set(x0 + x, 1, z0 + z, (x + z) % 2 == 0 ? Material.WHEAT : Material.CARROTS, "age=" + 2);
                }
            }
        }
        for (int x = -1; x <= 9; x++) {
            plan.set(x0 + x, 1, z0 - 1, Material.MANGROVE_FENCE);
            plan.set(x0 + x, 1, z0 + 7, Material.MANGROVE_FENCE);
        }
        plan.set(x0 - 1, 2, z0 - 1, Material.LANTERN);
        plan.set(x0 + 9, 2, z0 + 7, Material.LANTERN);
    }

    private static void decorate(BlockPlan plan, Random rnd, int x, int z) {
        if (!onMain(x, z) || plan.get(x, 0, z) == null || plan.get(x, 1, z) != null || Math.hypot(x, z + 6) < 5 || Math.hypot(x - spawn()[0], z - spawn()[2]) < 6) {
            return;
        }
        switch (rnd.nextInt(6)) {
            case 0 -> plan.set(x, 1, z, Material.SHORT_GRASS);
            case 1 -> plan.set(x, 1, z, Material.MOSS_CARPET);
            case 2 -> plan.set(x, 1, z, Material.RED_MUSHROOM);
            case 3 -> plan.set(x, 1, z, Material.BROWN_MUSHROOM);
            case 4 -> plan.set(x, 1, z, Material.FERN);
            default -> {
                plan.set(x, 1, z, Material.SHROOMLIGHT);
            }
        }
    }
}
