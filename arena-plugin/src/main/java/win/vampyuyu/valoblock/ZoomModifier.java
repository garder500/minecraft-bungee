package win.vampyuyu.valoblock;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bookkeeping of the sneak zoom (DM-10 Longbow). The client narrows the field of view when the player's movement speed
 * attribute is below the walking speed (the effect of a drawn bow), so the zoom is one temporary MULTIPLY_SCALAR_1
 * modifier on the movement speed. It is added once when the zoom starts and removed when it ends: it never stacks.
 * The service applies what {@link #update} says; the rules of when it is wanted are pure and tested.
 */
final class ZoomModifier {
  enum Op {
    ADD,
    REMOVE,
    NONE
  }

  /** The most movement speed a zoom takes away (the field of view narrows by about half of it). */
  static final double MAX_SLOW = 0.4;

  private final Set<UUID> active = new HashSet<>();

  /** Records the wanted state and says what to do to the attribute. */
  Op update(UUID id, boolean wanted) {
    if (wanted) return active.add(id) ? Op.ADD : Op.NONE;
    return active.remove(id) ? Op.REMOVE : Op.NONE;
  }

  boolean active(UUID id) {
    return active.contains(id);
  }

  int size() {
    return active.size();
  }

  /** The modifier amount (negative) for a weapon zoom: x2 gives -0.4, a weak zoom less, never more than {@link #MAX_SLOW}. */
  static double amount(double zoom) {
    if (zoom <= 1) return 0;
    return -Math.min(MAX_SLOW, (1 - 1 / zoom) * 0.8);
  }

  /** Whether the sneak zoom of the held weapon is on: sneaking, alive, in a fighting phase, not reloading. */
  static boolean wanted(VbWeapon held, boolean sneaking, boolean reloading, boolean fighting, boolean alive) {
    return ScopeInput.sneakZoom(held) && sneaking && !reloading && fighting && alive;
  }
}
