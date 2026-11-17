package win.vampyuyu.valoblock;

/**
 * Which look of a gun the pack should draw: the custom_model_data string 0 of the held stack
 * ({@code fire}, {@code reload}, {@code aim}, or none for the plain held model). Pure, so it is tested.
 */
final class GunVisual {
  /** The muzzle flash lasts two ticks. */
  static final long FIRE_MS = 100;

  static final String FIRE = "fire";
  static final String RELOAD = "reload";
  static final String AIM = "aim";
  static final String INSPECT = "inspect";
  /** A scoped sniper holds nothing in view: the pack maps this look to an empty model, only the scope overlay shows. */
  static final String SCOPE = "scope";
  /** The inspect pose after a weapon swap lasts one second. */
  static final long INSPECT_MS = 1000;
  static final String NONE = "";

  private GunVisual() {}

  /** Reload wins (the magazine is out), then the flash of a shot just fired, then the tighter hold of a sneaking player. */
  static String state(boolean reloading, long sinceShotMs, boolean sneaking) {
    return state(reloading, sinceShotMs, sneaking, false);
  }

  /** Same, with the inspect pose of a gun just drawn (below the flash, above the aiming hold). */
  static String state(boolean reloading, long sinceShotMs, boolean sneaking, boolean inspecting) {
    if (reloading) return RELOAD;
    if (sinceShotMs >= 0 && sinceShotMs < FIRE_MS) return FIRE;
    if (inspecting) return INSPECT;
    if (sneaking) return AIM;
    return NONE;
  }

  /**
   * Same, for a weapon of this zoom: while its scope is up (see {@link #scoped}) the gun is not drawn, whatever the
   * flash or the inspect pose, so it never blocks the target. Reload still wins (the scope drops while reloading).
   */
  static String state(boolean reloading, long sinceShotMs, boolean sneaking, boolean inspecting, double zoom) {
    if (scoped(zoom, sneaking, reloading)) return SCOPE;
    return state(reloading, sinceShotMs, sneaking, inspecting);
  }

  /** A sniper scope shows while aiming with a weapon that zooms. */
  static boolean scoped(double zoom, boolean sneaking, boolean reloading) {
    return zoom > 1 && sneaking && !reloading;
  }
}
