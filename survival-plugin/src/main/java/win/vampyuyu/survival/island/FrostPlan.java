package win.vampyuyu.survival.island;

import java.util.Random;
import org.bukkit.Material;

/**
 * The Frozen Heights (GDD 3.1), relative coordinates, surface at y = 0: a high island of snow and packed ice held up by
 * hanging icicles, six smaller peaks around it joined by ice bridges, fragments of frozen cities (glass, bricks,
 * lanterns suspended in the ice), a cooling yard for the advanced cooling machines and a landing platform at the Sky
 * Gate. The Boreal Watcher stands under the northern arch. Pure data, like {@link GrovePlan}.
 */
public final class FrostPlan {
    /** Bump when the layout changes so an existing zone is rebuilt (the marker file stores it). */
    public static final int VERSION = 1;
    public static final int MAIN_RADIUS = 24;
    public static final int PEAK_COUNT = 6;
    public static final int PEAK_ORBIT = 44;
    public static final int PEAK_RADIUS = 9;
    public static final int BORDER = 120;
    private static final long SEED = 0x6120_0041L;

    private FrostPlan() {}

    /** Where players arrive from the Sky Gate, relative to the centre: the landing platform on the south side. */
    public static int[] spawn() {
        return new int[] {0, 1, MAIN_RADIUS - 6};
    }

    /** Centre of a peak (x, z), on a circle around the main island. */
    public static int[] peakCenter(int index) {
        double angle = Math.toRadians(30 + 60.0 * index);
        return new int[] {(int) Math.round(PEAK_ORBIT * Math.cos(angle)), (int) Math.round(PEAK_ORBIT * Math.sin(angle))};
    }

    public static BlockPlan build(int maxBlocks) {
        BlockPlan plan = new BlockPlan(maxBlocks);
        Random rnd = new Random(SEED);

        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, MAIN_RADIUS, 12, d -> d == 0 ? Material.SNOW_BLOCK : d < 4 ? Material.PACKED_ICE
            : d < 7 ? Material.BLUE_ICE : Material.STONE);
        for (int i = 0; i < 7; i++) {
            icicle(plan, rnd, ShapeKit.polarX(MAIN_RADIUS * 0.6, 51.4 * i), ShapeKit.polarZ(MAIN_RADIUS * 0.6, 51.4 * i), 14 + rnd.nextInt(8));
        }

        for (int i = 0; i < PEAK_COUNT; i++) {
            int[] c = peakCenter(i);
            peak(plan, new Random(SEED + i), c[0], c[1], i);
            bridge(plan, c[0], c[1]);
        }

        arch(plan);
        coolingYard(plan, 10, -8);

        // snow drifts and ice outcrops on the main island
        for (int i = 0; i < 90; i++) {
            int x = rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS;
            int z = rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS;
            decorate(plan, rnd, x, z);
        }

        // landing platform at the Sky Gate
        int[] s = spawn();
        ShapeKit.disc(plan, s[0], 0, s[2], 4.5, Material.PACKED_ICE);
        ShapeKit.ring(plan, s[0], 0, s[2], 3.2, 4.4, Material.STONE_BRICKS);
        plan.set(s[0], 0, s[2], Material.SEA_LANTERN);
        plan.sign(s[0] + 2, 1, s[2] + 1, Material.SPRUCE_SIGN, "rotation=0", "Frozen Heights", "/hub to leave", "/island to go home", "");
        plan.anchor("spawn", s[0], s[1], s[2]);
        plan.anchor("watcher", 0, 1, -6);
        plan.biome(-MAIN_RADIUS - 4, -20, -MAIN_RADIUS - 4, MAIN_RADIUS + 4, 12, MAIN_RADIUS + 4, "minecraft:frozen_peaks");
        return plan;
    }

    /** True when (x, z) lies on the main island (used to keep the structures inside the snow). */
    public static boolean onMain(int x, int z) {
        return ShapeKit.inside(null, MAIN_RADIUS * ShapeKit.Outline.minFactor(), x, z);
    }

    private static void icicle(BlockPlan plan, Random rnd, int x, int z, int length) {
        int cx = x;
        int cz = z;
        for (int d = 6; d <= 6 + length; d++) {
            plan.set(cx, -d, cz, d % 5 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE);
            if (d % 3 == 0) {
                cx += rnd.nextInt(3) - 1;
                cz += rnd.nextInt(3) - 1;
            }
        }
        plan.set(cx, -7 - length, cz, Material.POINTED_DRIPSTONE, "vertical_direction=down,thickness=tip");
    }

    private static void peak(BlockPlan plan, Random rnd, int cx, int cz, int index) {
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        BlockPlan local = new BlockPlan();
        ShapeKit.floatingIsland(local, outline, PEAK_RADIUS, 6, d -> d == 0 ? Material.SNOW_BLOCK : d < 3 ? Material.PACKED_ICE : Material.STONE);
        local.blocks().forEach((key, blk) -> plan.set(cx + BlockPlan.keyX(key), BlockPlan.keyY(key), cz + BlockPlan.keyZ(key), blk));
        switch (index % 3) {
            case 0 -> {
                // crystal spire: the Stasis Crystals grow here
                ShapeKit.column(plan, cx, 1, 9, cz, Material.BLUE_ICE);
                ShapeKit.column(plan, cx, 10, 11, cz, Material.AMETHYST_BLOCK);
                plan.set(cx, 12, cz, Material.AMETHYST_CLUSTER, "facing=up");
                plan.set(cx + 1, 3, cz, Material.SEA_LANTERN);
            }
            case 1 -> {
                // a fragment of a frozen city: a few walls, a broken window, a lantern held in the ice
                ShapeKit.ring(plan, cx, 1, cz, 3.0, 4.4, Material.STONE_BRICKS);
                ShapeKit.ring(plan, cx, 2, cz, 3.0, 4.4, Material.LIGHT_BLUE_STAINED_GLASS);
                ShapeKit.ring(plan, cx, 3, cz, 3.0, 4.4, Material.STONE_BRICKS);
                plan.set(cx, 1, cz, Material.ICE);
                plan.set(cx, 2, cz, Material.LANTERN, "hanging=false");
            }
            default -> {
                // frozen pool
                ShapeKit.disc(plan, cx, 0, cz, 3.5, Material.BLUE_ICE);
                ShapeKit.disc(plan, cx, 0, cz, 2.5, Material.ICE);
                plan.set(cx, 1, cz, Material.SNOW);
            }
        }
        plan.set(cx + 4, 1, cz, Material.SEA_LANTERN);
    }

    private static void bridge(BlockPlan plan, int tx, int tz) {
        double len = Math.hypot(tx, tz);
        int steps = (int) len;
        for (int i = (int) (MAIN_RADIUS * 0.9); i < steps - PEAK_RADIUS + 2; i++) {
            double f = i / len;
            int x = (int) Math.round(tx * f);
            int z = (int) Math.round(tz * f);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    plan.set(x + dx, 0, z + dz, Material.PACKED_ICE);
                }
            }
            if (i % 4 == 0) {
                plan.set(x, 1, z, Material.LANTERN, "hanging=false");
                plan.set(x, -1, z, Material.BLUE_ICE);
            }
        }
    }

    private static void arch(BlockPlan plan) {
        for (int side : new int[] {-3, 3}) {
            ShapeKit.column(plan, side, 1, 7, -8, Material.BLUE_ICE);
            ShapeKit.column(plan, side, 1, 7, -4, Material.BLUE_ICE);
        }
        for (int x = -4; x <= 4; x++) {
            plan.set(x, 8, -8, Material.PACKED_ICE);
            plan.set(x, 8, -4, Material.PACKED_ICE);
            for (int z = -8; z <= -4; z++) {
                plan.set(x, 9, z, Material.SNOW_BLOCK);
            }
        }
        ShapeKit.disc(plan, 0, 0, -6, 2.4, Material.POLISHED_DEEPSLATE);
        plan.set(0, 1, -6, Material.SEA_LANTERN);
    }

    /** The cooling yard: a basin of ice with a hopper-fed cauldron row where players set up cooled machines. */
    private static void coolingYard(BlockPlan plan, int x0, int z0) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 7; z++) {
                plan.set(x0 + x, 0, z0 + z, (x + z) % 2 == 0 ? Material.PACKED_ICE : Material.BLUE_ICE);
            }
        }
        for (int x = -1; x <= 9; x++) {
            plan.set(x0 + x, 1, z0 - 1, Material.IRON_BARS);
            plan.set(x0 + x, 1, z0 + 7, Material.IRON_BARS);
        }
        plan.set(x0 - 1, 2, z0 - 1, Material.LANTERN);
        plan.set(x0 + 9, 2, z0 + 7, Material.LANTERN);
        plan.set(x0 + 4, 1, z0 + 3, Material.POWDER_SNOW_CAULDRON, "level=3");
    }

    private static void decorate(BlockPlan plan, Random rnd, int x, int z) {
        if (!onMain(x, z) || plan.get(x, 0, z) == null || plan.get(x, 1, z) != null || Math.hypot(x, z + 6) < 5 || Math.hypot(x - spawn()[0], z - spawn()[2]) < 6) {
            return;
        }
        switch (rnd.nextInt(6)) {
            case 0 -> plan.set(x, 1, z, Material.SNOW, "layers=2");
            case 1 -> plan.set(x, 1, z, Material.SNOW, "layers=1");
            case 2 -> plan.set(x, 1, z, Material.PACKED_ICE);
            case 3 -> plan.set(x, 1, z, Material.BLUE_ICE);
            case 4 -> plan.set(x, 1, z, Material.AMETHYST_CLUSTER, "facing=up");
            default -> plan.set(x, 1, z, Material.SEA_LANTERN);
        }
    }
}
