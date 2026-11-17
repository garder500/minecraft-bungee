package win.vampyuyu.valoblock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a real right-click from the repeats of a held button. The vanilla client sends the use packet again every
 * 4 ticks (200 ms) while the button stays down, so one slow click reaches the server as several interact events.
 * An ability must only react to the press: otherwise Rewind marks the spot and the repeat 400 ms later already
 * returns to it, a held Miasma Wall click flips the wall twice.
 */
final class PressGate {
  enum Verdict {
    /** A new click: act. */
    PRESS,
    /** The button is still held (the previous event was a moment ago): ignore. */
    REPEAT,
    /** A new click, but too soon after the last accepted one: ignore. */
    TOO_SOON
  }

  /** Two events closer than this belong to one held press (the client repeat is 200 ms, plus some lag). */
  static final long HELD_MS = 260;
  /** Minimum time between two accepted presses. */
  static final long GAP_MS = 350;

  private final Map<UUID, Long> lastEvent = new HashMap<>();
  private final Map<UUID, Long> lastPress = new HashMap<>();

  Verdict accept(UUID player, long now) {
    Long prev = lastEvent.put(player, now);
    if (prev != null && now - prev < HELD_MS) return Verdict.REPEAT;
    Long press = lastPress.get(player);
    if (press != null && now - press < GAP_MS) return Verdict.TOO_SOON;
    lastPress.put(player, now);
    return Verdict.PRESS;
  }

  void forget(UUID player) {
    lastEvent.remove(player);
    lastPress.remove(player);
  }
}
