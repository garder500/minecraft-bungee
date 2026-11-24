package win.vampyuyu.valoblock;

import java.util.List;

/**
 * The minimap's picture, no Bukkit: a vanilla map is 128 x 128 pixels, but the client draws a map held in the off hand big, so the
 * plan only takes the {@link #AREA} x {@link #AREA} top-left corner (2 px a cell for a 30 x 30 plan) and every other pixel is
 * {@link #CLEAR} (the transparent map colour; the ValoBlock pack also blanks the paper background): the map looks a quarter of its
 * old size and stays clear of the health plate below it. The base
 * layer (walls, floor, sites, spawns) is the same for everyone; the markers are drawn over it. Colours are 0xRRGGBB and the service
 * maps them to the nearest map colour. No lettering: shapes only (a site is magenta with one white dot for A and two for B).
 */
final class MiniMapMath {
  static final int SIZE = 128;
  static final int AREA = 64, MARGIN = 2;   // the corner the plan is drawn in
  static final int CLEAR = -1;              // not a colour: the service writes the transparent map colour
  static final int FLOOR = 0x2b303c, FLOOR2 = 0x262a35, WALL = 0x8d95a8, WALL_LO = 0x636b7d, HIGH = 0x4a5573, SITE = 0xa63fc0, FEATURE = 0xd9b23c,
      CRATE = 0x7a5a32, FOLIAGE = 0x2f6a3d, WATER = 0x1f3f73, ATTACK = 0xc9782e, DEFEND = 0x3f86bd, EDGE = 0x0b0d12, DOT = 0xffffff;
  static final int SELF = 0xffffff, ALLY = 0x5ad07a, DEAD = 0xe8636f, ENEMY = 0xff3030, SEED = 0xff4a8a;

  enum Kind {
    SELF,
    ALLY,
    DEAD,
    ENEMY,
    SEED
  }

  /** A marker at a point in blocks from the map corner; {@code yaw} is the Minecraft yaw in degrees (only SELF shows it). */
  record Marker(Kind kind, double x, double z, float yaw) {}

  private MiniMapMath() {}

  /** Pixels a plan cell takes. */
  static int cellPx(VbMap map) {
    return Math.max(1, AREA / Math.max(map.plan().get(0).length(), map.plan().size()));
  }

  /** Where the plan's top-left corner is in the picture. */
  static int[] origin(VbMap map) {
    int c = cellPx(map);
    return new int[] {MARGIN + (AREA - c * map.plan().get(0).length()) / 2, MARGIN + (AREA - c * map.plan().size()) / 2};
  }

  /** The pixel (x, y) of a point in blocks from the map corner, or null when it is outside the plan. */
  static int[] project(VbMap map, double x, double z) {
    int c = cellPx(map);
    int[] o = origin(map);
    int px = o[0] + (int) Math.floor(x / map.cell() * c), py = o[1] + (int) Math.floor(z / map.cell() * c);
    int w = c * map.plan().get(0).length(), h = c * map.plan().size();
    return px < o[0] || py < o[1] || px >= o[0] + w || py >= o[1] + h ? null : new int[] {px, py};
  }

  /** The unit vector a Minecraft yaw looks along on the ground plane (x, z): yaw 0 faces +z (south, down the picture), 90 faces -x. */
  static double[] facing(float yaw) {
    double r = Math.toRadians(yaw);
    return new double[] {-Math.sin(r), Math.cos(r)};
  }

  private static int cellColour(char ch, int cx, int cy) {
    return switch (ch) {
      case '#' -> WALL;
      case 'w' -> WATER;
      case 'A' -> SITE;
      case 'F' -> FEATURE;
      case 'R' -> ATTACK;
      case 'B' -> DEFEND;
      case 'h' -> HIGH;
      case 'c' -> CRATE;
      case 'g' -> FOLIAGE;
      default -> (cx + cy) % 2 == 0 ? FLOOR : FLOOR2;
    };
  }

  /** The base layer: 128 x 128 colours, row by row ({@link #CLEAR} outside the framed plan). */
  static int[] base(VbMap map) {
    int[] px = new int[SIZE * SIZE];
    java.util.Arrays.fill(px, CLEAR);
    int c = cellPx(map);
    int[] o = origin(map);
    int cols = map.plan().get(0).length(), rows = map.plan().size();
    rect(px, o[0] - 2, o[1] - 2, cols * c + 4, rows * c + 4, EDGE); // a 2 px dark frame round the plan
    for (int r = 0; r < rows; r++)
      for (int q = 0; q < cols; q++) {
        char ch = map.plan().get(r).charAt(q);
        for (int y = 0; y < c; y++)
          for (int x = 0; x < c; x++) {
            int colour = cellColour(ch, q, r);
            if (ch == '#' && (x == c - 1 || y == c - 1)) colour = WALL_LO; // a shaded edge so a wall reads as a block
            px[(o[1] + r * c + y) * SIZE + o[0] + q * c + x] = colour;
          }
      }
    List<VbSite> sites = map.sites();
    for (int i = 0; i < sites.size(); i++) {
      VbSite s = sites.get(i);
      int[] mid = project(map, (s.min()[0] + s.max()[0]) / 2, (s.min()[2] + s.max()[2]) / 2);
      if (mid == null) continue;
      for (int d = 0; d <= i; d++) rect(px, mid[0] - 2 + d * 3 - i, mid[1] - 1, 2, 2, DOT); // one dot for site A, two for site B
    }
    return px;
  }

  private static void rect(int[] px, int x, int y, int w, int h, int colour) {
    for (int yy = y; yy < y + h; yy++)
      for (int xx = x; xx < x + w; xx++) if (xx >= 0 && yy >= 0 && xx < SIZE && yy < SIZE) px[yy * SIZE + xx] = colour;
  }

  private static void set(int[] px, int x, int y, int colour) {
    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) px[y * SIZE + x] = colour;
  }

  /** Draws the markers over a copy of the base layer: you as a white square with a 3 px nose where you look, friends green (crossed red when dead), enemies red, the Seed a pink diamond. */
  static int[] draw(VbMap map, int[] base, List<Marker> markers) {
    int[] px = base.clone();
    for (Marker m : markers) {
      int[] p = project(map, m.x(), m.z());
      if (p == null) continue;
      switch (m.kind()) {
        case SEED -> {
          for (int d = -2; d <= 2; d++) {
            set(px, p[0] + d, p[1] + (2 - Math.abs(d)), SEED);
            set(px, p[0] + d, p[1] - (2 - Math.abs(d)), SEED);
          }
          set(px, p[0], p[1], DOT);
        }
        case DEAD -> {
          for (int d = -2; d <= 2; d++) {
            set(px, p[0] + d, p[1] + d, DEAD);
            set(px, p[0] + d, p[1] - d, DEAD);
          }
        }
        case ENEMY -> rect(px, p[0] - 1, p[1] - 1, 3, 3, ENEMY);
        case ALLY -> rect(px, p[0] - 1, p[1] - 1, 3, 3, ALLY);
        case SELF -> {
          double[] f = facing(m.yaw());
          for (int k = 2; k <= 4; k++) set(px, p[0] + (int) Math.round(f[0] * k), p[1] + (int) Math.round(f[1] * k), SELF);
          rect(px, p[0] - 1, p[1] - 1, 3, 3, SELF);
        }
      }
    }
    return px;
  }

  /** A cheap fingerprint of a marker list (the renderer only redraws when it changes). */
  static int fingerprint(List<Marker> markers) {
    int h = 1;
    for (Marker m : markers) {
      int[] p = {(int) Math.floor(m.x() * 2), (int) Math.floor(m.z() * 2)};
      h = 31 * h + m.kind().ordinal();
      h = 31 * h + p[0];
      h = 31 * h + p[1];
      if (m.kind() == Kind.SELF) h = 31 * h + Math.round(m.yaw() / 15f);
    }
    return h;
  }
}
