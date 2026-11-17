package win.vampyuyu.valoblock;

/**
 * How a sniper is aimed and fired, with no Bukkit (tested). Two kinds of zoom:
 * <ul>
 *   <li>a <b>hold scope</b> (the Watcher's Eye): the stack is a vanilla spyglass, so the client zooms and draws the
 *       spyglass overlay while the player holds right-click (it is "using" the item). Left-click fires.</li>
 *   <li>a <b>sneak zoom</b> (Longwind and any other weapon with a zoom): sneaking scopes, right-click fires.</li>
 * </ul>
 */
final class ScopeInput {
  enum Click {
    LEFT,
    RIGHT
  }

  enum Action {
    /** Shoot the gun. */
    FIRE,
    /** Start the vanilla spyglass use (zoom and overlay): the click must NOT be cancelled. */
    SCOPE,
    /** The knife. */
    STAB,
    OPEN_ARMORY,
    /** Cancel the click, do nothing. */
    NONE
  }

  /** Smallest cone half-angle (degrees) of a shot from a hold-scope weapon fired without its scope. */
  static final double UNSCOPED_SPREAD = 5.0;
  /** The base item that makes the client zoom and draw the spyglass overlay while the item is being used. */
  static final String SCOPE_BASE = "SPYGLASS";

  private ScopeInput() {}

  /** A weapon that zooms while its button is held (spyglass base item). */
  static boolean holdScope(VbWeapon w) {
    return w != null && w.zoom() > 1 && SCOPE_BASE.equalsIgnoreCase(w.baseItem());
  }

  /** A weapon that zooms while its owner sneaks. */
  static boolean sneakZoom(VbWeapon w) {
    return w != null && w.zoom() > 1 && !holdScope(w);
  }

  /** What a click with this item does. {@code w} is null for an item that is no weapon of the match. */
  static Action action(VbWeapon w, boolean armory, Click click) {
    if (armory) return click == Click.RIGHT ? Action.OPEN_ARMORY : Action.NONE;
    if (w == null) return Action.NONE;
    if (w.isGun()) {
      if (holdScope(w)) return click == Click.LEFT ? Action.FIRE : Action.SCOPE;
      return click == Click.RIGHT ? Action.FIRE : Action.NONE;
    }
    if (w.slot() == VbWeapon.Slot.MELEE) return click == Click.LEFT ? Action.STAB : Action.NONE;
    return Action.NONE;
  }

  /** Whether the scope (overlay, no gun model) is up: held right-click for a hold scope, sneaking for a sneak zoom. */
  static boolean scoped(VbWeapon w, boolean sneaking, boolean usingItem, boolean reloading) {
    if (w == null || reloading) return false;
    if (holdScope(w)) return usingItem;
    return w.zoom() > 1 && sneaking;
  }

  /** The cone of a shot: a hold-scope weapon fired unscoped is never tighter than {@link #UNSCOPED_SPREAD}. */
  static double spread(VbWeapon w, double base, boolean scoped) {
    return holdScope(w) && !scoped ? Math.max(base, UNSCOPED_SPREAD) : base;
  }

  /** The control hint of the tooltip. */
  static String hint(VbWeapon w) {
    return holdScope(w) ? "Hold right-click to scope, left-click to fire" : "Right-click fire";
  }
}
