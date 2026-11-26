package win.vampyuyu.valoblock;

/**
 * The Whispering Grove around Rootvault, all of it beyond the invisible border or high above the play (out of reach, so it never
 * changes the play): an island of earth and moss with roots hanging under it, a hedge of roots and moss along the border, a
 * forest of giant trunks with glowing crowns, the World Tree behind A whose crown spreads over the west of the map (only above
 * {@link SceneryKit#ABOVE_PLAY}), and the giant mushrooms behind B. Over the middle, the Heartseed turns (a display model);
 * behind B stands the Sporebloom (a display model). Pure.
 */
final class RootvaultScenery {
  private RootvaultScenery() {}

  static final String BARK = "dark_oak_wood", MOSS = "moss_block", ROOTS = "mangrove_roots";
  static final String LEAVES = "dark_oak_leaves[persistent=true]", AZALEA = "azalea_leaves[persistent=true]", BLOOM = "flowering_azalea_leaves[persistent=true]";
  /** The World Tree, behind site A (west). */
  static final int TREE_X = -16, TREE_Z = 32;

  static void build(VbPlan p, VbCanvas c) {
    int w = p.width, d = p.depth, m = VbMap.SCENERY_MARGIN;
    SceneryKit.Keep keep = (x, y, z) -> SceneryKit.clear(x, y, z, w, d);
    SceneryKit.island(c, w, d, -2, RootvaultScenery::earth);
    for (int x = -m; x < w + m; x++)
      for (int z = -m; z < d + m; z++) {
        if (!SceneryKit.onIsland(x, z, w, d)) continue;
        hangingRoots(c, x, z, w, d);
        int out = SceneryKit.outside(x, z, w, d);
        if (out <= 0) continue;
        ground(c, x, z, out);
        if (out >= 3 && out <= 7) hedge(c, x, z, out);
      }
    // the forest: trunks on a jittered grid, far enough out that the crowns stay clear of the border
    for (int gx = -m; gx < w + m; gx += 12)
      for (int gz = -m; gz < d + m; gz += 12) {
        int x = gx + SceneryKit.hash(gx, 1, gz, 7) - 3, z = gz + SceneryKit.hash(gx, 2, gz, 7) - 3;
        int out = SceneryKit.outside(x, z, w, d);
        if (out < 13 || !SceneryKit.onIsland(x, z, w, d) || SceneryKit.rim(x, z, w, d) > 0.93) continue;
        if (x < -1 && Math.abs(z - TREE_Z) < 30) continue; // the World Tree's place
        if (x > w && Math.abs(z - 32) < 18) continue; // the Sporebloom's place
        int r = 2 + SceneryKit.hash(x, 3, z, 3), h = 22 + SceneryKit.hash(x, 4, z, 20);
        tree(c, x, z, r, h, 5 + r, keep);
      }
    worldTree(c, keep);
    for (int[] mu : new int[][] {{w + 8, 12, 9, 5}, {w + 9, 54, 12, 6}, {w + 20, 46, 7, 4}, {w + 19, 16, 13, 5}}) mushroom(c, mu[0], mu[1], mu[2], mu[3], keep);
    // Over the middle the Heartseed turns (the canopy deck is at highY: it hangs well above it); behind B the Sporebloom.
    c.model("rootvault_heartseed", w / 2.0, 21, d / 2.0, 0, 6f, 6f);
    c.model("rootvault_sporebloom", w + 14.5, 15, 32, 0, 7f, 0f);
  }

  private static String earth(int x, int y, int z) {
    int band = Math.floorMod(y + (int) Math.round(SceneryKit.wave(x, z) * 1.5), 8);
    return switch (band) {
      case 0, 1 -> "rooted_dirt";
      case 2 -> "coarse_dirt";
      case 3 -> "packed_mud";
      case 4 -> MOSS;
      case 5 -> "mud_bricks";
      default -> "dirt";
    };
  }

  /** Roots hanging under the island, longer towards the middle. */
  private static void hangingRoots(VbCanvas c, int x, int z, int w, int d) {
    if (SceneryKit.hash(x, 7, z, 6) != 0) return;
    int bottom = SceneryKit.keel(x, z, w, d, -2);
    int len = 2 + SceneryKit.hash(x, 8, z, 7);
    for (int i = 1; i <= len && bottom - i > VbMap.SCENERY_LOW_Y; i++) c.set(x, bottom - i, z, i == len ? "hanging_roots" : ROOTS);
  }

  /** Mossy path along the border, then forest floor with ferns, grass and azalea bushes. */
  private static void ground(VbCanvas c, int x, int z, int out) {
    int h = SceneryKit.hash(x, 0, z, 10);
    if (out < 3) {
      c.set(x, -1, z, h < 6 ? MOSS : h < 8 ? "rooted_dirt" : "coarse_dirt");
      return;
    }
    c.set(x, -1, z, h < 5 ? MOSS : h < 8 ? "grass_block" : "podzol");
    int deco = SceneryKit.hash(x, 9, z, 14);
    if (deco == 0) c.set(x, 0, z, "fern");
    else if (deco == 1) c.set(x, 0, z, "short_grass");
    else if (deco == 2) c.set(x, 0, z, "moss_carpet");
    else if (deco == 3) c.set(x, 0, z, "azalea");
    else if (deco == 4) c.set(x, 0, z, "flowering_azalea");
  }

  /** A low wall of roots, moss and leaves along the border: the forest starts right behind it. */
  private static void hedge(VbCanvas c, int x, int z, int out) {
    int top = (int) Math.round(3 + SceneryKit.wave(x * 2, z * 2) * 1.2 - Math.abs(out - 5) * 0.8);
    for (int y = 0; y < top; y++) {
      int hh = SceneryKit.hash(x, y, z, 7);
      c.set(x, y, z, y == top - 1 ? (hh < 2 ? BLOOM : AZALEA) : hh < 3 ? ROOTS : MOSS);
    }
  }

  private static String crown(int x, int y, int z) {
    int h = SceneryKit.hash(x, y, z, 48);
    if (h == 0) return "shroomlight";
    return h < 18 ? LEAVES : h < 36 ? AZALEA : BLOOM;
  }

  /** A giant trunk with flared buttress roots, a few rising branches and a round crown with lights and hanging fruit. */
  static void tree(VbCanvas c, int cx, int cz, int r, int h, int cr, SceneryKit.Keep keep) {
    for (int y = 0; y <= h; y++) {
      double rr = r + (y < 4 ? (4 - y) * 0.7 : 0);
      for (int dx = (int) -Math.ceil(rr); dx <= Math.ceil(rr); dx++)
        for (int dz = (int) -Math.ceil(rr); dz <= Math.ceil(rr); dz++) {
          double q = Math.hypot(dx, dz);
          if (q > rr + 0.3 || q < rr - 1.2) continue; // the shell only
          int x = cx + dx, z = cz + dz;
          if (keep.ok(x, y, z)) c.set(x, y, z, SceneryKit.hash(x, y, z, 9) == 0 ? MOSS : BARK);
        }
    }
    for (int k = 0; k < 5; k++) { // buttress roots
      double a = k * Math.PI * 2 / 5 + SceneryKit.hash(cx, k, cz, 10) * 0.2;
      for (int s = r; s <= r + 4 + r; s++) {
        int x = cx + (int) Math.round(Math.cos(a) * s), z = cz + (int) Math.round(Math.sin(a) * s);
        int top = Math.max(0, 4 - (s - r));
        for (int y = 0; y <= top; y++) if (keep.ok(x, y, z)) c.set(x, y, z, top == 0 ? ROOTS : BARK);
      }
    }
    if (cr <= 0) return; // a trunk only: the caller makes its own crown
    for (int k = 0; k < 3; k++) { // branches
      double a = k * Math.PI * 2 / 3 + 0.5;
      for (int s = 0; s <= cr - 1; s++) {
        int x = cx + (int) Math.round(Math.cos(a) * s), z = cz + (int) Math.round(Math.sin(a) * s), y = h - 4 + s / 2;
        if (keep.ok(x, y, z)) c.set(x, y, z, BARK);
      }
    }
    int top = h + (int) (cr * 0.4);
    SceneryKit.blob(c, cx, top, cz, cr, cr * 0.65, RootvaultScenery::crown, keep);
    for (int k = 0; k < 6; k++) { // glowing fruit under the crown
      double a = k * Math.PI / 3 + 0.3;
      int x = cx + (int) Math.round(Math.cos(a) * cr * 0.6), z = cz + (int) Math.round(Math.sin(a) * cr * 0.6), y = top - (int) (cr * 0.5);
      if (keep.ok(x, y, z) && keep.ok(x, y - 1, z)) {
        c.set(x, y, z, "iron_chain");
        c.set(x, y - 1, z, k % 2 == 0 ? "verdant_froglight" : "ochre_froglight");
      }
    }
  }

  /** The World Tree behind A: a huge hollow trunk, roots like ridges, four great boughs and a crown that roofs the sky. */
  private static void worldTree(VbCanvas c, SceneryKit.Keep keep) {
    tree(c, TREE_X, TREE_Z, 7, 44, 0, keep);
    for (int k = 0; k < 4; k++) { // boughs out over the crown's reach
      double a = k * Math.PI / 2 + 0.4;
      for (int s = 6; s <= 22; s++) {
        int x = TREE_X + (int) Math.round(Math.cos(a) * s), z = TREE_Z + (int) Math.round(Math.sin(a) * s), y = 36 + s / 3;
        for (int t = 0; t <= 1; t++) if (keep.ok(x, y + t, z)) c.set(x, y + t, z, BARK);
      }
    }
    SceneryKit.blob(c, TREE_X + 4, 49, TREE_Z, 27, 8, RootvaultScenery::crown, keep);
    SceneryKit.blob(c, TREE_X - 2, 53, TREE_Z + 4, 15, 4, RootvaultScenery::crown, keep);
    // lanterns of light hanging from the crown over the west of the map
    for (int k = 0; k < 14; k++) {
      int x = TREE_X + 4 + (int) Math.round(Math.cos(k * 0.9) * (8 + k)), z = TREE_Z + (int) Math.round(Math.sin(k * 0.9) * (8 + k));
      for (int y = 37; y <= 41; y++) if (keep.ok(x, y, z)) c.set(x, y, z, y == 37 ? (k % 2 == 0 ? "verdant_froglight" : "shroomlight") : "iron_chain");
    }
  }

  /** A giant mushroom: a pale stem and a wide cap with lights under it. */
  private static void mushroom(VbCanvas c, int cx, int cz, int h, int r, SceneryKit.Keep keep) {
    for (int y = 0; y < h; y++)
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++) if (keep.ok(cx + dx, y, cz + dz)) c.set(cx + dx, y, cz + dz, "mushroom_stem");
    String cap = SceneryKit.hash(cx, 0, cz, 2) == 0 ? "red_mushroom_block" : "brown_mushroom_block";
    for (int dx = -r; dx <= r; dx++)
      for (int dz = -r; dz <= r; dz++) {
        double q = Math.hypot(dx, dz);
        if (q > r + 0.4) continue;
        int y = h + (q < r - 1.5 ? 1 : 0);
        int x = cx + dx, z = cz + dz;
        if (keep.ok(x, y, z)) c.set(x, y, z, cap);
        if (q > r - 1 && keep.ok(x, h - 1, z)) c.set(x, h - 1, z, cap);
        if (q < r - 1.5 && SceneryKit.hash(x, h, z, 5) == 0 && keep.ok(x, h - 1, z)) c.set(x, h - 1, z, "shroomlight");
      }
  }
}
