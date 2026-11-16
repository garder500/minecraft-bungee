package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;

/**
 * Rootvault's one-way hanging roots: every F cell is a root mouth. A canopy branch ends in a 2x2 hole above the cell;
 * whoever steps in falls slowly to the ground below (a slow-fall zone, fast entry into a site) and there is no way
 * back up: the roots cannot be climbed and the branch has no ladder. Pure geometry; the effect is applied by
 * {@link VbRealmService}.
 */
public final class RootDrops {
  private RootDrops() {}

  /** One root: its cell and the box of the drop (x, z in blocks from the map corner, y relative to the floor). */
  public record Root(int col, int row, double minX, double minZ, double maxX, double maxZ, double maxY) {
    /** In the shaft above the cell: above the floor and below the branch. */
    public boolean contains(double x, double y, double z) {
      return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y > 0.9 && y <= maxY;
    }
  }

  /** The branch height (the deck's top is {@code highY} high). */
  public static List<Root> roots(VbPlan p) {
    List<Root> out = new ArrayList<>();
    for (int[] f : p.cellsOf('F')) out.add(new Root(f[0], f[1], f[0] * p.cell, f[1] * p.cell, (f[0] + 1) * p.cell, (f[1] + 1) * p.cell, p.map.highY() + 1.5));
    return out;
  }

  /** Whether a position is inside any root shaft. */
  public static boolean inShaft(List<Root> roots, double x, double y, double z) {
    for (Root r : roots) if (r.contains(x, y, z)) return true;
    return false;
  }

  /** Ticks a slow-fall effect lasts: renewed every check while the player stays in the shaft, so it ends right after the landing. */
  public static final int EFFECT_TICKS = 12;

  /**
   * The canopy cells of the branch from the nearest high cell to a root, as (col, row): along the row of the high cell,
   * then along the column of the root, the root cell itself last.
   */
  public static List<int[]> branch(VbPlan p, int[] root) {
    int[] from = null;
    int best = Integer.MAX_VALUE;
    for (int[] h : p.cellsOf('h')) {
      int d = Math.abs(h[0] - root[0]) + Math.abs(h[1] - root[1]);
      if (d < best) {
        best = d;
        from = h;
      }
    }
    List<int[]> path = new ArrayList<>();
    if (from == null) return path;
    int c = from[0], r = from[1];
    while (c != root[0]) {
      c += Integer.signum(root[0] - c);
      path.add(new int[] {c, r});
    }
    while (r != root[1]) {
      r += Integer.signum(root[1] - r);
      path.add(new int[] {c, r});
    }
    return path;
  }
}
