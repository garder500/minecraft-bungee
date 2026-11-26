package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks of a themed map build: a sparse grid of block-data strings (Bukkit syntax, for example
 * {@code "ladder[facing=north]"}) in coordinates relative to the map origin (y 0 = feet level). Pure: no world access.
 * Setting a block twice keeps the last one, so a builder lays the body first and the details after.
 */
public final class VbCanvas {
  /** Receives the blocks, in a stable order (x, then y, then z). */
  public interface Sink {
    void set(int x, int y, int z, String blockData);
  }

  private final Map<Long, String> blocks = new HashMap<>();
  private final List<Model> models = new ArrayList<>();

  /**
   * A set piece blocks cannot make: an item-display model of the ValoBlock pack ({@code vampyuyu:valoblock/map/<id>}), centred
   * at (x, y, z) relative to the map origin, turned {@code yaw} degrees about Y, scaled, and spinning {@code spin} degrees a
   * second about Y (0: still). Scenery only: no collision, and shots only test blocks and players.
   */
  public record Model(String id, double x, double y, double z, float yaw, float scale, float spin) {}

  public void model(String id, double x, double y, double z, float yaw, float scale, float spin) {
    models.add(new Model(id, x, y, z, yaw, scale, spin));
  }

  /** The set pieces, in the order the builder added them. */
  public List<Model> models() {
    return List.copyOf(models);
  }

  private static long key(int x, int y, int z) {
    return ((long) (x + 4096) << 42) | ((long) (y + 1024) << 21) | (z + 4096);
  }

  public void set(int x, int y, int z, String data) {
    blocks.put(key(x, y, z), data);
  }

  /** The block at a place, or null when the build leaves it alone (air). */
  public String get(int x, int y, int z) {
    return blocks.get(key(x, y, z));
  }

  public boolean solid(int x, int y, int z) {
    String d = get(x, y, z);
    return d != null && !VbCanvas.passable(d);
  }

  public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String data) {
    for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
      for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, data);
  }

  public int size() {
    return blocks.size();
  }

  /** The material name of a block-data string (before the properties), upper case. */
  public static String material(String data) {
    int i = data.indexOf('[');
    return (i < 0 ? data : data.substring(0, i)).toUpperCase(java.util.Locale.ROOT);
  }

  /** Blocks a player walks through (or that do not stop a shot): air, ladders, banners, vines, roots, lanterns... */
  public static boolean passable(String data) {
    String m = material(data);
    return m.equals("AIR")
        || m.equals("LADDER")
        || m.equals("VINE")
        || m.equals("HANGING_ROOTS")
        || m.equals("LIGHT")
        || m.equals("GLOW_LICHEN")
        || m.endsWith("_WALL_BANNER")
        || m.endsWith("_CARPET")
        || m.endsWith("TORCH")
        || m.equals("SHORT_GRASS")
        || m.equals("FERN")
        || m.equals("BROWN_MUSHROOM")
        || m.equals("RED_MUSHROOM")
        || m.equals("LANTERN")
        || m.equals("CHAIN");
  }

  /** Every block, in a stable order. */
  public void emit(Sink sink) {
    long[] keys = new long[blocks.size()];
    int i = 0;
    for (long k : blocks.keySet()) keys[i++] = k;
    Arrays.sort(keys);
    for (long k : keys) sink.set((int) (k >> 42) - 4096, (int) ((k >> 21) & 0x1FFFFF) - 1024, (int) (k & 0x1FFFFF) - 4096, blocks.get(k));
  }

  /** The same blocks, one by one, but as air: what clears a build. */
  public void emitAir(Sink sink) {
    emit((x, y, z, d) -> sink.set(x, y, z, "air"));
  }
}
