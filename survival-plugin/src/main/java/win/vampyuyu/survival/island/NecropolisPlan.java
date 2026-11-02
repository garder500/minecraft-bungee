package win.vampyuyu.survival.island;

import java.util.Random;
import org.bukkit.Material;

/**
 * The Ethereal Necropolis (GDD 3.1), relative coordinates, surface at y = 0: a drifting island of deepslate and soul
 * soil where the memories of the dead float as particles, six smaller islands (ossuary, soul brazier, grave field)
 * joined by blackstone bridges, a field of soul sand where Soul Ash is found, and a landing platform at the Sky Gate.
 * The Judge of Lost Souls waits under the dais arch. Pure data, like {@link GrovePlan}.
 */
public final class NecropolisPlan {
    /** Bump when the layout changes so an existing Necropolis is rebuilt (the marker file stores it). */
    public static final int VERSION = 1;
    public static final int MAIN_RADIUS = 24;
    public static final int ISLET_COUNT = 6;
    public static final int ISLET_ORBIT = 44;
    public static final int ISLET_RADIUS = 9;
    public static final int BORDER = 120;
    private static final long SEED = 0x6120_0080L;

    private NecropolisPlan() {}

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

        // main island: soul soil and deepslate, tapering into hanging bone and chains below
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, MAIN_RADIUS, 12, d -> d == 0 ? (rnd.nextInt(3) == 0 ? Material.SOUL_SOIL
            : Material.DEEPSLATE) : d < 4 ? Material.COBBLED_DEEPSLATE : d < 7 ? Material.DEEPSLATE : Material.BLACKSTONE);
        for (int i = 0; i < 7; i++) {
            spike(plan, rnd, ShapeKit.polarX(MAIN_RADIUS * 0.6, 51.4 * i), ShapeKit.polarZ(MAIN_RADIUS * 0.6, 51.4 * i), 12 + rnd.nextInt(8));
        }

        // islets and the bridges to the main island
        for (int i = 0; i < ISLET_COUNT; i++) {
            int[] c = isletCenter(i);
            islet(plan, new Random(SEED + i), c[0], c[1], i);
            bridge(plan, c[0], c[1]);
        }

        // the dais where the Judge waits
        dais(plan);

        // soul ash field: soul sand and soul soil patches with soul fire, on the east side of the main island
        ashField(plan, 10, -8);

        // scattered graves and candles on the main island
        for (int i = 0; i < 90; i++) {
            int x = rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS;
            int z = rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS;
            decorate(plan, rnd, x, z);
        }

        // landing platform at the Sky Gate
        int[] s = spawn();
        ShapeKit.disc(plan, s[0], 0, s[2], 4.5, Material.POLISHED_DEEPSLATE);
        ShapeKit.ring(plan, s[0], 0, s[2], 3.2, 4.4, Material.DEEPSLATE_TILES);
        plan.set(s[0], 0, s[2], Material.SEA_LANTERN);
        plan.sign(s[0] + 2, 1, s[2] + 1, Material.CRIMSON_SIGN, "rotation=0", "Ethereal Necropolis", "/hub to leave", "/island to go home", "");
        plan.anchor("spawn", s[0], s[1], s[2]);
        plan.anchor("judge", 0, 1, -6);
        plan.biome(-MAIN_RADIUS - 4, -20, -MAIN_RADIUS - 4, MAIN_RADIUS + 4, 12, MAIN_RADIUS + 4, "minecraft:deep_dark");
        return plan;
    }

    /** True when (x, z) lies on the main island (used to keep the structures inside the rock). */
    public static boolean onMain(int x, int z) {
        return ShapeKit.inside(null, MAIN_RADIUS * ShapeKit.Outline.minFactor(), x, z);
    }

    private static void spike(BlockPlan plan, Random rnd, int x, int z, int length) {
        int cx = x;
        int cz = z;
        for (int d = 6; d <= 6 + length; d++) {
            plan.set(cx, -d, cz, d % 4 == 0 ? Material.BONE_BLOCK : Material.CHAIN, d % 4 == 0 ? null : "axis=y");
            if (d % 3 == 0) {
                cx += rnd.nextInt(3) - 1;
                cz += rnd.nextInt(3) - 1;
            }
        }
        plan.set(cx, -7 - length, cz, Material.SOUL_LANTERN, "hanging=true");
    }

    private static void islet(BlockPlan plan, Random rnd, int cx, int cz, int index) {
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        BlockPlan local = new BlockPlan();
        ShapeKit.floatingIsland(local, outline, ISLET_RADIUS, 6, d -> d == 0 ? Material.SOUL_SOIL : d < 3 ? Material.DEEPSLATE : Material.BLACKSTONE);
        local.blocks().forEach((key, blk) -> plan.set(cx + BlockPlan.keyX(key), BlockPlan.keyY(key), cz + BlockPlan.keyZ(key), blk));
        // each islet has its own feature
        switch (index % 3) {
            case 0 -> {
                // ossuary: a tower of bone blocks with a soul lantern on top
                ShapeKit.column(plan, cx, 1, 8, cz, Material.BONE_BLOCK);
                ShapeKit.ring(plan, cx, 1, cz, 2.5, 3.4, Material.STONE_BRICK_WALL);
                plan.set(cx, 9, cz, Material.SOUL_LANTERN, "hanging=false");
            }
            case 1 -> {
                // soul brazier: soul fire on a soul sand ring, the memories gather here
                ShapeKit.disc(plan, cx, 0, cz, 3.5, Material.SOUL_SAND);
                ShapeKit.disc(plan, cx, 1, cz, 1.5, Material.SOUL_SOIL);
                plan.set(cx, 2, cz, Material.SOUL_FIRE);
                for (int a = 0; a < 4; a++) {
                    ShapeKit.column(plan, cx + (a < 2 ? 3 : -3), 1, 3, cz + (a % 2 == 0 ? 3 : -3), Material.POLISHED_BLACKSTONE_BRICK_WALL);
                }
            }
            default -> {
                // grave field: rows of stone slabs and headstones
                for (int i = -3; i <= 3; i += 2) {
                    for (int j = -2; j <= 2; j += 2) {
                        plan.set(cx + i, 1, cz + j, Material.COBBLED_DEEPSLATE_SLAB, "type=bottom");
                        plan.set(cx + i, 2, cz + j + 1, Material.COBBLED_DEEPSLATE_WALL);
                    }
                }
            }
        }
        plan.set(cx + 4, 1, cz, Material.SOUL_LANTERN, "hanging=false");
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
                    plan.set(x + dx, 0, z + dz, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            if (i % 4 == 0) {
                plan.set(x, 1, z, Material.SOUL_LANTERN, "hanging=false");
                plan.set(x, -1, z, Material.CHAIN, "axis=y");
            }
        }
    }

    private static void dais(BlockPlan plan) {
        for (int side : new int[] {-3, 3}) {
            ShapeKit.column(plan, side, 1, 7, -8, Material.POLISHED_BLACKSTONE_BRICKS);
            ShapeKit.column(plan, side, 1, 7, -4, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int x = -4; x <= 4; x++) {
            plan.set(x, 8, -8, Material.CHISELED_POLISHED_BLACKSTONE);
            plan.set(x, 8, -4, Material.CHISELED_POLISHED_BLACKSTONE);
            for (int z = -8; z <= -4; z++) {
                plan.set(x, 9, z, Material.DEEPSLATE_TILES);
            }
        }
        for (int x = -2; x <= 2; x += 2) {
            plan.set(x, 7, -6, Material.CHAIN, "axis=y");
            plan.set(x, 6, -6, Material.SOUL_LANTERN, "hanging=true");
        }
        ShapeKit.disc(plan, 0, 0, -6, 2.4, Material.CRYING_OBSIDIAN);
        ShapeKit.ring(plan, 0, 0, -6, 2.4, 3.4, Material.GILDED_BLACKSTONE);
    }

    private static void ashField(BlockPlan plan, int x0, int z0) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 7; z++) {
                plan.set(x0 + x, 0, z0 + z, (x + z) % 3 == 0 ? Material.SOUL_SOIL : Material.SOUL_SAND);
                if ((x * 3 + z * 5) % 11 == 0) {
                    plan.set(x0 + x, 1, z0 + z, Material.SOUL_FIRE);
                }
            }
        }
        for (int x = -1; x <= 9; x++) {
            plan.set(x0 + x, 1, z0 - 1, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            plan.set(x0 + x, 1, z0 + 7, Material.POLISHED_BLACKSTONE_BRICK_WALL);
        }
        plan.set(x0 - 1, 2, z0 - 1, Material.SOUL_LANTERN, "hanging=false");
        plan.set(x0 + 9, 2, z0 + 7, Material.SOUL_LANTERN, "hanging=false");
    }

    private static void decorate(BlockPlan plan, Random rnd, int x, int z) {
        if (!onMain(x, z) || plan.get(x, 0, z) == null || plan.get(x, 1, z) != null || Math.hypot(x, z + 6) < 5 || Math.hypot(x - spawn()[0], z - spawn()[2]) < 6) {
            return;
        }
        switch (rnd.nextInt(6)) {
            case 0 -> plan.set(x, 1, z, Material.COBBLED_DEEPSLATE_SLAB, "type=bottom");
            case 1 -> plan.set(x, 1, z, Material.BONE_BLOCK);
            case 2 -> plan.set(x, 1, z, Material.COBBLED_DEEPSLATE_WALL);
            case 3 -> plan.set(x, 1, z, Material.SKELETON_SKULL, "rotation=" + rnd.nextInt(16));
            case 4 -> plan.set(x, 1, z, Material.SCULK_SENSOR);
            default -> plan.set(x, 1, z, Material.SOUL_LANTERN, "hanging=false");
        }
    }
}
