package win.vampyuyu.valoblock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Detects a key pressed twice within a short window (the swap-hands key opening the Armory). Pure: the caller gives the time. */
final class DoubleTap {
  static final long WINDOW_MS = 500;

  private final Map<UUID, Long> last = new ConcurrentHashMap<>();

  /** Records a press; true when it is the second within the window (the pair is then used up: a third press starts over). */
  boolean press(UUID id, long nowMs) {
    Long before = last.get(id);
    if (before != null && nowMs - before >= 0 && nowMs - before <= WINDOW_MS) {
      last.remove(id);
      return true;
    }
    last.put(id, nowMs);
    return false;
  }

  void forget(UUID id) {
    last.remove(id);
  }
}
