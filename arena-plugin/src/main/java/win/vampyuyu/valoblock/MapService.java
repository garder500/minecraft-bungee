package win.vampyuyu.valoblock;

import java.util.List;
import java.util.UUID;

/** Slice 5: the map (spawns, buy zones, barriers, sites) of the running match. */
public interface MapService {
  /** Sites from valoblock/sites.json until a real map is loaded. */
  MapService NOOP = new MapService() {};

  default List<VbSite> sites() {
    return VbData.sites();
  }

  /** Why the map cannot host a match, or null. */
  default String problem() {
    return null;
  }

  default void teleportToSpawn(UUID player, VbSide side) {}

  /** Buy-phase barriers up or down. */
  default void barriers(boolean up) {}
}
