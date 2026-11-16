package win.vampyuyu.valoblock;

import java.util.List;
import java.util.Locale;

/** Pieces the three themed builders share: columns, ladders, doorway arches, roofs, site floors. Pure. */
final class ThemeKit {
  private ThemeKit() {}

  static String lower(VbMap map, VbMap.Kind k) {
    return map.material(k).toLowerCase(Locale.ROOT);
  }

  /** Facing of a ladder that climbs a face looking towards (dx, dz) from the high cell: it faces away from the body. */
  static String ladderFacing(int dx, int dz) {
    return dx > 0 ? "west" : dx < 0 ? "east" : dz > 0 ? "north" : "south";
  }

  /**
   * Ladders up the high ground: on each connected group of h cells, {@code count} ladders on faces far from each other,
   * in the corner block of the low cell's edge (never on the walking line), from the floor to the platform.
   */
  static void ladders(VbPlan p, VbCanvas c, int count, int top) {
    for (List<int[]> g : p.groups('h'))
      for (int[] s : p.ladderSpots(g, count)) {
        int x0 = s[0] * p.cell, z0 = s[1] * p.cell;
        int x = s[2] > 0 ? x0 + p.cell - 1 : s[2] < 0 ? x0 : x0;
        int z = s[3] > 0 ? z0 + p.cell - 1 : s[3] < 0 ? z0 : z0;
        for (int y = 0; y < top; y++) c.set(x, y, z, "ladder[facing=" + ladderFacing(s[2], s[3]) + "]");
      }
  }

  /** An arch over a one-cell doorway: a lintel at y 4 and 5 with top stairs under it on both sides. */
  static void arch(VbPlan p, VbCanvas c, int col, int row, String lintel, String trim, String stairs) {
    char axis = p.doorway(col, row);
    if (axis == 0) return;
    int x0 = col * p.cell, z0 = row * p.cell, e = p.cell - 1;
    for (int dx = 0; dx <= e; dx++)
      for (int dz = 0; dz <= e; dz++) {
        c.set(x0 + dx, 4, z0 + dz, lintel);
        c.set(x0 + dx, 5, z0 + dz, (dx == 0 || dz == 0 || dx == e || dz == e) ? trim : lintel);
      }
    c.set(x0 + e / 2, 3, z0 + e / 2, "lantern[hanging=true]"); // the arch is a roof: it carries its own light
    for (int i = 0; i <= e; i++) {
      if (axis == 'z') { // passage along z: stairs on the west and east columns
        c.set(x0, 3, z0 + i, stairs + "[facing=east,half=top]");
        c.set(x0 + e, 3, z0 + i, stairs + "[facing=west,half=top]");
      } else {
        c.set(x0 + i, 3, z0, stairs + "[facing=south,half=top]");
        c.set(x0 + i, 3, z0 + e, stairs + "[facing=north,half=top]");
      }
    }
  }

  /** Whether the cell is inside the cell rectangle [c0..c1] x [r0..r1]. */
  static boolean in(int c, int r, int[] rect) {
    return c >= rect[0] && c <= rect[2] && r >= rect[1] && r <= rect[3];
  }

  /** The cell rectangle {c0, r0, c1, r1} of a site box. */
  static int[] siteCells(VbPlan p, VbSite s) {
    return new int[] {(int) (s.min()[0] / p.cell), (int) (s.min()[2] / p.cell), (int) (s.max()[0] / p.cell) - 1, (int) (s.max()[2] / p.cell) - 1};
  }

  /** Whether a cell of the plan is the given plan character (outside the plan: no). */
  static boolean is(VbPlan p, int c, int r, char ch) {
    return c >= 0 && r >= 0 && c < p.cols && r < p.rows && p.at(c, r) == ch;
  }

  /**
   * A flat roof over the walkable cells of a cell rectangle at height y, with posts in the outer corners and hanging
   * lanterns inside (so a roofed space is never dark). Posts and lanterns stay off the walking lines.
   */
  static void roof(VbPlan p, VbCanvas c, int[] rect, int y, String slab, String trim, String post, String lantern) {
    for (int r = rect[1]; r <= rect[3]; r++)
      for (int col = rect[0]; col <= rect[2]; col++) {
        if (!p.walk(col, r)) continue;
        int x0 = col * p.cell, z0 = r * p.cell, e = p.cell - 1;
        for (int dx = 0; dx <= e; dx++)
          for (int dz = 0; dz <= e; dz++) {
            boolean edge = (dx == 0 && !in(col - 1, r, rect)) || (dx == e && !in(col + 1, r, rect)) || (dz == 0 && !in(col, r - 1, rect)) || (dz == e && !in(col, r + 1, rect));
            c.set(x0 + dx, y, z0 + dz, edge ? trim : slab);
          }
        for (int dx : new int[] {0, e})
          for (int dz : new int[] {0, e}) {
            int x = x0 + dx, z = z0 + dz;
            if (p.lane(x, z)) continue;
            boolean outer = (dx == 0 ? !in(col - 1, r, rect) : !in(col + 1, r, rect)) || (dz == 0 ? !in(col, r - 1, rect) : !in(col, r + 1, rect));
            if (outer && (col + r) % 2 == 0) {
              for (int yy = 0; yy < y; yy++) c.set(x, yy, z, post);
            } else if (!outer || (col + r) % 2 == 1) {
              c.set(x, y - 1, z, lantern);
            }
          }
      }
  }
}
