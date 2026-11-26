package win.vampyuyu.valoblock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rootvault, the Whispering Grove: giant bark walls and a Hollow Trunk around site A with a leaf crown, moss floors,
 * azalea bushes (persistent leaves, so they never decay), mangrove roots, a mushroom grove in the middle, the canopy
 * bridge loop at +6 on log posts with railings and four ladders up, and the branches that end in the one-way root drops.
 */
final class RootvaultTheme implements VbThemes.Theme {
  private static final String LEAVES = "azalea_leaves[persistent=true]", BLOOM = "flowering_azalea_leaves[persistent=true]";

  @Override
  public void build(VbPlan p, VbCanvas c) {
    String attack = ThemeKit.lower(p.map, VbMap.Kind.ATTACK), defend = ThemeKit.lower(p.map, VbMap.Kind.DEFEND), site = ThemeKit.lower(p.map, VbMap.Kind.SITE);
    List<VbSite> sites = p.map.sites();
    int[] trunk = ThemeKit.siteCells(p, sites.get(0)), garden = ThemeKit.siteCells(p, sites.get(1));
    // Hollow Trunk: the foliage ring around site A is its bark (one cell wider than the site box).
    int[] bark = {trunk[0] - 1, trunk[1] - 1, trunk[2] + 1, trunk[3] + 1};
    int hy = p.map.highY(), e = p.cell - 1;
    // Canopy: deck cells (the loop, the branches, the root rims) and the trunk cells that carry the ladders.
    Set<Long> deck = new HashSet<>(), solidTrunk = new HashSet<>(), noRail = new HashSet<>();
    for (int[] h : p.cellsOf('h')) deck.add(key(h[0], h[1]));
    List<int[]> ladderSpots = new java.util.ArrayList<>();
    for (List<int[]> g : p.groups('h')) ladderSpots.addAll(p.ladderSpots(g, 4));
    for (int[] s : ladderSpots) {
      solidTrunk.add(key(s[0] + s[2], s[1] + s[3]));
      noRail.add(key(s[0] + s[2], s[1] + s[3]) * 8 + edgeIndex(-s[2], -s[3]));
    }
    List<int[]> branchCells = new java.util.ArrayList<>();
    for (int[] f : p.cellsOf('F')) {
      List<int[]> b = RootDrops.branch(p, f);
      branchCells.addAll(b);
      for (int[] cell : b) deck.add(key(cell[0], cell[1]));
    }
    Set<Long> onBranch = new HashSet<>();
    for (int[] b : branchCells) onBranch.add(key(b[0], b[1]));
    for (int row = 0; row < p.rows; row++)
      for (int col = 0; col < p.cols; col++) {
        char ch = p.at(col, row);
        boolean inBark = ThemeKit.in(col, row, bark) && ch == 'g' && !onBranch.contains(key(col, row));
        boolean mush = ch == 'g' && col >= 13 && col <= 16 && row >= 13 && row <= 15;
        for (int lx = 0; lx <= e; lx++)
          for (int lz = 0; lz <= e; lz++) {
            int x = col * p.cell + lx, z = row * p.cell + lz;
            if (ch == '#') {
              wall(p, c, x, z, p.wallTop(col, row));
              continue;
            }
            c.set(x, -1, z, floor(p, ch, col, row, lx, lz, x, z, attack, defend, site, ThemeKit.in(col, row, garden)));
            if (inBark) {
              for (int y = 0; y < 8; y++) c.set(x, y, z, y > 0 && p.hash(x, y, z, 7) == 0 ? "moss_block" : (lx == 0 || lz == 0 || lx == e || lz == e) && y % 3 == 2 ? "stripped_oak_log[axis=y]" : "oak_log[axis=y]");
            } else if (mush) {
              mushroom(p, c, col, row, lx, lz, x, z);
            } else if (ch == 'g') {
              c.set(x, 0, z, p.hash(x, 0, z, 4) == 0 ? BLOOM : LEAVES);
              c.set(x, 1, z, p.hash(x, 1, z, 4) == 0 ? BLOOM : LEAVES);
              if (p.hash(x, 2, z, 3) == 0) c.set(x, 2, z, LEAVES);
            } else if (ch == 'c') {
              c.set(x, 0, z, "oak_log[axis=y]");
              c.set(x, 1, z, lx == 0 || lz == 0 || lx == e || lz == e ? "stripped_oak_log[axis=y]" : "moss_block");
            }
          }
      }
    // Hollow Trunk crown: a dome of leaves over the whole trunk, with lights under it.
    for (int row = bark[1]; row <= bark[3]; row++)
      for (int col = bark[0]; col <= bark[2]; col++)
        for (int lx = 0; lx <= e; lx++)
          for (int lz = 0; lz <= e; lz++) {
            int x = col * p.cell + lx, z = row * p.cell + lz;
            boolean rim = col == bark[0] || col == bark[2] || row == bark[1] || row == bark[3];
            c.set(x, 8, z, p.hash(x, 8, z, 5) == 0 ? BLOOM : LEAVES);
            if (!rim) c.set(x, 9, z, p.hash(x, 9, z, 4) == 0 ? BLOOM : LEAVES);
            if (!rim && !p.lane(x, z) && lx % 2 == 0 && lz % 2 == 0 && (col + row) % 2 == 0) c.set(x, 7, z, "shroomlight");
          }
    canopy(p, c, deck, solidTrunk, noRail, branchCells, hy);
    details(p, c, garden);
    // Four ladders up to the canopy (log trunks behind them).
    ThemeKit.ladders(p, c, 4, hy);
    RootvaultScenery.build(p, c);
  }

  private static long key(int col, int row) {
    return (long) col * 100 + row;
  }

  private static int edgeIndex(int dx, int dz) {
    return dx > 0 ? 0 : dx < 0 ? 1 : dz > 0 ? 2 : 3;
  }

  private static String floor(VbPlan p, char ch, int col, int row, int lx, int lz, int x, int z, String attack, String defend, String site, boolean garden) {
    int e = p.cell - 1, h = p.hash(x, 0, z, 10);
    String base = h == 0 ? "rooted_dirt" : h == 1 ? "podzol" : "moss_block";
    switch (ch) {
      case 'R': // spawn rooms: a grid of team-colour lines (readable, not a checkerboard)
        return lx == 0 || lz == 0 ? attack : (lx == 2 && lz == 2) ? "orange_glazed_terracotta" : base;
      case 'B':
        return lx == 0 || lz == 0 ? defend : (lx == 2 && lz == 2) ? "light_blue_glazed_terracotta" : base;
      case 'A':
        {
          boolean border = (lx == 0 && !ThemeKit.is(p, col - 1, row, 'A')) || (lx == e && !ThemeKit.is(p, col + 1, row, 'A')) || (lz == 0 && !ThemeKit.is(p, col, row - 1, 'A')) || (lz == e && !ThemeKit.is(p, col, row + 1, 'A'));
          if (border) return site;
          return garden && p.hash(x, 2, z, 30) == 0 ? "shroomlight" : base;
        }
      case 'F':
        return (lx == 1 || lx == 2) && (lz == 1 || lz == 2) ? "yellow_terracotta" : "rooted_dirt";
      default:
        return base;
    }
  }

  private static void wall(VbPlan p, VbCanvas c, int x, int z, int top) {
    c.set(x, -1, z, "rooted_dirt");
    for (int y = 0; y < top; y++) {
      String m = y == top - 1 ? "moss_block" : p.hash(x, y, z, 9) == 0 ? "stripped_dark_oak_log[axis=y]" : p.hash(x, y, z, 5) == 0 ? "oak_log[axis=y]" : "dark_oak_log[axis=y]";
      c.set(x, y, z, m);
    }
    if (p.nextToOpen(x, z)) {
      int h = p.hash(x, 6, z, 6);
      if (h == 0) c.set(x, top, z, LEAVES);
      else if (h == 1) c.set(x, top, z, "lantern");
    }
  }

  /** A giant mushroom per grove cell: a 2x2 stem and a cap that stays inside the cell (they alternate red and brown). */
  private static void mushroom(VbPlan p, VbCanvas c, int col, int row, int lx, int lz, int x, int z) {
    int e = p.cell - 1;
    boolean red = (col + row) % 2 == 0;
    String cap = red ? "red_mushroom_block" : "brown_mushroom_block";
    if (lx >= 1 && lx <= e - 1 && lz >= 1 && lz <= e - 1) for (int y = 0; y <= 2; y++) c.set(x, y, z, "mushroom_stem");
    boolean rim = lx == 0 || lz == 0 || lx == e || lz == e;
    if (rim) c.set(x, 3, z, cap);
    c.set(x, 4, z, cap);
    if (!rim && lx == 1 && lz == 1) c.set(x, 3, z, "shroomlight");
  }

  private static void canopy(VbPlan p, VbCanvas c, Set<Long> deck, Set<Long> solidTrunk, Set<Long> noRail, List<int[]> branchCells, int hy) {
    int e = p.cell - 1, top = hy - 1;
    Set<Long> hole = new HashSet<>();
    for (int[] f : p.cellsOf('F')) {
      deck.add(key(f[0], f[1]));
      hole.add(key(f[0], f[1]));
    }
    for (int row = 0; row < p.rows; row++)
      for (int col = 0; col < p.cols; col++) {
        if (!deck.contains(key(col, row))) continue;
        boolean isHole = hole.contains(key(col, row));
        for (int lx = 0; lx <= e; lx++)
          for (int lz = 0; lz <= e; lz++) {
            int x = col * p.cell + lx, z = row * p.cell + lz;
            if (isHole && lx >= 1 && lx <= e - 1 && lz >= 1 && lz <= e - 1) continue; // the drop
            boolean edge = lx == 0 || lz == 0 || lx == e || lz == e;
            c.set(x, top, z, edge ? "stripped_oak_log[axis=y]" : "oak_planks");
            if (solidTrunk.contains(key(col, row))) for (int y = 0; y < top; y++) c.set(x, y, z, "oak_log[axis=y]");
            if (!edge) continue;
            // railings on every side that does not continue the deck
            int dx = lx == 0 ? -1 : lx == e ? 1 : 0, dz = lz == 0 ? -1 : lz == e ? 1 : 0;
            boolean open = false;
            if (dx != 0 && !deck.contains(key(col + dx, row)) && !noRail.contains(key(col, row) * 8 + edgeIndex(dx, 0))) open = true;
            if (dz != 0 && !deck.contains(key(col, row + dz)) && !noRail.contains(key(col, row) * 8 + edgeIndex(0, dz))) open = true;
            if (open && !isHole) c.set(x, hy, z, "oak_fence");
          }
        if (isHole) { // roots hang from the rim into the shaft
          for (int[] corner : new int[][] {{0, 0}, {e, 0}, {0, e}, {e, e}}) {
            int x = col * p.cell + corner[0], z = row * p.cell + corner[1];
            c.set(x, top - 1, z, "hanging_roots");
            c.set(x, top - 2, z, "hanging_roots");
          }
          int mid = e / 2;
          c.set(col * p.cell + mid, top - 1, row * p.cell, "hanging_roots");
        }
        // posts at the outer corners, lanterns under the rest: the ground under the canopy is never dark
        for (int dx : new int[] {0, e})
          for (int dz : new int[] {0, e}) {
            int x = col * p.cell + dx, z = row * p.cell + dz;
            if (p.lane(x, z) || c.get(x, top - 1, z) != null) continue;
            boolean outer = (dx == 0 ? !deck.contains(key(col - 1, row)) : !deck.contains(key(col + 1, row))) || (dz == 0 ? !deck.contains(key(col, row - 1)) : !deck.contains(key(col, row + 1)));
            if (outer && (col + row) % 2 == 0 && !isHole) for (int y = 0; y < top; y++) c.set(x, y, z, "oak_log[axis=y]");
            else if (!isHole && (col + row) % 2 == 1) c.set(x, top - 1, z, "lantern[hanging=true]");
          }
      }
  }

  /** Mangrove roots at the foot of the bark, small mushrooms and lights in the spore garden. Off the walking lines. */
  private static void details(VbPlan p, VbCanvas c, int[] garden) {
    for (int z = 0; z < p.depth; z++)
      for (int x = 0; x < p.width; x++) {
        int col = p.col(x), row = p.col(z);
        char ch = p.at(col, row);
        if (ch != '.' && ch != 'A' || p.lane(x, z) || c.get(x, 0, z) != null) continue;
        boolean nextToBark = false;
        for (int[] d : VbPlan.DIRS) {
          int nx = x + d[0], nz = z + d[1];
          if (nx < 0 || nz < 0 || nx >= p.width || nz >= p.depth) continue;
          String b = c.get(nx, 1, nz);
          if (b != null && b.startsWith("dark_oak_log") || b != null && b.startsWith("oak_log") && p.at(p.col(nx), p.col(nz)) == 'g') nextToBark = true;
        }
        int h = p.hash(x, 11, z, 20);
        if (nextToBark && h < 6) c.set(x, 0, z, "mangrove_roots");
        else if (ThemeKit.in(col, row, garden) && h == 7) c.set(x, 0, z, "red_mushroom");
        else if (ThemeKit.in(col, row, garden) && h == 8) c.set(x, 0, z, "brown_mushroom");
      }
  }
}
