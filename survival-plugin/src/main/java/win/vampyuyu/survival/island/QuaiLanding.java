package win.vampyuyu.survival.island;

import org.bukkit.Material;
import win.vampyuyu.survival.api.Realm;

/**
 * The placeholder a lit Gate leads to until its Realm exists: a small floating platform in the sky of the hub
 * world, in the Realm's colour, with a sign and the way back. Pure data, like {@link QuaiPlan}.
 */
public final class QuaiLanding {
    public static final int VERSION = 1;
    public static final int RADIUS = 7;
    /** The platforms sit on a circle around the hub centre, above the hub, inside the +-100 border. */
    public static final int ORBIT = 70;
    public static final int HEIGHT_ABOVE_HUB = 100;

    private QuaiLanding() {}

    /** X and Z of the platform centre. */
    public static int[] center(Realm realm) {
        double angle = Math.toRadians(45.0 * realm.ordinal());
        return new int[] {(int) Math.round(ORBIT * Math.sin(angle)), (int) Math.round(-ORBIT * Math.cos(angle))};
    }

    public static Material concrete(Realm realm) {
        return Material.valueOf(QuaiPlan.litMembrane(realm).name().replace("_STAINED_GLASS", "_CONCRETE"));
    }

    /** Relative to the platform centre, surface at y = 0. */
    public static BlockPlan build(Realm realm) {
        BlockPlan plan = new BlockPlan();
        ShapeKit.disc(plan, 0, 0, 0, RADIUS, Material.POLISHED_BLACKSTONE_BRICKS);
        ShapeKit.ring(plan, 0, 0, 0, RADIUS - 1.5, RADIUS - 0.5, concrete(realm));
        ShapeKit.disc(plan, 0, 0, 0, 2.5, Material.SMOOTH_QUARTZ);
        for (int k = 1; k <= 5; k++) {
            ShapeKit.disc(plan, 0, -k, 0, RADIUS * (1 - k / 7.0), k <= 1 ? Material.STONE_BRICKS : Material.DEEPSLATE);
        }
        for (int[] d : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            plan.set(d[0], 0, d[1], Material.SEA_LANTERN);
        }
        plan.set(0, 1, 2, Material.SMOOTH_QUARTZ);
        plan.sign(0, 2, 2, Material.OAK_SIGN, "rotation=8", "Realm of", realm.title(), "is not charted", "yet. /hub to leave");
        plan.anchor("spawn", 0, 1, 0);
        return plan;
    }
}
