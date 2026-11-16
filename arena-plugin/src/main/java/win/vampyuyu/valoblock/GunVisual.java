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
  static final String NONE = "";

  private GunVisual() {}

  /** Reload wins (the magazine is out), then the flash of a shot just fired, then the tighter hold of a sneaking player. */
  static String state(boolean reloading, long sinceShotMs, boolean sneaking) {
    if (reloading) return RELOAD;
    if (sinceShotMs >= 0 && sinceShotMs < FIRE_MS) return FIRE;
    if (sneaking) return AIM;
    return NONE;
  }

  /** A sniper scope shows while aiming with a weapon that zooms. */
  static boolean scoped(double zoom, boolean sneaking, boolean reloading) {
    return zoom > 1 && sneaking && !reloading;
  }
}
