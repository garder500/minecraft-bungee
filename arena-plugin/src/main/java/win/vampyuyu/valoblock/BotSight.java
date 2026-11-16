package win.vampyuyu.valoblock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reaction time: an enemy must have been in view for the level's reaction time before the bot shoots at it. A glance lost for a moment (grace) does not restart the clock. Pure, time passed in. */
public final class BotSight {
  /** Lost for longer than this and the bot has to notice the enemy again. */
  public static final long GRACE_MS = 150;

  private final Map<UUID, Long> firstSeen = new HashMap<>();
  private final Map<UUID, Long> lastSeen = new HashMap<>();

  public void update(long now, Set<UUID> visible) {
    for (UUID id : visible) {
      Long last = lastSeen.get(id);
      if (last == null || now - last > GRACE_MS) firstSeen.put(id, now);
      lastSeen.put(id, now);
    }
    var it = lastSeen.entrySet().iterator();
    while (it.hasNext()) {
      var e = it.next();
      if (now - e.getValue() > GRACE_MS) {
        firstSeen.remove(e.getKey());
        it.remove();
      }
    }
  }

  /** Whether the bot has had this enemy in view long enough to shoot. */
  public boolean ready(UUID id, long now, int reactionMs) {
    Long since = firstSeen.get(id);
    Long last = lastSeen.get(id);
    return since != null && last != null && now - last <= GRACE_MS && now - since >= reactionMs;
  }

  public void clear() {
    firstSeen.clear();
    lastSeen.clear();
  }
}
