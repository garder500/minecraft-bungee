package win.vampyuyu.arena;

import java.util.Collection;
import java.util.UUID;

/** Register additional game factories without changing arena protection or player snapshots. */
public interface ArenaPreset {
  String id();

  GamePreset create(ArenaConfig config, Collection<UUID> participants);

  /** As above, with roles forced for given players (admin testing); most games ignore them. */
  default GamePreset create(
      ArenaConfig config, Collection<UUID> participants, java.util.Map<UUID, String> forced) {
    return create(config, participants);
  }
}
