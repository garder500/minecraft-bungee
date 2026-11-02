package win.vampyuyu.survival.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.ShapeKit;

/**
 * The Tempestuous Archipelago (GDD 3.1, 12.5): seven islands in a storm, joined by moving platforms and propulsion
 * pads instead of bridges. Pure data like {@code AshesPlan}: coordinates are relative to the build origin, the landing
 * surface is at y = 0, every island has its own height. Everything gameplay needs (landmarks, Thunder Quartz nodes,
 * pads, platform tracks, the Sarcophagus seal) is listed here so the build and the rules cannot drift apart.
 */
public final class StormPlan {
    /** Bump to rebuild existing worlds when the layout changes. */
    public static final int VERSION = 1;
    /** World y of the landing surface. */
    public static final int ORIGIN_Y = 120;
    public static final int LANDING_RADIUS = 13;
    public static final int CAP = 60_000;
    /** A player under this height (relative to the origin) has fallen into the void. */
    public static final int VOID_Y = -40;

    /** Half size of a moving platform: it is a (2 x half + 1) square, two blocks thick. */
    public static final int PLATFORM_HALF = 2;
    public static final double PLATFORM_STEPS_PER_SECOND = 4;
    public static final int PLATFORM_DWELL_TICKS = 120;

    private StormPlan() {}

    /** A floating island. {@code y} is the height of its top layer, {@code rx, rz} where the rescue point is (relative to its centre). */
    public record Islet(String id, String name, int x, int y, int z, int radius) {
        /** Where a fallen player is put back: halfway to the rim on the north side, one block above the top layer. */
        public int[] rescue() {
            return new int[] {x, y + 1, z - radius / 2};
        }

        /** True when the column is over the island or its rim. */
        public boolean contains(double px, double pz, double margin) {
            return Math.hypot(px - x - 0.5, pz - z - 0.5) <= radius + margin;
        }
    }

    /** A Thunder Quartz node (a block of the island that is cut with a pickaxe and grows back). */
    public record Spot(String id, int x, int y, int z) {}

    /** A moving platform between two islands. */
    public record Platform(String id, String from, String to, PlatformMath.Track track) {}

    /** A propulsion pad: the centre of its 3 x 3 floor, and the point it throws the player to (feet). */
    public record Pad(String id, String from, String to, int x, int y, int z, double tx, double ty, double tz) {
        public PadMath.Launch launch() {
            return PadMath.launch(tx - (x + 0.5), ty - (y + 1), tz - (z + 0.5));
        }
    }

    public static final Islet LANDING = new Islet("landing", "Gale Landing", 0, 0, 0, LANDING_RADIUS);

    public static final List<Islet> ISLETS = List.of(
        LANDING,
        new Islet("vane", "Weathervane Isle", 45, 2, -10, 9),
        new Islet("field", "Rod Field", 76, 4, -2, 12),
        new Islet("cirrus", "Cirrus Shelf", 120, 10, 17, 10),
        new Islet("maelstrom", "Maelstrom Heart", 150, 8, 6, 11),
        new Islet("roost", "Roc Roost", 196, 15, 23, 10),
        new Islet("eyrie", "Dragon's Eyrie", 232, 20, 11, 16));

    /** The journey: platform, pad, platform, pad, platform, pad. Pads work both ways, so each one has a return pad. */
    public static final List<Platform> PLATFORMS = List.of(
        platform("landing", "vane"), platform("field", "cirrus"), platform("maelstrom", "roost"));

    public static final List<Pad> PADS = pads();

    public static final List<Spot> NODES = nodes();

    /** Where players arrive. */
    public static final int[] SPAWN = {0, 1, 5};

    /** The seal of the Sarcophagus: right-click it to enter the Tempest Bastion. */
    public static final int[] SEAL = {232, 21, 11 - 13};

    public static Islet at(String id) {
        for (Islet islet : ISLETS) {
            if (islet.id().equals(id)) {
                return islet;
            }
        }
        throw new IllegalArgumentException(id);
    }

    /** The island whose top the point is over (feet within a few blocks of its surface), or null. */
    public static Islet isletUnder(double x, double y, double z) {
        for (Islet islet : ISLETS) {
            if (islet.contains(x, z, 1.5) && y >= islet.y() - 1 && y <= islet.y() + 6) {
                return islet;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- platforms and pads

    private static double[] unit(Islet a, Islet b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double len = Math.hypot(dx, dz);
        return new double[] {dx / len, dz / len, len};
    }

    /** A platform from the rim of one island to the rim of the other, never overlapping either. */
    private static Platform platform(String from, String to) {
        Islet a = at(from);
        Islet b = at(to);
        double[] u = unit(a, b);
        double half = PLATFORM_HALF * (Math.abs(u[0]) + Math.abs(u[1])) + 1.2;
        double d0 = a.radius() + half;
        double d1 = u[2] - b.radius() - half;
        PlatformMath.Track track = new PlatformMath.Track(
            (int) Math.round(a.x() + u[0] * d0), a.y(), (int) Math.round(a.z() + u[1] * d0),
            (int) Math.round(a.x() + u[0] * d1), b.y(), (int) Math.round(a.z() + u[1] * d1));
        return new Platform(from + "-" + to, from, to, track);
    }

    private static List<Pad> pads() {
        List<Pad> list = new ArrayList<>();
        for (String[] pair : new String[][] {{"vane", "field"}, {"cirrus", "maelstrom"}, {"roost", "eyrie"}}) {
            list.add(pad(pair[0], pair[1]));
            list.add(pad(pair[1], pair[0]));
        }
        return List.copyOf(list);
    }

    private static Pad pad(String from, String to) {
        Islet a = at(from);
        Islet b = at(to);
        double[] u = unit(a, b);
        double inset = a.radius() - 3;
        int px = (int) Math.round(a.x() + u[0] * inset);
        int pz = (int) Math.round(a.z() + u[1] * inset);
        double tx = b.x() - u[0] * (b.radius() - 4) + 0.5;
        double tz = b.z() - u[1] * (b.radius() - 4) + 0.5;
        return new Pad(from + ">" + to, from, to, px, a.y(), pz, tx, b.y() + 1, tz);
    }

    private static List<Spot> nodes() {
        List<Spot> list = new ArrayList<>();
        addNodes(list, at("field"), 6, 8, 20);
        addNodes(list, at("maelstrom"), 4, 7, 45);
        addNodes(list, at("roost"), 4, 6, 20);
        return List.copyOf(list);
    }

    private static void addNodes(List<Spot> list, Islet islet, int count, int radius, int startDegrees) {
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(startDegrees + 360.0 * i / count);
            list.add(new Spot("node:" + islet.id() + i, islet.x() + (int) Math.round(radius * Math.sin(a)), islet.y(),
                islet.z() + (int) Math.round(-radius * Math.cos(a))));
        }
    }

    // ---------------------------------------------------------------- build

    public static BlockPlan build() {
        BlockPlan plan = new BlockPlan(CAP);
        Random rnd = new Random(6021L);
        for (Islet islet : ISLETS) {
            buildIslet(plan, islet, rnd);
        }
        landing(plan);
        vane(plan, at("vane"));
        field(plan, at("field"));
        cirrus(plan, at("cirrus"));
        maelstrom(plan, at("maelstrom"));
        roost(plan, at("roost"));
        eyrie(plan, at("eyrie"));
        for (Spot node : NODES) {
            plan.set(node.x(), node.y(), node.z(), Material.CHISELED_QUARTZ_BLOCK);
            plan.anchor(node.id(), node.x(), node.y(), node.z());
        }
        for (Pad pad : PADS) {
            buildPad(plan, pad);
        }
        plan.anchor("spawn", SPAWN[0], SPAWN[1], SPAWN[2]);
        return plan;
    }

    private static void buildIslet(BlockPlan plan, Islet islet, Random rnd) {
        int depth = islet == LANDING ? 9 : 5 + islet.radius() / 3;
        for (int k = 0; k <= depth; k++) {
            double r = islet.radius() * (1 - Math.pow(k / (depth + 1.0), 1.5));
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.25) {
                        continue;
                    }
                    plan.set(islet.x() + dx, islet.y() - k, islet.z() + dz, k == 0 ? top(islet, rnd) : body(k, rnd));
                }
            }
        }
    }

    private static Material top(Islet islet, Random rnd) {
        if (islet == LANDING) {
            return rnd.nextInt(5) == 0 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
        }
        double roll = rnd.nextDouble();
        return roll < 0.5 ? Material.ANDESITE : roll < 0.8 ? Material.STONE : roll < 0.95 ? Material.LIGHT_GRAY_CONCRETE
            : Material.WAXED_OXIDIZED_COPPER;
    }

    private static Material body(int k, Random rnd) {
        if (k >= 2 && rnd.nextInt(30) == 0) {
            return Material.WAXED_OXIDIZED_COPPER;
        }
        return k <= 2 ? Material.STONE : k <= 5 ? Material.COBBLESTONE : Material.COBBLED_DEEPSLATE;
    }

    private static void landing(BlockPlan plan) {
        ShapeKit.ring(plan, 0, 0, 0, LANDING_RADIUS - 1.5, LANDING_RADIUS - 0.5, Material.LIGHT_BLUE_CONCRETE);
        ShapeKit.disc(plan, 0, 0, 0, 2.5, Material.QUARTZ_BRICKS);
        for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            plan.set(d[0], 0, d[1], Material.SEA_LANTERN);
        }
        plan.set(0, 1, 0, Material.LODESTONE);
        plan.sign(0, 1, 3, Material.OAK_SIGN, "rotation=0", "Gate 6", "Tempestuous Archipelago", "Gale Landing", "/quai to return");
    }

    private static void vane(BlockPlan plan, Islet at) {
        for (int y = 1; y <= 14; y++) {
            plan.set(at.x(), at.y() + y, at.z(), y <= 12 ? Material.WAXED_OXIDIZED_COPPER : Material.LIGHTNING_ROD, y <= 12 ? null : "facing=up");
        }
        plan.fill(at.x() - 3, at.y() + 11, at.z(), at.x() + 3, at.y() + 11, at.z(), Material.IRON_BARS);
        plan.fill(at.x(), at.y() + 11, at.z() - 3, at.x(), at.y() + 11, at.z() + 3, Material.IRON_BARS);
        plan.set(at.x() + 3, at.y() + 12, at.z(), Material.LIGHTNING_ROD, "facing=up");
        plan.set(at.x() - 3, at.y() + 12, at.z(), Material.LIGHTNING_ROD, "facing=up");
    }

    private static void field(BlockPlan plan, Islet at) {
        for (int i = 0; i < 9; i++) {
            double a = Math.toRadians(40 * i + 10);
            int x = at.x() + (int) Math.round(4.5 * Math.sin(a));
            int z = at.z() + (int) Math.round(-4.5 * Math.cos(a));
            plan.set(x, at.y() + 1, z, Material.WAXED_COPPER_BLOCK);
            for (int h = 2; h <= 5; h++) {
                plan.set(x, at.y() + h, z, Material.LIGHTNING_ROD, "facing=up");
            }
        }
        ShapeKit.disc(plan, at.x(), at.y(), at.z(), 2.5, Material.WAXED_EXPOSED_CUT_COPPER);
    }

    private static void cirrus(BlockPlan plan, Islet at) {
        ShapeKit.disc(plan, at.x(), at.y(), at.z(), 4.5, Material.WHITE_CONCRETE);
        ShapeKit.ring(plan, at.x(), at.y() + 1, at.z(), 4, 4.5, Material.WHITE_STAINED_GLASS);
        for (int y = 1; y <= 5; y++) {
            plan.set(at.x(), at.y() + y, at.z(), y == 5 ? Material.SEA_LANTERN : Material.CHISELED_QUARTZ_BLOCK);
        }
    }

    private static void maelstrom(BlockPlan plan, Islet at) {
        for (int y = 1; y <= 22; y++) {
            double r = 2 + y * 0.32;
            for (int k = 0; k < 8; k++) {
                double a = Math.toRadians(y * 38 + k * 45);
                plan.set(at.x() + (int) Math.round(r * Math.cos(a)), at.y() + y, at.z() + (int) Math.round(r * Math.sin(a)),
                    k % 2 == 0 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.WHITE_STAINED_GLASS);
            }
        }
        plan.set(at.x(), at.y() + 1, at.z(), Material.WAXED_OXIDIZED_COPPER);
        plan.set(at.x(), at.y() + 2, at.z(), Material.LIGHTNING_ROD, "facing=up");
    }

    private static void roost(BlockPlan plan, Islet at) {
        ShapeKit.disc(plan, at.x(), at.y() + 1, at.z(), 4, Material.HAY_BLOCK);
        ShapeKit.ring(plan, at.x(), at.y() + 2, at.z(), 3.5, 4.5, Material.DARK_OAK_FENCE);
        ShapeKit.disc(plan, at.x(), at.y() + 2, at.z(), 2, Material.AIR);
        plan.set(at.x(), at.y() + 2, at.z(), Material.TURTLE_EGG);
        plan.sign(at.x(), at.y() + 1, at.z() + 7, Material.OAK_SIGN, "rotation=0", "Roc Roost", "The Storm Roc", "nests here.", "");
    }

    private static void eyrie(BlockPlan plan, Islet at) {
        ShapeKit.ring(plan, at.x(), at.y(), at.z(), 12, 13.5, Material.WAXED_OXIDIZED_CUT_COPPER);
        ShapeKit.disc(plan, at.x(), at.y(), at.z(), 4, Material.POLISHED_DEEPSLATE);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(45 * i + 22.5);
            int x = at.x() + (int) Math.round(11 * Math.sin(a));
            int z = at.z() + (int) Math.round(-11 * Math.cos(a));
            for (int y = 1; y <= 7; y++) {
                plan.set(x, at.y() + y, z, y == 7 ? Material.LIGHTNING_ROD : Material.WAXED_COPPER_BLOCK, y == 7 ? "facing=up" : null);
            }
        }
        // the sealed Sarcophagus on the far side: a wall of crying obsidian with the seal in the middle
        int sx = SEAL[0];
        int sz = SEAL[2];
        plan.fill(sx - 3, at.y() + 1, sz, sx + 3, at.y() + 6, sz, Material.CRYING_OBSIDIAN);
        plan.fill(sx - 1, at.y() + 2, sz, sx + 1, at.y() + 4, sz, Material.WAXED_OXIDIZED_CUT_COPPER);
        plan.set(sx, SEAL[1], sz + 1, Material.LODESTONE);
        plan.set(sx, at.y() + 5, sz, Material.LIGHTNING_ROD, "facing=up");
        plan.sign(sx - 2, at.y() + 1, sz + 2, Material.OAK_SIGN, "rotation=0", "Sealed", "The Thunder Dragon", "sleeps. Right-click", "the seal with a key.");
        plan.anchor("seal", sx, SEAL[1], sz + 1);
    }

    /** The 3 x 3 floor of a pad, with a sign naming the way. */
    private static void buildPad(BlockPlan plan, Pad pad) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                plan.set(pad.x() + dx, pad.y(), pad.z() + dz,
                    dx == 0 && dz == 0 ? Material.LIGHT_BLUE_GLAZED_TERRACOTTA : Material.WAXED_OXIDIZED_CUT_COPPER);
            }
        }
        double[] back = unit(at(pad.to()), at(pad.from()));
        plan.sign((int) Math.round(pad.x() + back[0] * 3), pad.y() + 1, (int) Math.round(pad.z() + back[1] * 3), Material.OAK_SIGN,
            "rotation=0", "Wind pad", "to " + at(pad.to()).name(), "Step on it. Sneak", "to stay put.");
        plan.anchor("pad:" + pad.id(), pad.x(), pad.y(), pad.z());
    }
}
