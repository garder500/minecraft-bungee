package win.vampyuyu.valoblock;

/**
 * The Citadel of Golden Sands around Hourfall, all of it beyond the invisible border (out of reach, so it never changes the play):
 * the island of layered sandstone the map stands on, a paved street then a rampart walk on every side, domed towers on the
 * corners, the City Gate behind the attackers, the Palace colonnade behind the defenders, the Obelisk behind A and the Scarab
 * pylon behind B. Above the Hourglass tower in mid, the giant hourglass and its gold ring are display models (the ValoBlock pack).
 * Pure: the blocks and models go into the canvas.
 */
final class HourfallScenery {
  private HourfallScenery() {}

  static final String SAND = "sand", SMOOTH = "smooth_sandstone", CUT = "cut_sandstone", CHISELED = "chiseled_sandstone", PLAIN = "sandstone";
  static final String GOLD = "gold_block", TURQ = "cyan_terracotta", LAPIS = "light_blue_terracotta";
  /** Rampart band: from this many blocks outside the border to {@link #WALL_OUT}. */
  static final int WALL_IN = 4, WALL_OUT = 8, WALL_TOP = 14;

  static void build(VbPlan p, VbCanvas c) {
    int w = p.width, d = p.depth, m = VbMap.SCENERY_MARGIN;
    SceneryKit.island(c, w, d, -2, HourfallScenery::strata);
    for (int x = -m; x < w + m; x++)
      for (int z = -m; z < d + m; z++) {
        int out = SceneryKit.outside(x, z, w, d);
        if (out <= 0) continue;
        if (out < WALL_IN || (out <= WALL_OUT && landmark(x, z, w, d))) c.set(x, -1, z, ((x + z) & 3) == 0 ? CUT : SMOOTH); // street
        else if (out <= WALL_OUT) rampart(c, x, z, w, d, out);
        else dune(c, x, z, w, d);
      }
    for (int[] t : new int[][] {{-9, -9}, {w + 8, -9}, {-9, d + 8}, {w + 8, d + 8}}) tower(c, t[0], t[1], 7, 24);
    for (int[] t : new int[][] {{-9, 86}, {w + 8, 86}}) tower(c, t[0], t[1], 5, 19);
    cityGate(c, w, d);
    palace(c, w);
    obelisk(c, -15, 34);
    pylon(c, w, 34);
    // Over the Hourglass tower: the hourglass hangs above the stone one, its ring turns around it (the tower top is at highY,
    // its sculpture reaches highY + 6: everything here is higher, out of every line of fire).
    double cx = w / 2.0, cz = d / 2.0;
    c.model("hourfall_hourglass", cx, 27, cz, 0, 7f, 3f);
    c.model("hourfall_ring", cx, 27, cz, 0, 8f, -12f);
    // The Scarab on the B pylon, facing the map (west).
    c.model("hourfall_scarab", w + 4.5, 25.5, 34, 90, 6f, 0f);
  }

  /** Where a landmark replaces the rampart: the Palace (north), the City Gate (south), the Scarab pylon (east, behind B). */
  private static boolean landmark(int x, int z, int w, int d) {
    return (z < -1 && Math.abs(x - w / 2) <= 26) || (z > d && Math.abs(x - w / 2) <= 22) || (x > w && Math.abs(z - 34) <= 13);
  }

  private static String strata(int x, int y, int z) {
    int band = Math.floorMod(y + (int) Math.round(SceneryKit.wave(x, z) * 1.2), 9);
    return switch (band) {
      case 0, 1 -> "orange_terracotta";
      case 2 -> "terracotta";
      case 3, 4 -> PLAIN;
      case 5 -> "smooth_red_sandstone";
      case 6 -> "red_sandstone";
      default -> SMOOTH;
    };
  }

  /** The rampart: a thick wall with a base course, a frieze, arrow slits and crenellations on both edges of the walk. */
  private static void rampart(VbCanvas c, int x, int z, int w, int d, int out) {
    c.set(x, -1, z, CUT);
    boolean inner = out == WALL_IN, outer = out == WALL_OUT;
    for (int y = 0; y < WALL_TOP; y++) {
      String b;
      if (y == 0) b = CUT;
      else if (y == 4 || y == 10) b = CHISELED;
      else if (inner && y >= 6 && y <= 7 && Math.floorMod(x + z, 7) == 0) b = "orange_stained_glass"; // slit windows
      else if (inner && y == 12) b = Math.floorMod(x + z, 6) == 0 ? GOLD : Math.floorMod(x + z, 3) == 0 ? TURQ : SMOOTH;
      else b = Math.floorMod(x * 7 + y * 3 + z * 5, 11) == 0 ? PLAIN : SMOOTH;
      c.set(x, y, z, b);
    }
    c.set(x, WALL_TOP, z, (inner || outer) ? CUT : SMOOTH); // the walk
    if ((inner || outer) && Math.floorMod(x + z, 2) == 0) c.set(x, WALL_TOP + 1, z, CHISELED);
    if (inner && Math.floorMod(x + z, 12) == 6) c.set(x, WALL_TOP + 2, z, "lantern");
  }

  /** Dunes beyond the rampart, falling towards the island rim. */
  private static void dune(VbCanvas c, int x, int z, int w, int d) {
    if (!SceneryKit.onIsland(x, z, w, d)) return;
    double fade = Math.max(0, 1 - SceneryKit.rim(x, z, w, d)) * 6;
    int h = (int) Math.round(Math.min(fade, 1.6 + SceneryKit.wave(x, z) * 1.3));
    c.set(x, -1, z, h <= 0 ? "smooth_sandstone" : SAND);
    for (int y = 0; y < h; y++) c.set(x, y, z, SAND);
  }

  /** A round tower with a band of windows, crenellations and a gold-tipped dome. */
  static void tower(VbCanvas c, int cx, int cz, int r, int top) {
    for (int dx = -r; dx <= r; dx++)
      for (int dz = -r; dz <= r; dz++) {
        double q = Math.hypot(dx, dz);
        if (q > r + 0.4) continue;
        boolean edge = q > r - 0.8;
        int x = cx + dx, z = cz + dz;
        c.set(x, -1, z, CUT);
        for (int y = 0; y <= top; y++) {
          if (!edge && y < top) continue; // hollow: only the shell and the roof
          String b = y == 0 || y == top ? CUT : y % 8 == 4 ? CHISELED : (y % 8 == 6 && Math.floorMod(dx * 3 + dz, 5) == 0) ? "orange_stained_glass" : SMOOTH;
          if (y == top - 2 && edge) b = Math.floorMod(dx + dz, 4) == 0 ? GOLD : TURQ;
          c.set(x, y, z, b);
        }
        if (edge && Math.floorMod(dx + dz, 2) == 0) c.set(x, top + 1, z, CHISELED);
      }
    // the dome
    int dr = r - 2;
    for (int y = 0; y <= dr; y++) {
      double rr = Math.sqrt(Math.max(0, dr * dr - y * y));
      for (int dx = -dr; dx <= dr; dx++)
        for (int dz = -dr; dz <= dr; dz++) {
          double q = Math.hypot(dx, dz);
          if (q <= rr + 0.3 && q > rr - 1.3) c.set(cx + dx, top + 1 + y, cz + dz, y >= dr - 1 ? GOLD : (y % 2 == 0 ? SMOOTH : CUT));
        }
    }
    for (int y = top + dr + 2; y <= top + dr + 4; y++) c.set(cx, y, cz, GOLD);
    c.set(cx, top + dr + 5, cz, "lightning_rod");
  }

  /** Behind the attackers' spawn (south): two great towers and a sealed arch with gold-studded doors and a sun disc above. */
  private static void cityGate(VbCanvas c, int w, int d) {
    int z0 = d + WALL_IN, z1 = d + 14, cx = w / 2;
    for (int side : new int[] {-1, 1}) {
      int x0 = cx + side * 12, x1 = cx + side * 22;
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
        for (int z = z0; z <= z1; z++) {
          for (int y = 0; y <= 28; y++) {
            boolean face = z == z0 || x == x0 || x == x1 || z == z1;
            if (!face && y < 28) continue;
            String b = y % 9 == 0 ? CHISELED : y == 24 ? (Math.floorMod(x, 3) == 0 ? GOLD : TURQ) : (y >= 15 && y <= 17 && face && Math.floorMod(x, 4) == 2) ? "orange_stained_glass" : SMOOTH;
            c.set(x, y, z, b);
          }
          if ((x + z) % 2 == 0 && (z == z0 || z == z1 || x == x0 || x == x1)) c.set(x, 29, z, CHISELED);
        }
    }
    // The arch between them: a thick wall with a semicircular sealed doorway.
    for (int x = cx - 11; x <= cx + 11; x++)
      for (int z = z0; z <= z0 + 4; z++)
        for (int y = 0; y <= 23; y++) {
          double r = Math.hypot(x + 0.5 - (cx + 0.5), Math.max(0, y - 9));
          boolean opening = Math.abs(x + 0.5 - (cx + 0.5)) < 7 && (y <= 9 || r < 7);
          if (opening) {
            if (z == z0 + 2) c.set(x, y, z, (Math.floorMod(x, 3) == 0 && y % 3 == 1) ? GOLD : "stripped_dark_oak_wood");
            continue;
          }
          boolean rimStone = Math.abs(x + 0.5 - (cx + 0.5)) < 8.2 && (y <= 9 || r < 8.2);
          c.set(x, y, z, rimStone ? CHISELED : y == 20 ? TURQ : y == 21 ? GOLD : SMOOTH);
        }
    // the sun disc over the arch
    for (int dx = -3; dx <= 3; dx++)
      for (int dy = -3; dy <= 3; dy++)
        if (Math.hypot(dx, dy) < 3.4) c.set(cx + dx, 27 + dy, z0, Math.hypot(dx, dy) < 2 ? "shroomlight" : GOLD);
  }

  /** Behind the defenders' spawn (north): a tall colonnade under an entablature with a gold band and a glowing lunette. */
  private static void palace(VbCanvas c, int w) {
    int cx = w / 2, zf = -1 - WALL_IN, zb = -1 - 12;
    for (int x = cx - 26; x <= cx + 26; x++) {
      // back wall
      for (int z = zb; z <= zb + 2; z++)
        for (int y = 0; y <= 22; y++) c.set(x, y, z, y % 7 == 0 ? CHISELED : SMOOTH);
      // floor of the portico
      for (int z = zb + 3; z <= zf; z++) c.set(x, -1, z, ((x + z) & 1) == 0 ? CUT : "smooth_quartz");
      // columns every four blocks
      if (Math.floorMod(x - cx, 4) == 0)
        for (int z = zf - 1; z <= zf; z++)
          for (int y = 0; y <= 16; y++) c.set(x, y, z, y == 0 || y == 16 ? CHISELED : y == 15 ? GOLD : CUT);
      // entablature
      for (int z = zb + 3; z <= zf; z++)
        for (int y = 17; y <= 20; y++) c.set(x, y, z, y == 18 ? (Math.floorMod(x, 4) == 0 ? GOLD : TURQ) : y == 20 ? CHISELED : SMOOTH);
      // stepped pediment
      int h = 26 - Math.abs(x - cx) / 3;
      for (int y = 21; y <= h; y++) c.set(x, y, zf, y == h ? CHISELED : SMOOTH);
      if (Math.abs(x - cx) % 6 == 3) c.set(x, 14, zf - 1, "lantern[hanging=true]");
    }
    // the lunette: a glowing half disc of amber glass in the pediment
    for (int dx = -5; dx <= 5; dx++)
      for (int dy = 0; dy <= 5; dy++) {
        double q = Math.hypot(dx, dy);
        if (q < 5.4) c.set(cx + dx, 21 + dy, zf, q < 4.3 ? (dy == 0 ? "shroomlight" : "orange_stained_glass") : GOLD);
      }
  }

  /** The Obelisk behind A: a tapering needle on a stepped plinth, a gold pyramidion, hour lines cut in its faces. */
  private static void obelisk(VbCanvas c, int cx, int cz) {
    for (int step = 0; step < 3; step++) {
      int r = 6 - step;
      for (int dx = -r; dx <= r; dx++)
        for (int dz = -r; dz <= r; dz++) c.set(cx + dx, step, cz + dz, step == 2 ? CHISELED : CUT);
    }
    int y = 3;
    int[][] tiers = {{3, 14}, {2, 14}, {1, 12}};
    for (int[] t : tiers)
      for (int k = 0; k < t[1]; k++, y++)
        for (int dx = -t[0]; dx <= t[0]; dx++)
          for (int dz = -t[0]; dz <= t[0]; dz++) {
            boolean faceMid = (Math.abs(dx) == t[0] && dz == 0) || (Math.abs(dz) == t[0] && dx == 0);
            c.set(cx + dx, y, cz + dz, faceMid && y % 4 == 0 ? TURQ : y % 10 == 0 ? CHISELED : SMOOTH);
          }
    for (int k = 0; k < 3; k++) c.set(cx, y + k, cz, GOLD);
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = -1; dz <= 1; dz++) c.set(cx + dx, y, cz + dz, GOLD);
    c.set(cx, y + 3, cz, "lightning_rod");
  }

  /** The Scarab pylon behind B: two battered towers and a lintel across, the scarab model sits on it (facing the map). */
  private static void pylon(VbCanvas c, int w, int cz) {
    int x0 = w + WALL_IN, x1 = w + 14;
    for (int side : new int[] {-1, 1}) {
      int za = cz + side * 4, zb = cz + side * 13;
      for (int y = 0; y <= 30; y++) {
        int in = y / 6; // the batter: the faces lean in one block every six
        for (int x = x0 + in; x <= x1 - in; x++)
          for (int z = Math.min(za, zb) + (side > 0 ? 0 : in); z <= Math.max(za, zb) - (side > 0 ? in : 0); z++) {
            boolean face = x == x0 + in || x == x1 - in || z == Math.min(za, zb) + (side > 0 ? 0 : in) || z == Math.max(za, zb) - (side > 0 ? in : 0);
            if (!face && y < 30) continue;
            String b = y == 30 ? CHISELED : y % 6 == 5 ? (Math.floorMod(z, 3) == 0 ? GOLD : TURQ) : (x == x0 + in && y > 8 && y < 26 && Math.floorMod(z - cz, 4) == 0) ? LAPIS : SMOOTH;
            c.set(x, y, z, b);
          }
      }
    }
    // the gateway between the towers (sealed) and the lintel the scarab stands on
    for (int z = cz - 3; z <= cz + 3; z++) {
      for (int y = 0; y <= 18; y++) c.set(x0 + 1, y, z, y <= 12 ? "stripped_dark_oak_wood" : SMOOTH);
      for (int x = x0; x <= x0 + 4; x++)
        for (int y = 19; y <= 22; y++) c.set(x, y, z, y == 20 ? GOLD : CHISELED);
    }
    for (int z = cz - 3; z <= cz + 3; z += 3) c.set(x0 - 1, 18, z, "lantern[hanging=true]");
  }
}
