package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Named regions of the Skyblock worlds, shown as the location line of the sidebar. First match wins. */
public final class Zones {
    /** A region: world token or name plus an inclusive bounding box. */
    public record Zone(String name, String world, double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        public boolean contains(String worldName, double x, double y, double z) {
            return world.equalsIgnoreCase(worldName) && x >= minX && x <= maxX && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
        }
    }

    private final List<Zone> zones;

    public Zones(List<Zone> zones) {
        this.zones = List.copyOf(zones);
    }

    /**
     * Reads {@code zones: [{name: Village, world: hub, min: [x,y,z], max: [x,y,z]}]}. The world is a token
     * (hub/world/nether/end) or a world name; {@code resolveWorld} turns tokens into names. Bad entries are skipped.
     */
    public static Zones parse(List<Map<?, ?>> raw, java.util.function.Function<String, String> resolveWorld,
            List<String> problems) {
        List<Zone> zones = new ArrayList<>();
        for (Map<?, ?> map : raw) {
            try {
                double[] min = triple(map.get("min"));
                double[] max = triple(map.get("max"));
                String world = resolveWorld.apply(String.valueOf(map.get("world")));
                zones.add(new Zone(String.valueOf(map.get("name")), world, Math.min(min[0], max[0]),
                    Math.min(min[1], max[1]), Math.min(min[2], max[2]), Math.max(min[0], max[0]),
                    Math.max(min[1], max[1]), Math.max(min[2], max[2])));
            } catch (RuntimeException e) {
                problems.add("zone " + map + ": " + e.getMessage());
            }
        }
        return new Zones(zones);
    }

    private static double[] triple(Object value) {
        if (value instanceof List<?> list && list.size() == 3) {
            double[] out = new double[3];
            for (int i = 0; i < 3; i++) {
                if (!(list.get(i) instanceof Number n)) {
                    throw new IllegalArgumentException("coordinates must be numbers");
                }
                out[i] = n.doubleValue();
            }
            return out;
        }
        throw new IllegalArgumentException("expected [x, y, z]");
    }

    public Optional<String> find(String worldName, double x, double y, double z) {
        for (Zone zone : zones) {
            if (zone.contains(worldName, x, y, z)) {
                return Optional.of(zone.name());
            }
        }
        return Optional.empty();
    }

    public int size() {
        return zones.size();
    }

}
