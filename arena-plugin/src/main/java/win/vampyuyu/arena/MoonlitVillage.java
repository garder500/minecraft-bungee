package win.vampyuyu.arena;

import static org.bukkit.Material.*;

import org.bukkit.Material;

/**
 * The Werewolf waiting hub in ww_sky: the Moonlit Village, a round floating island (radius {@value #R}) with a rocky,
 * root-hung underside. In the middle the council circle: twenty stumps around a bonfire, under a ring of rune stones
 * (a display model). Around it a great leaning tree hung with lanterns, a ruined bell tower with its bell and a wolf
 * weathervane, a howling wolf statue on a rock (display model), three lit cottages, standing stones and lamp posts.
 * Waiting players see it at midnight ({@link WaitingHubs#joined}). Coordinates: see {@link HubPlan}.
 */
final class MoonlitVillage {
  static final int R = 18;
  /** The walkable disc ends at the fence ring at this radius. */
  static final double FENCE = 15.0;
  static final double SPAWN_X = 0.5, SPAWN_Z = 12.5;
  static final int TREE_X = -10, TREE_Z = -7;
  static final int TOWER_X = 9, TOWER_Z = -8;
  static final int ROCK_X = 0, ROCK_Z = -15;
  static final int[][] COTTAGES = {{-10, 9}, {10, 9}, {-13, 0}};

  private MoonlitVillage() {}

  static double dist(int x, int z) {
    return Math.hypot(x, z);
  }

  /** The island's edge radius in a direction, a little ragged. */
  static double edge(int x, int z) {
    double a = Math.atan2(z, x);
    return R + 0.6 * Math.sin(a * 5) + 0.5 * Math.sin(a * 9 + 1.3);
  }

  static boolean island(int x, int z) {
    return dist(x, z) <= edge(x, z);
  }

  static HubPlan plan() {
    HubPlan p = new HubPlan();
    terrain(p);
    paths(p);
    council(p);
    tree(p);
    tower(p);
    rock(p);
    for (int[] c : COTTAGES) cottage(p, c[0], c[1]);
    standingStones(p);
    lampPosts(p);
    fenceRing(p);
    plants(p);
    hangings(p);
    lights(p);
    return p;
  }

  private static void terrain(HubPlan p) {
    for (int x = -R - 2; x <= R + 2; x++) {
      for (int z = -R - 2; z <= R + 2; z++) {
        if (!island(x, z)) continue;
        double d = dist(x, z);
        double n = HubPlan.noise(x, z, 7);
        p.set(x, 0, z, GRASS_BLOCK);
        // rim hills beyond the fence
        if (d > FENCE + 0.5) {
          int h = (int) Math.round(1 + (d - FENCE) * 0.5 + n * 1.5);
          if (d > edge(x, z) - 1.2) h = Math.max(0, h - 1);
          for (int y = 0; y < h; y++) p.set(x, y, z, y == 0 ? DIRT : n < 0.4 ? MOSSY_COBBLESTONE : STONE);
          p.set(x, h, z, GRASS_BLOCK);
        }
        // underside: deeper toward the middle, ragged
        int depth = (int) Math.min(22, Math.max(2, (edge(x, z) - d) * 1.25 + n * 3));
        for (int y = -1; y >= -depth; y--) {
          Material m;
          double m2 = HubPlan.noise(x * 3 + y, z, 11);
          if (y >= -2) m = m2 < 0.2 ? ROOTED_DIRT : DIRT;
          else if (y == -depth) m = m2 < 0.5 ? COBBLESTONE : MOSSY_COBBLESTONE;
          else m = m2 < 0.35 ? STONE : m2 < 0.6 ? ANDESITE : m2 < 0.8 ? TUFF : COBBLESTONE;
          p.set(x, y, z, m);
        }
        if (depth <= 4 && n < 0.45) p.set(x, -depth - 1, z, HANGING_ROOTS);
      }
    }
  }

  /** The distance from a cell to a segment. */
  private static double seg(double x, double z, double ax, double az, double bx, double bz) {
    double dx = bx - ax, dz = bz - az;
    double t = Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)));
    return Math.hypot(x - (ax + t * dx), z - (az + t * dz));
  }

  private static void paths(HubPlan p) {
    double[][] to = {
      {0, 14}, {COTTAGES[0][0] + 3, COTTAGES[0][1]}, {COTTAGES[1][0] - 3, COTTAGES[1][1]},
      {COTTAGES[2][0] + 3, COTTAGES[2][1]}, {TOWER_X, TOWER_Z + 3}, {ROCK_X, ROCK_Z + 3}, {13, -1}
    };
    for (int x = -R; x <= R; x++) {
      for (int z = -R; z <= R; z++) {
        if (dist(x, z) > FENCE) continue;
        for (double[] t : to) {
          double w = 0.9 + 0.4 * HubPlan.noise(x, z, 3);
          if (seg(x, z, 0, 0, t[0], t[1]) <= w && dist(x, z) > 7.5) p.set(x, 0, z, DIRT_PATH);
        }
      }
    }
  }

  private static void council(HubPlan p) {
    for (int x = -8; x <= 8; x++) {
      for (int z = -8; z <= 8; z++) {
        double d = dist(x, z);
        double n = HubPlan.noise(x, z, 5);
        if (d <= 3.5) p.set(x, 0, z, n < 0.5 ? COBBLESTONE : MOSSY_COBBLESTONE);
        else if (d > 5.0 && d <= 7.5) p.set(x, 0, z, n < 0.55 ? COARSE_DIRT : PACKED_MUD);
      }
    }
    for (int i = 0; i < 20; i++) {
      double a = Math.toRadians(i * 18 + 9);
      int x = (int) Math.round(6.4 * Math.cos(a)), z = (int) Math.round(6.4 * Math.sin(a));
      if (x == 0 && z > 0) continue; // the way in from the spawn
      p.set(x, 1, z, i % 2 == 0 ? SPRUCE_LOG : STRIPPED_SPRUCE_LOG);
    }
    p.set(0, 1, 0, CAMPFIRE);
    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}})
      p.set(d[0], 1, d[1], MOSSY_STONE_BRICK_SLAB);
  }

  private static void leaves(HubPlan p, int cx, int cy, int cz, double r, int seed) {
    int ri = (int) Math.ceil(r);
    for (int x = -ri; x <= ri; x++)
      for (int y = -ri + 1; y <= ri - 1; y++)
        for (int z = -ri; z <= ri; z++) {
          double d = Math.sqrt(x * x + y * y * 1.8 + z * z);
          if (d <= r - HubPlan.noise(cx + x, cz + z + y * 31, seed) * 1.2)
            if (p.at(cx + x, cy + y, cz + z) == null)
              p.set(cx + x, cy + y, cz + z, DARK_OAK_LEAVES, "[persistent=true]");
        }
  }

  private static void tree(HubPlan p) {
    int tx = TREE_X, tz = TREE_Z;
    for (int y = 1; y <= 19; y++) {
      int lean = y > 7 ? 1 : 0;
      for (int dx = 0; dx <= 1; dx++)
        for (int dz = 0; dz <= 1; dz++) p.set(tx + dx - lean, y, tz + dz - lean, DARK_OAK_LOG);
    }
    // root flare
    int[][] flare = {{-1, 0}, {-1, 1}, {2, 0}, {2, 1}, {0, -1}, {1, -1}, {0, 2}, {1, 2}, {-2, 1}, {3, 0}};
    for (int[] f : flare) p.set(tx + f[0], 1, tz + f[1], DARK_OAK_WOOD);
    p.set(tx - 1, 2, tz + 1, DARK_OAK_WOOD);
    p.set(tx + 2, 2, tz, DARK_OAK_WOOD);
    // four branches climbing out, each ending in a leaf crown with a lantern under it
    int[][] branches = {{-1, 0, 11, 4}, {-1, 1, 13, 5}, {1, -1, 14, 4}, {-1, -1, 16, 4}, {0, 1, 17, 3}};
    int seed = 20;
    for (int[] b : branches) {
      int x = tx - 1 + (b[0] > 0 ? 1 : 0), z = tz - 1 + (b[1] > 0 ? 1 : 0), y = b[2];
      for (int i = 0; i < b[3]; i++) {
        x += b[0];
        z += b[1];
        if (i % 2 == 1) y++;
        p.set(x, y, z, DARK_OAK_WOOD);
      }
      leaves(p, x, y + 1, z, 3.2, seed++);
      p.set(x, y - 1, z, IRON_CHAIN);
      p.set(x, y - 2, z, LANTERN, "[hanging=true]");
      p.clear(x, y - 3, z);
    }
    leaves(p, tx - 1, 21, tz - 1, 4.2, seed);
  }

  private static void tower(HubPlan p) {
    int cx = TOWER_X, cz = TOWER_Z;
    for (int y = 0; y <= 12; y++) {
      for (int dx = -2; dx <= 2; dx++) {
        for (int dz = -2; dz <= 2; dz++) {
          boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
          boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
          if (y == 0) {
            p.set(cx + dx, 0, cz + dz, STONE_BRICKS);
            continue;
          }
          if (!edge && y != 12) {
            p.clear(cx + dx, y, cz + dz);
            continue;
          }
          double n = HubPlan.noise(cx + dx + y * 7, cz + dz, 9);
          Material m = corner ? STRIPPED_DARK_OAK_LOG : n < 0.2 ? MOSSY_STONE_BRICKS : n < 0.32 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
          if (y == 12) m = corner ? STRIPPED_DARK_OAK_LOG : DARK_OAK_PLANKS;
          if (!corner && y > 8 && y < 12 && n > 0.9) continue; // weathered gaps high up
          p.set(cx + dx, y, cz + dz, m);
        }
      }
    }
    // door toward the village, lit windows
    p.set(cx, 1, cz + 2, DARK_OAK_DOOR, "[facing=south,half=lower,hinge=left,open=false]");
    p.set(cx, 2, cz + 2, DARK_OAK_DOOR, "[facing=south,half=upper,hinge=left,open=false]");
    for (int[] w : new int[][] {{0, 2}, {-2, 0}}) {
      p.set(cx + w[0], 7, cz + w[1], YELLOW_STAINED_GLASS_PANE);
      p.set(cx + w[0], 8, cz + w[1], YELLOW_STAINED_GLASS_PANE);
      p.set(cx + w[0] / 2, 7, cz + w[1] / 2, SHROOMLIGHT);
    }
    p.set(cx, 2, cz, LIGHT, "[level=10]");
    // belfry: corner posts, the bell under the roof
    for (int y = 13; y <= 16; y++)
      for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) p.set(cx + c[0], y, cz + c[1], STRIPPED_DARK_OAK_LOG);
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++) {
        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
        if (edge && !(Math.abs(dx) == 2 && Math.abs(dz) == 2)) p.set(cx + dx, 13, cz + dz, DARK_OAK_FENCE);
      }
    for (int dx = -2; dx <= 2; dx++) p.set(cx + dx, 16, cz, STRIPPED_DARK_OAK_LOG, "[axis=x]");
    p.set(cx, 15, cz, BELL, "[attachment=ceiling,facing=south]");
    // stepped slate roof with a spire
    for (int k = 0; k <= 3; k++) {
      int r = 3 - k;
      for (int dx = -r; dx <= r; dx++)
        for (int dz = -r; dz <= r; dz++) {
          boolean rim = Math.abs(dx) == r || Math.abs(dz) == r;
          if (k == 0 || rim) p.set(cx + dx, 17 + k, cz + dz, rim ? DEEPSLATE_TILES : DARK_OAK_PLANKS);
        }
    }
    p.set(cx, 21, cz, DEEPSLATE_TILE_WALL);
    p.set(cx, 22, cz, LIGHTNING_ROD);
    for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) p.set(cx + c[0], 16, cz + c[1], LANTERN, "[hanging=true]");
  }

  private static void rock(HubPlan p) {
    for (int dx = -4; dx <= 4; dx++) {
      for (int dz = -3; dz <= 3; dz++) {
        double d = Math.hypot(dx, dz * 1.2);
        if (d > 3.8) continue;
        int x = ROCK_X + dx, z = ROCK_Z + dz;
        if (!island(x, z)) continue;
        double n = HubPlan.noise(x, z, 13);
        int h = (int) Math.round(Math.max(2, 4.2 - d * 0.5 + n));
        for (int y = 0; y <= h; y++) {
          double m = HubPlan.noise(x + y * 5, z, 17);
          p.set(x, y, z, y == h && m < 0.4 ? MOSS_BLOCK : m < 0.4 ? STONE : m < 0.7 ? ANDESITE : MOSSY_COBBLESTONE);
        }
      }
    }
  }

  /** The top of the statue rock (y of its highest block at the rock centre). */
  static int rockTop() {
    return (int) Math.round(Math.max(2, 4.2 + HubPlan.noise(ROCK_X, ROCK_Z, 13)));
  }

  private static void cottage(HubPlan p, int cx, int cz) {
    // the door faces the council circle along x
    int face = cx > 0 ? -1 : 1;
    for (int dx = -2; dx <= 2; dx++) {
      for (int dz = -2; dz <= 2; dz++) {
        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
        p.set(cx + dx, 0, cz + dz, SPRUCE_PLANKS);
        for (int y = 1; y <= 3; y++) {
          if (!edge) {
            p.clear(cx + dx, y, cz + dz);
            continue;
          }
          p.set(cx + dx, y, cz + dz, corner ? STRIPPED_SPRUCE_LOG : y == 1 ? COBBLESTONE : BIRCH_PLANKS);
        }
      }
    }
    int fx = cx + face * 2;
    p.set(fx, 1, cz, SPRUCE_DOOR, "[facing=" + (face > 0 ? "east" : "west") + ",half=lower,hinge=left,open=false]");
    p.set(fx, 2, cz, SPRUCE_DOOR, "[facing=" + (face > 0 ? "east" : "west") + ",half=upper,hinge=left,open=false]");
    // lit windows beside the door and on the gable ends
    for (int[] w : new int[][] {{fx, cz - 1}, {fx, cz + 1}, {cx, cz - 2}, {cx, cz + 2}}) {
      p.set(w[0], 2, w[1], YELLOW_STAINED_GLASS_PANE);
      int ix = w[0] == fx ? fx - face : w[0], iz = w[0] == fx ? w[1] : w[1] + (w[1] < cz ? 1 : -1);
      p.set(ix, 2, iz, SHROOMLIGHT);
    }
    // gable roof along z
    for (int k = 0; k <= 2; k++) {
      int y = 4 + k;
      for (int dz = -3; dz <= 3; dz++) {
        p.set(cx - 3 + k, y, cz + dz, SPRUCE_STAIRS, "[facing=east,half=bottom]");
        p.set(cx + 3 - k, y, cz + dz, SPRUCE_STAIRS, "[facing=west,half=bottom]");
      }
      for (int dx = -2 + k; dx <= 2 - k; dx++) {
        if (Math.abs(dx) == 3 - k) continue;
        p.set(cx + dx, y, cz - 2, BIRCH_PLANKS);
        p.set(cx + dx, y, cz + 2, BIRCH_PLANKS);
      }
    }
    for (int dz = -3; dz <= 3; dz++) p.set(cx, 7, cz + dz, SPRUCE_SLAB, "[type=bottom]");
    // chimney with a little smoke
    for (int y = 4; y <= 7; y++) p.set(cx, y, cz + 2, COBBLESTONE);
    p.set(cx, 8, cz + 2, CAMPFIRE, "[lit=true,signal_fire=false,facing=north]");
    // a lantern post by the door
    p.set(fx + face, 1, cz + 2, SPRUCE_FENCE);
    p.set(fx + face, 2, cz + 2, LANTERN, "[hanging=false]");
  }

  private static void standingStones(HubPlan p) {
    int[][] stones = {{13, -4, 4}, {12, 4, 3}, {-6, -12, 3}, {5, -13, 4}, {-12, -4, 3}};
    for (int[] s : stones) {
      for (int y = 1; y <= s[2]; y++) {
        double n = HubPlan.noise(s[0], s[1] + y, 23);
        p.set(s[0], y, s[1], y == s[2] ? MOSSY_COBBLESTONE : n < 0.5 ? STONE : ANDESITE);
      }
    }
  }

  private static void lampPosts(HubPlan p) {
    int[][] posts = {{2, 8}, {-2, 8}, {5, -7}, {-8, 5}, {8, -3}, {-3, -9}, {11, -2}};
    for (int[] c : posts) {
      p.set(c[0], 1, c[1], SPRUCE_FENCE);
      p.set(c[0], 2, c[1], SPRUCE_FENCE);
      p.set(c[0], 3, c[1], LANTERN, "[hanging=false]");
    }
  }

  private static void fenceRing(HubPlan p) {
    for (int x = -R; x <= R; x++) {
      for (int z = -R; z <= R; z++) {
        double d = dist(x, z);
        if (d <= FENCE - 0.5 || d > FENCE + 0.5) continue;
        if (p.at(x, 1, z) != null || p.material(x, 0, z) != GRASS_BLOCK && p.material(x, 0, z) != DIRT_PATH) continue;
        p.set(x, 1, z, SPRUCE_FENCE);
      }
    }
  }

  private static void plants(HubPlan p) {
    for (int x = -R; x <= R; x++) {
      for (int z = -R; z <= R; z++) {
        if (!island(x, z)) continue;
        for (int y = 0; y <= 4; y++) {
          if (p.material(x, y, z) != GRASS_BLOCK || p.at(x, y + 1, z) != null) continue;
          double n = HubPlan.noise(x, z, 31);
          Material m =
              n < 0.16 ? SHORT_GRASS : n < 0.22 ? FERN : n < 0.245 ? LILY_OF_THE_VALLEY : n < 0.26 ? FIREFLY_BUSH : n < 0.27 ? BLUE_ORCHID : null;
          if (m != null) p.set(x, y + 1, z, m);
        }
      }
    }
  }

  private static void hangings(HubPlan p) {
    int[][] chains = {{8, 12, 4}, {-12, 9, 5}, {14, -6, 3}, {-7, -14, 6}, {-15, -5, 4}, {3, 15, 5}};
    for (int[] c : chains) {
      int x = c[0], z = c[1];
      if (!island(x, z)) continue;
      int y = -1;
      while (p.at(x, y, z) != null && y > -30) y--;
      for (int i = 0; i < c[2]; i++) p.set(x, y - i, z, IRON_CHAIN);
      p.set(x, y - c[2], z, LANTERN, "[hanging=true]");
    }
  }

  /** Soft invisible light over the walkable disc, so the circle reads at midnight between the lanterns. */
  private static void lights(HubPlan p) {
    for (int x = -12; x <= 12; x += 6)
      for (int z = -12; z <= 12; z += 6)
        if (dist(x, z) < FENCE - 1 && p.at(x, 3, z) == null) p.set(x, 3, z, LIGHT, "[level=8]");
  }
}
