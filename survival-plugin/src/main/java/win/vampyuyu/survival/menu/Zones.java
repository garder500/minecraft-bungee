package win.vampyuyu.survival.menu;

import java.util.List;
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
