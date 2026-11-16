package win.vampyuyu.valoblock;

/** Slice 5: score, timer, fuse bar, kill feed, health, ammo. */
public interface VbHud {
  VbHud NOOP = new VbHud() {};

  default void render(VbMatch match, long now) {}

  default void clear(VbMatch match) {}
}
