package win.vampyuyu.survival.astral;

import org.bukkit.Material;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.ShapeKit;

/**
 * The block plan of the Astral Interiority (GDD 4.5): the Rift Landing, a sea of liquid memory with ruined islets, the Nervous
 * Tree and its synapse lifts, and a causeway around a singularity ending on the Throne. Pure data like {@code DepthsPlan}:
 * every position comes from {@link AstralLayout}.
 */
public final class AstralPlan {
    private AstralPlan() {}

    public static BlockPlan build() {
        BlockPlan plan = new BlockPlan(AstralLayout.CAP);
        landing(plan);
        sea(plan);
        for (AstralLayout.Islet islet : AstralLayout.ISLETS) {
            islet(plan, islet);
        }
        // the road: landing -> library -> amphitheatre -> lighthouse -> cradle -> shore -> Tree
        int px = 0;
        int pz = 0;
        int pr = AstralLayout.LANDING_RADIUS - 1;
        for (AstralLayout.Islet islet : AstralLayout.ISLETS) {
            bridge(plan, px, pz, pr, 0, islet.x(), islet.z(), islet.radius() - 1);
            px = islet.x();
            pz = islet.z();
            pr = islet.radius() - 1;
        }
        bridge(plan, px, pz, pr, 0, AstralLayout.TREE[0], AstralLayout.TREE[2], AstralLayout.TREE_BASE_RADIUS - 1);
        tree(plan);
        throne(plan);
        for (AstralLayout.Landmark mark : AstralLayout.LANDMARKS) {
            plan.anchor("landmark:" + mark.id(), mark.x(), mark.y(), mark.z());
        }
        plan.anchor("spawn", AstralLayout.SPAWN[0], AstralLayout.SPAWN[1], AstralLayout.SPAWN[2]);
        return plan;
    }

    // ---------------------------------------------------------------- landing and sea

    private static void landing(BlockPlan plan) {
        int radius = AstralLayout.LANDING_RADIUS;
        int depth = 7;
        for (int k = 0; k <= depth; k++) {
            double r = radius * (1 - Math.pow(k / (depth + 1.0), 1.5));
            ShapeKit.disc(plan, 0, -k, 0, r, k == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BLACKSTONE);
        }
        ShapeKit.ring(plan, 0, 0, 0, radius - 1.5, radius - 0.5, Material.CRYING_OBSIDIAN);
        ShapeKit.disc(plan, 0, 0, 0, 2.5, Material.GILDED_BLACKSTONE);
        for (int[] d : new int[][] {{7, 0}, {-7, 0}, {0, 7}, {0, -7}}) {
            plan.set(d[0], 0, d[1], Material.SEA_LANTERN);
        }
        plan.sign(0, 1, 2, Material.OAK_SIGN, "rotation=0", "Act III", "Astral", "Interiority", "/quai to leave");
    }

    private static void sea(BlockPlan plan) {
        int x0 = AstralLayout.SEA_MIN_X;
        int x1 = AstralLayout.SEA_MAX_X;
        int w = AstralLayout.SEA_HALF_WIDTH;
        int floor = -AstralLayout.SEA_DEPTH - 1;
        plan.fill(x0, floor, -w, x1, floor, w, Material.DEEPSLATE_TILES);
        plan.fill(x0 + 1, floor + 1, -w + 1, x1 - 1, -1, w - 1, Material.WATER);
        // the curb keeps the liquid in: a wall around the basin up to the height of the walkways
        plan.fill(x0, floor + 1, -w, x1, 0, -w, Material.DEEPSLATE_TILES);
        plan.fill(x0, floor + 1, w, x1, 0, w, Material.DEEPSLATE_TILES);
        plan.fill(x0, floor + 1, -w, x0, 0, w, Material.DEEPSLATE_TILES);
        plan.fill(x1, floor + 1, -w, x1, 0, w, Material.DEEPSLATE_TILES);
        // drowned spires standing in the memory, decor only
        for (int i = 0; i < 18; i++) {
            int x = x0 + 6 + Math.floorMod(i * 37, x1 - x0 - 12);
            int z = -w + 4 + Math.floorMod(i * 53, 2 * w - 8);
            plan.fill(x, floor + 1, z, x, -2 - Math.floorMod(i, 3), z, Material.STONE_BRICKS);
        }
    }

    // ---------------------------------------------------------------- islets

    private static void islet(BlockPlan plan, AstralLayout.Islet islet) {
        int x = islet.x();
        int z = islet.z();
        int r = islet.radius();
        for (int k = 0; k <= 3; k++) {
            ShapeKit.disc(plan, x, -k, z, Math.max(1, r - k), k == 0 ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
        }
        ShapeKit.ring(plan, x, 0, z, r - 1, r, Material.CRACKED_STONE_BRICKS);
        switch (islet.id()) {
            case "library" -> {
                for (int a = 0; a < 360; a += 30) {
                    if (a % 90 == 0) {
                        continue;
                    }
                    int bx = x + ShapeKit.polarX(r - 2, a);
                    int bz = z + ShapeKit.polarZ(r - 2, a);
                    plan.fill(bx, 1, bz, bx, 3 - (a / 30) % 2, bz, Material.BOOKSHELF);
                }
                plan.set(x, 1, z, Material.LECTERN, "facing=south");
            }
            case "amphitheatre" -> {
                ShapeKit.ring(plan, x, 1, z, r - 2.5, r - 1.5, Material.SMOOTH_QUARTZ);
                ShapeKit.ring(plan, x, 2, z, r - 1.5, r - 0.5, Material.SMOOTH_QUARTZ);
                ShapeKit.disc(plan, x, 0, z, 2.5, Material.CHISELED_QUARTZ_BLOCK);
                plan.set(x, 1, z, Material.NOTE_BLOCK);
            }
            case "lighthouse" -> {
                plan.fill(x - 1, 1, z - 1, x + 1, 11, z + 1, Material.STONE_BRICKS);
                plan.fill(x - 1, 12, z - 1, x + 1, 12, z + 1, Material.SEA_LANTERN);
                plan.fill(x - 2, 13, z - 2, x + 2, 13, z + 2, Material.SMOOTH_STONE_SLAB);
            }
            case "cradle" -> {
                for (int a = 0; a < 360; a += 60) {
                    int bx = x + ShapeKit.polarX(r - 2, a);
                    int bz = z + ShapeKit.polarZ(r - 2, a);
                    plan.fill(bx, 1, bz, bx, 3, bz, Material.STONE_BRICK_WALL);
                    plan.set(bx, 4, bz, Material.SOUL_LANTERN);
                }
                plan.set(x, 1, z, Material.RESPAWN_ANCHOR);
            }
            default -> {
                for (int[] d : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
                    plan.set(x + d[0], 1, z + d[1], Material.SOUL_LANTERN);
                }
            }
        }
        // an impossible architecture: a pillar hanging from nothing beside the islet
        plan.fill(x - r - 2, 6, z, x - r - 2, 16, z, Material.END_STONE_BRICKS);
        plan.set(x - r - 2, 5, z, Material.SEA_LANTERN);
    }

    // ---------------------------------------------------------------- Nervous Tree

    private static void tree(BlockPlan plan) {
        int cx = AstralLayout.TREE[0];
        int cz = AstralLayout.TREE[2];
        int radius = AstralLayout.TREE_BASE_RADIUS;
        for (int k = 0; k <= 9; k++) {
            double r = radius * (1 - Math.pow(k / 10.0, 1.4));
            ShapeKit.disc(plan, cx, -k, cz, r, k == 0 ? Material.WARPED_NYLIUM : k <= 2 ? Material.NETHERRACK : Material.DEEPSLATE);
        }
        ShapeKit.ring(plan, cx, 0, cz, radius - 1.5, radius - 0.5, Material.CRIMSON_NYLIUM);
        // the trunk, with a ring of shroomlight every seventh layer
        for (int y = 1; y < AstralLayout.CROWN_Y; y++) {
            ShapeKit.disc(plan, cx, y, cz, AstralLayout.TRUNK_RADIUS, y % 7 == 0 ? Material.SHROOMLIGHT : Material.CRIMSON_HYPHAE);
        }
        for (int i = 1; i <= AstralLayout.PLATFORMS; i++) {
            int[] p = AstralLayout.platform(i);
            for (int k = 0; k <= 2; k++) {
                ShapeKit.disc(plan, p[0], p[1] - k, p[2], Math.max(1, AstralLayout.PLATFORM_RADIUS - k), k == 0 ? Material.WARPED_WART_BLOCK : Material.NETHER_WART_BLOCK);
            }
            line(plan, cx, cz, p[0], p[2], p[1], Material.WARPED_HYPHAE);
        }
        for (AstralLayout.Synapse synapse : AstralLayout.SYNAPSES) {
            plan.set(synapse.x(), synapse.y() - 1, synapse.z(), Material.SEA_LANTERN);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                plan.set(synapse.x() + d[0], synapse.y() - 1, synapse.z() + d[1], Material.LIGHT_BLUE_GLAZED_TERRACOTTA);
            }
            plan.anchor(synapse.id(), synapse.x(), synapse.y(), synapse.z());
        }
        // the Crown
        int y = AstralLayout.CROWN_Y;
        for (int k = 0; k <= 3; k++) {
            ShapeKit.disc(plan, cx, y - k, cz, Math.max(2, AstralLayout.CROWN_RADIUS - k * 2), k == 0 ? Material.WARPED_WART_BLOCK : Material.NETHER_WART_BLOCK);
        }
        ShapeKit.ring(plan, cx, y, cz, AstralLayout.CROWN_RADIUS - 1, AstralLayout.CROWN_RADIUS, Material.SHROOMLIGHT);
        plan.sign(cx + 2, y + 1, cz - 2, Material.OAK_SIGN, "rotation=8", "Crown of", "Synapses", "East: the", "Throne");
        // the way to the Throne
        bridge(plan, cx, cz, AstralLayout.CROWN_RADIUS - 1, y, AstralLayout.NEXUS[0], AstralLayout.NEXUS[2], AstralLayout.NEXUS_RADIUS - 1);
    }

    /** A one block wide branch from the trunk to a platform. */
    private static void line(BlockPlan plan, int x0, int z0, int x1, int z1, int y, Material material) {
        int steps = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0));
        for (int s = AstralLayout.TRUNK_RADIUS; s <= steps - AstralLayout.PLATFORM_RADIUS + 1; s++) {
            int x = (int) Math.round(x0 + (x1 - x0) * (s / (double) steps));
            int z = (int) Math.round(z0 + (z1 - z0) * (s / (double) steps));
            plan.set(x, y, z, material);
        }
    }

    // ---------------------------------------------------------------- Gravitational Throne

    private static void throne(BlockPlan plan) {
        int y = AstralLayout.SINGULARITY[1];
        int sx = AstralLayout.SINGULARITY[0];
        int sz = AstralLayout.SINGULARITY[2];
        // the Nexus
        for (int k = 0; k <= 3; k++) {
            ShapeKit.disc(plan, AstralLayout.NEXUS[0], y - k, AstralLayout.NEXUS[2], Math.max(2, AstralLayout.NEXUS_RADIUS - k * 2),
                k == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.OBSIDIAN);
        }
        ShapeKit.ring(plan, AstralLayout.NEXUS[0], y, AstralLayout.NEXUS[2], AstralLayout.NEXUS_RADIUS - 1.5, AstralLayout.NEXUS_RADIUS - 0.5, Material.CRYING_OBSIDIAN);
        // the singularity: a black sphere and its accretion ring
        int r = AstralLayout.SINGULARITY_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        plan.set(sx + dx, y + dy, sz + dz, Material.BLACK_CONCRETE);
                    }
                }
            }
        }
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double d = Math.hypot(dx, dz);
                if (d >= r + 1 && d <= 10) {
                    int h = Math.floorMod(dx * 7 + dz * 11, 5);
                    plan.set(sx + dx, y, sz + dz, h == 0 ? Material.WHITE_CONCRETE : h < 3 ? Material.ORANGE_CONCRETE : Material.YELLOW_CONCRETE);
                }
            }
        }
        // the causeway: the half of the ring of radius ARC_RADIUS on the north side (z <= 0), from the Nexus to the dais
        double r0 = AstralLayout.ARC_RADIUS - 2.5;
        double r1 = AstralLayout.ARC_RADIUS + 2.5;
        int ri = (int) Math.ceil(r1) + 1;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= 0; dz++) {
                double d = Math.hypot(dx, dz);
                if (d >= r0 && d <= r1) {
                    boolean edge = d < r0 + 1 || d > r1 - 1;
                    plan.set(sx + dx, y, sz + dz, edge ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
                    plan.set(sx + dx, y - 1, sz + dz, Material.OBSIDIAN);
                }
            }
        }
        for (AstralLayout.Anchor anchor : AstralLayout.ANCHORS) {
            plan.set(anchor.x(), anchor.y(), anchor.z(), Material.LODESTONE);
            for (int h = 1; h <= 3; h++) {
                plan.set(anchor.x(), anchor.y() + h, anchor.z(), Material.END_ROD, "facing=up");
            }
            plan.anchor(anchor.id(), anchor.x(), anchor.y(), anchor.z());
        }
        // the dais and the Throne
        int tx = AstralLayout.THRONE[0];
        int tz = AstralLayout.THRONE[2];
        ShapeKit.disc(plan, tx, y, tz, 4, Material.CRYING_OBSIDIAN);
        ShapeKit.disc(plan, tx, y - 1, tz, 3, Material.OBSIDIAN);
        plan.set(tx, y + 1, tz, Material.QUARTZ_STAIRS, "facing=west,half=bottom");
        plan.fill(tx + 1, y + 1, tz, tx + 1, y + 4, tz, Material.QUARTZ_PILLAR);
        plan.set(tx, y + 2, tz - 1, Material.QUARTZ_SLAB);
        plan.set(tx, y + 2, tz + 1, Material.QUARTZ_SLAB);
        plan.anchor("throne", tx, y + 1, tz);
    }

    // ---------------------------------------------------------------- walkways

    /**
     * A 3 wide glass walkway at height {@code y} from one platform to the next, with a lantern post every 10 blocks. The
     * first four arguments after the plan are the start (centre, inset to skip) and the height.
     */
    static void bridge(BlockPlan plan, int x0, int z0, int startInset, int y, int x1, int z1, int endInset) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        double ux = (x1 - x0) / len;
        double uz = (z1 - z0) / len;
        double px = -uz;
        double pz = ux;
        int last = (int) Math.round(len - endInset);
        for (int s = startInset; s <= last; s++) {
            for (int w = -1; w <= 1; w++) {
                int x = (int) Math.round(x0 + ux * s + px * w);
                int z = (int) Math.round(z0 + uz * s + pz * w);
                plan.set(x, y, z, Math.abs(w) == 1 ? Material.BLUE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS);
            }
            if (s % 10 == 5) {
                int x = (int) Math.round(x0 + ux * s + px * 2);
                int z = (int) Math.round(z0 + uz * s + pz * 2);
                plan.set(x, y, z, Material.DEEPSLATE_TILES);
                plan.set(x, y + 1, z, Material.COBBLED_DEEPSLATE_WALL);
                plan.set(x, y + 2, z, Material.SEA_LANTERN);
            }
        }
    }
}
