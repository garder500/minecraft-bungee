package win.vampyuyu.valoblock;

/** Whether a straight walk between two waypoints crosses solid blocks. Pure: the blocks come from a probe (the canvas in tests, the world in a match). */
public final class LinkCheck {
  private LinkCheck() {}

  /** Solid (stops a body) block at integer coordinates. */
  public interface Solid {
    boolean at(int x, int y, int z);
  }

  private static final double STEP = 0.2;

  /**
   * The bot moves in a straight line, y interpolated along it (see {@link BotMover}). It is blocked when, at some point of the line,
   * a cell its head or chest is in is solid. The feet cell is ignored on the way up or down a step (it is the floor of that step).
   */
  public static boolean blocked(Waypoints.Node a, Waypoints.Node b, Solid s) {
    double len = Math.hypot(b.x() - a.x(), b.z() - a.z());
    int n = Math.max(1, (int) Math.ceil(len / STEP));
    for (int i = 0; i <= n; i++) {
      double t = (double) i / n;
      double x = a.x() + (b.x() - a.x()) * t, z = a.z() + (b.z() - a.z()) * t, y = a.y() + (b.y() - a.y()) * t;
      int fx = (int) Math.floor(x), fz = (int) Math.floor(z);
      int head = (int) Math.floor(y + 1.7);
      int chest = (int) Math.floor(y + 1.0);
      if (s.at(fx, head, fz) || s.at(fx, chest, fz)) return true;
    }
    return false;
  }
}
