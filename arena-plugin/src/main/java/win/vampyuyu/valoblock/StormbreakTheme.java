package win.vampyuyu.valoblock;

import java.util.List;

/**
 * Stormbreak, the Tempestuous Archipelago: deepslate and tuff cliffs with oxidised copper, storm glass, islands standing
 * in a sea (the kill volume), narrow copper bridges, the Lighthouse tower and its raised ring on A, the Wreck (a ship's hull
 * of dark planks) on B, the Eyrie with its lightning rod in the middle, and copper wind pads on every F cell.
 */
final class StormbreakTheme implements VbThemes.Theme {
  @Override
  public void build(VbPlan p, VbCanvas c) {
    String attack = ThemeKit.lower(p.map, VbMap.Kind.ATTACK), defend = ThemeKit.lower(p.map, VbMap.Kind.DEFEND), site = ThemeKit.lower(p.map, VbMap.Kind.SITE);
    List<VbSite> sites = p.map.sites();
    int[] lighthouse = ThemeKit.siteCells(p, sites.get(0)), wreck = ThemeKit.siteCells(p, sites.get(1));
    int e = p.cell - 1, hy = p.map.highY();
    for (int row = 0; row < p.rows; row++)
      for (int col = 0; col < p.cols; col++) {
        char ch = p.at(col, row);
        char bridge = bridgeAxis(p, col, row);
        boolean inWreck = ThemeKit.in(col, row, new int[] {wreck[0] - 1, wreck[1] - 2, wreck[2] + 1, wreck[3] + 1});
        for (int lx = 0; lx <= e; lx++)
          for (int lz = 0; lz <= e; lz++) {
            int x = col * p.cell + lx, z = row * p.cell + lz;
            if (ch == 'w') {
              sea(c, x, z);
              continue;
            }
            if (bridge != 0) {
              boolean deck = bridge == 'z' ? lx >= 1 && lx <= e - 1 : lz >= 1 && lz <= e - 1;
              if (!deck) {
                sea(c, x, z);
                continue;
              }
              sea(c, x, z);
              c.set(x, -1, z, p.hash(x, 0, z, 6) == 0 ? "exposed_cut_copper" : "weathered_cut_copper");
              c.set(x, -2, z, "deepslate_bricks");
              boolean pile = (bridge == 'z' ? row : col) % 3 == 0 && (bridge == 'z' ? lx : lz) == e / 2 && (bridge == 'z' ? lz : lx) == e / 2;
              if (pile) for (int y = -5; y <= -3; y++) c.set(x, y, z, "deepslate_bricks");
              // chain posts with lanterns on the deck edge of every other bridge cell
              if ((bridge == 'z' ? row : col) % 2 == 0 && (bridge == 'z' ? lz : lx) == 0 && (bridge == 'z' ? lx : lz) == 1) c.set(x, 0, z, "lantern");
              continue;
            }
            if (ch == '#') {
              cliff(p, c, x, z, col, row, inWreck, lighthouse, wreck);
              continue;
            }
            // island body down to the sea bed, the floor on top
            for (int y = -5; y <= -2; y++) c.set(x, y, z, p.hash(x, y, z, 4) == 0 ? "deepslate" : y >= -3 ? "tuff" : "cobbled_deepslate");
            c.set(x, -1, z, floor(p, ch, col, row, lx, lz, x, z, attack, defend, site));
            if (ch == 'c') {
              c.set(x, 0, z, "waxed_weathered_copper");
              c.set(x, 1, z, lx == 0 || lz == 0 || lx == e || lz == e ? "deepslate_tiles" : "tinted_glass");
            } else if (ch == 'h') {
              for (int y = 0; y < hy; y++) c.set(x, y, z, y == hy - 1 ? ((lx == 0 || lz == 0 || lx == e || lz == e) ? "exposed_cut_copper" : "polished_deepslate") : "deepslate_bricks");
            }
          }
      }
    lighthouse(p, c, lighthouse);
    wreck(p, c, wreck);
    eyrie(p, c);
    // Storm lanterns on the island floors and a lamp over each pad, so nothing is dark in the rain.
    for (WindPads.Pad pad : WindPads.pads(p)) {
      int x = pad.col() * p.cell + p.cell / 2, z = pad.row() * p.cell + p.cell / 2;
      c.set(x, -1, z, "sea_lantern");
    }
  }

  /** 'x' or 'z' for a cell that is a one-cell bridge over the sea (see {@link GustClock#pushAxis}: the push is across the bridge), else 0. */
  static char bridgeAxis(VbPlan p, int col, int row) {
    char push = GustClock.pushAxis(p, col, row);
    if (push == 0 || p.at(col, row) == 'F') return 0;
    return push == 'x' ? 'z' : 'x'; // the bridge runs along the other axis
  }

  private static void sea(VbCanvas c, int x, int z) {
    c.set(x, -5, z, "tuff");
    for (int y = -4; y <= -2; y++) c.set(x, y, z, "water");
  }

  private static String floor(VbPlan p, char ch, int col, int row, int lx, int lz, int x, int z, String attack, String defend, String site) {
    int e = p.cell - 1, h = p.hash(x, 0, z, 9);
    String base = h == 0 ? "polished_deepslate" : h == 1 ? "tuff_bricks" : "deepslate_tiles";
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
        return lx >= 1 && lx <= e - 1 && lz >= 1 && lz <= e - 1 ? "cyan_concrete" : "waxed_oxidized_copper";
      default:
        return base;
    }
  }

  private static void cliff(VbPlan p, VbCanvas c, int x, int z, int col, int row, boolean inWreck, int[] lighthouse, int[] wreck) {
    int top = p.wallTop(col, row);
    boolean ex = p.nextToOpen(x, z);
    for (int y = -5; y < top; y++) {
      String m;
      if (inWreck && p.at(col, row) == '#' && y >= 0) {
        m = y == top - 1 ? "dark_oak_stairs[facing=" + ((x + z) % 2 == 0 ? "north" : "south") + "]" : ex && y % 3 == 1 && p.hash(x, y, z, 3) == 0 ? "stripped_dark_oak_log[axis=y]" : p.hash(x, y, z, 7) == 0 ? "spruce_planks" : p.hash(x, y, z, 11) == 0 ? "dark_prismarine" : "dark_oak_planks";
        if (y == 2 && ex && p.hash(x, 4, z, 6) == 0) m = "tinted_glass"; // portholes
      } else if (y < 0) m = "deepslate";
      else if (y == top - 1) m = ex && p.hash(x, y, z, 5) == 0 ? "oxidized_cut_copper" : "tuff_bricks";
      else if (y == 0 || y == 1) m = p.hash(x, y, z, 5) == 0 ? "cobbled_deepslate" : "deepslate_bricks";
      else if (ex && y == top - 2 && p.hash(x, y, z, 9) == 0) m = "waxed_oxidized_copper";
      else if (ex && y == 2 && p.hash(x, y, z, 10) == 0) m = "light_blue_stained_glass"; // storm glass
      else m = p.hash(x, y, z, 6) == 0 ? "calcite" : p.hash(x, y, z, 3) == 0 ? "deepslate_bricks" : "tuff";
      c.set(x, y, z, m);
    }
    if (ex && p.hash(x, 6, z, 14) == 0) c.set(x, top, z, "lantern");
  }

  /** A white tower with red bands in the corner cell of the A compound and a storm-glass lamp room on top. */
  private static void lighthouse(VbPlan p, VbCanvas c, int[] site) {
    int col = site[0] - 2, row = site[1] - 2; // the north-west corner wall cell of the compound
    if (!p.wall(col, row)) return;
    int x0 = col * p.cell, z0 = row * p.cell, e = p.cell - 1, mid = e / 2, h = 15;
    for (int dx = 0; dx <= e; dx++)
      for (int dz = 0; dz <= e; dz++) {
        boolean corner = (dx == 0 || dx == e) && (dz == 0 || dz == e);
        int x = x0 + dx, z = z0 + dz;
        for (int y = p.wallTop(col, row); y < h; y++) c.set(x, y, z, "air");
        if (corner) {
          for (int y = 0; y < p.wallTop(col, row); y++) c.set(x, y, z, "air");
          continue;
        }
        for (int y = 0; y < h; y++) c.set(x, y, z, (y / 3) % 2 == 0 ? "white_concrete" : "red_concrete");
        // lamp room: storm glass around a sea lantern, a copper roof
        for (int y = h; y < h + 3; y++) c.set(x, y, z, dx == mid && dz == mid ? "sea_lantern" : (dx == 0 || dx == e || dz == 0 || dz == e) ? "blue_stained_glass" : "tinted_glass");
        c.set(x, h + 3, z, "oxidized_cut_copper");
      }
    c.set(x0 + mid, h + 4, z0 + mid, "lightning_rod");
    // the tower stands on its own cell: keep the floor under it
    for (int dx = 0; dx <= e; dx++) for (int dz = 0; dz <= e; dz++) c.set(x0 + dx, -1, z0 + dz, "deepslate_bricks");
  }

  /** The broken mast and torn sail of the Wreck, in the north wall of the B compound. */
  private static void wreck(VbPlan p, VbCanvas c, int[] site) {
    int col = (site[0] + site[2]) / 2, row = site[1] - 2;
    if (!p.wall(col, row)) return;
    int x = col * p.cell + p.cell / 2, z = row * p.cell + p.cell / 2;
    for (int y = 0; y < 13; y++) c.set(x, y, z, "stripped_dark_oak_log[axis=y]");
    for (int dx = -3; dx <= 3; dx++) c.set(x + dx, 11, z, "spruce_fence");
    for (int dx = -2; dx <= 2; dx++) for (int y = 6; y <= 10; y++) if (p.hash(x + dx, y, z, 4) != 0) c.set(x + dx, y, z - 1, "white_wool");
    c.set(x, 13, z, "lantern");
  }

  /** The Eyrie's roost: two raised wall cells with a copper top and a lightning rod, lit by a storm lantern. */
  private static void eyrie(VbPlan p, VbCanvas c) {
    for (int col : new int[] {14, 15}) {
      int row = 12;
      if (!p.wall(col, row)) continue;
      for (int dx = 0; dx < p.cell; dx++)
        for (int dz = 0; dz < p.cell; dz++) {
          int x = col * p.cell + dx, z = row * p.cell + dz;
          for (int y = p.wallTop(col, row); y < 9; y++) c.set(x, y, z, p.hash(x, y, z, 5) == 0 ? "calcite" : "tuff_bricks");
          c.set(x, 9, z, "oxidized_cut_copper");
        }
    }
    c.set(14 * p.cell + p.cell - 1, 10, 12 * p.cell + p.cell / 2, "lightning_rod");
    c.set(15 * p.cell, 10, 12 * p.cell + p.cell / 2, "lantern");
  }
}
