package win.vampyuyu.arena;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * The blocks of one waiting hub, relative to its centre (x, z) and floor (y = 0 is the floor layer, players stand at
 * y = 1). Pure data, no server: {@link WaitingHubs} places it, the tests read it. Fences, panes, iron bars and walls
 * get their connection states from their planned neighbours ({@link #resolved()}), because the hub is placed without
 * physics and an unconnected pane is a thin post a player walks through.
 */
final class HubPlan {
  /** One planned block: a material and its block state ("[facing=east,half=top]"), or null for the default. */
  record Block(Material material, String state) {}

  private final Map<Long, Block> blocks = new LinkedHashMap<>();

  static long key(int x, int y, int z) {
    return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
  }

  static int kx(long k) {
    return (int) (k >> 42) << 10 >> 10;
  }

  static int ky(long k) {
    return (int) ((k >> 22) & 0xFFFFF) << 12 >> 12;
  }

  static int kz(long k) {
    return (int) (k & 0x3FFFFF) << 10 >> 10;
  }

  void set(int x, int y, int z, Material m) {
    set(x, y, z, m, null);
  }

  void set(int x, int y, int z, Material m, String state) {
    blocks.put(key(x, y, z), new Block(m, state));
  }

  /** Sets only where nothing is planned yet. */
  void soft(int x, int y, int z, Material m) {
    blocks.putIfAbsent(key(x, y, z), new Block(m, null));
  }

  void clear(int x, int y, int z) {
    blocks.remove(key(x, y, z));
  }

  void fill(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
    for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
      for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, m);
  }

  Block at(int x, int y, int z) {
    return blocks.get(key(x, y, z));
  }

  Material material(int x, int y, int z) {
    Block b = blocks.get(key(x, y, z));
    return b == null ? Material.AIR : b.material();
  }

  int size() {
    return blocks.size();
  }

  /** A stable pseudo-random value in [0, 1) for a cell: the same hub every build. */
  static double noise(int x, int z, int seed) {
    long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
    h ^= h >>> 31;
    h *= 0xBF58476D1CE4E5B9L;
    h ^= h >>> 29;
    return (h >>> 11) / (double) (1L << 53);
  }

  /** The blocks with the connection states of fences, panes, bars and walls filled in from their neighbours. */
  Map<Long, Block> resolved() {
    Map<Long, Block> out = new LinkedHashMap<>(blocks.size());
    for (var e : blocks.entrySet()) {
      long k = e.getKey();
      Block b = e.getValue();
      Family f = family(b.material());
      if (f == Family.NONE || b.state() != null) {
        out.put(k, b);
        continue;
      }
      int x = kx(k), y = ky(k), z = kz(k);
      boolean n = joins(f, at(x, y, z - 1)), s = joins(f, at(x, y, z + 1));
      boolean w = joins(f, at(x - 1, y, z)), ea = joins(f, at(x + 1, y, z));
      String state;
      if (f == Family.WALL) {
        boolean straight = (n && s && !w && !ea) || (w && ea && !n && !s);
        boolean above = at(x, y + 1, z) != null && solid(at(x, y + 1, z).material());
        state =
            "[north=" + low(n) + ",south=" + low(s) + ",west=" + low(w) + ",east=" + low(ea)
                + ",up=" + (!straight || above) + "]";
      } else {
        state = "[north=" + n + ",south=" + s + ",west=" + w + ",east=" + ea + "]";
      }
      out.put(k, new Block(b.material(), state));
    }
    return out;
  }

  private static String low(boolean b) {
    return b ? "low" : "none";
  }

  enum Family {
    NONE,
    FENCE,
    PANE,
    WALL
  }

  static Family family(Material m) {
    String n = m.name();
    if (n.endsWith("_FENCE")) return Family.FENCE;
    if (n.endsWith("_PANE") || n.equals("IRON_BARS")) return Family.PANE;
    if (n.endsWith("_WALL") && !n.endsWith("_WALL_SIGN") && !n.endsWith("_WALL_BANNER")
        && !n.endsWith("_WALL_HEAD") && !n.endsWith("_WALL_SKULL") && !n.endsWith("_WALL_FAN")
        && !n.endsWith("_WALL_TORCH") && !n.endsWith("_WALL_HANGING_SIGN")) return Family.WALL;
    return Family.NONE;
  }

  private static boolean joins(Family f, Block other) {
    if (other == null) return false;
    Family g = family(other.material());
    if (g != Family.NONE) return g == f || (f == Family.WALL && g == Family.PANE) || (f == Family.PANE && g == Family.WALL);
    return solid(other.material());
  }

  /** A full block a fence, pane or wall connects to (and stands on). */
  static boolean solid(Material m) {
    if (m == Material.BARRIER) return true;
    String n = m.name();
    if (m.isAir() || m == Material.LIGHT) return false;
    for (String part :
        new String[] {
          "SLAB", "STAIRS", "END_ROD", "LANTERN", "CHAIN", "CAMPFIRE", "CARPET", "ROD", "BELL",
          "TORCH", "DOOR", "LEAVES", "ROOTS", "SHORT_GRASS", "TALL_GRASS", "FERN", "BUSH", "LILY", "ORCHID",
          "POPPY", "DANDELION", "PATH", "VINE", "SAPLING", "FLOWER", "BUTTON", "PLATE", "SIGN"
        }) if (n.contains(part)) return false;
    return true;
  }
}
