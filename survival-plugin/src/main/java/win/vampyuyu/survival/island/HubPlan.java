package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/**
 * The procedurally built hub, relative coordinates, ground surface at y = 0 (design: {@code docs/skyblock-notes/hub.md}).
 * It is the last anchored fragment of Aethelgard: a floating disc of pale stone with a broken rim and drifting debris,
 * a Well of Remembrance with a crystallised-memory spire at its centre, a survivors' camp whose NPCs stand in ruins,
 * three lore gates (island north, Crypt barrow east, Rift tear west) and, on the south axis, a processional that ends
 * at the Threshold and the bridge of the Quai des Huit. Pure data: {@link PlanJob} places it in batches. Everything is
 * deterministic (a position hash, no random generator) so a plan can be rebuilt and tested.
 */
public final class HubPlan {
    /** Bump when the layout changes so existing hubs are rebuilt (the marker file stores it). */
    public static final int VERSION = 5; // 5: the crystal monolith, taller arches and colonnade, deep keel, lights, display models
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Z = 14;
    /** The Quai bridge (QuaiPlan) starts at this hub Z; the processional ends one block before it. */
    public static final int CAUSEWAY_END_Z = 44;

    /** A designated spot for an NPC. */
    public record Pad(String id, String title, int x, int z) {}

    /** Pads in a fixed order; ids are the contract with the features that spawn NPCs there. */
    public static final List<Pad> PADS = List.of(
        new Pad("merchant", "Merchant", -18, 9),
        new Pad("archivist", "Archivist", -20, -9),
        new Pad("guard", "Guard", 8, 18),
        new Pad("contract_hunter", "Contract Hunter", 19, 8),
        new Pad("quest_master", "Quest Master", -11, -17),
        new Pad("banker", "Banker", 11, -17),
        new Pad("boss_arena", "Boss Arena", 20, -14),
        new Pad("island_gate", "Island Gate", 0, -22),
        new Pad("dungeon_gate", "Dungeon Gate", 21, 0),
        new Pad("rift_gate", "The Rift", -21, 0),
        // Foot of the Threshold: no NPC, a pad and a sign for the Quai des Huit.
        new Pad("eight_docks", "Quai des Huit", 0, 37));

    private static final double[][] BITES = bites();
    /** Rim spires and their positions (hub coordinates). */
    static final int[][] RIM_SPIRES = {{-25, -19}, {-22, 18}, {24, 19}, {27, -11}};

    private HubPlan() {}

    public static Pad pad(String id) {
        for (Pad pad : PADS) {
            if (pad.id().equals(id)) {
                return pad;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- ground

    /** Radius of the fragment at a polar angle (fixed formula, 27 to 37 blocks). */
    static double radiusAt(double angle) {
        return 32 + 2.2 * Math.sin(2 * angle + 0.7) + 1.6 * Math.sin(3 * angle + 2.1) + 1.0 * Math.sin(5 * angle + 4.0);
    }

    private static double[][] bites() {
        double[] angles = {58, 232, 128, 195};
        double[] radii = {4.5, 5.0, 4.0, 4.0};
        double[][] out = new double[angles.length][];
        for (int i = 0; i < angles.length; i++) {
            double th = Math.toRadians(angles[i]);
            double r = radiusAt(th) - 0.5;
            out[i] = new double[] {r * Math.cos(th), r * Math.sin(th), radii[i]};
        }
        return out;
    }

    /** True when a column has ground (the disc minus the broken-out bites, plus the approaches). */
    public static boolean ground(int x, int z) {
        if (approach(x, z)) {
            return true;
        }
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d > radiusAt(Math.atan2(z, x))) {
            return false;
        }
        for (double[] bite : BITES) {
            if (Math.hypot(x - bite[0], z - bite[1]) <= bite[2]) {
                return false;
            }
        }
        return true;
    }

    /** The north road with its landing disc, and the south processional (9 wide, 11 at the Threshold). */
    private static boolean approach(int x, int z) {
        if (Math.abs(x) <= (z <= -26 && z >= -32 ? 4 : 3) && z <= -20 && z >= -41) {
            return true;
        }
        if (Math.hypot(x, z + 38.0) <= 5.5) {
            return true;
        }
        return z >= 20 && z <= CAUSEWAY_END_Z && Math.abs(x) <= (z >= 36 ? 5 : 4);
    }

    private static int h(int x, int z, int salt) {
        int n = x * 374761393 + z * 668265263 + salt * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return (n ^ (n >>> 16)) & 0x7fffffff;
    }

    // ---------------------------------------------------------------- plans

    /** The spawn pad alone (under 300 blocks), built synchronously so the hub is usable at once. */
    public static BlockPlan emergencySpawn() {
        BlockPlan plan = new BlockPlan();
        spawnPad(plan);
        return plan;
    }

    /**
     * The whole hub. {@code cap} bounds the number of blocks; the plan is ordered so a cap cuts decoration, never the
     * road: spawn, centre, roads and pads, gates, causeway, camp, spires, debris, underside.
     */
    public static BlockPlan build(int cap) {
        BlockPlan plan = new BlockPlan(cap);
        spawnPad(plan);
        wellCourt(plan);
        paths(plan);
        for (Pad pad : PADS) {
            padBase(plan, pad);
        }
        causewayPaving(plan);
        groundFill(plan);
        clearOldRingScraps(plan);
        wellFurniture(plan);
        islandGate(plan);
        crypt(plan);
        riftTear(plan);
        processional(plan);
        for (Pad pad : PADS) {
            ruin(plan, pad);
        }
        waymarks(plan);
        spire(plan, 0, 0, 1.0);
        for (int[] at : RIM_SPIRES) {
            spire(plan, at[0], at[1], 0.6);
        }
        rimRuins(plan);
        breach(plan);
        debris(plan);
        underside(plan);
        lights(plan);
        double tip = spireHeight(1.0) + 1.5;
        plan.model(BlockPlan.Model.of("structure/hub_memory_shards", 0.5, tip, 0.5, 6f).spin(7, "y"));
        plan.model(BlockPlan.Model.of("structure/hub_memory_motes", 0.5, tip - 1, 0.5, 6f).spin(-15, "y"));
        plan.anchor("spawn", SPAWN_X, 1, SPAWN_Z);
        for (Pad pad : PADS) {
            plan.anchor("pad:" + pad.id(), pad.x(), 1, pad.z());
        }
        return plan;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Invisible light blocks four above the ground on a loose grid, so the fragment reads at night (the lanterns are
     * pools of gold, these fill the dark between them). Never where a block already stands.
     */
    private static void lights(BlockPlan plan) {
        for (int x = -36; x <= 36; x += 7) {
            for (int z = -40; z <= CAUSEWAY_END_Z; z += 7) {
                int lx = x + h(x, z, 31) % 3 - 1;
                int lz = z + h(x, z, 32) % 3 - 1;
                if (!ground(lx, lz) || plan.get(lx, 4, lz) != null || plan.get(lx, 3, lz) != null) {
                    continue;
                }
                plan.set(lx, 4, lz, Material.LIGHT, "level=13");
            }
        }
    }

    /** Sets a top surface block only where nothing was placed yet (earlier, more specific pieces win). */
    private static void top(BlockPlan plan, int x, int z, Material m) {
        if (plan.get(x, 0, z) == null) {
            plan.set(x, 0, z, m);
        }
    }

    private static Material floorMaterial(int x, int z) {
        int r = h(x, z, 1) % 100;
        if (r < 30) {
            return Material.CALCITE;
        }
        if (r < 55) {
            return Material.STONE_BRICKS;
        }
        if (r < 70) {
            return Material.CRACKED_STONE_BRICKS;
        }
        if (r < 80) {
            return Material.MOSSY_STONE_BRICKS;
        }
        if (r < 95) {
            return Material.POLISHED_DIORITE;
        }
        return Material.MOSS_BLOCK;
    }

    private static boolean nearRim(int x, int z, int cells) {
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dz = -cells; dz <= cells; dz++) {
                if (!ground(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void groundFill(BlockPlan plan) {
        for (int x = -45; x <= 45; x++) {
            for (int z = -46; z <= CAUSEWAY_END_Z; z++) {
                if (!ground(x, z)) {
                    continue;
                }
                Material m = floorMaterial(x, z);
                if (nearRim(x, z, 1)) {
                    int r = h(x, z, 2) % 4;
                    m = r == 0 ? Material.TUFF : r == 1 ? Material.COBBLESTONE : r == 2 ? Material.MOSSY_COBBLESTONE : Material.CRACKED_STONE_BRICKS;
                }
                top(plan, x, z, m);
            }
        }
    }

    /**
     * The old hub (versions 1 and 2) had a ring road out to z = 44 and lamp posts: the wipe stops at z = 41 so the Quai
     * bridge survives, and this clears what is left of the ring at the foot of the processional.
     */
    private static void clearOldRingScraps(BlockPlan plan) {
        for (int x = -16; x <= 16; x++) {
            for (int z = 42; z <= CAUSEWAY_END_Z; z++) {
                if (plan.get(x, 0, z) == null) {
                    plan.set(x, 0, z, Material.AIR);
                }
                plan.set(x, -1, z, plan.get(x, -1, z) == null && ground(x, z) ? Material.STONE_BRICKS : Material.AIR);
                for (int y = 1; y <= 5; y++) {
                    if (plan.get(x, y, z) == null) {
                        plan.set(x, y, z, Material.AIR);
                    }
                }
            }
        }
    }

    private static boolean isLight(Material m) {
        return m == Material.LANTERN || m == Material.SOUL_LANTERN || m == Material.SEA_LANTERN || m == Material.END_ROD
            || m == Material.CAMPFIRE || m == Material.GLOWSTONE;
    }

    /** True for blocks that give light (tests). */
    public static boolean emitsLight(Material m) {
        return isLight(m);
    }

    private static void lampPost(BlockPlan plan, int x, int z, int height) {
        ShapeKit.column(plan, x, 1, height, z, Material.STONE_BRICK_WALL);
        plan.set(x, height + 1, z, Material.LANTERN, "hanging=false");
    }

    /** Standing sign rotation (0 = text faces south, 4 = west, 8 = north, 12 = east) for a facing vector. */
    static int signRotation(double dx, double dz) {
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        if (yaw < 0) {
            yaw += 360;
        }
        return (int) Math.round(yaw / 22.5) % 16;
    }

    // ---------------------------------------------------------------- spawn and centre

    /** The Landing: a paved disc with four lantern posts, on the south axis. */
    private static void spawnPad(BlockPlan plan) {
        int z = SPAWN_Z;
        ShapeKit.disc(plan, 0, 0, z, 4.6, Material.POLISHED_DIORITE);
        ShapeKit.disc(plan, 0, 0, z, 3.4, Material.SMOOTH_QUARTZ);
        ShapeKit.disc(plan, 0, 0, z, 1.6, Material.CHISELED_STONE_BRICKS);
        plan.set(0, 0, z, Material.SEA_LANTERN);
        for (int[] d : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            plan.set(d[0], 0, z + d[1], Material.SEA_LANTERN);
        }
        ShapeKit.disc(plan, 0, -1, z, 4.6, Material.STONE_BRICKS);
        for (int sx : new int[] {-5, 5}) {
            for (int dz : new int[] {-3, 3}) {
                plan.set(sx, 0, z + dz, Material.SMOOTH_QUARTZ);
                ShapeKit.column(plan, sx, 1, 3, z + dz, Material.QUARTZ_PILLAR);
                plan.set(sx, 4, z + dz, Material.LANTERN, "hanging=false");
            }
        }
    }

    /** The court of the Well: concentric rings of pale stone. */
    private static void wellCourt(BlockPlan plan) {
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt((double) x * x + z * z);
                if (d > 9.3) {
                    continue;
                }
                Material m;
                if (d <= 3.5) {
                    m = Material.CHISELED_QUARTZ_BLOCK;
                } else if (d <= 5.5) {
                    m = Material.SMOOTH_QUARTZ;
                } else if (d <= 7.5) {
                    m = h(x, z, 4) % 5 == 0 ? Material.CRACKED_STONE_BRICKS : Material.POLISHED_DIORITE;
                } else if (d <= 8.5) {
                    m = Material.CHISELED_STONE_BRICKS;
                } else {
                    m = Material.QUARTZ_BRICKS;
                }
                top(plan, x, z, m);
            }
        }
    }

    /** Eight sigil stones around the spire (an echo of the Quai's eight gates), each with a lantern. */
    private static void wellFurniture(BlockPlan plan) {
        for (int k = 0; k < 8; k++) {
            int x = ShapeKit.polarX(6.5, 22.5 + 45 * k);
            int z = ShapeKit.polarZ(6.5, 22.5 + 45 * k);
            plan.set(x, 1, z, Material.CHISELED_STONE_BRICKS);
            plan.set(x, 2, z, Material.STONE_BRICK_WALL);
            plan.set(x, 3, z, Material.LANTERN, "hanging=false");
        }
    }

    /** Waymarks around the Well: where each road leads. */
    private static void waymarks(BlockPlan plan) {
        String[][] posts = {{"4", "-11", "North", "Island Gate", ""}, {"11", "4", "East", "Crypt Depths", ""},
            {"-4", "11", "South", "Quai des Huit", "Sky Gates"}, {"-11", "-4", "West", "The Rift", ""}};
        for (String[] post : posts) {
            int x = Integer.parseInt(post[0]);
            int z = Integer.parseInt(post[1]);
            plan.set(x, 0, z, Material.POLISHED_ANDESITE);
            plan.sign(x, 1, z, Material.OAK_SIGN, "rotation=" + signRotation(-x, -z), post[2], post[3], post[4], "");
        }
    }

    /** Height of the Memory Spire's monolith at scale 1 (its tip cluster is one above). */
    public static final int SPIRE_HEIGHT = 30;

    /** Height of a spire's monolith at a scale. */
    static int spireHeight(double scale) {
        return Math.max(6, (int) Math.round(SPIRE_HEIGHT * scale));
    }

    /**
     * The Memory Spire: a tapering monolith of amethyst banded with light blue glass, lit from inside by sea lanterns,
     * four lesser shards leaning out of its foot, a cluster at its tip. At scale 1 it is the landmark of the whole hub
     * (thirty blocks); the rim spires are smaller copies. Floating crystal rings turn round its tip (display models).
     */
    private static void spire(BlockPlan plan, int cx, int cz, double scale) {
        int height = spireHeight(scale);
        double base = 2.6 * Math.max(scale, 0.6);
        ShapeKit.disc(plan, cx, 1, cz, base + 0.8, Material.AMETHYST_BLOCK);
        for (int y = 2; y <= height; y++) {
            double t = (double) (y - 2) / Math.max(1, height - 2);
            double r = Math.max(0.45, base * Math.pow(1 - t, 0.75));
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt((double) dx * dx + dz * dz);
                    if (d > r + 0.01) {
                        continue;
                    }
                    boolean skin = d > r - 1.0;
                    Material m = skin && y % 6 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS
                        : !skin && y % 5 == 3 ? Material.SEA_LANTERN : Material.AMETHYST_BLOCK;
                    plan.set(cx + dx, y, cz + dz, m);
                }
            }
        }
        plan.set(cx, height + 1, cz, Material.AMETHYST_CLUSTER, "facing=up");
        // four lesser shards leaning out of the foot
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + 90 * k + 20 * scale);
            int h = (int) Math.round(height * (0.42 + 0.07 * k));
            for (int y = 2; y <= h; y++) {
                double t = (double) (y - 2) / Math.max(1, h - 2);
                double out = base + 0.4 + (y - 2) * 0.16;
                double r = Math.max(0.5, 1.2 * Math.max(scale, 0.6) * (1 - t));
                int x0 = cx + (int) Math.round(Math.cos(a) * out);
                int z0 = cz + (int) Math.round(Math.sin(a) * out);
                int ri = (int) Math.ceil(r);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (Math.sqrt((double) dx * dx + dz * dz) <= r + 0.01) {
                            plan.set(x0 + dx, y, z0 + dz, y % 4 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.AMETHYST_BLOCK);
                        }
                    }
                }
                if (y == h) {
                    plan.set(x0, y + 1, z0, Material.AMETHYST_CLUSTER, "facing=up");
                }
            }
        }
        plan.set(cx, 1, cz, Material.AMETHYST_BLOCK);
    }

    // ---------------------------------------------------------------- roads and pads

    /** Paved roads from the Well to every pad, 3 wide. */
    private static void paths(BlockPlan plan) {
        for (Pad pad : PADS) {
            if (pad.id().equals("eight_docks")) {
                continue;
            }
            double len = Math.hypot(pad.x(), pad.z());
            for (double t = 8; t <= len; t += 0.5) {
                double px = pad.x() * t / len;
                double pz = pad.z() * t / len;
                for (double w = -1.2; w <= 1.21; w += 0.6) {
                    int x = (int) Math.round(px + (-pad.z() / len) * w);
                    int z = (int) Math.round(pz + (pad.x() / len) * w);
                    top(plan, x, z, Math.abs(w) < 0.4 ? Material.QUARTZ_BRICKS : Material.POLISHED_DIORITE);
                }
            }
        }
    }

    /** The paved strip of the processional and the north road. */
    private static void causewayPaving(BlockPlan plan) {
        for (int z = 10; z <= CAUSEWAY_END_Z; z++) {
            for (int x = -3; x <= 3; x++) {
                Material m = Math.abs(x) <= 1 ? Material.QUARTZ_BRICKS : Math.abs(x) == 2 ? Material.POLISHED_DIORITE : Material.CALCITE;
                if ((z == 27 || z == 28) && h(x, z, 6) % 3 == 0) {
                    m = Material.CRACKED_STONE_BRICKS; // a crack in the road, still walkable
                }
                top(plan, x, z, m);
            }
        }
        for (int z = -41; z <= -9; z++) {
            for (int x = -1; x <= 1; x++) {
                if (ground(x, z)) {
                    top(plan, x, z, x == 0 ? Material.QUARTZ_BRICKS : Material.POLISHED_DIORITE);
                }
            }
        }
        ShapeKit.disc(plan, 0, 0, -38, 5.0, Material.SMOOTH_QUARTZ);
        ShapeKit.disc(plan, 0, 0, -38, 2.4, Material.CHISELED_QUARTZ_BLOCK);
        plan.set(0, 0, -38, Material.SEA_LANTERN);
    }

    /** A local frame at a pad: a = distance outward (away from the centre), b = sideways. */
    private record Frame(int px, int pz, int ox, int oz) {
        static Frame of(Pad pad) {
            if (Math.abs(pad.x()) >= Math.abs(pad.z())) {
                return new Frame(pad.x(), pad.z(), Integer.signum(pad.x()), 0);
            }
            return new Frame(pad.x(), pad.z(), 0, Integer.signum(pad.z()));
        }

        int x(int a, int b) {
            return px + a * ox + b * (-oz);
        }

        int z(int a, int b) {
            return pz + a * oz + b * ox;
        }

        String facingIn() {
            return ox > 0 ? "west" : ox < 0 ? "east" : oz > 0 ? "north" : "south";
        }

        void set(BlockPlan plan, int a, int b, int y, Material m) {
            plan.set(x(a, b), y, z(a, b), m);
        }

        void set(BlockPlan plan, int a, int b, int y, Material m, String props) {
            plan.set(x(a, b), y, z(a, b), m, props);
        }

        void column(BlockPlan plan, int a, int b, int y0, int y1, Material m) {
            ShapeKit.column(plan, x(a, b), y0, y1, z(a, b), m);
        }
    }

    /** The 5x5 dais of a pad, a lamp post and a sign beside it. */
    private static void padBase(BlockPlan plan, Pad pad) {
        int px = pad.x();
        int pz = pad.z();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Material m;
                if (x == 0 && z == 0) {
                    m = Material.CHISELED_STONE_BRICKS;
                } else if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    m = Material.POLISHED_DIORITE;
                } else if (Math.abs(x) + Math.abs(z) < 4) {
                    m = Material.SMOOTH_QUARTZ;
                } else {
                    m = Material.POLISHED_ANDESITE;
                }
                plan.set(px + x, 0, pz + z, m);
            }
        }
        Frame f = Frame.of(pad);
        plan.set(f.x(0, 3), 0, f.z(0, 3), Material.SMOOTH_QUARTZ);
        lampPost(plan, f.x(0, 3), f.z(0, 3), 2);
        int sx = f.x(0, -3);
        int sz = f.z(0, -3);
        plan.set(sx, 0, sz, Material.SMOOTH_QUARTZ);
        String[] lines = pad.id().equals("eight_docks") ? new String[] {"Quai des Huit", "Eight Sky Gates", "Walk on"}
            : new String[] {"", pad.title(), ""};
        plan.sign(sx, 1, sz, Material.OAK_SIGN, "rotation=" + signRotation(-px, -pz), lines[0], lines[1], lines[2], "");
    }

    // ---------------------------------------------------------------- camp ruins

    private static void ruin(BlockPlan plan, Pad pad) {
        Frame f = Frame.of(pad);
        switch (pad.id()) {
            case "merchant" -> stall(plan, f);
            case "archivist" -> archive(plan, f);
            case "guard" -> watchPost(plan, f);
            case "contract_hunter" -> noticeWall(plan, f);
            case "quest_master" -> campfire(plan, f);
            case "banker" -> vault(plan, f);
            case "boss_arena" -> ring(plan, pad);
            default -> { }
        }
    }

    /** A broken back wall of pale brick with side stubs; every ruin starts from it. */
    private static void alcove(BlockPlan plan, Frame f, int height) {
        for (int b = -3; b <= 3; b++) {
            int hh = Math.max(2, height - (h(f.x(4, b), f.z(4, b), 8) % 3));
            f.column(plan, 4, b, 1, hh, wallMaterial(f.x(4, b), f.z(4, b)));
            if (Math.abs(b) == 3) {
                f.set(plan, 4, b, hh + 1, Material.LANTERN, "hanging=false");
            }
        }
        for (int a = 2; a <= 3; a++) {
            for (int b : new int[] {-3, 3}) {
                f.column(plan, a, b, 1, 2 + (a == 2 ? 0 : 1), wallMaterial(f.x(a, b), f.z(a, b)));
            }
        }
        f.set(plan, 3, -3, 4, Material.LANTERN, "hanging=false");
    }

    private static Material wallMaterial(int x, int z) {
        int r = h(x, z, 9) % 10;
        return r < 5 ? Material.STONE_BRICKS : r < 7 ? Material.CRACKED_STONE_BRICKS : r < 9 ? Material.MOSSY_STONE_BRICKS : Material.CALCITE;
    }

    /** The salvagers' stall: spruce canopy on two posts, a counter and barrels. */
    private static void stall(BlockPlan plan, Frame f) {
        alcove(plan, f, 3);
        for (int b : new int[] {-2, 2}) {
            f.column(plan, 2, b, 1, 3, Material.SPRUCE_FENCE);
        }
        for (int a = 1; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                f.set(plan, a, b, 4, Material.SPRUCE_SLAB);
            }
        }
        for (int b = -2; b <= 2; b++) {
            f.set(plan, 3, b, 1, Material.SPRUCE_SLAB);
        }
        f.set(plan, 3, -2, 2, Material.BARREL);
        f.set(plan, 3, 2, 2, Material.BARREL);
        f.set(plan, 2, 0, 3, Material.LANTERN, "hanging=true");
    }

    /** A roofless archive: a bookshelf wall and a lectern. */
    private static void archive(BlockPlan plan, Frame f) {
        alcove(plan, f, 5);
        for (int b = -2; b <= 2; b++) {
            for (int y = 1; y <= 3; y++) {
                if (h(f.x(3, b), f.z(3, b), y) % 5 != 0) {
                    f.set(plan, 3, b, y, Material.BOOKSHELF);
                }
            }
        }
        f.set(plan, 2, 2, 1, Material.LECTERN, "facing=" + f.facingIn());
        f.set(plan, 2, -2, 1, Material.BARREL);
    }

    /** The stub of a tower: a broken ring open towards the pad. */
    private static void watchPost(BlockPlan plan, Frame f) {
        for (int a = 3; a <= 6; a++) {
            for (int b = -2; b <= 2; b++) {
                boolean edge = a == 3 || a == 6 || Math.abs(b) == 2;
                if (!edge || (a == 3 && Math.abs(b) <= 1)) {
                    continue;
                }
                boolean corner = (a == 3 || a == 6) && Math.abs(b) == 2;
                int height = corner ? 5 : 2 + h(f.x(a, b), f.z(a, b), 10) % 3;
                f.column(plan, a, b, 1, height, corner ? Material.QUARTZ_PILLAR : wallMaterial(f.x(a, b), f.z(a, b)));
                if (corner) {
                    f.set(plan, a, b, 6, Material.LANTERN, "hanging=false");
                }
            }
        }
        f.set(plan, 6, 0, 2, Material.IRON_BARS);
        f.set(plan, 4, 0, 1, Material.BARREL);
    }

    /** A wall of notices: fletching and cartography tables, targets, hanging lanterns. */
    private static void noticeWall(BlockPlan plan, Frame f) {
        alcove(plan, f, 4);
        f.set(plan, 3, -1, 1, Material.FLETCHING_TABLE);
        f.set(plan, 3, 1, 1, Material.CARTOGRAPHY_TABLE);
        f.set(plan, 3, -2, 2, Material.TARGET);
        f.set(plan, 3, 2, 2, Material.TARGET);
        f.set(plan, 3, 0, 3, Material.TARGET);
    }

    /** The camp fire: a tent, a fire and hay seats. */
    private static void campfire(BlockPlan plan, Frame f) {
        for (int b = -2; b <= 2; b++) {
            f.set(plan, 3, b, 1, Material.WHITE_WOOL);
            f.set(plan, 4, b, 2, Material.LIGHT_GRAY_WOOL);
            f.set(plan, 5, b, 1, Material.WHITE_WOOL);
        }
        f.set(plan, 4, 0, 1, Material.AIR);
        f.set(plan, 3, -3, 1, Material.HAY_BLOCK);
        f.set(plan, 3, 3, 1, Material.HAY_BLOCK);
        f.set(plan, 2, 3, 1, Material.CAMPFIRE);
        f.set(plan, 4, -3, 1, Material.BARREL);
        f.set(plan, 4, 3, 1, Material.BARREL);
        f.set(plan, 4, 3, 2, Material.LANTERN, "hanging=false");
        f.set(plan, 4, -3, 2, Material.LANTERN, "hanging=false");
    }

    /** The bank: a deepslate wall, a barred counter and a gold block behind it. */
    private static void vault(BlockPlan plan, Frame f) {
        for (int b = -3; b <= 3; b++) {
            f.column(plan, 4, b, 1, 4 - h(f.x(4, b), f.z(4, b), 11) % 2, b % 2 == 0 ? Material.DEEPSLATE_BRICKS : Material.POLISHED_DEEPSLATE);
        }
        for (int b = -2; b <= 2; b++) {
            f.column(plan, 3, b, 1, 2, Material.IRON_BARS);
        }
        f.set(plan, 3, 0, 1, Material.AIR);
        f.set(plan, 3, 0, 2, Material.AIR);
        f.set(plan, 4, 0, 1, Material.GOLD_BLOCK);
        f.set(plan, 4, -1, 1, Material.BARREL);
        f.set(plan, 4, 1, 1, Material.BARREL);
        f.set(plan, 3, -3, 3, Material.LANTERN, "hanging=false");
        f.set(plan, 3, 3, 3, Material.LANTERN, "hanging=false");
        for (int b : new int[] {-3, 3}) {
            f.column(plan, 3, b, 1, 2, Material.DEEPSLATE_BRICKS);
        }
    }

    /** The Challenger's Ring: a blackstone circle with eight broken pillars, open to the road. */
    private static void ring(BlockPlan plan, Pad pad) {
        ShapeKit.ring(plan, pad.x(), 0, pad.z(), 3.0, 3.6, Material.POLISHED_BLACKSTONE_BRICKS);
        Frame f = Frame.of(pad);
        for (int k = 0; k < 8; k++) {
            int x = pad.x() + ShapeKit.polarX(4.8, 22.5 + 45 * k);
            int z = pad.z() + ShapeKit.polarZ(4.8, 22.5 + 45 * k);
            if (Math.abs(x - f.x(-5, 0)) + Math.abs(z - f.z(-5, 0)) < 4) {
                continue; // the opening towards the road
            }
            plan.set(x, 0, z, Material.POLISHED_BLACKSTONE_BRICKS);
            int height = 2 + h(x, z, 12) % 3;
            ShapeKit.column(plan, x, 1, height, z, Material.QUARTZ_PILLAR);
            if (k % 2 == 0) {
                plan.set(x, height + 1, z, Material.LANTERN, "hanging=false");
            }
        }
        plan.set(pad.x(), 0, pad.z(), Material.CRYING_OBSIDIAN);
    }

    // ---------------------------------------------------------------- gates

    /**
     * A ruined arch spanning a road: pillars at +-half along the span axis, a lintel that may be broken on one side
     * ({@code broken} = -1 or +1, 0 for whole), hanging lanterns beneath.
     */
    private static void arch(BlockPlan plan, int cx, int cz, boolean spanX, int half, int height, int broken) {
        for (int k = -half; k <= half; k++) {
            int x = spanX ? cx + k : cx;
            int z = spanX ? cz : cz + k;
            boolean pillar = Math.abs(k) == half;
            if (pillar) {
                int hh = broken != 0 && Integer.signum(k) == broken ? height - 3 : height;
                plan.set(x, 0, z, Material.SMOOTH_QUARTZ);
                ShapeKit.column(plan, x, 1, hh, z, Material.QUARTZ_PILLAR);
                plan.set(x, hh + 1, z, Material.CHISELED_QUARTZ_BLOCK);
            } else if (broken == 0 || Integer.signum(k) != broken || k == 0) {
                plan.set(x, height + 1, z, k == 0 ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_BLOCK);
                if (Math.abs(k) == half - 2 || k == 0) {
                    plan.set(x, height, z, Material.LANTERN, "hanging=true");
                }
            }
        }
    }

    /** The Island Gate: a tall pale arch at the end of the north road, an amethyst keystone, a landing beyond. */
    private static void islandGate(BlockPlan plan) {
        arch(plan, 0, -29, true, 4, 13, 0);
        plan.set(0, 15, -29, Material.AMETHYST_BLOCK);
        plan.set(0, 16, -29, Material.AMETHYST_CLUSTER, "facing=up");
        for (int sx : new int[] {-4, 4}) {
            plan.set(sx, 15, -29, Material.AMETHYST_CLUSTER, "facing=up");
        }
        for (int sx : new int[] {-5, 5}) {
            lampPost(plan, sx, -26, 2);
        }
        for (int k = 0; k < 4; k++) {
            double th = Math.toRadians(45 + 90 * k);
            int x = (int) Math.round(4.8 * Math.cos(th));
            int z = -38 + (int) Math.round(4.8 * Math.sin(th));
            plan.set(x, 1, z, Material.AMETHYST_BLOCK);
            plan.set(x, 2, z, Material.AMETHYST_CLUSTER, "facing=up");
        }
    }

    /** The Crypt barrow: a mound with a sealed doorway on its west face, soul lanterns either side. */
    private static void crypt(BlockPlan plan) {
        int cx = 29;
        for (int x = cx - 5; x <= cx + 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (!ground(x, z)) {
                    continue;
                }
                double d = Math.sqrt((double) (x - cx) * (x - cx) + z * z);
                for (int y = 1; y <= 5; y++) {
                    if (d * d + (y - 0.5) * (y - 0.5) * 1.5 <= 26) {
                        Material m = y >= 4 ? (h(x, z, 13) % 3 == 0 ? Material.MOSS_BLOCK : Material.TUFF)
                            : y >= 2 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE_BRICKS;
                        plan.set(x, y, z, m);
                    }
                }
            }
        }
        for (int x = cx - 5; x <= cx - 2; x++) {
            for (int z = -1; z <= 1; z++) {
                plan.set(x, 1, z, Material.AIR);
                plan.set(x, 2, z, Material.AIR);
            }
        }
        for (int z = -1; z <= 1; z++) {
            plan.set(cx - 1, 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
            plan.set(cx - 1, 2, z, z == 0 ? Material.IRON_BARS : Material.POLISHED_BLACKSTONE_BRICKS);
        }
        plan.set(cx - 1, 1, 0, Material.CHISELED_POLISHED_BLACKSTONE);
        for (int sz : new int[] {-2, 2}) {
            ShapeKit.column(plan, cx - 5, 1, 3, sz, Material.DEEPSLATE_BRICKS);
            plan.set(cx - 5, 4, sz, Material.SOUL_LANTERN, "hanging=false");
        }
        for (int z = -2; z <= 2; z++) {
            plan.set(cx - 5, 3, z, Material.DEEPSLATE_TILES);
        }
        plan.set(cx - 5, 3, 0, Material.CHISELED_DEEPSLATE);
    }

    /** The Rift tear: obsidian pillars, a broken lintel, purple glass shards hanging in the gap, floating amethyst. */
    private static void riftTear(BlockPlan plan) {
        int x = -29;
        for (int sz : new int[] {-4, 4}) {
            plan.set(x, 0, sz, Material.OBSIDIAN);
            for (int y = 1; y <= (sz < 0 ? 15 : 12); y++) {
                plan.set(x, y, sz, y % 3 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
            }
        }
        for (int z = -4; z <= 1; z++) {
            plan.set(x, 13, z, z == -1 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
        }
        for (int z = -3; z <= 3; z++) {
            for (int y = 1; y <= 10; y++) {
                if (h(x + y, z, 14) % 100 < 55 - (y > 8 ? 30 : 0)) {
                    plan.set(x, y, z, Material.PURPLE_STAINED_GLASS_PANE);
                }
            }
        }
        for (int[] s : new int[][] {{-31, 13, -2}, {-27, 15, 2}, {-30, 17, 0}}) {
            plan.set(s[0], s[1], s[2], Material.AMETHYST_BLOCK);
            plan.set(s[0], s[1] + 1, s[2], Material.AMETHYST_CLUSTER, "facing=up");
            plan.set(s[0], s[1] - 1, s[2], Material.AMETHYST_CLUSTER, "facing=down");
        }
        for (int sz : new int[] {-6, 6}) {
            lampPost(plan, x + 2, sz, 2);
        }
    }

    /** The processional: ruined arches, lantern posts, the Threshold at its end. */
    private static void processional(BlockPlan plan) {
        arch(plan, 0, 24, true, 4, 11, 0);
        arch(plan, 0, 32, true, 4, 11, 1);
        for (int z = 20; z <= 40; z += 6) {
            if (z == 24 || z == 32 || z == 38) {
                continue;
            }
            for (int sx : new int[] {-4, 4}) {
                if (ground(sx, z)) {
                    plan.set(sx, 0, z, Material.POLISHED_ANDESITE);
                    lampPost(plan, sx, z, 2);
                }
            }
        }
        // the Threshold: tall pillars either side of the road, a crowned lintel
        arch(plan, 0, 41, true, 5, 16, 0);
        plan.set(0, 18, 41, Material.SEA_LANTERN);
        plan.set(0, 19, 41, Material.END_ROD, "facing=up");
        plan.set(-5, 18, 41, Material.AMETHYST_CLUSTER, "facing=up");
        plan.set(5, 18, 41, Material.AMETHYST_CLUSTER, "facing=up");
        for (int sx : new int[] {-6, 6}) { // buttresses so the Threshold does not read as two sticks
            plan.set(sx, 0, 41, Material.POLISHED_DIORITE);
            ShapeKit.column(plan, sx, 1, 7, 41, Material.QUARTZ_PILLAR);
            plan.set(sx, 8, 41, Material.CHISELED_QUARTZ_BLOCK);
            plan.set(sx, 9, 41, Material.LANTERN, "hanging=false");
        }
    }

    // ---------------------------------------------------------------- rim and debris

    /** Broken pillars and rubble near the rim (never near a road or a pad). */
    private static void rimRuins(BlockPlan plan) {
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                if (!ground(x, z) || Math.abs(x) < 7 || Math.abs(z) < 7 || !nearRim(x, z, 3) || nearRim(x, z, 1)) {
                    continue;
                }
                if (nearPad(x, z, 8) || nearSpire(x, z, 5) || h(x, z, 15) % 40 != 0) {
                    continue;
                }
                // a colonnade that once stood round the ceremonial quarter: a few still tall, most broken
                int height = h(x, z, 16) % 3 == 0 ? 7 + h(x, z, 18) % 7 : 1 + h(x, z, 16) % 4;
                boolean cracked = h(x, z, 17) % 3 == 0;
                ShapeKit.column(plan, x, 1, height, z, cracked ? Material.CRACKED_STONE_BRICKS : Material.QUARTZ_PILLAR);
                if (height >= 7) {
                    plan.set(x, height + 1, z, Material.CHISELED_QUARTZ_BLOCK);
                    if (!cracked) {
                        plan.set(x, height + 2, z, Material.LANTERN, "hanging=false");
                    }
                }
                if (ground(x + 1, z) && plan.get(x + 1, 1, z) == null) {
                    plan.set(x + 1, 1, z, Material.MOSS_CARPET);
                }
            }
        }
    }

    private static boolean nearPad(int x, int z, int dist) {
        for (Pad pad : PADS) {
            if (Math.abs(pad.x() - x) <= dist && Math.abs(pad.z() - z) <= dist) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearSpire(int x, int z, int dist) {
        for (int[] at : RIM_SPIRES) {
            if (Math.abs(at[0] - x) <= dist && Math.abs(at[1] - z) <= dist) {
                return true;
            }
        }
        return false;
    }

    /** Inverted islets and loose bricks drifting around the fragment (clear of the Quai: its deck starts at z = 50). */
    private static void debris(BlockPlan plan) {
        int[][] pieces = {{-44, -6, -12, 4}, {45, -3, 14, 3}, {-40, 5, -30, 3}, {36, 6, -36, 3}, {50, -10, -8, 2},
            {-50, 0, 18, 4}, {-34, -8, 38, 3}, {40, -6, 34, 3}};
        for (int[] p : pieces) {
            for (int k = 0; k <= 4; k++) {
                double r = p[3] * (1 - k * 0.22);
                if (r < 0.6) {
                    break;
                }
                Material m = k == 0 ? (h(p[0], p[2], 18) % 2 == 0 ? Material.MOSSY_COBBLESTONE : Material.CALCITE)
                    : k < 3 ? Material.TUFF : Material.COBBLED_DEEPSLATE;
                ShapeKit.disc(plan, p[0], p[1] - k, p[2], r, m);
            }
            plan.set(p[0], p[1] + 1, p[2], Material.AMETHYST_CLUSTER, "facing=up");
            if (p[3] >= 3) {
                ShapeKit.column(plan, p[0] + 1, p[1] + 1, p[1] + 2 + h(p[0], p[2], 19) % 3, p[2], Material.QUARTZ_PILLAR);
            }
        }
        for (int i = 0; i < 40; i++) {
            double th = Math.toRadians(h(i, 1, 20) % 360);
            double r = 40 + h(i, 2, 21) % 14;
            int x = (int) Math.round(r * Math.cos(th));
            int z = (int) Math.round(r * Math.sin(th));
            if (z > 30 && Math.abs(x) < 28) {
                continue;
            }
            if (ground(x, z)) {
                continue;
            }
            int y = -8 + h(i, 3, 22) % 18;
            plan.set(x, y, z, h(i, 4, 23) % 4 == 0 ? Material.AMETHYST_BLOCK : h(i, 4, 23) % 4 == 1 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS);
        }
    }

    // ---------------------------------------------------------------- the Breach

    /**
     * The Breach ({@link HubBreach}): a torn islet north-west of the fragment, dark stone split by crying obsidian (its
     * one glow colour is violet), joined to the rim by a railed bridge with a warning sign at each end. The only place of
     * the hub where players take damage: the Breath's immune cells come through here.
     */
    private static void breach(BlockPlan plan) {
        int cx = HubBreach.X;
        int cz = HubBreach.Z;
        int r = HubBreach.RADIUS;
        double[] cracks = {0.3, 1.5, 2.6};
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (!HubBreach.ground(x, z)) {
                    continue;
                }
                double dx = x - cx;
                double dz = z - cz;
                boolean crack = false;
                for (double a : cracks) {
                    double along = dx * Math.cos(a) + dz * Math.sin(a);
                    double across = Math.abs(-dx * Math.sin(a) + dz * Math.cos(a) + 1.2 * Math.sin(along * 0.5));
                    crack |= across < 0.55 && Math.abs(along) > 3 && Math.abs(along) < r - 2 && h(x, z, 40) % 5 != 0;
                }
                int roll = h(x, z, 41) % 20;
                Material m = crack ? Material.CRYING_OBSIDIAN : roll < 9 ? Material.COBBLED_DEEPSLATE
                    : roll < 13 ? Material.CRACKED_DEEPSLATE_TILES : roll < 17 ? Material.TUFF : Material.DEEPSLATE_TILES;
                plan.set(x, 0, z, m);
                int depth = breachDepth(x, z);
                for (int k = 1; k <= depth; k++) {
                    boolean shell = k == 1 || k == depth || breachDepth(x + 1, z) < k || breachDepth(x - 1, z) < k
                        || breachDepth(x, z + 1) < k || breachDepth(x, z - 1) < k;
                    if (shell) {
                        plan.set(x, -k, z, k <= 2 ? Material.TUFF : Material.DEEPSLATE);
                    }
                }
                boolean rim = !HubBreach.ground(x + 1, z) || !HubBreach.ground(x - 1, z) || !HubBreach.ground(x, z + 1)
                    || !HubBreach.ground(x, z - 1);
                if (rim && h(x, z, 42) % 4 == 0 && Math.hypot(x - HubBreach.BRIDGE_TO[0], z - HubBreach.BRIDGE_TO[1]) > 4) {
                    ShapeKit.column(plan, x, 1, 1 + h(x, z, 43) % 2, z, Material.COBBLED_DEEPSLATE_WALL);
                }
            }
        }
        // the tear at the centre: a ring of crying obsidian around amethyst, violet shards hanging above
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 2.6 && d >= 1.6) {
                    plan.set(x, 0, z, Material.CRYING_OBSIDIAN);
                } else if (d < 1.6) {
                    plan.set(x, 0, z, Material.AMETHYST_BLOCK);
                }
            }
        }
        plan.set(cx, 1, cz, Material.AMETHYST_CLUSTER, "facing=up");
        int[][] shards = {{1, 5, 0}, {-1, 7, 1}, {0, 9, -1}, {2, 11, 1}, {-2, 6, -2}};
        for (int[] sh : shards) {
            plan.set(cx + sh[0], sh[1], cz + sh[2], Material.PURPLE_STAINED_GLASS);
        }
        // four broken pillars with soul lanterns
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            int x = (int) Math.round(cx + Math.cos(a) * 7);
            int z = (int) Math.round(cz + Math.sin(a) * 7);
            int top = 2 + h(x, z, 44) % 4;
            ShapeKit.column(plan, x, 1, top, z, i % 2 == 0 ? Material.CRACKED_DEEPSLATE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
            plan.set(x, top + 1, z, Material.SOUL_LANTERN, "hanging=false");
        }
        // the bridge: three wide, railed, from inside the hub rim to the islet
        int[] from = HubBreach.BRIDGE_FROM;
        int[] to = HubBreach.BRIDGE_TO;
        double len = Math.hypot(to[0] - from[0], to[1] - from[1]);
        double ux = (to[0] - from[0]) / len;
        double uz = (to[1] - from[1]) / len;
        int minX = Math.min(from[0], to[0]) - 4;
        int maxX = Math.max(from[0], to[0]) + 4;
        int minZ = Math.min(from[1], to[1]) - 4;
        int maxZ = Math.max(from[1], to[1]) + 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (ground(x, z) || HubBreach.ground(x, z)) {
                    continue;
                }
                double t = (x - from[0]) * ux + (z - from[1]) * uz;
                double w = Math.abs(-(x - from[0]) * uz + (z - from[1]) * ux);
                if (t < -3 || t > len + 2 || w > 2.0) {
                    continue;
                }
                plan.set(x, 0, z, w <= 1.0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
                plan.set(x, -1, z, Material.DEEPSLATE_TILES);
                if (w > 1.0) {
                    plan.set(x, 1, z, Material.COBBLED_DEEPSLATE_WALL);
                }
            }
        }
        int sx = (int) Math.round(from[0] - ux * 2 + uz * 2.5);
        int sz = (int) Math.round(from[1] - uz * 2 - ux * 2.5);
        plan.set(sx, 0, sz, Material.POLISHED_DEEPSLATE);
        plan.sign(sx, 1, sz, Material.DARK_OAK_SIGN, "rotation=" + signRotation(-ux, -uz), "The Breach", "No sanctuary:",
            "you can be hurt", "past this point");
        int gx = (int) Math.round(to[0] + ux * 3 - uz * 2.5);
        int gz = (int) Math.round(to[1] + uz * 3 + ux * 2.5);
        if (HubBreach.ground(gx, gz) && plan.get(gx, 1, gz) == null) {
            plan.set(gx, 0, gz, Material.POLISHED_DEEPSLATE);
            plan.sign(gx, 1, gz, Material.DARK_OAK_SIGN, "rotation=" + signRotation(-ux, -uz), "The Breach", "Immune cells",
                "come through", "in an invasion");
        }
    }

    private static int breachDepth(int x, int z) {
        if (!HubBreach.ground(x, z)) {
            return 0;
        }
        double u = Math.hypot(x - HubBreach.X, z - HubBreach.Z) / HubBreach.RADIUS;
        return Math.max(2, (int) Math.round(9 * Math.pow(Math.max(0, 1 - u), 0.8)) + h(x, z, 45) % 2);
    }

    // ---------------------------------------------------------------- underside

    private static int keelDepth(int x, int z) {
        if (!ground(x, z)) {
            return 0;
        }
        double d = Math.sqrt((double) x * x + z * z);
        double u = d / radiusAt(Math.atan2(z, x));
        if (u > 1 || approach(x, z) && u > 0.85) {
            return 3;
        }
        int depth = Math.max(2, (int) Math.round(24 * Math.pow(1 - u, 1.1)) + h(x, z, 3) % 3 - 1);
        if (h(x, z, 5) % 23 == 0 && u < 0.8) {
            depth += 3 + h(x, z, 6) % 4;
        }
        return depth;
    }

    /** A hollow shell: two solid layers under the surface, then a thin skin and whatever shows at the rim. */
    private static void underside(BlockPlan plan) {
        for (int x = -42; x <= 42; x++) {
            for (int z = -46; z <= CAUSEWAY_END_Z; z++) {
                int depth = keelDepth(x, z);
                if (depth == 0) {
                    continue;
                }
                for (int k = 1; k <= depth; k++) {
                    boolean shell = k <= 1 || k > depth - 2 || keelDepth(x + 1, z) < k || keelDepth(x - 1, z) < k
                        || keelDepth(x, z + 1) < k || keelDepth(x, z - 1) < k;
                    if (!shell) {
                        continue;
                    }
                    Material m = k <= 1 ? Material.STONE_BRICKS : k <= 4 ? (h(x, z + k, 24) % 3 == 0 ? Material.TUFF : Material.COBBLESTONE)
                        : k <= 8 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE;
                    plan.set(x, -k, z, m);
                }
                if (depth >= 6 && h(x, z, 25) % 17 == 0) {
                    plan.set(x, -depth - 1, z, Material.AMETHYST_CLUSTER, "facing=down");
                } else if (depth <= 5 && depth >= 3 && h(x, z, 26) % 9 == 0) {
                    // roots and old chains hang off the rim of the fragment
                    int len = 2 + h(x, z, 27) % 5;
                    boolean chain = h(x, z, 28) % 3 == 0;
                    for (int k = 1; k <= len; k++) {
                        plan.set(x, -depth - k, z, chain ? Material.IRON_CHAIN : Material.HANGING_ROOTS, chain ? "axis=y" : null);
                    }
                    if (chain) {
                        plan.set(x, -depth - len - 1, z, Material.SOUL_LANTERN, "hanging=true");
                    }
                }
            }
        }
    }

    /** All the blocks of the plan that lie outside the hub square (tests). */
    static List<int[]> outOfBounds(BlockPlan plan, int limit) {
        List<int[]> out = new ArrayList<>();
        for (long key : plan.blocks().keySet()) {
            int x = BlockPlan.keyX(key);
            int z = BlockPlan.keyZ(key);
            if (Math.abs(x) > limit || Math.abs(z) > limit) {
                out.add(new int[] {x, BlockPlan.keyY(key), z});
            }
        }
        return out;
    }
}
