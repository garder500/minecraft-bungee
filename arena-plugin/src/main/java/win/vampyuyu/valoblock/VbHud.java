package win.vampyuyu.valoblock;

/** Slice 5: score, timer, fuse bar, kill feed, health, ammo. */
public interface VbHud {
  VbHud NOOP = new VbHud() {};

  default void render(VbMatch match, long now) {}

  default void clear(VbMatch match) {}

  /** The shooter hit someone (head shot, or a kill). */
  default void hitMarker(java.util.UUID shooter, boolean head, boolean kill) {}
}
