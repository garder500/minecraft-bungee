package win.vampyuyu.valoblock;

import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import win.vampyuyu.arena.ArenaSession;

/** The map of the running match: built at a slot of the ValoBlock world, spawns, barriers, orbs. */
public interface MapService {
  /** Sites from valoblock/sites.json until a real map is loaded. */
  MapService NOOP = new MapService() {};

  default List<VbSite> sites() {
    return VbData.sites();
  }

  /** Why the map cannot host a match, or null. */
  default String problem(ArenaSession s) {
    return null;
  }

  /** The world the match is played in, or null when the map service is not active. */
  default World world() {
    return null;
  }

  /** The session is about to start: reserve a slot and build the map (before the match exists). */
  default void prepare(ArenaSession s) {}

  /** The match exists: make its map the active one and put the humans on their spawns. */
  default void started(ArenaSession s, VbMatch match) {}

  /** The arena stopped: clear the map, free the slot. */
  default void release(ArenaSession s) {}

  /** Whether the place belongs to the map this session is playing on. */
  default boolean covers(ArenaSession s, org.bukkit.Location where) {
    return false;
  }

  /** Puts a human on the next spawn of its side: alive again, full health, facing the middle. */
  default void teleportToSpawn(UUID player, VbSide side) {}

  /** The phase changed: barriers up in the buy phase, orbs in combat. */
  default void phaseChanged(VbMatch match, VbPhase from, VbPhase to) {}
}
