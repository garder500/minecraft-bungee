package win.vampyuyu.survival.depths;

import java.util.List;

/**
 * Where everything is in the Abyssal Depths (GDD 3.1): pure coordinates, relative to the build origin, surface of the
 * landing at y = 0. The build ({@link DepthsPlan}) and the rules read the same lists, so they cannot drift apart.
 *
 * <p>A dome is the lower half of a sphere of water held by a prismarine shell: the water surface is at y = -1, the
 * rim players walk on at y = 0, the deepest point {@code radius + 1} blocks under the surface.
 */
public final class DepthsLayout {
    /** Bump to rebuild existing worlds when the layout changes. */
    public static final int VERSION = 1;
    /** World y of the landing surface. */
    public static final int ORIGIN_Y = 100;
    public static final int LANDING_RADIUS = 12;
    /** Plans placed above this many blocks are cut: the test keeps the layout well below it. */
    public static final int CAP = 60_000;
    /** Where players arrive. */
    public static final int[] SPAWN = {0, 1, 5};
    /** Width of the rim around a dome, outside the shell. */
    public static final int RIM = 2;
    /** Under this height (relative) the player has fallen off the Realm. */
    public static final int VOID_DEPTH = 45;

    private DepthsLayout() {}

    /** A suspended dome of water centred on {@code x, z}. */
    public record Dome(String id, int x, int z, int radius) {}

    /** A place to discover. {@code y} is the floor level to stand on. */
    public record Landmark(String id, String name, int x, int y, int z, String lore) {}

    /** A pump to restart (world quest), on the rim of a dome. */
    public record Pump(String id, String dome, int x, int y, int z) {}

    /** Irregular gravity over a round zone (GDD 12.5): LOW = slow fall and big jumps, DRIFT = periodic lift. */
    public record Gravity(String id, int x, int z, int radius, Kind kind) {
        public enum Kind { LOW, DRIFT }
    }

    public static final List<Dome> DOMES = List.of(
        new Dome("shoals", 0, -48, 11),
        new Dome("kelp", 62, -82, 13),
        new Dome("trench", 130, -70, 14),
        new Dome("chain", 200, -104, 17));

    /** The small platform with the Drowned Bell, between the Kelp Spire and the Hollow Trench. */
    public static final int[] BELL = {96, -76, 5};

    public static final List<Pump> PUMPS = List.of(
        pump("shoals", -12, 0), pump("kelp", 0, -14), pump("trench", 0, 15));

    public static final List<Landmark> LANDMARKS = List.of(
        new Landmark("shoals", "Lantern Shoals", dome("shoals").x(), 1, dome("shoals").z(),
            "Lights that were never lit by hand. Fish gather under them, as if they still expected a ship."),
        new Landmark("kelp", "Kelp Spire", dome("kelp").x(), 1, dome("kelp").z(),
            "A single stalk of kelp as tall as a tower. Beneath it, the water forgets which way is down."),
        new Landmark("bell", "Drowned Bell", BELL[0], 1, BELL[1],
            "It rings when the pumps stall. Somebody once kept watch here, so that the sea would not rise."),
        new Landmark("trench", "Hollow Trench", dome("trench").x(), 1, dome("trench").z(),
            "The deepest dome. The Devourer drank the ocean and left this much behind, to keep the weight of it."),
        new Landmark("chain", "Chain Dome", dome("chain").x(), 1, dome("chain").z(),
            "Chains thicker than a house run into the water. The Leviathan keeps the gravity of a world that is gone."));

    public static final List<Gravity> GRAVITY = List.of(
        new Gravity("crest", dome("shoals").x(), dome("shoals").z(), 15, Gravity.Kind.LOW),
        new Gravity("spire", dome("kelp").x(), dome("kelp").z(), 15, Gravity.Kind.LOW),
        new Gravity("drift", dome("trench").x(), dome("trench").z(), 16, Gravity.Kind.DRIFT));

    public static Dome dome(String id) {
        for (Dome dome : DOMES) {
            if (dome.id().equals(id)) {
                return dome;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static Pump pump(String dome, int dx, int dz) {
        Dome d = dome(dome);
        return new Pump("pump:" + dome, dome, d.x() + dx, 1, d.z() + dz);
    }

    /**
     * The dome whose water holds this point (relative coordinates, centre of the sphere at the water surface), or
     * null. The point has to be at or under the surface.
     */
    public static Dome waterAt(double x, double y, double z) {
        if (y > 0) {
            return null;
        }
        for (Dome dome : DOMES) {
            double dx = x - dome.x();
            double dy = y + 1;
            double dz = z - dome.z();
            if (dx * dx + dy * dy + dz * dz < (double) (dome.radius() - 1) * (dome.radius() - 1)) {
                return dome;
            }
        }
        return null;
    }

    /** Blocks under the water surface for a relative height (0 at the surface). */
    public static int depth(double y) {
        return Math.max(0, (int) Math.floor(-1 - y));
    }

    /** The gravity zone over this column and height, or null. Zones reach from the deepest water to 24 blocks above the rim. */
    public static Gravity gravityAt(double x, double y, double z) {
        if (y > 24 || y < -20) {
            return null;
        }
        for (Gravity zone : GRAVITY) {
            if (Math.hypot(x - zone.x(), z - zone.z()) <= zone.radius()) {
                return zone;
            }
        }
        return null;
    }

    /** True for the landing, the shells and the rims: nothing there can be built on, broken or drained. */
    public static boolean protectedSpot(int x, int y, int z) {
        if (y >= -12 && Math.hypot(x, z) <= LANDING_RADIUS + 1) {
            return true;
        }
        for (Dome dome : DOMES) {
            double dist = Math.hypot(x - dome.x(), z - dome.z());
            if (y >= 0 && y <= 1 && dist <= dome.radius() + RIM + 0.5) {
                return true;
            }
            double sphere = Math.sqrt(dist * dist + (double) (y + 1) * (y + 1));
            if (y <= -1 && sphere >= dome.radius() - 1.5 && sphere <= dome.radius() + 1.5) {
                return true;
            }
        }
        return false;
    }
}
