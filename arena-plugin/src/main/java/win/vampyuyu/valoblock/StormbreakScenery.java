package win.vampyuyu.valoblock;

/**
 * The Tempestuous Archipelago around Stormbreak, all of it beyond the invisible border or high above the play (out of reach, so
 * it never changes the play): the sea runs on past the border to a ring of cliffs on the rim of a floating rock basin, sea
 * stacks rise from it, the great Lighthouse stands on its own rock behind A with its sweeping beam (a display model), storm
 * clouds hang over everything (only above {@link SceneryKit#ABOVE_PLAY} over the map), the broken mast of the Wreck rises over
 * B and a storm core crackles over the Eyrie in the middle (display models). Inside the border the sea is the theme's own and
 * is never touched here. Pure.
 */
final class StormbreakScenery {
  private StormbreakScenery() {}

  /** The great Lighthouse, behind site A (west). */
  static final int LIGHT_X = -15, LIGHT_Z = 37;
  /** Where the sea ends and the cliffs of the basin begin (rim metric). */
  static final double CLIFF = 0.92;

  static void build(VbPlan p, VbCanvas c) {
    int w = p.width, d = p.depth, m = VbMap.SCENERY_MARGIN;
    SceneryKit.Keep keep = (x, y, z) -> SceneryKit.clear(x, y, z, w, d);
    SceneryKit.island(c, w, d, -6, StormbreakScenery::rock);
    for (int x = -m; x < w + m; x++)
      for (int z = -m; z < d + m; z++) {
        if (!SceneryKit.onIsland(x, z, w, d) || SceneryKit.outside(x, z, w, d) <= 0) continue;
        double r = SceneryKit.rim(x, z, w, d);
        if (r < CLIFF) sea(c, x, z);
        else cliff(c, x, z, w, d, r);
      }
    // sea stacks on a jittered grid, clear of the border and of the Lighthouse rock
    for (int gx = -m; gx < w + m; gx += 13)
      for (int gz = -m; gz < d + m; gz += 13) {
        int x = gx + SceneryKit.hash(gx, 1, gz, 7) - 3, z = gz + SceneryKit.hash(gx, 2, gz, 7) - 3;
        int out = SceneryKit.outside(x, z, w, d);
        if (out < 7 || SceneryKit.rim(x, z, w, d) > CLIFF - 0.03 || SceneryKit.hash(x, 3, z, 3) == 0) continue;
        if (Math.hypot(x - LIGHT_X, z - LIGHT_Z) < 16) continue;
        stack(c, x, z, 2 + SceneryKit.hash(x, 4, z, 3), 6 + SceneryKit.hash(x, 5, z, 20), keep);
      }
    lighthouse(c, keep);
    int[][] clouds = {{-10, -12}, {w / 2, -18}, {w + 12, 20}, {w + 8, d - 10}, {w / 2 - 20, d + 16}, {-14, d - 30}, {w / 2 + 10, d / 2}};
    for (int[] cl : clouds) cloud(c, cl[0], 46 + SceneryKit.hash(cl[0], 0, cl[1], 7), cl[1], 9 + SceneryKit.hash(cl[0], 1, cl[1], 5), keep);
    // the beam turns in the lamp room; the mast rises over the Wreck (B); the storm core over the Eyrie's rod (mid)
    c.model("stormbreak_beam", LIGHT_X + 0.5, 42, LIGHT_Z + 0.5, 0, 6f, 36f);
    c.model("stormbreak_mast", 127.5, 18, 37.5, 25, 6f, 0f);
    c.model("stormbreak_stormcore", 75, 19, 62.5, 0, 5f, 20f);
  }

  private static String rock(int x, int y, int z) {
    int band = Math.floorMod(y + (int) Math.round(SceneryKit.wave(x, z) * 1.3), 9);
    return switch (band) {
      case 0 -> "calcite";
      case 1, 2 -> "tuff";
      case 3 -> "deepslate";
      case 4 -> "smooth_basalt";
      case 5, 6 -> "cobbled_deepslate";
      default -> "deepslate_tiles";
    };
  }

  /** Rock of a sea stack: dark and mottled, no pale bands (striped pillars read as more lighthouses). */
  private static String stackRock(int x, int y, int z) {
    int h = SceneryKit.hash(x, y, z, 10);
    return h < 4 ? "deepslate" : h < 6 ? "tuff" : h < 8 ? "cobbled_deepslate" : h < 9 ? "smooth_basalt" : "mossy_cobblestone";
  }

  /** The sea past the border: the theme's water layers over a tuff floor, the same depth as inside. */
  private static void sea(VbCanvas c, int x, int z) {
    c.set(x, -5, z, SceneryKit.hash(x, 0, z, 5) == 0 ? "gravel" : "tuff");
    for (int y = -4; y <= -2; y++) c.set(x, y, z, "water");
  }

  /** The cliffs on the rim: rising with the rim, a shell (only the faces and the top layers). */
  private static void cliff(VbCanvas c, int x, int z, int w, int d, double r) {
    int top = cliffTop(x, z, w, d, r);
    boolean face = false;
    for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
      int nx = x + n[0], nz = z + n[1];
      if (!SceneryKit.onIsland(nx, nz, w, d) || SceneryKit.rim(nx, nz, w, d) < CLIFF || cliffTop(nx, nz, w, d, SceneryKit.rim(nx, nz, w, d)) < top) face = true;
    }
    for (int y = face ? -5 : top - 2; y <= top; y++) c.set(x, y, z, y == top && SceneryKit.hash(x, y, z, 4) == 0 ? "moss_block" : rock(x, y, z));
  }

  private static int cliffTop(int x, int z, int w, int d, double r) {
    int top = (int) Math.round(-2 + (r - CLIFF) / (1 - CLIFF) * 12 + SceneryKit.wave(x, z) * 2.2);
    return SceneryKit.outside(x, z, w, d) < 3 ? Math.min(top, -2) : top; // near the corners of the map: no rock on the border
  }

  /** A sea stack: a ragged pillar of banded rock, foam at its foot, sometimes a moss cap. */
  private static void stack(VbCanvas c, int cx, int cz, int r, int h, SceneryKit.Keep keep) {
    for (int y = -5; y <= h; y++) {
      double rr = r + (y < 0 ? 1 : 0) - (y > h - 3 ? 1 : 0) + SceneryKit.wave(cx + y, cz) * 0.3;
      for (int dx = -r - 1; dx <= r + 1; dx++)
        for (int dz = -r - 1; dz <= r + 1; dz++) {
          double q = Math.hypot(dx, dz);
          if (q > rr + 0.3 || (q < rr - 1.2 && y < h)) continue;
          int x = cx + dx, z = cz + dz;
          if (keep.ok(x, y, z)) c.set(x, y, z, y == h && SceneryKit.hash(x, y, z, 3) == 0 ? "moss_block" : stackRock(x, y, z));
        }
    }
    for (int dx = -r - 2; dx <= r + 2; dx++) // foam ring on the water
      for (int dz = -r - 2; dz <= r + 2; dz++) {
        double q = Math.hypot(dx, dz);
        if (q > r + 1.3 && q <= r + 2.3 && SceneryKit.hash(cx + dx, 0, cz + dz, 2) == 0 && keep.ok(cx + dx, -2, cz + dz)) c.set(cx + dx, -2, cz + dz, "white_concrete_powder");
      }
  }

  /** The great Lighthouse on its rock: banded stone, a copper gallery, and on it the lamp room with its beam (a display model). */
  private static void lighthouse(VbCanvas c, SceneryKit.Keep keep) {
    for (int dx = -9; dx <= 9; dx++) // the rock
      for (int dz = -9; dz <= 9; dz++) {
        double q = Math.hypot(dx, dz) + SceneryKit.wave(dx * 3, dz * 3) * 0.6;
        if (q > 8.5) continue;
        int top = (int) Math.round(3 - q * 0.5);
        for (int y = -5; y <= top; y++) if (keep.ok(LIGHT_X + dx, y, LIGHT_Z + dz)) c.set(LIGHT_X + dx, y, LIGHT_Z + dz, rock(LIGHT_X + dx, y, LIGHT_Z + dz));
      }
    for (int y = 3; y <= 35; y++) { // the tower, tapering from radius 5 to 3
      double r = 5 - (y - 3) / 16.0;
      for (int dx = -5; dx <= 5; dx++)
        for (int dz = -5; dz <= 5; dz++) {
          double q = Math.hypot(dx, dz);
          if (q > r + 0.3 || q < r - 1.2) continue;
          int x = LIGHT_X + dx, z = LIGHT_Z + dz;
          String b = y % 8 < 4 ? "calcite" : "polished_deepslate";
          if (y % 8 == 0) b = "oxidized_cut_copper";
          if (y % 6 == 2 && Math.floorMod(dx + dz + y, 7) == 0) b = "blue_stained_glass"; // a spiral of windows
          if (keep.ok(x, y, z)) c.set(x, y, z, b);
        }
    }
    for (int dx = -6; dx <= 6; dx++) // the gallery
      for (int dz = -6; dz <= 6; dz++) {
        double q = Math.hypot(dx, dz);
        int x = LIGHT_X + dx, z = LIGHT_Z + dz;
        if (q <= 6.3 && keep.ok(x, 35, z)) c.set(x, 35, z, "oxidized_cut_copper");
        if (q > 5.4 && q <= 6.3 && keep.ok(x, 36, z)) c.set(x, 36, z, "waxed_oxidized_copper_grate");
      }
    c.set(LIGHT_X, 35, LIGHT_Z, "sea_lantern"); // the lamp room itself is the beam model, standing on the gallery
  }

  /** A storm cloud: a cluster of puffs, pale on top and grey underneath, ragged at the edges. */
  private static void cloud(VbCanvas c, int cx, int cy, int cz, int r, SceneryKit.Keep keep) {
    SceneryKit.Strata puff = (x, y, z) -> y >= cy + 1 ? "white_wool" : y >= cy - 1 ? "light_gray_wool" : SceneryKit.hash(x, y, z, 3) == 0 ? "gray_wool" : "light_gray_wool";
    int n = 5 + SceneryKit.hash(cx, 2, cz, 3);
    for (int k = 0; k < n; k++) {
      double a = k * 2.4 + SceneryKit.hash(cx, k, cz, 6) * 0.3;
      double dist = k == 0 ? 0 : r * (0.45 + 0.1 * SceneryKit.hash(cx, k + 9, cz, 4));
      int px = cx + (int) Math.round(Math.cos(a) * dist), pz = cz + (int) Math.round(Math.sin(a) * dist * 0.7);
      double pr = r * (k == 0 ? 0.75 : 0.45 + 0.08 * SceneryKit.hash(cx, k + 3, cz, 4));
      SceneryKit.blob(c, px, cy + (k == 0 ? 1 : SceneryKit.hash(cx, k + 5, cz, 3) - 1), pz, pr, pr * 0.55, puff, keep);
    }
  }
}
