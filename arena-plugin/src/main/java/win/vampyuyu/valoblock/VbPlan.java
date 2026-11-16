package win.vampyuyu.valoblock;

import java.util.List;

/** The plan of a map seen cell by cell and block by block, with the helpers the themed builders share. Pure. */
public final class VbPlan {
  public final VbMap map;
  public final int cols, rows, cell, wallH, width, depth;
  private final List<String> plan;
  private final boolean[][] lane;
  private final long seed;

  public VbPlan(VbMap map) {
    this.map = map;
    this.plan = map.plan();
    this.cols = plan.get(0).length();
    this.rows = plan.size();
    this.cell = map.cell();
    this.wallH = map.wallHeight();
    this.width = map.width();
    this.depth = map.depth();
    this.seed = map.id().hashCode();
    lane = new boolean[width][depth];
    for (int r = 0; r < rows; r++)
      for (int c = 0; c < cols; c++) {
        if (!walk(c, r)) continue;
        mark(c, r, c, r);
        if (walk(c + 1, r)) mark(c, r, c + 1, r);
        if (walk(c, r + 1)) mark(c, r, c, r + 1);
      }
  }

  /** The blocks the straight line between two cell centres touches (half a block each side). */
  private void mark(int c0, int r0, int c1, int r1) {
    double ax = (c0 + 0.5) * cell, az = (r0 + 0.5) * cell, bx = (c1 + 0.5) * cell, bz = (r1 + 0.5) * cell;
    int x0 = (int) Math.floor(Math.min(ax, bx) - 0.5), x1 = (int) Math.floor(Math.max(ax, bx) + 0.5 - 1e-9);
    int z0 = (int) Math.floor(Math.min(az, bz) - 0.5), z1 = (int) Math.floor(Math.max(az, bz) + 0.5 - 1e-9);
    for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (x >= 0 && z >= 0 && x < width && z < depth) lane[x][z] = true;
  }

  /** Plan character of a cell; outside the plan is wall. */
  public char at(int c, int r) {
    return c < 0 || r < 0 || c >= cols || r >= rows ? '#' : plan.get(r).charAt(c);
  }

  public boolean walk(int c, int r) {
    return ".RBAFh".indexOf(at(c, r)) >= 0 && c >= 0 && r >= 0 && c < cols && r < rows;
  }

  public boolean wall(int c, int r) {
    return c >= 0 && r >= 0 && c < cols && r < rows && at(c, r) == '#';
  }

  public boolean sea(int c, int r) {
    return at(c, r) == 'w' && c >= 0 && r >= 0 && c < cols && r < rows;
  }

  public int col(int x) {
    return Math.floorDiv(x, cell);
  }

  /** A block on the line a bot or a player walks between two cell centres: decor stays off these. */
  public boolean lane(int x, int z) {
    return x >= 0 && z >= 0 && x < width && z < depth && lane[x][z];
  }

  /** Whether a block touches (side by side) a block of a walkable cell. */
  public boolean nextToOpen(int x, int z) {
    int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    for (int[] o : d) {
      int nx = x + o[0], nz = z + o[1];
      if (nx < 0 || nz < 0 || nx >= width || nz >= depth) continue;
      if (walk(col(nx), col(nz))) return true;
    }
    return false;
  }

  /** Wall top of a cell: {@code wallHeight} to {@code wallHeight + 2}, varied from cell to cell for a skyline. */
  public int wallTop(int c, int r) {
    return wallH + hash(c, r, 7, 3);
  }

  /** A stable pseudo-random number from 0 to n-1 for a place (same map, same number). */
  public int hash(int x, int y, int z, int n) {
    long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L + z * 0x27D4EB2F165667C5L;
    h ^= h >>> 29;
    h *= 0xBF58476D1CE4E5B9L;
    h ^= h >>> 32;
    return (int) Math.floorMod(h, (long) n);
  }

  /** Neighbour cell offsets: east, west, south, north. */
  public static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

  /** Axis of a one-cell doorway: 'x' when the passage runs along x (walls north and south), 'z' when it runs along z, else 0. */
  public char doorway(int c, int r) {
    if (!walk(c, r)) return 0;
    boolean ns = wall(c, r - 1) && wall(c, r + 1) && walk(c - 1, r) && walk(c + 1, r);
    boolean ew = wall(c - 1, r) && wall(c + 1, r) && walk(c, r - 1) && walk(c, r + 1);
    return ns ? 'x' : ew ? 'z' : 0;
  }

  /** Cells (col,row) of a plan character, in reading order. */
  public java.util.List<int[]> cellsOf(char ch) {
    java.util.List<int[]> out = new java.util.ArrayList<>();
    for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (at(c, r) == ch) out.add(new int[] {c, r});
    return out;
  }

  /** The connected groups of cells holding a plan character, each in reading order. */
  public java.util.List<java.util.List<int[]>> groups(char ch) {
    boolean[][] seen = new boolean[rows][cols];
    java.util.List<java.util.List<int[]>> out = new java.util.ArrayList<>();
    for (int[] s : cellsOf(ch)) {
      if (seen[s[1]][s[0]]) continue;
      java.util.List<int[]> g = new java.util.ArrayList<>();
      java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
      q.add(s);
      seen[s[1]][s[0]] = true;
      while (!q.isEmpty()) {
        int[] cur = q.poll();
        g.add(cur);
        for (int[] d : DIRS) {
          int nc = cur[0] + d[0], nr = cur[1] + d[1];
          if (at(nc, nr) == ch && nc >= 0 && nr >= 0 && nc < cols && nr < rows && !seen[nr][nc]) {
            seen[nr][nc] = true;
            q.add(new int[] {nc, nr});
          }
        }
      }
      g.sort(java.util.Comparator.<int[]>comparingInt(a -> a[1]).thenComparingInt(a -> a[0]));
      out.add(g);
    }
    return out;
  }

  /**
   * Ladder spots on a group of high-ground cells: faces whose outside neighbour is a walkable low cell, the first one
   * and then each time the one farthest from those already chosen. Each is {col, row (the low cell), dx, dz (towards the high cell)}.
   */
  public java.util.List<int[]> ladderSpots(java.util.List<int[]> group, int count) {
    java.util.List<int[]> faces = new java.util.ArrayList<>();
    for (int[] g : group)
      for (int[] d : DIRS) {
        int nc = g[0] + d[0], nr = g[1] + d[1];
        if (walk(nc, nr) && at(nc, nr) != 'h' && at(nc, nr) != 'F') faces.add(new int[] {nc, nr, -d[0], -d[1]});
      }
    java.util.List<int[]> out = new java.util.ArrayList<>();
    if (faces.isEmpty()) return out;
    out.add(faces.get(0));
    while (out.size() < count && out.size() < faces.size()) {
      int[] best = null;
      long bd = -1;
      for (int[] f : faces) {
        long d = Long.MAX_VALUE;
        for (int[] o : out) d = Math.min(d, (long) (f[0] - o[0]) * (f[0] - o[0]) + (long) (f[1] - o[1]) * (f[1] - o[1]));
        if (d > bd) {
          bd = d;
          best = f;
        }
      }
      out.add(best);
    }
    return out;
  }
}
