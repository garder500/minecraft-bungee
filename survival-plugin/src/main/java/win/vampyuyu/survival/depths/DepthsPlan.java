package win.vampyuyu.survival.depths;

import org.bukkit.Material;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.ShapeKit;

/**
 * The block plan of the Abyssal Depths (GDD 3.1): suspended domes of water in prismarine shells, joined by narrow
 * bridges, and a Chain Dome whose sealed door keeps the Leviathan. Pure data like {@code AshesPlan}; every position
 * comes from {@link DepthsLayout}.
 */
public final class DepthsPlan {
    /** The iron chain block: renamed {@code iron_chain} in the newest versions, so asked by name and never assumed. */
    private static final Material CHAIN = chain();

    private DepthsPlan() {}

    private static Material chain() {
        Material found = Material.matchMaterial("IRON_CHAIN");
        if (found == null) {
            found = Material.matchMaterial("CHAIN");
        }
        return found == null ? Material.IRON_BARS : found;
    }

    public static BlockPlan build() {
        BlockPlan plan = new BlockPlan(DepthsLayout.CAP);
        landing(plan);
        for (DepthsLayout.Dome dome : DepthsLayout.DOMES) {
            dome(plan, dome);
        }
        bell(plan);
        // the road: landing -> shoals -> kelp -> bell -> trench -> chain
        DepthsLayout.Dome shoals = DepthsLayout.dome("shoals");
        DepthsLayout.Dome kelp = DepthsLayout.dome("kelp");
        DepthsLayout.Dome trench = DepthsLayout.dome("trench");
        DepthsLayout.Dome chain = DepthsLayout.dome("chain");
        bridge(plan, 0, 0, DepthsLayout.LANDING_RADIUS - 1, shoals.x(), shoals.z(), shoals.radius() + DepthsLayout.RIM);
        bridge(plan, shoals.x(), shoals.z(), shoals.radius() + 1, kelp.x(), kelp.z(), kelp.radius() + DepthsLayout.RIM);
        bridge(plan, kelp.x(), kelp.z(), kelp.radius() + 1, DepthsLayout.BELL[0], DepthsLayout.BELL[1], DepthsLayout.BELL[2]);
        bridge(plan, DepthsLayout.BELL[0], DepthsLayout.BELL[1], DepthsLayout.BELL[2] - 1, trench.x(), trench.z(),
            trench.radius() + DepthsLayout.RIM);
        bridge(plan, trench.x(), trench.z(), trench.radius() + 1, chain.x(), chain.z(), chain.radius() + DepthsLayout.RIM);
        for (DepthsLayout.Pump pump : DepthsLayout.PUMPS) {
            plan.set(pump.x(), pump.y() - 1, pump.z(), Material.DARK_PRISMARINE);
            plan.set(pump.x(), pump.y(), pump.z(), Material.CONDUIT);
            plan.anchor(pump.id(), pump.x(), pump.y(), pump.z());
        }
        for (DepthsLayout.Landmark mark : DepthsLayout.LANDMARKS) {
            plan.anchor("landmark:" + mark.id(), mark.x(), mark.y(), mark.z());
        }
        plan.anchor("spawn", DepthsLayout.SPAWN[0], DepthsLayout.SPAWN[1], DepthsLayout.SPAWN[2]);
        return plan;
    }

    private static void landing(BlockPlan plan) {
        int radius = DepthsLayout.LANDING_RADIUS;
        int depth = 8;
        for (int k = 0; k <= depth; k++) {
            double r = radius * (1 - Math.pow(k / (depth + 1.0), 1.5));
            ShapeKit.disc(plan, 0, -k, 0, r, k == 0 ? Material.PRISMARINE_BRICKS : k <= 2 ? Material.PRISMARINE : Material.DARK_PRISMARINE);
        }
        ShapeKit.ring(plan, 0, 0, 0, radius - 1.5, radius - 0.5, Material.DARK_PRISMARINE);
        ShapeKit.disc(plan, 0, 0, 0, 2.5, Material.PRISMARINE);
        for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            plan.set(d[0], 0, d[1], Material.SEA_LANTERN);
        }
        plan.set(0, 1, 0, Material.LODESTONE);
        plan.sign(0, 1, 3, Material.OAK_SIGN, "rotation=0", "Gate 7", "Abyssal Depths", "Pressure Landing", "/quai to return");
    }

    /** A bowl of water in a shell, with a walkable rim and sea lanterns shining into the water. */
    private static void dome(BlockPlan plan, DepthsLayout.Dome dome) {
        int r = dome.radius();
        int cx = dome.x();
        int cz = dome.z();
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                for (int dy = -r - 1; dy <= 0; dy++) {
                    double dist = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
                    if (dist <= r - 1) {
                        plan.set(cx + dx, -1 + dy, cz + dz, Material.WATER);
                    } else if (dist <= r) {
                        plan.set(cx + dx, -1 + dy, cz + dz, shell(dx, dy, dz));
                    }
                }
            }
        }
        // the rim at the height of the landing, over the shell and a little outside it
        ShapeKit.ring(plan, cx, 0, cz, r + 0.5, r + DepthsLayout.RIM, Material.PRISMARINE_BRICKS);
        ShapeKit.ring(plan, cx, -1, cz, r + 0.5, r + DepthsLayout.RIM, Material.PRISMARINE);
        ShapeKit.ring(plan, cx, 0, cz, r + DepthsLayout.RIM - 0.5, r + DepthsLayout.RIM, Material.DARK_PRISMARINE);
        if (dome.id().equals("chain")) {
            sarcophagus(plan, dome);
        }
    }

    private static Material shell(int dx, int dy, int dz) {
        int h = dx * 7 + dy * 13 + dz * 5;
        if (Math.floorMod(h, 23) == 0 && dy < -2) {
            return Material.SEA_LANTERN;
        }
        return Math.floorMod(h, 5) == 0 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS;
    }

    /** The sealed door of the Sarcophagus on the far side of the Chain Dome (the Leviathan fight is the Bastion). */
    private static void sarcophagus(BlockPlan plan, DepthsLayout.Dome dome) {
        int x = dome.x() + dome.radius() + DepthsLayout.RIM + 3;
        int z = dome.z();
        plan.fill(x, 1, z - 4, x, 8, z + 4, Material.DARK_PRISMARINE);
        for (int dz = -2; dz <= 2; dz += 2) {
            for (int y = 1; y <= 7; y++) {
                plan.set(x - 1, y, z + dz, CHAIN, CHAIN == Material.IRON_BARS ? null : "axis=y");
            }
        }
        plan.fill(x - 1, 8, z - 3, x - 1, 8, z + 3, Material.IRON_BARS);
        plan.sign(x - 2, 1, z, Material.OAK_SIGN, "rotation=4", "Sealed", "The Chained", "Leviathan still", "pulls the sea.");
        plan.anchor("sarcophagus", x - 2, 1, z);
    }

    private static void bell(BlockPlan plan) {
        int x = DepthsLayout.BELL[0];
        int z = DepthsLayout.BELL[1];
        int r = DepthsLayout.BELL[2];
        for (int k = 0; k <= 3; k++) {
            ShapeKit.disc(plan, x, -k, z, Math.max(1, r - k), k == 0 ? Material.PRISMARINE_BRICKS : Material.DARK_PRISMARINE);
        }
        for (int y = 1; y <= 4; y++) {
            plan.set(x, y, z, Material.PRISMARINE_WALL);
        }
        plan.set(x, 5, z, Material.BELL, "attachment=floor,facing=north");
        plan.set(x + 3, 0, z, Material.SEA_LANTERN);
        plan.set(x - 3, 0, z, Material.SEA_LANTERN);
    }

    /** A 3 wide walkway from one platform to the next, with a lantern post every 10 blocks. */
    static void bridge(BlockPlan plan, int x0, int z0, int startInset, int x1, int z1, int endInset) {
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
                plan.set(x, 0, z, Math.abs(w) == 1 ? Material.DARK_PRISMARINE : Material.PRISMARINE_BRICKS);
                plan.set(x, -1, z, Material.PRISMARINE);
            }
            if (s % 10 == 5) {
                int x = (int) Math.round(x0 + ux * s + px * 2);
                int z = (int) Math.round(z0 + uz * s + pz * 2);
                plan.set(x, 0, z, Material.DARK_PRISMARINE);
                plan.set(x, 1, z, Material.PRISMARINE_WALL);
                plan.set(x, 2, z, Material.SEA_LANTERN);
            }
        }
    }
}
