package win.vampyuyu.survival.island;

import java.util.List;
import org.bukkit.Material;
import win.vampyuyu.survival.api.Realm;

/**
 * The return arch of a Realm landing: the same shape in all eight Realms (pillars five high around a 3 x 3 veil of
 * stained glass, buttresses, a pointed crown, a wall sign on the pillar, the Gate's halo turning above), two blocks
 * behind the arrival spot, only the palette changes. Walking into the veil takes the player back to the Quai ({@code SkyGates}). Pure geometry, no server needed.
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

    /** Height of the pillars over the floor (the veil is three high, the pointed crown goes above it). */
    public static final int PILLAR_TOP = 4;
    /** Height of the halo's centre over the standing level, and its scale. */
    public static final double HALO_DY = 8.5;
    static final float HALO_SCALE = 1.5f;

    /**
     * Adds the arch of a Realm to the plan, around the arrival spot. Call it after the rest of the landing. The same
     * shape everywhere: a threshold seven wide, two pillars five high around the veil, low buttresses with lights, a
     * pointed crown (lintel, a row above it, the keystone light), lights on the pillars. Over it the halo of the
     * Realm's Gate turns (a display model, {@link #models}): the way home is the Gate's own light.
     */
    public static void build(BlockPlan plan, Realm realm, int sx, int sy, int sz) {
        Palette p = palette(realm);
        Side d = side(realm);
        // free the arch's box and the strip in front of the veil (decor placed earlier must not stand in the way)
        for (int u = -REACH - 1; u <= REACH + 1; u++) {
            for (int y = sy; y <= sy + PILLAR_TOP + 2; y++) {
                plan.air(d.x(sx, u, DZ), y, d.z(sz, u, DZ));
                if (Math.abs(u) <= REACH) {
                    plan.air(d.x(sx, u, DZ - 1), y, d.z(sz, u, DZ - 1));
                }
            }
        }
        for (int u = -REACH - 1; u <= REACH + 1; u++) {
            plan.set(d.x(sx, u, DZ), sy - 1, d.z(sz, u, DZ), p.pillar());
        }
        // pillars, five high, and their lights
        for (int side : new int[] {-REACH, REACH}) {
            for (int y = sy; y <= sy + PILLAR_TOP; y++) {
                plan.set(d.x(sx, side, DZ), y, d.z(sz, side, DZ), p.pillar());
            }
            plan.set(d.x(sx, side, DZ), sy + PILLAR_TOP + 1, d.z(sz, side, DZ), p.light());
            // a low buttress outside each pillar, a light on it
            int out = side < 0 ? side - 1 : side + 1;
            plan.set(d.x(sx, out, DZ), sy, d.z(sz, out, DZ), p.pillar());
            plan.set(d.x(sx, out, DZ), sy + 1, d.z(sz, out, DZ), p.cap());
            plan.set(d.x(sx, out, DZ), sy + 2, d.z(sz, out, DZ), p.light());
        }
        // the pointed crown: the lintel over the veil, a narrower row, the keystone
        for (int u = -1; u <= 1; u++) {
            plan.set(d.x(sx, u, DZ), sy + VEIL_HEIGHT, d.z(sz, u, DZ), p.cap());
        }
        plan.set(d.x(sx, -1, DZ), sy + VEIL_HEIGHT + 1, d.z(sz, -1, DZ), p.cap());
        plan.set(d.x(sx, 1, DZ), sy + VEIL_HEIGHT + 1, d.z(sz, 1, DZ), p.cap());
        plan.set(d.x(sx, 0, DZ), sy + VEIL_HEIGHT + 1, d.z(sz, 0, DZ), p.light());
        plan.set(d.x(sx, 0, DZ), sy + VEIL_HEIGHT + 2, d.z(sz, 0, DZ), p.cap());
        for (int[] cell : veilCells(realm, sx, sy, sz)) {
            plan.set(cell[0], cell[1], cell[2], p.veil());
        }
        List<String> lines = signLines(realm);
        plan.sign(d.x(sx, REACH, DZ - 1), sy + 1, d.z(sz, REACH, DZ - 1), p.wallSign(), "facing=" + d.signFacing,
            lines.get(0), lines.get(1), lines.get(2), lines.get(3));
    }

    /**
     * The display model over a Realm's arch, in world coordinates: the lit halo of its Gate (the Quai's model), in the
     * Realm's colour, turning in the plane of the veil, seen by everybody (the way home is always open).
     */
    public static List<BlockPlan.Model> models(Realm realm, int sx, int sy, int sz) {
        Side d = side(realm);
        double x = d.x(sx, 0, DZ) + 0.5;
        double z = d.z(sz, 0, DZ) + 0.5;
        float yaw = (float) Math.toDegrees(Math.atan2(d.ox, d.oz));
        return List.of(BlockPlan.Model.of("structure/quai_halo_lit", x, sy + HALO_DY, z, HALO_SCALE).tilt(0, yaw, 0)
            .spin(16, "z").color(QuaiPlan.haloColor(realm)));
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
