package win.vampyuyu.valoblock;

import java.util.List;
import java.util.UUID;

/** Slice 3: agents, their skins (applied at lock in, restored at match end) and abilities. */
public interface AgentService {
  AgentService NOOP = new AgentService() {};

  default List<String> agents() {
    return List.of();
  }

  /** The player locked an agent in: apply its skin and abilities. */
  default void lockedIn(VbMatch match, UUID player, String agent) {}

  /** Match over, quit or crash recovery: give the player's own skin back. */
  default void restore(UUID player) {}
}
