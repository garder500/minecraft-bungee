package win.vampyuyu.survival.island;

import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;
import org.bukkit.Material;

/** Small geometry helpers shared by every generator (islands, platforms, hub). */
public final class ShapeKit {
    private ShapeKit() {}

    /** Wobbly outline: a radius multiplier for a given angle, between about 0.84 and 1.16. */
    public static final class Outline {
        private final double p1;
        private final double p2;
        private final double p3;

        public Outline(Random rnd) {
            p1 = rnd.nextDouble() * Math.PI * 2;
            p2 = rnd.nextDouble() * Math.PI * 2;
            p3 = rnd.nextDouble() * Math.PI * 2;
        }

        public double factor(double angle) {
            return 1 + 0.08 * Math.sin(2 * angle + p1) + 0.05 * Math.sin(3 * angle + p2) + 0.03 * Math.sin(5 * angle + p3);
        }

        /** Smallest factor over a full turn (for placing structures safely inside). */
        public static double minFactor() {
            return 0.84;
        }
    }

    /** True when (x,z) is inside a disc of radius r around (0,0) deformed by the outline. */
    public static boolean inside(Outline outline, double radius, int x, int z) {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        if (dist < 0.0001) {
            return true;
        }
        return dist <= radius * (outline == null ? 1 : outline.factor(Math.atan2(z, x)));
    }

    /**
     * A floating island: layer d (0 = top at y=0, going down) is a shrinking wobbly disc. {@code material} picks the
     * block for a layer index. The deepest layers keep a tapering tip.
     */
    public static void floatingIsland(BlockPlan plan, Outline outline, double radius, int depth,
            IntFunction<Material> material) {
        for (int d = 0; d <= depth; d++) {
            double scale = 1 - Math.pow(d / (depth + 1.0), 1.5);
            double r = radius * scale;
            int ri = (int) Math.ceil(r * 1.2) + 1;
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    if (inside(outline, r, x, z)) {
                        plan.set(x, -d, z, material.apply(d));
                    }
                }
            }
        }
    }

    public static void disc(BlockPlan plan, int cx, int y, int cz, double r, Material material) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                if (x * x + z * z <= r * r + 0.25) {
                    plan.set(cx + x, y, cz + z, material);
                }
            }
        }
    }

    /** A ring (annulus) of blocks between r0 (inclusive) and r1 (inclusive). */
    public static void ring(BlockPlan plan, int cx, int y, int cz, double r0, double r1, Material material) {
        int ri = (int) Math.ceil(r1);
        for (int x = -ri; x <= ri; x++) {
            for (int z = -ri; z <= ri; z++) {
                double d2 = x * x + z * z;
                if (d2 <= r1 * r1 + 0.25 && d2 >= r0 * r0 - 0.25) {
                    plan.set(cx + x, y, cz + z, material);
                }
            }
        }
    }

    /** A leaf blob: sphere-ish, corners trimmed. */
    public static void leaves(BlockPlan plan, int cx, int cy, int cz, double r, Material leaves) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++) {
            for (int y = -ri; y <= ri; y++) {
                for (int z = -ri; z <= ri; z++) {
                    if (x * x + y * y + z * z <= r * r + 0.5 && plan.get(cx + x, cy + y, cz + z) == null) {
                        plan.set(cx + x, cy + y, cz + z, leaves, "persistent=true");
                    }
                }
            }
        }
    }

    /** A vertical column of one block. */
    public static void column(BlockPlan plan, int x, int y0, int y1, int z, Material material) {
        for (int y = y0; y <= y1; y++) {
            plan.set(x, y, z, material);
        }
    }

    /** Replaces blocks of a gravity material that have nothing under them (they would fall later). */
    public static void fixSupport(BlockPlan plan, Material falling, Material replacement) {
        for (var entry : List.copyOf(plan.blocks().entrySet())) {
            BlockPlan.Blk blk = entry.getValue();
            if (blk.material() != falling) {
                continue;
            }
            long key = entry.getKey();
            int x = BlockPlan.keyX(key);
            int y = BlockPlan.keyY(key);
            int z = BlockPlan.keyZ(key);
            Material below = plan.get(x, y - 1, z);
            if (below == null || below.isAir() || below == falling) {
                plan.set(x, y, z, replacement);
            }
        }
    }

    /** Index of the polar angle (degrees) to a rounded block offset. */
    public static int polarX(double radius, double degrees) {
        return (int) Math.round(radius * Math.cos(Math.toRadians(degrees)));
    }

    public static int polarZ(double radius, double degrees) {
        return (int) Math.round(radius * Math.sin(Math.toRadians(degrees)));
    }
}
