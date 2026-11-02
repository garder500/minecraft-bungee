package win.vampyuyu.survival.island;

import java.util.Random;
import org.bukkit.Material;

/**
 * The Citadel of Golden Sands (GDD 3.1), relative coordinates, surface at y = 0: a main island of gold-streaked sand
 * around a giant hourglass, six dune islets joined by sandstone bridges, a stepped pyramid in the north where the
 * Temporal Pharaoh sleeps, columns of golden dust that "flow" upward, and a landing platform at the Sky Gate.
 * Pure data, like {@link GrovePlan}.
 */
public final class CitadelPlan {
    /** Bump when the layout changes so an existing Citadel is rebuilt (the marker file stores it). */
    public static final int VERSION = 1;
    public static final int MAIN_RADIUS = 26;
    public static final int ISLET_COUNT = 6;
    public static final int ISLET_ORBIT = 46;
    public static final int ISLET_RADIUS = 9;
    public static final int BORDER = 120;
    private static final long SEED = 0x6120_0042L;

    private CitadelPlan() {}

    /** Where players arrive from the Sky Gate, relative to the centre: the landing platform on the south side. */
    public static int[] spawn() {
        return new int[] {0, 1, MAIN_RADIUS - 6};
    }

    /** Centre of an islet (x, z), on a circle around the main island. */
    public static int[] isletCenter(int index) {
        double angle = Math.toRadians(30 + 60.0 * index);
        return new int[] {(int) Math.round(ISLET_ORBIT * Math.cos(angle)), (int) Math.round(ISLET_ORBIT * Math.sin(angle))};
    }

    /** Where the dust columns stand (x, z). */
    public static int[][] dustColumns() {
        return new int[][] {{14, 0}, {-14, 0}, {0, 14}, {0, -14}};
    }

    public static BlockPlan build(int maxBlocks) {
        BlockPlan plan = new BlockPlan(maxBlocks);
        Random rnd = new Random(SEED);

        // main island: gold-streaked sand over sandstone, red sandstone deeper down
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, MAIN_RADIUS, 13, d -> d == 0 ? (rnd.nextInt(9) == 0 ? Material.SUSPICIOUS_SAND
            : Material.SAND) : d < 4 ? Material.SANDSTONE : d < 8 ? Material.CUT_SANDSTONE : Material.RED_SANDSTONE);

        // islets and the bridges to the main island
        for (int i = 0; i < ISLET_COUNT; i++) {
            int[] c = isletCenter(i);
            islet(plan, new Random(SEED + i), c[0], c[1], i);
            bridge(plan, c[0], c[1]);
        }

        hourglass(plan);
        for (int[] c : dustColumns()) {
            dustColumn(plan, c[0], c[1]);
        }
        pyramid(plan);

        // dunes and cacti on the main island
        for (int i = 0; i < 80; i++) {
            decorate(plan, rnd, rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS, rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS);
        }

        // landing platform at the Sky Gate
        int[] s = spawn();
        ShapeKit.disc(plan, s[0], 0, s[2], 4.5, Material.SMOOTH_SANDSTONE);
        ShapeKit.ring(plan, s[0], 0, s[2], 3.2, 4.4, Material.CUT_SANDSTONE);
        plan.set(s[0], 0, s[2], Material.GOLD_BLOCK);
        plan.sign(s[0] + 2, 1, s[2] + 1, Material.BIRCH_SIGN, "rotation=0", "Golden Sands", "/hub to leave", "/island to go home", "");
        plan.anchor("spawn", s[0], s[1], s[2]);
        plan.anchor("pharaoh", 0, 2, -16);
        plan.anchor("hourglass", 0, 1, 0);
        plan.biome(-MAIN_RADIUS - 4, -20, -MAIN_RADIUS - 4, MAIN_RADIUS + 4, 14, MAIN_RADIUS + 4, "minecraft:desert");
        ShapeKit.fixSupport(plan, Material.SAND, Material.SANDSTONE);
        return plan;
    }

    /** True when (x, z) lies on the main island (used to keep the decor inside the sand). */
    public static boolean onMain(int x, int z) {
        return ShapeKit.inside(null, MAIN_RADIUS * ShapeKit.Outline.minFactor(), x, z);
    }

    /** A giant hourglass at the centre: two glass cones, sand in the lower bulb, a gold plinth and a dial of 12 marks. */
    private static void hourglass(BlockPlan plan) {
        ShapeKit.disc(plan, 0, 0, 0, 7.5, Material.POLISHED_ANDESITE);
        ShapeKit.ring(plan, 0, 0, 0, 5.5, 6.5, Material.GOLD_BLOCK);
        for (int i = 0; i < 12; i++) {
            plan.set(ShapeKit.polarX(7, 30.0 * i), 0, ShapeKit.polarZ(7, 30.0 * i), i % 3 == 0 ? Material.GILDED_BLACKSTONE : Material.CHISELED_SANDSTONE);
        }
        // profile: radius by height above the plinth, narrow in the middle
        double[] radius = {4.5, 4.0, 3.2, 2.4, 1.6, 0.8, 0.0, 0.8, 1.6, 2.4, 3.2, 4.0, 4.5};
        for (int h = 0; h < radius.length; h++) {
            int y = 1 + h;
            double r = radius[h];
            if (r > 0) {
                ShapeKit.ring(plan, 0, y, 0, Math.max(0, r - 1), r, Material.YELLOW_STAINED_GLASS);
            } else {
                plan.set(0, y, 0, Material.GLASS);
            }
            if (h < 6 && r > 1.5) {
                ShapeKit.disc(plan, 0, y, 0, r - 1.2, Material.SAND);
            }
        }
        plan.set(0, 7, 0, Material.GLOWSTONE);
        ShapeKit.column(plan, 0, 8, 12, 0, Material.AIR);
        ShapeKit.disc(plan, 0, 14, 0, 4.5, Material.GOLD_BLOCK);
        plan.set(0, 15, 0, Material.GLOWSTONE);
        plan.set(4, 1, 0, Material.LANTERN, "hanging=false");
        plan.set(-4, 1, 0, Material.LANTERN, "hanging=false");
    }

    /** A column of golden dust: it rises from the ground, as the rivers of the Citadel flow upside down. */
    private static void dustColumn(BlockPlan plan, int x, int z) {
        ShapeKit.disc(plan, x, 0, z, 2.2, Material.GILDED_BLACKSTONE);
        for (int y = 1; y <= 22; y++) {
            plan.set(x, y, z, y % 6 == 0 ? Material.GOLD_BLOCK : Material.YELLOW_STAINED_GLASS);
        }
        plan.set(x, 23, z, Material.GLOWSTONE);
    }

    /** The Pharaoh's pyramid on the north side: four terraces, a gate facing south and the sleeping chamber inside. */
    private static void pyramid(BlockPlan plan) {
        int cz = -16;
        int[] half = {8, 6, 4, 2};
        for (int t = 0; t < half.length; t++) {
            for (int x = -half[t]; x <= half[t]; x++) {
                for (int z = -half[t]; z <= half[t]; z++) {
                    plan.set(x, 1 + t, cz + z, t % 2 == 0 ? Material.SMOOTH_SANDSTONE : Material.CUT_SANDSTONE);
                }
            }
        }
        // chamber under the second terrace and the corridor to the gate facing the hourglass
        plan.fill(-3, 1, cz - 3, 3, 2, cz + 3, Material.AIR);
        plan.fill(-1, 1, cz + 4, 1, 2, cz + 8, Material.AIR);
        plan.set(0, 1, cz, Material.GOLD_BLOCK);
        plan.set(-2, 1, cz + 8, Material.CHISELED_SANDSTONE);
        plan.set(2, 1, cz + 8, Material.CHISELED_SANDSTONE);
        plan.set(-2, 3, cz + 8, Material.GOLD_BLOCK);
        plan.set(2, 3, cz + 8, Material.GOLD_BLOCK);
        plan.set(0, 4, cz, Material.GOLD_BLOCK);
        plan.set(0, 5, cz, Material.GLOWSTONE);
        plan.set(-3, 1, cz + 10, Material.LANTERN, "hanging=false");
        plan.set(3, 1, cz + 10, Material.LANTERN, "hanging=false");
    }

    private static void islet(BlockPlan plan, Random rnd, int cx, int cz, int index) {
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        BlockPlan local = new BlockPlan();
        ShapeKit.floatingIsland(local, outline, ISLET_RADIUS, 6, d -> d == 0 ? Material.SAND : d < 3 ? Material.SANDSTONE : Material.RED_SANDSTONE);
        local.blocks().forEach((key, blk) -> plan.set(cx + BlockPlan.keyX(key), BlockPlan.keyY(key), cz + BlockPlan.keyZ(key), blk));
        switch (index % 3) {
            case 0 -> {
                // obelisk: the Chrono Sand glitters at its foot
                ShapeKit.column(plan, cx, 1, 9, cz, Material.CHISELED_SANDSTONE);
                plan.set(cx, 10, cz, Material.GOLD_BLOCK);
                plan.set(cx + 1, 1, cz, Material.SUSPICIOUS_SAND);
                plan.set(cx - 1, 1, cz, Material.SUSPICIOUS_SAND);
            }
            case 1 -> {
                // dead tree and cacti
                ShapeKit.column(plan, cx, 1, 4, cz, Material.STRIPPED_ACACIA_LOG);
                plan.set(cx + 1, 4, cz, Material.STRIPPED_ACACIA_LOG);
                plan.set(cx + 3, 1, cz + 2, Material.CACTUS);
                plan.set(cx - 3, 1, cz - 2, Material.CACTUS);
                plan.set(cx + 2, 1, cz - 3, Material.DEAD_BUSH);
            }
            default -> {
                // an oasis that still holds some time
                ShapeKit.disc(plan, cx, 0, cz, 3.5, Material.SANDSTONE);
                ShapeKit.disc(plan, cx, 0, cz, 2.5, Material.WATER);
                plan.set(cx, 1, cz + 3, Material.SUGAR_CANE);
                plan.set(cx + 4, 1, cz, Material.LANTERN, "hanging=false");
            }
        }
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
                    plan.set(x + dx, 0, z + dz, Material.SMOOTH_SANDSTONE);
                }
            }
            if (i % 4 == 0) {
                plan.set(x, 1, z, Material.LANTERN, "hanging=false");
            }
        }
    }

    private static void decorate(BlockPlan plan, Random rnd, int x, int z) {
        if (!onMain(x, z) || plan.get(x, 0, z) == null || plan.get(x, 1, z) != null || Math.hypot(x, z) < 10
                || Math.hypot(x, z + 16) < 12 || Math.hypot(x - spawn()[0], z - spawn()[2]) < 6) {
            return;
        }
        switch (rnd.nextInt(5)) {
            case 0 -> plan.set(x, 1, z, Material.DEAD_BUSH);
            case 1 -> {
                plan.set(x, 1, z, Material.CACTUS);
                if (rnd.nextBoolean()) {
                    plan.set(x, 2, z, Material.CACTUS);
                }
            }
            case 2 -> plan.set(x, 1, z, Material.SANDSTONE_SLAB);
            case 3 -> plan.set(x, 1, z, Material.CUT_SANDSTONE_SLAB);
            default -> plan.set(x, 1, z, Material.GOLD_BLOCK);
        }
    }
}
