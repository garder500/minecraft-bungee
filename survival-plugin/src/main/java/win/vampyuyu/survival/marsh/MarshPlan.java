package win.vampyuyu.survival.marsh;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.ShapeKit;

/**
 * The Putrid Marshes (GDD 3.1), relative coordinates, surface at y = 0: a sunken bog island of mud and moss with
 * stagnant pools and dead mangroves, four islets joined by plank causeways (the Vat Works, the Landfill, the Weeping
 * Mangrove where Rot Resin oozes, the Overflow Pit), the Rot Vats where expired items are salvaged, and a landing at
 * the Sky Gate. Pure data, like the other Realm plans.
 */
public final class MarshPlan {
    /** Bump when the layout changes so an existing Marsh is rebuilt (the marker file stores it). */
    public static final int VERSION = 1;
    public static final int ORIGIN_Y = 100;
    public static final int MAIN_RADIUS = 22;
    public static final int ISLET_ORBIT = 40;
    public static final int ISLET_RADIUS = 8;
    public static final double BORDER = 140;
    private static final long SEED = 0x6120_0043L;
    /** Landing platform, where the Gate drops the player. */
    public static final int[] SPAWN = {0, 1, 15};

    /** A place of the plan, relative to the origin. */
    public record Spot(int x, int y, int z) {}

    /** The Rot Vats: right-click one with expired items to salvage them. */
    public static final List<Spot> VATS = List.of(new Spot(7, 1, 12), new Spot(-7, 1, 12), new Spot(0, 1, -2));

    private MarshPlan() {}

    /** Centre of an islet (x, z), on a circle around the main island. */
    public static int[] isletCenter(int index) {
        double angle = Math.toRadians(45 + 90.0 * index);
        return new int[] {(int) Math.round(ISLET_ORBIT * Math.cos(angle)), (int) Math.round(ISLET_ORBIT * Math.sin(angle))};
    }

    public static BlockPlan build(int maxBlocks) {
        BlockPlan plan = new BlockPlan(maxBlocks);
        Random rnd = new Random(SEED);

        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        ShapeKit.floatingIsland(plan, outline, MAIN_RADIUS, 10, d -> d == 0 ? (rnd.nextInt(3) == 0 ? Material.MOSS_BLOCK
            : Material.MUD) : d < 4 ? Material.MUD : d < 7 ? Material.PACKED_MUD : Material.MUDDY_MANGROVE_ROOTS);

        for (int i = 0; i < 5; i++) {
            pool(plan, rnd, i);
        }
        for (int i = 0; i < 4; i++) {
            int[] c = isletCenter(i);
            islet(plan, new Random(SEED + i), c[0], c[1], i);
            causeway(plan, c[0], c[1]);
        }
        for (int i = 0; i < 70; i++) {
            decorate(plan, rnd, rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS, rnd.nextInt(2 * MAIN_RADIUS) - MAIN_RADIUS);
        }
        for (Spot vat : VATS) {
            vat(plan, vat);
        }

        // landing platform at the Sky Gate
        ShapeKit.disc(plan, SPAWN[0], 0, SPAWN[2] - 1, 4.5, Material.MUD_BRICKS);
        ShapeKit.ring(plan, SPAWN[0], 0, SPAWN[2] - 1, 3.2, 4.4, Material.PACKED_MUD);
        plan.set(SPAWN[0], 0, SPAWN[2] - 1, Material.SHROOMLIGHT);
        plan.sign(SPAWN[0] + 2, 1, SPAWN[2], Material.MANGROVE_SIGN, "rotation=0", "Putrid Marshes", "/hub to leave", "Rot Vats: salvage",
            "expired items");
        plan.anchor("spawn", SPAWN[0], SPAWN[1], SPAWN[2]);
        plan.biome(-MAIN_RADIUS - 60, -20, -MAIN_RADIUS - 60, MAIN_RADIUS + 60, 30, MAIN_RADIUS + 60, "minecraft:mangrove_swamp");
        return plan;
    }

    /** True when (x, z) lies on the main island. */
    public static boolean onMain(int x, int z) {
        return ShapeKit.inside(null, MAIN_RADIUS * ShapeKit.Outline.minFactor(), x, z);
    }

    private static void pool(BlockPlan plan, Random rnd, int index) {
        double angle = 72.0 * index + 20;
        int cx = ShapeKit.polarX(11, angle);
        int cz = ShapeKit.polarZ(11, angle) - 2;
        ShapeKit.disc(plan, cx, 0, cz, 3.5, Material.CLAY);
        ShapeKit.disc(plan, cx, 0, cz, 2.6, Material.WATER);
        plan.set(cx, 1, cz, Material.LILY_PAD);
        if (rnd.nextBoolean()) {
            plan.set(cx + 1, 1, cz - 1, Material.SLIME_BLOCK);
        }
    }

    private static void vat(BlockPlan plan, Spot at) {
        ShapeKit.disc(plan, at.x(), 0, at.z(), 2.2, Material.MUD_BRICKS);
        plan.set(at.x(), at.y(), at.z(), Material.WATER_CAULDRON, "level=3");
        plan.set(at.x() + 1, 1, at.z(), Material.MANGROVE_FENCE);
        plan.set(at.x() - 1, 1, at.z(), Material.MANGROVE_FENCE);
        plan.set(at.x() + 1, 2, at.z(), Material.LANTERN, "hanging=false");
    }

    private static void islet(BlockPlan plan, Random rnd, int cx, int cz, int index) {
        ShapeKit.Outline outline = new ShapeKit.Outline(rnd);
        BlockPlan local = new BlockPlan();
        ShapeKit.floatingIsland(local, outline, ISLET_RADIUS, 6, d -> d == 0 ? Material.MUD : d < 3 ? Material.PACKED_MUD : Material.MUDDY_MANGROVE_ROOTS);
        local.blocks().forEach((key, blk) -> plan.set(cx + BlockPlan.keyX(key), BlockPlan.keyY(key), cz + BlockPlan.keyZ(key), blk));
        switch (index) {
            case 0 -> { // Vat Works: stained brick chimneys over the pipes
                for (int side : new int[] {-3, 3}) {
                    ShapeKit.column(plan, cx + side, 1, 6, cz, Material.MUD_BRICKS);
                    plan.set(cx + side, 7, cz, Material.CAMPFIRE, "lit=true");
                }
                for (int x = -3; x <= 3; x++) {
                    plan.set(cx + x, 1, cz + 3, Material.COPPER_BLOCK);
                    plan.set(cx + x, 2, cz + 3, Material.WEATHERED_COPPER);
                }
            }
            case 1 -> { // Landfill: heaps of discarded blocks
                Material[] junk = {Material.COARSE_DIRT, Material.GRAVEL, Material.COBBLED_DEEPSLATE, Material.ROOTED_DIRT, Material.OAK_PLANKS};
                for (int i = 0; i < 14; i++) {
                    int x = cx + rnd.nextInt(11) - 5;
                    int z = cz + rnd.nextInt(11) - 5;
                    int h = 1 + rnd.nextInt(3);
                    ShapeKit.column(plan, x, 1, h, z, junk[rnd.nextInt(junk.length)]);
                }
            }
            case 2 -> { // Weeping Mangrove: Rot Resin oozes from the roots
                ShapeKit.column(plan, cx, 1, 8, cz, Material.MANGROVE_LOG);
                ShapeKit.leaves(plan, cx, 9, cz, 4.4, Material.MANGROVE_LEAVES);
                for (int a = 0; a < 6; a++) {
                    plan.set(ShapeKit.polarX(2.5, 60 * a) + cx, 1, ShapeKit.polarZ(2.5, 60 * a) + cz, Material.MUDDY_MANGROVE_ROOTS);
                    plan.set(ShapeKit.polarX(3.5, 60 * a + 30) + cx, 1, ShapeKit.polarZ(3.5, 60 * a + 30) + cz, Material.SLIME_BLOCK);
                }
            }
            default -> { // Overflow Pit: a sunken sculk mouth behind iron bars, where the Abomination sleeps
                ShapeKit.disc(plan, cx, 0, cz, 4.5, Material.SCULK);
                ShapeKit.disc(plan, cx, 0, cz, 2.8, Material.SLIME_BLOCK);
                ShapeKit.ring(plan, cx, 1, cz, 3.0, 4.4, Material.IRON_BARS);
                plan.set(cx, 1, cz, Material.SCULK_CATALYST);
                plan.sign(cx, 2, cz + 6, Material.MANGROVE_SIGN, "rotation=0", "Overflow Pit", "Sealed. Enter the", "Marsh Bastion", "(/bastion)");
            }
        }
        plan.set(cx + 4, 1, cz, Material.SHROOMLIGHT);
    }

    private static void causeway(BlockPlan plan, int tx, int tz) {
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
            if (i % 5 == 0) {
                plan.set(x, 1, z, Material.SOUL_LANTERN, "hanging=false");
            }
        }
    }

    private static void decorate(BlockPlan plan, Random rnd, int x, int z) {
        if (!onMain(x, z) || plan.get(x, 0, z) == null || plan.get(x, 1, z) != null || Math.hypot(x - SPAWN[0], z - SPAWN[2]) < 7) {
            return;
        }
        for (Spot vat : VATS) {
            if (Math.hypot(x - vat.x(), z - vat.z()) < 4) {
                return;
            }
        }
        switch (rnd.nextInt(6)) {
            case 0 -> plan.set(x, 1, z, Material.SHORT_GRASS);
            case 1 -> plan.set(x, 1, z, Material.BROWN_MUSHROOM);
            case 2 -> plan.set(x, 1, z, Material.FERN);
            case 3 -> ShapeKit.column(plan, x, 1, 3 + rnd.nextInt(3), z, Material.MANGROVE_LOG);
            case 4 -> plan.set(x, 1, z, Material.MOSS_CARPET);
            default -> plan.set(x, 1, z, Material.SHROOMLIGHT);
        }
    }
}
