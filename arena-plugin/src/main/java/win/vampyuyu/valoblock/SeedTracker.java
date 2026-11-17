package win.vampyuyu.valoblock;

import java.util.Objects;
import java.util.UUID;

/**
 * Follows who carries the Rift Seed from one look to the next and says what changed, so the world side (the item, the back
 * display, the dropped Seed) can follow without knowing why: a death and a drop with Q both end with nobody carrying in the
 * combat phase, a plant ends the same way but with the Seed planted. Pure: fed the match's carrier, phase and planted flag.
 */
final class SeedTracker {
  enum Kind { NONE, GAINED, LOST, DROPPED }

  /** {@code roundStart}: gained during the buy phase (the title is for this one); {@code from} is who had it before, or null. */
  record Change(Kind kind, UUID from, UUID to, boolean roundStart) {
    static final Change NONE = new Change(Kind.NONE, null, null, false);
  }

  private UUID last;

  UUID last() {
    return last;
  }

  Change update(UUID carrier, VbPhase phase, boolean planted) {
    if (Objects.equals(carrier, last)) return Change.NONE;
    UUID from = last;
    last = carrier;
    if (carrier != null) return new Change(Kind.GAINED, from, carrier, phase == VbPhase.BUY);
    return new Change(phase == VbPhase.COMBAT && !planted ? Kind.DROPPED : Kind.LOST, from, null, false);
  }

  /** The round is over: nobody carries, the next gain is a fresh one. */
  void reset() {
    last = null;
  }
}
