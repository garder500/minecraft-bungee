package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/**
 * The procedurally built hub (relative coordinates, plaza surface at y = 0): a patterned plaza with a fountain, the
 * spawn pad, eight radial NPC pads plus the island and dungeon gates, four avenues, a ring road with lamp posts and
 * four towers. Pure data: {@link PlanJob} places it in batches.
 */
public final class HubPlan {
    /** Bump when the layout changes so existing hubs are rebuilt (the marker file stores it). */
    public static final int VERSION = 1;
    public static final int PLAZA_RADIUS = 26;
    public static final int RING_INNER = 37;
    public static final int RING_OUTER = 43;
    public static final int SPAWN_X = 0;
    public static final int SPAWN_Z = 14;

    /** A designated spot for an NPC. */
    public record Pad(String id, String title, int x, int z) {}

    /** Pads in a fixed order; ids are the contract with the features that spawn NPCs there. */
    public static final List<Pad> PADS = buildPads();

    private HubPlan() {}

    private static List<Pad> buildPads() {
        String[][] ring = {{"merchant", "Merchant"}, {"banker", "Banker"}, {"reforger", "Reforger"},
            {"enchanter", "Enchanter"}, {"quest_master", "Quest Master"}, {"minion_vendor", "Minion Vendor"},
            {"boss_arena", "Boss Arena"}, {"fairy_souls", "Fairy Guide"}};
        List<Pad> pads = new ArrayList<>();
        for (int k = 0; k < ring.length; k++) {
            double angle = 22.5 + 45 * k;
            pads.add(new Pad(ring[k][0], ring[k][1], ShapeKit.polarX(19.5, angle), ShapeKit.polarZ(19.5, angle)));
        }
        pads.add(new Pad("island_gate", "Island Gate", 0, -19));
        pads.add(new Pad("dungeon_gate", "Dungeon Gate", 33, 0));
        return List.copyOf(pads);
    }

    public static Pad pad(String id) {
        for (Pad pad : PADS) {
            if (pad.id().equals(id)) {
                return pad;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- plans

    /** The spawn pad alone (about 100 blocks), built synchronously so the hub is usable at once. */
    public static BlockPlan emergencySpawn() {
        BlockPlan plan = new BlockPlan();
        spawnPad(plan);
        return plan;
    }

    /** The whole hub. {@code cap} bounds the number of blocks; the most visible parts come first. */
    public static BlockPlan build(int cap) {
        BlockPlan plan = new BlockPlan(cap);
        spawnPad(plan);
        plazaTop(plan);
        fountain(plan);
        avenues(plan);
        ringRoad(plan);
        for (Pad pad : PADS) {
            pad(plan, pad);
        }
        planters(plan);
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                tower(plan, sx * 33, sz * 33);
            }
        }
        islandGateArch(plan);
        signposts(plan);
        plazaUnderside(plan);
        plan.anchor("spawn", SPAWN_X, 1, SPAWN_Z);
        for (Pad pad : PADS) {
            plan.anchor("pad:" + pad.id(), pad.x(), 1, pad.z());
        }
        return plan;
    }

    // ---------------------------------------------------------------- pieces

    private static void spawnPad(BlockPlan plan) {
        int z = SPAWN_Z;
        ShapeKit.disc(plan, 0, 0, z, 3.6, Material.POLISHED_BLACKSTONE_BRICKS);
        ShapeKit.disc(plan, 0, 0, z, 2.6, Material.POLISHED_BLACKSTONE);
        ShapeKit.disc(plan, 0, 0, z, 1.5, Material.GOLD_BLOCK);
        plan.set(0, 0, z, Material.SEA_LANTERN);
        for (int[] d : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            plan.set(d[0], 0, z + d[1], Material.SEA_LANTERN);
        }
        ShapeKit.disc(plan, 0, -1, z, 4.6, Material.STONE_BRICKS);
        for (int sx : new int[] {-5, 5}) {
            for (int dz : new int[] {-3, 3}) {
                ShapeKit.column(plan, sx, 1, 3, z + dz, Material.QUARTZ_PILLAR);
                plan.set(sx, 4, z + dz, Material.LANTERN, "hanging=false");
                plan.set(sx, 0, z + dz, Material.SMOOTH_QUARTZ);
            }
        }
    }

    private static Material plazaMaterial(int x, int z) {
        double d = Math.sqrt((double) x * x + z * z);
        if (d <= 5.5) {
            return Material.POLISHED_DIORITE;
        }
        if (d <= 8.5) {
            return Material.POLISHED_ANDESITE;
        }
        if (d <= 9.5) {
            return Material.SMOOTH_QUARTZ;
        }
        if (d > 24.5) {
            return Material.DEEPSLATE_TILES;
        }
        if (d > 23.5) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        if (d > 19 && d <= 20.2) {
            return Material.CHISELED_STONE_BRICKS;
        }
        for (int k = 0; k < 8; k++) {
            double th = Math.toRadians(k * 45);
            double along = x * Math.cos(th) + z * Math.sin(th);
            double lateral = Math.abs(-x * Math.sin(th) + z * Math.cos(th));
            if (along > 0 && lateral < 0.7) {
                return Material.QUARTZ_BRICKS;
            }
        }
        return ((x + z) & 1) == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
    }

    private static void plazaTop(BlockPlan plan) {
        int r = PLAZA_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= r * r + 0.25) {
                    plan.set(x, 0, z, plazaMaterial(x, z));
                }
            }
        }
    }

    private static void plazaUnderside(BlockPlan plan) {
        int r = PLAZA_RADIUS;
        ShapeKit.disc(plan, 0, -1, 0, r, Material.STONE_BRICKS);
        for (int k = 2; k <= 14; k++) {
            double scale = 1 - (k - 1) / 15.0;
            Material m = k <= 3 ? Material.STONE_BRICKS : k <= 8 ? Material.COBBLED_DEEPSLATE : Material.DEEPSLATE;
            ShapeKit.disc(plan, 0, -k, 0, r * scale, m);
        }
    }

    private static void fountain(BlockPlan plan) {
        ShapeKit.disc(plan, 0, 0, 0, 5.4, Material.POLISHED_DIORITE);
        ShapeKit.disc(plan, 0, 0, 0, 4.4, Material.WATER);
        ShapeKit.disc(plan, 0, -1, 0, 4.4, Material.STONE_BRICKS);
        ShapeKit.disc(plan, 0, -1, 0, 3, Material.SEA_LANTERN);
        ShapeKit.ring(plan, 0, 1, 0, 4.5, 5.4, Material.QUARTZ_SLAB);
        // tier 1
        ShapeKit.disc(plan, 0, 1, 0, 3, Material.SMOOTH_QUARTZ);
        ShapeKit.ring(plan, 0, 2, 0, 2.2, 3, Material.QUARTZ_SLAB);
        ShapeKit.disc(plan, 0, 2, 0, 2.1, Material.WATER);
        // tier 2
        ShapeKit.disc(plan, 0, 4, 0, 2, Material.SMOOTH_QUARTZ);
        ShapeKit.column(plan, 0, 1, 8, 0, Material.QUARTZ_PILLAR);
        ShapeKit.ring(plan, 0, 5, 0, 1.2, 2, Material.QUARTZ_SLAB);
        ShapeKit.disc(plan, 0, 5, 0, 1.1, Material.WATER);
        plan.set(0, 5, 0, Material.QUARTZ_PILLAR);
        plan.set(0, 9, 0, Material.SEA_LANTERN);
        plan.set(0, 10, 0, Material.END_ROD, "facing=up");
    }

    private static void pad(BlockPlan plan, Pad pad) {
        int px = pad.x();
        int pz = pad.z();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                plan.set(px + x, 0, pz + z, x == 0 && z == 0 ? Material.CHISELED_STONE_BRICKS : Material.POLISHED_ANDESITE);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    if (Math.abs(x) + Math.abs(z) < 4) {
                        plan.set(px + x, 0, pz + z, Material.SMOOTH_QUARTZ);
                    }
                }
            }
        }
        double len = Math.max(1, Math.sqrt((double) px * px + (double) pz * pz));
        int ux = (int) Math.round(px / len * 3);
        int uz = (int) Math.round(pz / len * 3);
        // lamp post behind the pad (away from the plaza centre)
        plan.set(px + ux, 0, pz + uz, Material.SMOOTH_QUARTZ);
        ShapeKit.column(plan, px + ux, 1, 2, pz + uz, Material.STONE_BRICK_WALL);
        plan.set(px + ux, 3, pz + uz, Material.LANTERN, "hanging=false");
        // sign in front (towards the centre)
        int sx = px - ux;
        int sz = pz - uz;
        int rotation = signRotation(-px, -pz);
        plan.sign(sx, 1, sz, Material.OAK_SIGN, "rotation=" + rotation, "", pad.title(), "", "");
        plan.set(sx, 0, sz, Material.SMOOTH_QUARTZ);
    }

    /** Standing sign rotation (0 = text faces south, 4 = west, 8 = north, 12 = east) for a facing vector. */
    static int signRotation(double dx, double dz) {
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        if (yaw < 0) {
            yaw += 360;
        }
        return (int) Math.round(yaw / 22.5) % 16;
    }

    private static void planters(BlockPlan plan) {
        for (int k = 0; k < 4; k++) {
            double angle = 45 + k * 90;
            int cx = ShapeKit.polarX(22.5, angle);
            int cz = ShapeKit.polarZ(22.5, angle);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    plan.set(cx + x, 0, cz + z, Material.MOSS_BLOCK);
                }
            }
            plan.set(cx, 1, cz, Material.FLOWERING_AZALEA);
            plan.set(cx + 1, 1, cz, Material.AZALEA);
            plan.set(cx - 1, 1, cz + 1, Material.MOSS_CARPET);
        }
    }

    private static void avenues(BlockPlan plan) {
        for (int d = PLAZA_RADIUS - 1; d <= RING_INNER + 1; d++) {
            for (int w = -2; w <= 2; w++) {
                Material m = w == 0 ? Material.QUARTZ_BRICKS : Math.abs(w) == 2 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS;
                plan.set(w, 0, -d, m);
                plan.set(w, 0, d, m);
                plan.set(d, 0, w, m);
                plan.set(-d, 0, w, m);
                plan.set(w, -1, -d, Material.STONE_BRICKS);
                plan.set(w, -1, d, Material.STONE_BRICKS);
                plan.set(d, -1, w, Material.STONE_BRICKS);
                plan.set(-d, -1, w, Material.STONE_BRICKS);
            }
        }
    }

    private static void ringRoad(BlockPlan plan) {
        for (int x = -RING_OUTER - 1; x <= RING_OUTER + 1; x++) {
            for (int z = -RING_OUTER - 1; z <= RING_OUTER + 1; z++) {
                double d = Math.sqrt((double) x * x + z * z);
                if (d >= RING_INNER - 0.5 && d <= RING_OUTER + 0.5) {
                    Material m = d < RING_INNER + 1 || d > RING_OUTER - 1 ? Material.POLISHED_ANDESITE
                        : (d > 39.5 && d < 40.5) ? Material.QUARTZ_BRICKS : Material.STONE_BRICKS;
                    plan.set(x, 0, z, m);
                    plan.set(x, -1, z, Material.STONE_BRICKS);
                }
            }
        }
        for (int k = 0; k < 12; k++) {
            int lx = ShapeKit.polarX(RING_OUTER + 1, k * 30 + 15);
            int lz = ShapeKit.polarZ(RING_OUTER + 1, k * 30 + 15);
            plan.set(lx, 0, lz, Material.POLISHED_BLACKSTONE_BRICKS);
            ShapeKit.column(plan, lx, 1, 3, lz, Material.STONE_BRICK_WALL);
            plan.set(lx, 4, lz, Material.LANTERN, "hanging=false");
        }
    }

    private static void tower(BlockPlan plan, int cx, int cz) {
        ShapeKit.disc(plan, cx, 0, cz, 4.6, Material.STONE_BRICKS);
        for (int y = 1; y <= 16; y++) {
            ShapeKit.ring(plan, cx, y, cz, 2.4, 3.3, y % 5 == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        }
        for (int y = 8; y <= 10; y++) {
            plan.set(cx + 3, y, cz, Material.GLASS_PANE);
            plan.set(cx - 3, y, cz, Material.GLASS_PANE);
            plan.set(cx, y, cz + 3, Material.GLASS_PANE);
            plan.set(cx, y, cz - 3, Material.GLASS_PANE);
        }
        for (int i = 0; i < 4; i++) {
            double r = 4.4 - i;
            ShapeKit.disc(plan, cx, 17 + i, cz, Math.max(r - (i == 0 ? 0 : 0.6), 0.5), Material.DEEPSLATE_TILES);
        }
        ShapeKit.disc(plan, cx, 16, cz, 2, Material.SEA_LANTERN);
        plan.set(cx, 21, cz, Material.DEEPSLATE_BRICKS);
        plan.set(cx, 22, cz, Material.LIGHTNING_ROD, "facing=up");
        for (int k = 2; k <= 9; k++) {
            ShapeKit.disc(plan, cx, -k, cz, 4.6 * (1 - (k - 1) / 10.0), k < 4 ? Material.STONE_BRICKS : Material.COBBLED_DEEPSLATE);
        }
    }

    private static void islandGateArch(BlockPlan plan) {
        int z = -22;
        for (int sx : new int[] {-3, 3}) {
            ShapeKit.column(plan, sx, 1, 7, z, Material.QUARTZ_PILLAR);
            plan.set(sx, 8, z, Material.SEA_LANTERN);
        }
        for (int x = -3; x <= 3; x++) {
            plan.set(x, 8, z, x == 0 ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_BLOCK);
        }
        plan.set(-2, 9, z, Material.QUARTZ_SLAB);
        plan.set(2, 9, z, Material.QUARTZ_SLAB);
        plan.set(0, 9, z, Material.QUARTZ_STAIRS, "facing=south");
    }

    private static void signposts(BlockPlan plan) {
        String[][] posts = {{"0", "-30", "North", "Island Gate", "Plaza"}, {"30", "0", "East", "Dungeon Gate", "Ring road"},
            {"0", "30", "South", "Spawn", "Plaza"}, {"-30", "0", "West", "Boss Arena", "Ring road"}};
        for (String[] post : posts) {
            int x = Integer.parseInt(post[0]) + (post[0].equals("0") ? 3 : 0);
            int z = Integer.parseInt(post[1]) + (post[1].equals("0") ? 3 : 0);
            plan.set(x, 0, z, Material.POLISHED_ANDESITE);
            plan.sign(x, 1, z, Material.OAK_SIGN, "rotation=" + signRotation(-x, -z), post[2], post[3], post[4], "");
        }
    }
}
