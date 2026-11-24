package win.vampyuyu.survival.island;

import java.util.List;
import org.bukkit.Material;
import win.vampyuyu.survival.api.Realm;

/**
 * The return arch of a Realm landing: the same small shape in all eight Realms (two pillars and a lintel around a
 * 3 x 3 veil of stained glass, a wall sign on the left face), two blocks behind the arrival spot, only the palette
 * changes. Walking into the veil takes the player back to the Quai ({@code SkyGates}). Pure geometry, no server needed.
 *
 * <p>Everything is relative to the arrival spot {@code (sx, sy, sz)}: the spot a player stands on after the Gate
 * ({@code sy} is the standing height, the floor is {@code sy - 1}). The arch faces north (-z), like the arrival view
 * of every landing, and stands {@link #DZ} blocks south of the spot.
 */
public final class ReturnArch {
    /** Blocks between the arrival spot and the veil (the veil block is at {@code sz + DZ}). */
    public static final int DZ = 2;
    /** The veil covers {@code -1..1} around the spot's x, the pillars stand at {@code +-2}. */
    public static final int REACH = 2;
    public static final int VEIL_HEIGHT = 3;

    /**
     * Which side of the arrival spot the arch stands on: {@code out} points from the spot to the veil, {@code across}
     * runs along the veil. South (behind the arrival view) everywhere, except where a path leaves the landing there.
     */
    public enum Side {
        SOUTH(0, 1, 1, 0, "north"), EAST(1, 0, 0, 1, "west");

        final int ox, oz, ax, az;
        final String signFacing;

        Side(int ox, int oz, int ax, int az, String signFacing) {
            this.ox = ox; this.oz = oz; this.ax = ax; this.az = az; this.signFacing = signFacing;
        }

        int x(int sx, int u, int v) { return sx + ax * u + ox * v; }
        int z(int sz, int u, int v) { return sz + az * u + oz * v; }
    }

    /** The Grove's bridge to its first islet leaves the landing southwards: its arch stands east. */
    public static Side side(Realm realm) {
        return realm == Realm.GROVE ? Side.EAST : Side.SOUTH;
    }

    /** The blocks of one Realm's arch. {@code wallSign} is the wall variant of the Realm's sign wood. */
    public record Palette(Material pillar, Material cap, Material light, Material veil, Material wallSign) {}

    private ReturnArch() {}

    public static Palette palette(Realm realm) {
        return switch (realm) {
            case ASHES -> new Palette(Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE, Material.SHROOMLIGHT,
                Material.ORANGE_STAINED_GLASS, Material.OAK_WALL_SIGN);
            case GROVE -> new Palette(Material.MANGROVE_LOG, Material.MOSS_BLOCK, Material.SHROOMLIGHT,
                Material.LIME_STAINED_GLASS, Material.BIRCH_WALL_SIGN);
            case HEIGHTS -> new Palette(Material.PACKED_ICE, Material.STONE_BRICKS, Material.SEA_LANTERN,
                Material.LIGHT_BLUE_STAINED_GLASS, Material.SPRUCE_WALL_SIGN);
            case CITADEL -> new Palette(Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, Material.GLOWSTONE,
                Material.YELLOW_STAINED_GLASS, Material.BIRCH_WALL_SIGN);
            case MARSHES -> new Palette(Material.MUD_BRICKS, Material.PACKED_MUD, Material.SHROOMLIGHT,
                Material.GREEN_STAINED_GLASS, Material.BIRCH_WALL_SIGN);
            case ARCHIPELAGO -> new Palette(Material.QUARTZ_BRICKS, Material.CHISELED_QUARTZ_BLOCK, Material.SEA_LANTERN,
                Material.WHITE_STAINED_GLASS, Material.OAK_WALL_SIGN);
            case DEPTHS -> new Palette(Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE, Material.SEA_LANTERN,
                Material.BLUE_STAINED_GLASS, Material.OAK_WALL_SIGN);
            case NECROPOLIS -> new Palette(Material.DEEPSLATE_TILES, Material.CRYING_OBSIDIAN, Material.SEA_LANTERN,
                Material.PURPLE_STAINED_GLASS, Material.BIRCH_WALL_SIGN);
        };
    }

    /** The Realm's name as it fits on a sign line (about 15 characters). */
    public static String signName(Realm realm) {
        return switch (realm) {
            case CITADEL -> "Golden Sands";
            case ARCHIPELAGO -> "Archipelago";
            case NECROPOLIS -> "Necropolis";
            default -> realm.title();
        };
    }

    /** The same four lines on every landing, each short enough for one sign line. */
    public static List<String> signLines(Realm realm) {
        return List.of(signName(realm), "Walk through the", "arch to the Quai", "/hub or /island");
    }

    /** Adds the arch of a Realm to the plan, around the arrival spot. Call it after the rest of the landing. */
    public static void build(BlockPlan plan, Realm realm, int sx, int sy, int sz) {
        Palette p = palette(realm);
        Side d = side(realm);
        // free the arch's box and the strip in front of the veil (decor placed earlier must not stand in the way)
        for (int u = -REACH; u <= REACH; u++) {
            for (int y = sy; y <= sy + VEIL_HEIGHT + 1; y++) {
                plan.air(d.x(sx, u, DZ), y, d.z(sz, u, DZ));
                plan.air(d.x(sx, u, DZ - 1), y, d.z(sz, u, DZ - 1));
            }
        }
        for (int u = -REACH; u <= REACH; u++) {
            plan.set(d.x(sx, u, DZ), sy - 1, d.z(sz, u, DZ), p.pillar());
            plan.set(d.x(sx, u, DZ), sy + VEIL_HEIGHT, d.z(sz, u, DZ), p.cap());
        }
        for (int y = sy; y < sy + VEIL_HEIGHT; y++) {
            plan.set(d.x(sx, -REACH, DZ), y, d.z(sz, -REACH, DZ), p.pillar());
            plan.set(d.x(sx, REACH, DZ), y, d.z(sz, REACH, DZ), p.pillar());
        }
        plan.set(d.x(sx, -REACH, DZ), sy + VEIL_HEIGHT + 1, d.z(sz, -REACH, DZ), p.light());
        plan.set(d.x(sx, REACH, DZ), sy + VEIL_HEIGHT + 1, d.z(sz, REACH, DZ), p.light());
        plan.set(d.x(sx, 0, DZ), sy + VEIL_HEIGHT + 1, d.z(sz, 0, DZ), p.light()); // keystone: an arch, not a box
        for (int[] cell : veilCells(realm, sx, sy, sz)) {
            plan.set(cell[0], cell[1], cell[2], p.veil());
        }
        List<String> lines = signLines(realm);
        plan.sign(d.x(sx, REACH, DZ - 1), sy + 1, d.z(sz, REACH, DZ - 1), p.wallSign(), "facing=" + d.signFacing,
            lines.get(0), lines.get(1), lines.get(2), lines.get(3));
    }

    /** The glass cells of the veil, as {x, y, z}. */
    public static int[][] veilCells(Realm realm, int sx, int sy, int sz) {
        Side d = side(realm);
        int[][] cells = new int[3 * VEIL_HEIGHT][];
        int i = 0;
        for (int u = -1; u <= 1; u++) {
            for (int y = sy; y < sy + VEIL_HEIGHT; y++) {
                cells[i++] = new int[] {d.x(sx, u, DZ), y, d.z(sz, u, DZ)};
            }
        }
        return cells;
    }

    /** Centre of the veil in world coordinates, for particles. */
    public static double[] veilCentre(Realm realm, int sx, int sy, int sz) {
        Side d = side(realm);
        return new double[] {d.x(sx, 0, DZ) + 0.5, sy + VEIL_HEIGHT / 2.0, d.z(sz, 0, DZ) + 0.5};
    }

    /**
     * Whether a position touches the veil: on the face toward the arrival spot, within the opening and a little
     * beyond the pillars, up to the lintel. The arrival spot itself is a full block outside it.
     */
    public static boolean inVeil(Realm realm, double x, double y, double z, int sx, int sy, int sz) {
        Side d = side(realm);
        double dx = x - (sx + 0.5);
        double dz = z - (sz + 0.5);
        double u = d.ax * dx + d.az * dz;
        double v = d.ox * dx + d.oz * dz;
        return Math.abs(u) <= 1.8 && y >= sy - 0.5 && y <= sy + VEIL_HEIGHT && v >= DZ - 1.0 && v <= DZ + 0.5;
    }
}
