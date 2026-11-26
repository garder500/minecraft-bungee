package win.vampyuyu.valoblock;

/**
 * Pieces the map sceneries share (all beyond the invisible border or under the floor, out of reach): the distance to the play
 * box, a smooth deterministic wave for organic shapes, and the island the map stands on, built as a shell (only the blocks
 * that can be seen from outside). Pure.
 */
final class SceneryKit {
  private SceneryKit() {}

  /** The block of an island layer at a place. */
  interface Strata {
    String at(int x, int y, int z);
  }

  /** Blocks outside the play box: 0 inside (or on the border line), else the distance to it along the nearest axis. */
  static int outside(int x, int z, int w, int d) {
    return Math.max(Math.max(-1 - x, x - w), Math.max(-1 - z, z - d));
  }

  /** A smooth wave around 0 (roughly -2.4..2.4), the same for the same place every time. */
  static double wave(int x, int z) {
    return Math.sin(x * 0.21 + z * 0.07) + Math.sin(z * 0.17 - x * 0.05) * 0.8 + Math.sin((x + z) * 0.09) * 0.6;
  }

  /** A deterministic hash of a place in [0, mod). */
  static int hash(int x, int y, int z, int mod) {
    long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
    h ^= h >>> 13;
    h *= 0x5bd1e995L;
    h ^= h >>> 15;
    return (int) Math.floorMod(h, mod);
  }

  /** Distance from the centre in a rounded-square metric, 1 at the island rim. */
  static double rim(int x, int z, int w, int d) {
    int m = VbMap.SCENERY_MARGIN;
    double dx = (x + 0.5 - w / 2.0) / (w / 2.0 + m - 1), dz = (z + 0.5 - d / 2.0) / (d / 2.0 + m - 1);
    return Math.pow(Math.pow(Math.abs(dx), 4) + Math.pow(Math.abs(dz), 4), 0.25);
  }

  /** How deep the island goes under a column (a y below {@code top}), or 0 outside the island. */
  static int keel(int x, int z, int w, int d, int top) {
    double r = rim(x, z, w, d) + wave(x, z) * 0.012;
    if (r > 1) return 0;
    double depth = 4 + 28 * Math.pow(1 - r, 0.7) + wave(z, x) * 2.5;
    return top - (int) Math.round(Math.max(2, Math.min(top - VbMap.SCENERY_LOW_Y - 1, depth)));
  }

  /** Whether a column is on the island. */
  static boolean onIsland(int x, int z, int w, int d) {
    return keel(x, z, w, d, -2) != 0;
  }

  /**
   * The island under the map, from {@code top} down, as a shell: the bottom of every column and the steps of its sides. Beyond
   * the border the layer at {@code top} is always laid (the scenery's ground stands on rock).
   */
  static void island(VbCanvas c, int w, int d, int top, Strata strata) {
    int m = VbMap.SCENERY_MARGIN;
    for (int x = -m; x < w + m; x++)
      for (int z = -m; z < d + m; z++) {
        int bottom = keel(x, z, w, d, top);
        if (bottom == 0) continue;
        int show = bottom + 1;
        for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
          int nb = keel(x + n[0], z + n[1], w, d, top);
          show = Math.max(show, nb == 0 ? top : nb - 1);
        }
        for (int y = bottom; y <= Math.min(top, show); y++) c.set(x, y, z, strata.at(x, y, z));
        if (outside(x, z, w, d) > 0) c.set(x, top, z, strata.at(x, top, z));
      }
  }

  /** Where a scenery shape may put a block (the play box stays clear below {@link #ABOVE_PLAY}). */
  interface Keep {
    boolean ok(int x, int y, int z);
  }

  /** Scenery may hang over the play box only this high above the floor (well over the border's top and every line of fire). */
  static final int ABOVE_PLAY = 32;

  /** Whether scenery may stand at a place: inside the scenery box, and two blocks clear of the border or high above the play. */
  static boolean clear(int x, int y, int z, int w, int d) {
    int m = VbMap.SCENERY_MARGIN;
    if (x < -m || z < -m || x >= w + m || z >= d + m || y < VbMap.SCENERY_LOW_Y || y > VbMap.SCENERY_HIGH_Y) return false;
    return y >= ABOVE_PLAY || y < -1 || outside(x, z, w, d) >= 2;
  }

  /** An ellipsoid shell (about 1.6 blocks thick): radius rx on x and z, ry on y. */
  static void blob(VbCanvas c, int cx, int cy, int cz, double rx, double ry, Strata block, Keep keep) {
    for (int dx = (int) -Math.ceil(rx); dx <= Math.ceil(rx); dx++)
      for (int dz = (int) -Math.ceil(rx); dz <= Math.ceil(rx); dz++)
        for (int dy = (int) -Math.ceil(ry); dy <= Math.ceil(ry); dy++) {
          double q = Math.sqrt((dx * dx + dz * dz) / (rx * rx) + (dy * dy) / (ry * ry));
          double inner = 1 - 1.6 / Math.min(rx, ry);
          if (q > 1 || q < inner) continue;
          int x = cx + dx, y = cy + dy, z = cz + dz;
          if (y > VbMap.SCENERY_HIGH_Y || y < VbMap.SCENERY_LOW_Y || !keep.ok(x, y, z)) continue;
          c.set(x, y, z, block.at(x, y, z));
        }
  }
}
