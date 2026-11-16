package win.vampyuyu.valoblock;

import java.util.List;

/**
 * Hourfall, the Citadel of Golden Sands: sandstone walls in smooth and chiselled bands with sparing gold trims and
 * turquoise inlays, arches over the doorways, flat roofs over the side halls and the Scarab vault, the Hourglass
 * tower in the middle (high ground, two ladders), sand drifts, lanterns and banners, and a sundial on the A courtyard.
 * The four Time Tide gate cells stay open here (an arch above): the realm service closes them with sand walls.
 */
final class HourfallTheme implements VbThemes.Theme {
  /** Side halls, as cell rectangles {c0, r0, c1, r1}: flat roofs on posts. */
  private static final int[][] HALLS = {{3, 13, 9, 16}, {20, 13, 26, 16}};

  @Override
  public void build(VbPlan p, VbCanvas c) {
    String attack = ThemeKit.lower(p.map, VbMap.Kind.ATTACK), defend = ThemeKit.lower(p.map, VbMap.Kind.DEFEND), site = ThemeKit.lower(p.map, VbMap.Kind.SITE);
    int hy = p.map.highY();
    for (int row = 0; row < p.rows; row++)
      for (int col = 0; col < p.cols; col++) {
        char ch = p.at(col, row);
        for (int lx = 0; lx < p.cell; lx++)
          for (int lz = 0; lz < p.cell; lz++) {
            int x = col * p.cell + lx, z = row * p.cell + lz;
            if (ch == '#') {
              wall(p, c, x, z, p.wallTop(col, row));
              continue;
            }
            c.set(x, -1, z, floor(p, ch, col, row, lx, lz, x, z, attack, defend, site));
            if (ch == 'c') {
              c.set(x, 0, z, "cut_sandstone");
              c.set(x, 1, z, (lx == 0 || lz == 0 || lx == p.cell - 1 || lz == p.cell - 1) ? "smooth_sandstone" : "chiseled_sandstone");
            } else if (ch == 'h') {
              for (int y = 0; y < hy - 1; y++) c.set(x, y, z, p.hash(x, y, z, 11) == 0 ? "sandstone" : "cut_sandstone");
              boolean rim = lx == 0 || lz == 0 || lx == p.cell - 1 || lz == p.cell - 1;
              c.set(x, hy - 1, z, rim ? "chiseled_sandstone" : "smooth_sandstone");
            }
          }
      }
    // Sundial on A, scarab inlay on B.
    List<VbSite> sites = p.map.sites();
    if (sites.size() >= 2) {
      sundial(p, c, sites.get(0));
      scarab(p, c, sites.get(1));
    }
    // Arches over every doorway and every gate.
    for (int row = 0; row < p.rows; row++)
      for (int col = 0; col < p.cols; col++) ThemeKit.arch(p, c, col, row, "cut_sandstone", p.at(col, row) == 'F' ? "chiseled_sandstone" : "smooth_sandstone", "smooth_sandstone_stairs");
    for (int[] g : TimeTide.gates(p)) c.set(g[0] * p.cell + p.cell / 2, 5, g[1] * p.cell + p.cell / 2, "gold_block");
    // Flat roofs: the two side halls and the Scarab vault.
    for (int[] hall : HALLS) ThemeKit.roof(p, c, hall, 5, "cut_sandstone", "chiseled_sandstone", "cut_sandstone", "lantern[hanging=true]");
    if (sites.size() >= 2) ThemeKit.roof(p, c, ThemeKit.siteCells(p, sites.get(1)), 5, "cut_sandstone", "chiseled_sandstone", "cut_sandstone", "lantern[hanging=true]");
    // The Hourglass: two ladders up every tower, a glass-and-sand sculpture on the big one.
    ThemeKit.ladders(p, c, 2, hy);
    for (List<int[]> g : p.groups('h')) {
      if (g.size() < 8) continue;
      int minC = g.stream().mapToInt(a -> a[0]).min().getAsInt(), maxC = g.stream().mapToInt(a -> a[0]).max().getAsInt();
      int minR = g.stream().mapToInt(a -> a[1]).min().getAsInt(), maxR = g.stream().mapToInt(a -> a[1]).max().getAsInt();
      int cx = (minC + maxC + 1) * p.cell / 2, cz = (minR + maxR + 1) * p.cell / 2;
      String[] stack = {"chiseled_sandstone", "sand", "glass", "glass", "sand", "chiseled_sandstone"};
      for (int dx = -1; dx <= 0; dx++) for (int dz = -1; dz <= 0; dz++) for (int i = 0; i < stack.length; i++) c.set(cx + dx, hy + i, cz + dz, stack[i]);
    }
    details(p, c);
  }

  private static String floor(VbPlan p, char ch, int col, int row, int lx, int lz, int x, int z, String attack, String defend, String site) {
    int e = p.cell - 1;
    String base = p.hash(x, 0, z, 9) == 0 ? "cut_sandstone" : "smooth_sandstone";
    switch (ch) {
      case 'R':
        return (x + z) % 2 == 0 ? attack : base;
      case 'B':
        return (x + z) % 2 == 0 ? defend : base;
      case 'A':
        {
          boolean border = (lx == 0 && !ThemeKit.is(p, col - 1, row, 'A')) || (lx == e && !ThemeKit.is(p, col + 1, row, 'A')) || (lz == 0 && !ThemeKit.is(p, col, row - 1, 'A')) || (lz == e && !ThemeKit.is(p, col, row + 1, 'A'));
          return border ? site : base;
        }
      case 'F':
        return (lx == 1 || lx == 2) && (lz == 1 || lz == 2) ? "light_blue_terracotta" : "chiseled_sandstone";
      default:
        if ((col + row) % 6 == 0 && lx >= 1 && lx <= 2 && lz >= 1 && lz <= 2) return "light_blue_terracotta";
        if ((lx == 0 || lz == 0) && p.hash(x, 1, z, 3) == 0) return "cut_sandstone";
        return base;
    }
  }

  private static void wall(VbPlan p, VbCanvas c, int x, int z, int top) {
    boolean ex = p.nextToOpen(x, z);
    c.set(x, -1, z, "smooth_sandstone");
    for (int y = 0; y < top; y++) {
      String m;
      if (y == top - 1) m = (x + z) % 2 == 0 ? "chiseled_sandstone" : "cut_sandstone";
      else if (y == top - 2 && ex) m = p.hash(x, y, z, 13) == 0 ? "gold_block" : "smooth_sandstone";
      else if (y == 2 && ex && p.hash(x, y, z, 5) == 0) m = p.hash(x, 1, z, 2) == 0 ? "light_blue_terracotta" : "cyan_terracotta";
      else if (y == 0) m = "sandstone";
      else m = p.hash(x, y, z, 17) == 0 ? "sandstone" : "cut_sandstone";
      c.set(x, y, z, m);
    }
  }

  private static void sundial(VbPlan p, VbCanvas c, VbSite s) {
    double cx = (s.min()[0] + s.max()[0]) / 2, cz = (s.min()[2] + s.max()[2]) / 2;
    for (int dx = -8; dx <= 8; dx++)
      for (int dz = -8; dz <= 8; dz++) {
        int x = (int) cx + dx, z = (int) cz + dz;
        if (!ThemeKit.is(p, p.col(x), p.col(z), 'A')) continue;
        double r = Math.hypot(dx + 0.5 - (cx - Math.floor(cx)), dz + 0.5 - (cz - Math.floor(cz)));
        if (r >= 5.5 && r < 6.6) c.set(x, -1, z, "chiseled_sandstone");
        else if (r >= 3.0 && r < 3.9) c.set(x, -1, z, "light_blue_terracotta");
      }
    for (int k = 0; k < 12; k++) { // the twelve hour marks, one gold block each
      double a = k * Math.PI / 6;
      int x = (int) Math.round(cx + 4.7 * Math.cos(a) - 0.5), z = (int) Math.round(cz + 4.7 * Math.sin(a) - 0.5);
      if (ThemeKit.is(p, p.col(x), p.col(z), 'A')) c.set(x, -1, z, "gold_block");
    }
  }

  private static void scarab(VbPlan p, VbCanvas c, VbSite s) {
    int cx = (int) ((s.min()[0] + s.max()[0]) / 2), cz = (int) ((s.min()[2] + s.max()[2]) / 2);
    for (int dx = -9; dx <= 9; dx++)
      for (int dz = -9; dz <= 9; dz++) {
        int x = cx + dx, z = cz + dz, d = Math.abs(dx) + Math.abs(dz);
        if (!ThemeKit.is(p, p.col(x), p.col(z), 'A')) continue;
        if (d == 6 || d == 7) c.set(x, -1, z, "cyan_terracotta");
        else if (d == 3 && (dx == 0 || dz == 0)) c.set(x, -1, z, "chiseled_sandstone");
      }
  }

  /** Sand drifts against the walls, banners on the faces, lanterns on the wall tops. All off the walking lines. */
  private static void details(VbPlan p, VbCanvas c) {
    for (int z = 0; z < p.depth; z++)
      for (int x = 0; x < p.width; x++) {
        int col = p.col(x), row = p.col(z);
        char ch = p.at(col, row);
        if (ch == '#') {
          if (p.nextToOpen(x, z) && p.hash(x, 5, z, 15) == 0) c.set(x, p.wallTop(col, row), z, "lantern");
          continue;
        }
        if (ch != '.' || p.lane(x, z)) continue;
        String facing = null;
        if (p.wall(p.col(x + 1), row)) facing = "west";
        else if (x > 0 && p.wall(p.col(x - 1), row)) facing = "east";
        else if (p.wall(col, p.col(z + 1))) facing = "north";
        else if (z > 0 && p.wall(col, p.col(z - 1))) facing = "south";
        boolean wallSide = (x + 1 < p.width && p.wall(p.col(x + 1), row) && p.col(x + 1) != col) || (x > 0 && p.wall(p.col(x - 1), row) && p.col(x - 1) != col) || (z + 1 < p.depth && p.wall(col, p.col(z + 1)) && p.col(z + 1) != row) || (z > 0 && p.wall(col, p.col(z - 1)) && p.col(z - 1) != row);
        if (facing == null || !wallSide || c.get(x, 0, z) != null) continue;
        int h = p.hash(x, 9, z, 24);
        if (h < 5) c.set(x, 0, z, "sand");
        else if (h == 6) c.set(x, 3, z, (p.hash(x, 2, z, 2) == 0 ? "yellow" : "light_blue") + "_wall_banner[facing=" + facing + "]");
      }
  }
}
