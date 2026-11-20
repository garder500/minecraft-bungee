package win.vampyuyu.survival.island;

import java.util.Random;
import org.bukkit.Location;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * The Breach: the one place of the hub where players can be hurt. A torn islet north-west of the fragment, joined by a
 * narrow bridge, where the Breath's immune cells come through when an invasion targets the hub. The rest of the hub
 * stays a safe zone (no player damage), so a fight there would have no stake. Hub coordinates (the hub plan is built at
 * world x = 0, z = 0), pure data: {@link HubPlan} builds it, the island tracker and the Breath feature read it.
 */
public final class HubBreach {
    /** Centre and radius of the islet. */
    public static final int X = -62;
    public static final int Z = -30;
    public static final int RADIUS = 13;
    /** Where the bridge leaves the hub rim and where it reaches the islet. */
    static final int[] BRIDGE_FROM = {-27, -13};
    static final int[] BRIDGE_TO = {-51, -25};
    /** Height band (relative to the hub surface) that counts as inside: the islet, not the void under it. */
    static final int BELOW = 24;
    static final int ABOVE = 40;

    private HubBreach() {}

    /** True for a hub column of the islet (its outline, a little wobbly, never wider than RADIUS). */
    public static boolean ground(int x, int z) {
        double dx = x - X;
        double dz = z - Z;
        double a = Math.atan2(dz, dx);
        double r = RADIUS - 1.2 - 1.0 * Math.sin(3 * a + 0.4) - 0.6 * Math.sin(5 * a + 2.2);
        return dx * dx + dz * dz <= r * r;
    }

    /** True when a hub position (relative to the hub surface y) is inside the Breach, where damage applies. */
    public static boolean contains(double x, double y, double z) {
        double dx = x - X;
        double dz = z - Z;
        return dx * dx + dz * dz <= (RADIUS + 0.5) * (RADIUS + 0.5) && y >= -BELOW && y <= ABOVE;
    }

    /** True for a location of the hub world inside the Breach. {@code hubY} is the y of the hub surface. */
    public static boolean contains(Location at, int hubY) {
        return at != null && at.getWorld() != null && SkyblockWorlds.HUB.equals(at.getWorld().getName())
            && contains(at.getX(), at.getY() - hubY, at.getZ());
    }

    /** A random column of the islet for a spawn, away from the bridge end (players arrive there). */
    public static int[] randomColumn(Random rng) {
        for (int i = 0; i < 40; i++) {
            int x = X - RADIUS + rng.nextInt(2 * RADIUS + 1);
            int z = Z - RADIUS + rng.nextInt(2 * RADIUS + 1);
            if (ground(x, z) && Math.hypot(x - BRIDGE_TO[0], z - BRIDGE_TO[1]) > 6) {
                return new int[] {x, z};
            }
        }
        return new int[] {X, Z};
    }
}
