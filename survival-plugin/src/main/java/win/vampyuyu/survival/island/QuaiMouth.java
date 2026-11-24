package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/**
 * The Devourer's mouth on the Quai des Huit (GDD 4.4): a dark rift in the south rim of the platform, between Gates 4
 * and 5 and opposite the bridge. Relative coordinates (platform centre, surface y = 0). The opening is an ellipse in
 * the plane z = {@link #Z}; a player stands in front of it (north side, the dais side). The world holds it sealed
 * (obsidian); a player who may enter sees it open (client-side block changes, see {@link SkyGates}): a magenta glass
 * membrane with a sea lantern behind it. Both are solid, like the Gates: walking up to the opening is what triggers
 * the entrance. Pure data and geometry.
 */
public final class QuaiMouth {
    /** Relative Z of the membrane plane. */
    public static final int Z = 19;
    static final double CENTER_Y = 4;
    static final double RADIUS_X = 3.0;
    static final double RADIUS_Y = 3.4;
    static final double FRAME = 1.5;

    public static final Material SEALED = Material.OBSIDIAN;
    public static final Material OPEN_MEMBRANE = Material.MAGENTA_STAINED_GLASS;
    public static final Material OPEN_LIGHT = Material.SEA_LANTERN;

    /** Cells of the membrane (z = Z) and of the backing behind it (z = Z + 1), {x, y, z}. */
    public static final List<int[]> MEMBRANE = cells(Z);
    public static final List<int[]> BACKING = cells(Z + 1);
    /** Sign in front of the mouth (the sign block is at y = 1), on the west side of the path. */
    public static final int[] SIGN = {-2, Z - 6};

    private QuaiMouth() {}

    /** Ellipse value: below 1 inside the ellipse of the given radii. */
    static double shape(int x, int y, double rx, double ry) {
        double dx = x / rx;
        double dy = (y - CENTER_Y) / ry;
        return dx * dx + dy * dy;
    }

    static boolean inside(int x, int y) {
        return shape(x, y, RADIUS_X, RADIUS_Y) <= 1;
    }

    static boolean inFrame(int x, int y) {
        return y >= 1 && shape(x, y, RADIUS_X + FRAME, RADIUS_Y + FRAME) <= 1;
    }

    private static List<int[]> cells(int z) {
        List<int[]> out = new ArrayList<>();
        for (int y = 1; y <= 8; y++) {
            for (int x = -4; x <= 4; x++) {
                if (inside(x, y)) {
                    out.add(new int[] {x, y, z});
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * Whether a position (hub world X, Y relative to the platform surface, Z relative to the platform centre) is at the
     * opening, on the dais side of it. Never overlaps a Gate zone (checked by the tests).
     */
    public static boolean inMouth(double x, double yRel, double relZ) {
        return Math.abs(x - 0.5) <= 1.9 && yRel >= -0.5 && yRel <= 7 && relZ >= Z - 1.4 && relZ < Z + 0.9;
    }

    /** Adds the structure to a plan. */
    static void build(BlockPlan plan) {
        // the way from the dais: a dark strip with a pale core, lanterns on short posts near the mouth
        for (int z = 8; z <= Z - 2; z++) {
            plan.set(0, 0, z, Material.QUARTZ_BRICKS);
            plan.set(-1, 0, z, Material.POLISHED_BLACKSTONE);
            plan.set(1, 0, z, Material.POLISHED_BLACKSTONE);
        }
        for (int sx : new int[] {-2, 2}) {
            plan.set(sx, 0, Z - 3, Material.POLISHED_BLACKSTONE_BRICKS);
            ShapeKit.column(plan, sx, 1, 2, Z - 3, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            plan.set(sx, 3, Z - 3, Material.SOUL_LANTERN, "hanging=false");
        }
        // the frame: two layers around the opening and a back wall that closes it
        for (int y = 1; y <= 9; y++) {
            for (int x = -5; x <= 5; x++) {
                if (!inFrame(x, y)) {
                    continue;
                }
                boolean open = inside(x, y);
                for (int z = Z - 1; z <= Z + 1; z++) {
                    if (open && z < Z) {
                        continue;
                    }
                    plan.set(x, y, z, open ? SEALED : stone(x, y, z));
                }
            }
        }
        // teeth: dripstone hanging from the top of the opening, stalagmites rising from its lip
        for (int x : new int[] {-2, 0, 2}) {
            int top = topOf(x);
            if (top > 0) {
                plan.set(x, top, Z - 1, Material.POINTED_DRIPSTONE, "thickness=tip,vertical_direction=down");
            }
        }
        for (int x : new int[] {-2, 2}) {
            int bottom = bottomOf(x);
            if (bottom > 1) {
                plan.set(x, bottom, Z - 1, Material.POINTED_DRIPSTONE, "thickness=tip,vertical_direction=up");
            }
        }
        plan.set(SIGN[0], 0, SIGN[1], Material.SMOOTH_QUARTZ);
        plan.sign(SIGN[0], 1, SIGN[1], Material.OAK_SIGN, "rotation=" + HubPlan.signRotation(0, -1),
            "The Mouth", "of the", "Devourer", "Sealed");
        plan.anchor("mouth", 0, 1, Z - 2);
    }

    /** Highest open cell of a column of the opening (0 when the column is not part of it). */
    static int topOf(int x) {
        for (int y = 8; y >= 1; y--) {
            if (inside(x, y)) {
                return y;
            }
        }
        return 0;
    }

    static int bottomOf(int x) {
        for (int y = 1; y <= 8; y++) {
            if (inside(x, y)) {
                return y;
            }
        }
        return 0;
    }

    private static Material stone(int x, int y, int z) {
        if (shape(x, y, RADIUS_X + 0.45, RADIUS_Y + 0.45) <= 1) {
            return Material.OBSIDIAN;
        }
        return switch (Math.floorMod(x * 31 + y * 17 + z * 7, 4)) {
            case 0 -> Material.BLACKSTONE;
            case 1 -> Material.POLISHED_BLACKSTONE_BRICKS;
            case 2 -> Material.DEEPSLATE_TILES;
            default -> Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
        };
    }
}
