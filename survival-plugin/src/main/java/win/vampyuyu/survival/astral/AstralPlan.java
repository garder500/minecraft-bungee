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
        spectacle(plan);
        for (BlockPlan.Model model : models()) {
            plan.model(model);
        }
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
                    plan.set(sx + dx, y, sz + dz, h == 0 ? Material.PEARLESCENT_FROGLIGHT : h < 3 ? Material.MAGMA_BLOCK : Material.OCHRE_FROGLIGHT);
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

    // ---------------------------------------------------------------- spectacle (plan version 2)

    private static int h(int x, int z, int salt) {
        int n = x * 374761393 + z * 668265263 + salt * 1274126177;
        n = (n ^ (n >>> 13)) * 1274126177;
        return (n ^ (n >>> 16)) & 0x7fffffff;
    }

    private static void put(BlockPlan plan, int x, int y, int z, Material m) {
        if (plan.get(x, y, z) == null) {
            plan.set(x, y, z, m);
        }
    }

    /**
     * What makes the inside of the Devourer read as a place: the wound players came in through, standing behind the
     * landing; flotsam of devoured ideas along the basin's curb and fragments drifting over the sea; veins and hanging
     * roots on the Nervous Tree; a glowing accretion disc and a monumental Throne. Never on a walkway, a pad or a lift.
     */
    private static void spectacle(BlockPlan plan) {
        riftWound(plan);
        flotsam(plan);
        drift(plan);
        treeVeins(plan);
        throneBack(plan);
        nexusDebris(plan);
    }

    /** The tear behind the landing: an upright ellipse of obsidian, its inside hung with violet glass and amethyst. */
    private static void riftWound(BlockPlan plan) {
        int z = -AstralLayout.LANDING_RADIUS - 2;
        double rx = 6.5;
        double ry = 10;
        double cy = 8;
        for (int x = -9; x <= 9; x++) {
            for (int y = -3; y <= 20; y++) {
                double e = (x / rx) * (x / rx) + ((y - cy) / ry) * ((y - cy) / ry);
                double eo = (x / (rx + 1.6)) * (x / (rx + 1.6)) + ((y - cy) / (ry + 1.6)) * ((y - cy) / (ry + 1.6));
                if (eo <= 1 && e > 1) {
                    plan.set(x, y, z, h(x, y, 61) % 3 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                    if (h(x, y, 62) % 4 == 0) {
                        plan.set(x, y, z + 1, Material.CRYING_OBSIDIAN);
                    }
                } else if (e <= 1 && h(x, y, 63) % 100 < 60) {
                    plan.set(x, y, z, h(x, y, 64) % 7 == 0 ? Material.AMETHYST_BLOCK : Material.PURPLE_STAINED_GLASS);
                }
            }
        }
    }

    /** Stacks of what the Devourer swallowed along the curb, so the sea stops reading as a box. */
    private static void flotsam(BlockPlan plan) {
        int x0 = AstralLayout.SEA_MIN_X;
        int x1 = AstralLayout.SEA_MAX_X;
        int w = AstralLayout.SEA_HALF_WIDTH;
        Material[] stuff = {Material.BOOKSHELF, Material.CHISELED_BOOKSHELF, Material.QUARTZ_PILLAR, Material.CRACKED_STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS, Material.CALCITE, Material.AMETHYST_BLOCK, Material.TINTED_GLASS};
        java.util.List<int[]> curb = new java.util.ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            curb.add(new int[] {x, -w});
            curb.add(new int[] {x, w});
        }
        for (int z = -w; z <= w; z++) {
            curb.add(new int[] {x0, z});
            curb.add(new int[] {x1, z});
        }
        for (int[] c : curb) {
            if (h(c[0], c[1], 65) % 3 != 0 || nearWalkway(plan, c[0], c[1])) {
                continue;
            }
            int height = 1 + h(c[0], c[1], 66) % 6;
            for (int y = 1; y <= height; y++) {
                put(plan, c[0], y, c[1], stuff[h(c[0] + y, c[1], 67) % stuff.length]);
            }
            if (height >= 4 && h(c[0], c[1], 68) % 3 == 0) {
                put(plan, c[0], height + 1, c[1], Material.SOUL_LANTERN);
            }
        }
    }

    private static boolean nearWalkway(BlockPlan plan, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Material m = plan.get(x + dx, 0, z + dz);
                if (m == Material.BLUE_STAINED_GLASS || m == Material.LIGHT_BLUE_STAINED_GLASS) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Fragments of swallowed places drifting over the sea, out of reach. */
    private static void drift(BlockPlan plan) {
        for (int i = 0; i < 22; i++) {
            int x = AstralLayout.SEA_MIN_X + 8 + h(i, 1, 70) % (AstralLayout.SEA_MAX_X - AstralLayout.SEA_MIN_X - 16);
            int z = -AstralLayout.SEA_HALF_WIDTH + 6 + h(i, 2, 71) % (2 * AstralLayout.SEA_HALF_WIDTH - 12);
            int y = 9 + h(i, 3, 72) % 14;
            int r = 2 + h(i, 4, 73) % 2;
            for (int k = 0; k <= r + 1; k++) {
                double rk = r * (1 - k / (r + 2.0));
                int ri = (int) Math.ceil(rk);
                for (int dx = -ri; dx <= ri; dx++) {
                    for (int dz = -ri; dz <= ri; dz++) {
                        if (Math.hypot(dx, dz) <= rk) {
                            put(plan, x + dx, y - k, z + dz, k == 0 ? Material.MOSSY_STONE_BRICKS : Material.DEEPSLATE);
                        }
                    }
                }
            }
            switch (i % 4) {
                case 0 -> { put(plan, x, y + 1, z, Material.BOOKSHELF); put(plan, x, y + 2, z, Material.BOOKSHELF); put(plan, x, y + 3, z, Material.LANTERN); }
                case 1 -> { put(plan, x, y + 1, z, Material.QUARTZ_PILLAR); put(plan, x, y + 2, z, Material.QUARTZ_PILLAR); put(plan, x, y + 3, z, Material.CHISELED_QUARTZ_BLOCK); }
                case 2 -> { put(plan, x, y + 1, z, Material.AMETHYST_BLOCK); put(plan, x, y + 2, z, Material.AMETHYST_CLUSTER); }
                default -> put(plan, x, y + 1, z, Material.SOUL_LANTERN);
            }
        }
    }

    /** Glowing veins up the trunk of the Nervous Tree and weeping roots under its base. */
    private static void treeVeins(BlockPlan plan) {
        int cx = AstralLayout.TREE[0];
        int cz = AstralLayout.TREE[2];
        int r = AstralLayout.TRUNK_RADIUS;
        for (int y = 1; y < AstralLayout.CROWN_Y; y++) {
            for (int v = 0; v < 4; v++) {
                double a = Math.toRadians(v * 90 + y * 6);
                int x = cx + (int) Math.round(Math.cos(a) * r);
                int z = cz + (int) Math.round(Math.sin(a) * r);
                if (plan.get(x, y, z) == Material.CRIMSON_HYPHAE) {
                    plan.set(x, y, z, y % 3 == 0 ? Material.SHROOMLIGHT : Material.CRYING_OBSIDIAN);
                }
            }
        }
        int base = AstralLayout.TREE_BASE_RADIUS;
        for (int i = 0; i < 28; i++) {
            double a = Math.toRadians(i * 360.0 / 28);
            int x = cx + (int) Math.round(Math.cos(a) * (base - 3));
            int z = cz + (int) Math.round(Math.sin(a) * (base - 3));
            int top = -4;
            while (top > -10 && plan.get(x, top, z) != null) {
                top--;
            }
            int len = 3 + h(x, z, 74) % 8;
            for (int k = 0; k < len; k++) {
                put(plan, x, top - k, z, k == len - 1 ? Material.WEEPING_VINES : Material.WEEPING_VINES_PLANT);
            }
        }
    }

    /** The Throne made monumental: a tall back of quartz banded in obsidian, two flanking spires, an amethyst crown. */
    private static void throneBack(BlockPlan plan) {
        int tx = AstralLayout.THRONE[0];
        int tz = AstralLayout.THRONE[2];
        int y = AstralLayout.THRONE[1];
        for (int k = 5; k <= 14; k++) {
            plan.set(tx + 1, y + k, tz, k % 4 == 0 ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_PILLAR);
        }
        plan.set(tx + 1, y + 15, tz, Material.AMETHYST_BLOCK);
        plan.set(tx + 1, y + 16, tz, Material.AMETHYST_CLUSTER);
        for (int side : new int[] {-2, 2}) {
            for (int k = 1; k <= 9; k++) {
                plan.set(tx + 2, y + k, tz + side, k % 3 == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
            }
            plan.set(tx + 2, y + 10, tz + side, Material.END_ROD);
        }
    }

    /** Rubble the Nexus has pulled in, drifting round the singularity at a distance. */
    private static void nexusDebris(BlockPlan plan) {
        int sx = AstralLayout.SINGULARITY[0];
        int sy = AstralLayout.SINGULARITY[1];
        for (int i = 0; i < 14; i++) {
            double a = Math.toRadians(i * 360.0 / 14 + 7);
            double d = AstralLayout.ARC_RADIUS + 9 + h(i, 5, 75) % 8;
            int x = sx + (int) Math.round(Math.cos(a) * d);
            int z = (int) Math.round(Math.sin(a) * d);
            int y = sy - 6 + h(i, 6, 76) % 18;
            int r = 1 + h(i, 7, 77) % 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= r * r + 1) {
                            put(plan, x + dx, y + dy, z + dz, h(dx + i, dz, 78) % 4 == 0 ? Material.CRYING_OBSIDIAN : Material.BLACKSTONE);
                        }
                    }
                }
            }
        }
    }

    /**
     * The display models of the Interiority, relative to its plan origin: the accretion rings turning round the
     * singularity, and a violet halo behind the Throne.
     */
    public static java.util.List<BlockPlan.Model> models() {
        double sx = AstralLayout.SINGULARITY[0] + 0.5;
        double sy = AstralLayout.SINGULARITY[1] + 0.5;
        double sz = AstralLayout.SINGULARITY[2] + 0.5;
        return java.util.List.of(
            BlockPlan.Model.of("structure/quai_halo_lit", sx, sy, sz, 7f).tilt(90, 0, 0).spin(28, "z").color(0xFFB040),
            BlockPlan.Model.of("structure/quai_halo_lit", sx, sy, sz, 8.5f).tilt(72, 0, 18).spin(-20, "z").color(0xFFFFFF),
            BlockPlan.Model.of("structure/quai_halo_lit", sx, sy, sz, 10f).tilt(104, 30, 0).spin(14, "z").color(0xC27BFF),
            BlockPlan.Model.of("structure/quai_halo_lit", AstralLayout.THRONE[0] + 1.5, AstralLayout.THRONE[1] + 9.5,
                AstralLayout.THRONE[2] + 0.5, 3.2f).tilt(0, 90, 0).spin(10, "z").color(0xD060FF));
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
