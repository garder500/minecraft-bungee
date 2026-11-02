package win.vampyuyu.survival.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.ShapeKit;

/**
 * The Realm of the Cracked Ashes (GDD 3.1): basalt islets and a metal foundry floating over suspended seas of magma,
 * joined by narrow bridges. Pure data like {@code QuaiPlan}: coordinates are relative to the build origin, surface of
 * the Ember Landing at y = 0. Everything that gameplay needs (landmarks, vents, Cinderheart nodes, heat tiers) is
 * listed here so the build and the rules cannot drift apart.
 */
public final class AshesPlan {
    /** Bump to rebuild existing worlds when the layout changes. */
    public static final int VERSION = 1;
    /** World y of the landing surface (the Nether ceiling is 128, the tallest structure is 22 above). */
    public static final int ORIGIN_Y = 96;
    public static final int LANDING_RADIUS = 13;
    /** Plans placed above this many blocks are cut: the test keeps the layout well below it. */
    public static final int CAP = 30_000;
    /** Blocks the heat of an islet reaches beyond its edge (bridges and open air next to it). */
    public static final int HEAT_MARGIN = 10;

    private AshesPlan() {}

    /** A floating island. {@code tier} is the heat tier on it (0 = cooled, 3 = the caldera). */
    public record Islet(String id, int x, int z, int radius, int tier) {}

    /** A place to discover. {@code y} is the floor level to stand on. */
    public record Landmark(String id, String name, int x, int y, int z, String lore) {}

    /** A Cinderheart node or a vent. */
    public record Spot(String id, int x, int y, int z) {}

    public static final Islet LANDING = new Islet("landing", 0, 0, LANDING_RADIUS, 0);

    public static final List<Islet> ISLETS = List.of(
        LANDING,
        islet("needle", 0, 48, 9, 1),
        islet("foundry", 72, 60, 10, 1),
        islet("cinder", 145, 52, 12, 1),
        islet("tarn", 215, 64, 11, 2),
        islet("rib", 285, 58, 9, 1),
        islet("caldera", 330, 100, 16, 3));

    /** Suspended seas of magma between the islets (floor of basalt, a rim, lava inside), at y = {@link #SEA_Y}. */
    public static final List<int[]> SEAS = List.of(
        polar(36, 62, 13), polar(108, 66, 13), polar(180, 72, 14), polar(250, 78, 13), polar(310, 62, 12));
    public static final int SEA_Y = -9;

    public static final List<Landmark> LANDMARKS = List.of(
        new Landmark("needle", "The Needle", at("needle").x(), 1, at("needle").z(),
            "A spire of basalt older than the sky. Its tip hums when the Devourer breathes in."),
        new Landmark("foundry", "Slag Foundry", at("foundry").x(), 1, at("foundry").z(),
            "Forges that ran before the Devourer came. Whoever built them kept the fire, not the fuel."),
        new Landmark("tarn", "Ember Tarn", at("tarn").x(), 1, at("tarn").z(),
            "The magma here is not boiling. It is breathing, slowly, in time with the Colossus."),
        new Landmark("rib", "Rib of the Colossus", at("rib").x() , 1, at("rib").z(),
            "Bone that is not bone. The Sarcophagus grew around a living heart, and these are its ribs."),
        new Landmark("caldera", "Caldera Rim", at("caldera").x(), 1, at("caldera").z(),
            "Beneath the seal the Lava Colossus regulates the heat of a world that no longer exists."));

    /** Heat vents to stabilise (world quest): one on the foundry, one at the tarn, one on the caldera rim. */
    public static final List<Spot> VENTS = List.of(
        spot("vent:foundry", at("foundry"), 4, 0), spot("vent:tarn", at("tarn"), 0, -6), spot("vent:caldera", at("caldera"), -10, 0));

    /** Cinderheart nodes: a ring on the cinder fields and a few on the rib islet. */
    public static final List<Spot> NODES = nodes();

    /** Where players arrive. */
    public static final int[] SPAWN = {0, 1, 5};

    public static Islet at(String id) {
        for (Islet islet : ISLETS) {
            if (islet.id().equals(id)) {
                return islet;
            }
        }
        throw new IllegalArgumentException(id);
    }

    /** Heat tier from the islets alone: the tier of the islet whose heat reaches this column (the hottest wins). */
    public static int baseTier(double x, double z) {
        int tier = -1;
        for (Islet islet : ISLETS) {
            double dist = Math.hypot(x - islet.x(), z - islet.z());
            if (dist <= islet.radius() + (islet.tier() == 0 ? 0 : HEAT_MARGIN)) {
                tier = Math.max(tier, islet.tier());
            }
        }
        // bridges and open air between the islets: warm
        return tier < 0 ? 1 : tier;
    }

    /** Distance from {@code x, z} to the nearest cooled platform, used to tell the landing from its bridges. */
    public static boolean onLanding(double x, double z) {
        return Math.hypot(x, z) <= LANDING_RADIUS;
    }

    // ---------------------------------------------------------------- build

    public static BlockPlan build() {
        BlockPlan plan = new BlockPlan(CAP);
        Random rnd = new Random(2904L);
        for (Islet islet : ISLETS) {
            buildIslet(plan, islet, rnd);
        }
        for (int[] sea : SEAS) {
            buildSea(plan, sea[0], sea[1], sea[2]);
        }
        for (Islet islet : ISLETS) {
            if (islet != LANDING) {
                bridge(plan, 0, 0, islet.x(), islet.z(), islet.radius() - 2);
            }
        }
        landing(plan);
        needle(plan, at("needle"));
        foundry(plan, at("foundry"));
        tarn(plan, at("tarn"));
        rib(plan, at("rib"));
        caldera(plan, at("caldera"));
        for (Spot node : NODES) {
            plan.set(node.x(), node.y(), node.z(), Material.GILDED_BLACKSTONE);
            plan.anchor(node.id(), node.x(), node.y(), node.z());
        }
        for (Spot vent : VENTS) {
            plan.set(vent.x(), vent.y(), vent.z(), Material.BLAST_FURNACE, "facing=south,lit=true");
            plan.anchor(vent.id(), vent.x(), vent.y(), vent.z());
        }
        for (Landmark mark : LANDMARKS) {
            plan.anchor("landmark:" + mark.id(), mark.x(), mark.y(), mark.z());
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
                    plan.set(islet.x() + dx, -k, islet.z() + dz, k == 0 ? top(islet, rnd) : body(k, rnd));
                }
            }
        }
    }

    private static Material top(Islet islet, Random rnd) {
        if (islet == LANDING) {
            return rnd.nextInt(5) == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BASALT;
        }
        double roll = rnd.nextDouble();
        if (roll < 0.04 * islet.tier()) {
            return Material.MAGMA_BLOCK;
        }
        return roll < 0.45 ? Material.BASALT : roll < 0.8 ? Material.SMOOTH_BASALT : Material.BLACKSTONE;
    }

    private static Material body(int k, Random rnd) {
        if (k >= 2 && rnd.nextInt(40) == 0) {
            return Material.MAGMA_BLOCK;
        }
        return k <= 2 ? Material.BASALT : Material.BLACKSTONE;
    }

    private static void buildSea(BlockPlan plan, int cx, int cz, int r) {
        ShapeKit.disc(plan, cx, SEA_Y - 1, cz, r, Material.BASALT);
        ShapeKit.disc(plan, cx, SEA_Y, cz, r - 1.5, Material.LAVA);
        ShapeKit.ring(plan, cx, SEA_Y, cz, r - 1.5, r, Material.BLACKSTONE);
        ShapeKit.ring(plan, cx, SEA_Y + 1, cz, r - 0.5, r, Material.BLACKSTONE);
        ShapeKit.disc(plan, cx, SEA_Y - 2, cz, r - 3, Material.MAGMA_BLOCK);
    }

    /** A 3 wide walkway from the edge of one islet to the edge of another, with a lantern post every 10 blocks. */
    private static void bridge(BlockPlan plan, int x0, int z0, int x1, int z1, int endInset) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        double ux = (x1 - x0) / len;
        double uz = (z1 - z0) / len;
        double px = -uz;
        double pz = ux;
        int steps = (int) Math.round(len - endInset);
        for (int s = LANDING_RADIUS - 2; s <= steps; s++) {
            for (int w = -1; w <= 1; w++) {
                int x = (int) Math.round(x0 + ux * s + px * w);
                int z = (int) Math.round(z0 + uz * s + pz * w);
                plan.set(x, 0, z, Math.abs(w) == 1 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BASALT);
                plan.set(x, -1, z, Material.BLACKSTONE);
            }
            if (s % 10 == 5) {
                int x = (int) Math.round(x0 + ux * s + px * 2);
                int z = (int) Math.round(z0 + uz * s + pz * 2);
                plan.set(x, 0, z, Material.POLISHED_BLACKSTONE);
                plan.set(x, 1, z, Material.POLISHED_BLACKSTONE_WALL);
                plan.set(x, 2, z, Material.SHROOMLIGHT);
            }
        }
    }

    private static void landing(BlockPlan plan) {
        ShapeKit.ring(plan, 0, 0, 0, LANDING_RADIUS - 1.5, LANDING_RADIUS - 0.5, Material.ORANGE_CONCRETE);
        ShapeKit.disc(plan, 0, 0, 0, 2.5, Material.SMOOTH_QUARTZ);
        for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            plan.set(d[0], 0, d[1], Material.SHROOMLIGHT);
        }
        plan.set(0, 1, 0, Material.LODESTONE);
        plan.sign(0, 1, 3, Material.OAK_SIGN, "rotation=0", "Gate 1", "Cracked Ashes", "Ember Landing", "/quai to return");
    }

    private static void needle(BlockPlan plan, Islet at) {
        for (int y = 1; y <= 16; y++) {
            double r = 3.2 * (1 - y / 17.0) + 0.5;
            ShapeKit.disc(plan, at.x(), y, at.z(), r, y % 5 == 0 ? Material.GILDED_BLACKSTONE : Material.BASALT);
        }
        plan.set(at.x(), 17, at.z(), Material.SHROOMLIGHT);
    }

    private static void foundry(BlockPlan plan, Islet at) {
        ShapeKit.disc(plan, at.x(), 0, at.z(), 7, Material.IRON_BLOCK);
        ShapeKit.ring(plan, at.x(), 0, at.z(), 6, 7, Material.WAXED_OXIDIZED_CUT_COPPER);
        for (int y = 1; y <= 4; y++) {
            plan.set(at.x() - 4, y, at.z() - 4, Material.WAXED_OXIDIZED_COPPER);
            plan.set(at.x() - 4, y, at.z() + 4, Material.WAXED_OXIDIZED_COPPER);
            plan.set(at.x() + 4, y, at.z() - 4, Material.WAXED_OXIDIZED_COPPER);
        }
        plan.fill(at.x() - 4, 5, at.z() - 4, at.x() + 4, 5, at.z() - 4, Material.IRON_BARS);
        plan.set(at.x(), 1, at.z(), Material.ANVIL, "facing=east");
        plan.set(at.x() + 2, 1, at.z() + 2, Material.CAULDRON);
        plan.set(at.x() - 2, 1, at.z() + 2, Material.GRINDSTONE, "face=floor,facing=north");
    }

    private static void tarn(BlockPlan plan, Islet at) {
        ShapeKit.disc(plan, at.x(), 0, at.z(), 4.5, Material.LAVA);
        ShapeKit.disc(plan, at.x(), -1, at.z(), 5.5, Material.MAGMA_BLOCK);
        ShapeKit.ring(plan, at.x(), 0, at.z(), 4.5, 5.5, Material.BLACKSTONE);
        ShapeKit.ring(plan, at.x(), 1, at.z(), 4.5, 5.5, Material.BLACKSTONE_SLAB);
    }

    private static void rib(BlockPlan plan, Islet at) {
        for (int arch = 0; arch < 2; arch++) {
            for (int t = 0; t <= 24; t++) {
                double a = Math.PI * t / 24.0;
                int u = (int) Math.round(Math.cos(a) * 6);
                int y = 1 + (int) Math.round(Math.sin(a) * 9);
                int x = arch == 0 ? at.x() + u : at.x();
                int z = arch == 0 ? at.z() : at.z() + u;
                plan.set(x, y, z, Material.BONE_BLOCK, "axis=y");
                plan.set(x + (arch == 0 ? 0 : 1), y, z + (arch == 0 ? 1 : 0), Material.BONE_BLOCK, "axis=y");
            }
        }
    }

    private static void caldera(BlockPlan plan, Islet at) {
        ShapeKit.disc(plan, at.x(), 0, at.z(), 8, Material.LAVA);
        ShapeKit.disc(plan, at.x(), -1, at.z(), 9, Material.MAGMA_BLOCK);
        ShapeKit.ring(plan, at.x(), 0, at.z(), 8, 9.5, Material.OBSIDIAN);
        ShapeKit.ring(plan, at.x(), 1, at.z(), 8.5, 9.5, Material.OBSIDIAN);
        // the sealed door of the Sarcophagus on the far side (Colossus fight: a later phase)
        int dz = -14;
        plan.fill(at.x() - 3, 1, at.z() + dz, at.x() + 3, 6, at.z() + dz, Material.CRYING_OBSIDIAN);
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 4; y++) {
                plan.set(at.x() + x, y, at.z() + dz, Material.RESPAWN_ANCHOR, "charges=4");
            }
        }
        plan.sign(at.x(), 1, at.z() + dz + 2, Material.OAK_SIGN, "rotation=0", "Sealed", "The Lava Colossus", "still sleeps.", "");
        plan.anchor("sarcophagus", at.x(), 1, at.z() + dz + 1);
    }

    // ---------------------------------------------------------------- layout helpers

    private static Islet islet(String id, double degrees, double dist, int radius, int tier) {
        int[] c = polar(degrees, dist, 0);
        return new Islet(id, c[0], c[1], radius, tier);
    }

    private static int[] polar(double degrees, double dist, int radius) {
        double a = Math.toRadians(degrees);
        return new int[] {(int) Math.round(dist * Math.sin(a)), (int) Math.round(-dist * Math.cos(a)), radius};
    }

    private static Spot spot(String id, Islet at, int dx, int dz) {
        return new Spot(id, at.x() + dx, 1, at.z() + dz);
    }

    private static List<Spot> nodes() {
        List<Spot> list = new ArrayList<>();
        Islet cinder = at("cinder");
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i + 20);
            list.add(new Spot("node:cinder" + i, cinder.x() + (int) Math.round(7 * Math.sin(a)), 0,
                cinder.z() + (int) Math.round(-7 * Math.cos(a))));
        }
        Islet rib = at("rib");
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(90 * i + 45);
            list.add(new Spot("node:rib" + i, rib.x() + (int) Math.round(5 * Math.sin(a)), 0,
                rib.z() + (int) Math.round(-5 * Math.cos(a))));
        }
        return List.copyOf(list);
    }
}
