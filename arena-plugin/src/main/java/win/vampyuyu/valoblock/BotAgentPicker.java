package win.vampyuyu.valoblock;

import java.util.*;

/** Which agent a bot takes: a free one whose role nobody of the team holds yet, else any free one. Pure. */
public final class BotAgentPicker {
  private BotAgentPicker() {}

  /**
   * @param free agents nobody of the team holds (a human's lock never appears here)
   * @param held agents already held by the team
   * @return an agent id, or null when none is free
   */
  public static String pick(List<String> free, Collection<String> held, VbAgents agents, Random random) {
    if (free.isEmpty()) return null;
    Set<String> covered = new HashSet<>();
    for (String id : held) {
      VbAgent a = agents.get(id);
      if (a != null) covered.add(a.role());
    }
    List<String> needed = new ArrayList<>();
    for (String id : free) {
      VbAgent a = agents.get(id);
      if (a != null && !covered.contains(a.role())) needed.add(id);
    }
    List<String> pool = needed.isEmpty() ? free : needed;
    return pool.get(random.nextInt(pool.size()));
  }
}
