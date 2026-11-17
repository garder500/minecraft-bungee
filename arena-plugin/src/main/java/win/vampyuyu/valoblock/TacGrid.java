package win.vampyuyu.valoblock;

/**
 * The geometry of the tactical map menu, no Bukkit (mirrors resource-pack/vb_layout.py, which the pack test keeps in step). The plan is
 * square and takes the 5 x 5 middle slots of the 54-slot chest (columns 2 to 6, rows 0 to 4). Two levels:
 * <ul>
 *   <li>the overview: the whole plan, one slot = one zone = {@code cols / 5} x {@code rows / 5} cells (6 x 6 for a 30 x 30 plan);
 *   <li>a zone: its cells zoomed, the fine grid of 5 x 5 slots over it (1.2 cells a slot).
 * </ul>
 * Everything is in blocks from the map's min corner (x along columns, z along rows), like the map files.
 */
final class TacGrid {
  static final int GRID = 5, FIRST_COL = 2, SLOTS = 9;
  static final int TEAM_COL = 0, BACK = 45, INFO = 49, CANCEL = 53;
  /** Overview level. */
  static final int OVERVIEW = -1;

  private TacGrid() {}

  /** Chest slot of the plan cell (gx, gy), 0 to 4 each. */
  static int slot(int gx, int gy) {
    return gy * SLOTS + FIRST_COL + gx;
  }

  /** Plan cell (gx, gy) of a chest slot, or null when the slot is not over the plan. */
  static int[] gridOf(int slot) {
    if (slot < 0 || slot >= SLOTS * GRID) return null;
    int gx = slot % SLOTS - FIRST_COL, gy = slot / SLOTS;
    return gx < 0 || gx >= GRID ? null : new int[] {gx, gy};
  }

  /** Zone index (row * 5 + column) of an overview cell, 0 to 24. */
  static int zone(int gx, int gy) {
    return gy * GRID + gx;
  }

  /** Slot of a teammate marker row (0 to 3, the left column). */
  static int teamSlot(int index) {
    return index * SLOTS + TEAM_COL;
  }

  /** Plan cells (not blocks) of the point at the centre of a grid slot: zone is OVERVIEW or 0 to 24. */
  static double[] centreCells(VbMap map, int zone, int gx, int gy) {
    int cols = map.plan().get(0).length(), rows = map.plan().size();
    double zw = cols / (double) GRID, zh = rows / (double) GRID;
    if (zone == OVERVIEW) return new double[] {(gx + 0.5) * zw, (gy + 0.5) * zh};
    int zc = zone % GRID, zr = zone / GRID;
    return new double[] {zc * zw + (gx + 0.5) * zw / GRID, zr * zh + (gy + 0.5) * zh / GRID};
  }

  /** The point (x, z in blocks from the map corner) at the centre of a grid slot of a level. */
  static double[] centre(VbMap map, int zone, int gx, int gy) {
    double[] c = centreCells(map, zone, gx, gy);
    return new double[] {c[0] * map.cell(), c[1] * map.cell()};
  }

  /** Whether a plan character is somewhere a player stands (floor, spawns, sites, feature pads, high ground). */
  static boolean walkable(char c) {
    return ".RBAFh".indexOf(c) >= 0;
  }

  /** The plan character under a point in blocks (outside the plan is wall). */
  static char at(VbMap map, double x, double z) {
    int c = (int) Math.floor(x / map.cell()), r = (int) Math.floor(z / map.cell());
    if (c < 0 || r < 0 || r >= map.plan().size() || c >= map.plan().get(0).length()) return '#';
    return map.plan().get(r).charAt(c);
  }

  /**
   * Where a click lands: the point itself on a walkable cell, else the centre of the nearest walkable cell within {@code reach} cells of
   * it (a click on a wall edge still works), else null. Returns {x, z, standY} (standY: blocks above the floor, the high ground's height).
   */
  static double[] snap(VbMap map, double x, double z, double reach) {
    char ch = at(map, x, z);
    if (walkable(ch)) return new double[] {x, z, map.standY(ch)};
    int cols = map.plan().get(0).length(), rows = map.plan().size();
    int c0 = (int) Math.floor(x / map.cell()), r0 = (int) Math.floor(z / map.cell());
    double best = Double.MAX_VALUE;
    double[] out = null;
    int span = (int) Math.ceil(reach) + 1;
    for (int r = r0 - span; r <= r0 + span; r++)
      for (int c = c0 - span; c <= c0 + span; c++) {
        if (c < 0 || r < 0 || c >= cols || r >= rows || !walkable(map.plan().get(r).charAt(c))) continue;
        double cx = (c + 0.5) * map.cell(), cz = (r + 0.5) * map.cell();
        double d = Math.hypot(cx - x, cz - z) / map.cell();
        if (d <= reach && d < best) {
          best = d;
          out = new double[] {cx, cz, map.standY(map.plan().get(r).charAt(c))};
        }
      }
    return out;
  }

  /** The grid cell (gx, gy) a point (blocks) falls in at a level, or null when it is outside the view (another zone). */
  static int[] cellOf(VbMap map, int zone, double x, double z) {
    int cols = map.plan().get(0).length(), rows = map.plan().size();
    double cx = x / map.cell(), cz = z / map.cell();
    double zw = cols / (double) GRID, zh = rows / (double) GRID;
    double rx = cx, rz = cz;
    double w = zw, h = zh;
    if (zone != OVERVIEW) {
      rx = cx - (zone % GRID) * zw;
      rz = cz - (zone / GRID) * zh;
      w = zw / GRID;
      h = zh / GRID;
      if (rx < 0 || rz < 0 || rx >= zw || rz >= zh) return null;
    }
    int gx = (int) Math.floor(rx / w), gy = (int) Math.floor(rz / h);
    return gx < 0 || gy < 0 || gx >= GRID || gy >= GRID ? null : new int[] {gx, gy};
  }

  /** The zone (0 to 24) a point (blocks) is in. */
  static int zoneOf(VbMap map, double x, double z) {
    int[] g = cellOf(map, OVERVIEW, Math.max(0, Math.min(map.width() - 1e-6, x)), Math.max(0, Math.min(map.depth() - 1e-6, z)));
    return g == null ? 0 : zone(g[0], g[1]);
  }

  /** What a click on grid cell (gx, gy) of a level picks: the snapped {x, z, standY}, or null when there is nothing to stand on there. */
  static double[] pick(VbMap map, int zone, int gx, int gy, double reach) {
    double[] c = centre(map, zone, gx, gy);
    return snap(map, c[0], c[1], reach);
  }

  /** Whether some walkable cell of a zone (0 to 24) lies within {@code range} blocks of a point; false for a zone of walls and sea. */
  static boolean zoneReachable(VbMap map, int zone, double px, double pz, double range) {
    int cols = map.plan().get(0).length(), rows = map.plan().size();
    double zw = cols / (double) GRID, zh = rows / (double) GRID;
    int c0 = (int) Math.round((zone % GRID) * zw), c1 = (int) Math.round((zone % GRID + 1) * zw);
    int r0 = (int) Math.round((zone / GRID) * zh), r1 = (int) Math.round((zone / GRID + 1) * zh);
    for (int r = r0; r < r1; r++)
      for (int c = c0; c < c1; c++)
        if (walkable(map.plan().get(r).charAt(c)) && Math.hypot((c + 0.5) * map.cell() - px, (r + 0.5) * map.cell() - pz) <= range) return true;
    return false;
  }

  /** What is in a zone worth a word in the tooltip: its sites and the spawns. */
  static java.util.List<String> features(VbMap map, int zone) {
    int cols = map.plan().get(0).length(), rows = map.plan().size();
    double zw = cols / (double) GRID, zh = rows / (double) GRID;
    double x0 = (zone % GRID) * zw * map.cell(), x1 = (zone % GRID + 1) * zw * map.cell();
    double z0 = (zone / GRID) * zh * map.cell(), z1 = (zone / GRID + 1) * zh * map.cell();
    java.util.List<String> out = new java.util.ArrayList<>();
    for (VbSite s : map.sites()) {
      double cx = (s.min()[0] + s.max()[0]) / 2, cz = (s.min()[2] + s.max()[2]) / 2;
      if (cx >= x0 && cx < x1 && cz >= z0 && cz < z1) out.add("Site " + s.id() + ": " + s.name());
    }
    boolean attack = false, defend = false;
    for (int r = (int) Math.round(z0 / map.cell()); r < Math.round(z1 / map.cell()); r++)
      for (int c = (int) Math.round(x0 / map.cell()); c < Math.round(x1 / map.cell()); c++) {
        char ch = map.plan().get(r).charAt(c);
        attack |= ch == 'R';
        defend |= ch == 'B';
      }
    if (attack) out.add("Seedbearer spawn");
    if (defend) out.add("Sealer spawn");
    return out;
  }

  /** Index of a map id in the pack's numbering of the plan glyphs. */
  static int mapIndex(String id) {
    return VbData.MAP_IDS.indexOf(id);
  }
}
