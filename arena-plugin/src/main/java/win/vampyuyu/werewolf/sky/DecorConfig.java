package win.vampyuyu.werewolf.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * The look of a sky platform, read from JSON (resource werewolf/decor/default.json). Parsing and
 * validation are pure: unknown keys and block names are rejected and the number of blocks is capped.
 * {@link #generate} turns the config plus a player count into block offsets relative to the campfire
 * centre; dy 0 is the platform floor, the campfire stands at dy 1 and the underside hangs at dy < 0.
 */
public final class DecorConfig {
  public static final String DEFAULT_RESOURCE = "/werewolf/decor/default.json";

  /** Upper bound for "maxBlocks", whatever the file says. */
  public static final int HARD_CAP = 20_000;

  /** Blocks that must never appear in a decor, even though they are valid materials. */
  private static final Set<Material> FORBIDDEN =
      Set.of(
          Material.TNT,
          Material.LAVA,
          Material.WATER,
          Material.FIRE,
          Material.SOUL_FIRE,
          Material.MAGMA_BLOCK,
          Material.BEDROCK,
          Material.BARRIER,
          Material.COMMAND_BLOCK,
          Material.CHAIN_COMMAND_BLOCK,
          Material.REPEATING_COMMAND_BLOCK,
          Material.STRUCTURE_BLOCK,
          Material.JIGSAW);

  /** One block of the decor, relative to the campfire centre. */
  public record Placement(int dx, int dy, int dz, Material material) {}

  /** The village around the platform: houses, trees and lamps on a grassy ring {@code width} blocks wide. */
  record Village(int width, Material ground, Material path, Material wall, Material log, Material roof,
      Material glass, Material leaves, Material fence, Material lamp) {}

  private final int maxBlocks;
  private Village village;
  private final Material floorInner, floorOuter, campfire, railing, lantern;
  private final double innerMargin;
  private final List<Material> bench, underside;
  private final int lanternEvery, minDepth, maxDepth;
  private final double depthFactor;

  private DecorConfig(
      int maxBlocks,
      Material floorInner,
      Material floorOuter,
      double innerMargin,
      Material campfire,
      List<Material> bench,
      Material railing,
      Material lantern,
      int lanternEvery,
      List<Material> underside,
      double depthFactor,
      int minDepth,
      int maxDepth) {
    this.maxBlocks = maxBlocks;
    this.floorInner = floorInner;
    this.floorOuter = floorOuter;
    this.innerMargin = innerMargin;
    this.campfire = campfire;
    this.bench = bench;
    this.railing = railing;
    this.lantern = lantern;
    this.lanternEvery = lanternEvery;
    this.underside = underside;
    this.depthFactor = depthFactor;
    this.minDepth = minDepth;
    this.maxDepth = maxDepth;
  }

  public int maxBlocks() {
    return maxBlocks;
  }

  /** Loads the decor shipped inside the jar. */
  public static DecorConfig loadDefault() {
    try (InputStream in = DecorConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
      if (in == null) throw new IllegalStateException("Missing resource " + DEFAULT_RESOURCE);
      return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + DEFAULT_RESOURCE, e);
    }
  }

  public static DecorConfig load(InputStream in) throws IOException {
    try (in) {
      return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  /** @throws IllegalArgumentException on malformed JSON, unknown keys, unknown or forbidden blocks. */
  public static DecorConfig parse(String json) {
    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Decor is not a valid JSON object: " + e.getMessage(), e);
    }
    only(root, "decor", "version", "maxBlocks", "floor", "campfire", "bench", "railing", "lanterns", "underside", "village");
    if (root.has("version") && integer(root, "version", 1, 1) != 1)
      throw new IllegalArgumentException("Unsupported decor version " + root.get("version"));
    int cap = root.has("maxBlocks") ? integer(root, "maxBlocks", 1, HARD_CAP) : 8000;

    JsonObject floor = object(root, "floor");
    only(floor, "floor", "inner", "outer", "innerMargin");
    Material inner = block(floor, "inner"), outer = block(floor, "outer");
    double margin = floor.has("innerMargin") ? number(floor, "innerMargin", 0, 10) : 2.5;

    JsonObject fire = object(root, "campfire");
    only(fire, "campfire", "block");
    Material fireBlock = block(fire, "block");

    List<Material> bench = List.of();
    if (root.has("bench")) {
      JsonObject o = object(root, "bench");
      only(o, "bench", "blocks");
      bench = blocks(o, "blocks");
    }
    Material rail = null;
    if (root.has("railing")) {
      JsonObject o = object(root, "railing");
      only(o, "railing", "block");
      rail = block(o, "block");
    }
    Material lamp = null;
    int every = 1;
    if (root.has("lanterns")) {
      if (rail == null) throw new IllegalArgumentException("Lanterns hang on the railing, which is missing");
      JsonObject o = object(root, "lanterns");
      only(o, "lanterns", "block", "everySeats");
      lamp = block(o, "block");
      every = o.has("everySeats") ? integer(o, "everySeats", 1, PlatformLayout.MAX_PLAYERS) : 1;
    }
    List<Material> under = List.of();
    double factor = 0.6;
    int minD = 4, maxD = 14;
    if (root.has("underside")) {
      JsonObject o = object(root, "underside");
      only(o, "underside", "blocks", "depthFactor", "minDepth", "maxDepth");
      under = blocks(o, "blocks");
      if (o.has("depthFactor")) factor = number(o, "depthFactor", 0.1, 3);
      if (o.has("minDepth")) minD = integer(o, "minDepth", 1, 40);
      if (o.has("maxDepth")) maxD = integer(o, "maxDepth", 1, 40);
      if (minD > maxD) throw new IllegalArgumentException("underside.minDepth is above maxDepth");
    }
    DecorConfig config =
        new DecorConfig(cap, inner, outer, margin, fireBlock, bench, rail, lamp, every, under, factor, minD, maxD);
    if (root.has("village")) {
      JsonObject o = object(root, "village");
      only(o, "village", "width", "ground", "path", "wall", "log", "roof", "glass", "leaves", "fence", "lamp");
      int width = o.has("width") ? integer(o, "width", 0, 24) : 16;
      if (width > 0)
        config.village =
            new Village(
                width,
                o.has("ground") ? block(o, "ground") : Material.GRASS_BLOCK,
                o.has("path") ? block(o, "path") : Material.DIRT_PATH,
                o.has("wall") ? block(o, "wall") : Material.OAK_PLANKS,
                o.has("log") ? block(o, "log") : Material.OAK_LOG,
                o.has("roof") ? block(o, "roof") : Material.DARK_OAK_PLANKS,
                o.has("glass") ? block(o, "glass") : Material.GLASS,
                o.has("leaves") ? block(o, "leaves") : Material.OAK_LEAVES,
                o.has("fence") ? block(o, "fence") : Material.OAK_FENCE,
                o.has("lamp") ? block(o, "lamp") : Material.LANTERN);
    }
    // Fail at load time rather than at the first game: the biggest platform must fit under the cap.
    config.generate(PlatformLayout.MAX_PLAYERS);
    return config;
  }

  /** Every block of the decor for this many players; throws when the config exceeds its own cap. */
  public List<Placement> generate(int players) {
    PlatformLayout layout = PlatformLayout.of(players);
    double radius = layout.platformRadius(), ring = layout.ringRadius();
    int reach = (int) Math.ceil(radius);
    Map<Long, Placement> blocks = new LinkedHashMap<>();

    // Floor: a disc, planks near the fire and a different block on the outer walkway.
    List<int[]> edge = new ArrayList<>();
    for (int x = -reach; x <= reach; x++)
      for (int z = -reach; z <= reach; z++) {
        if (!inDisc(x, z, radius)) continue;
        double d = Math.hypot(x, z);
        put(blocks, x, 0, z, d <= ring + innerMargin ? floorInner : floorOuter);
        if (!inDisc(x + 1, z, radius) || !inDisc(x - 1, z, radius) || !inDisc(x, z + 1, radius) || !inDisc(x, z - 1, radius))
          edge.add(new int[] {x, z});
      }

    // Underside: the floating island, narrowing layer by layer.
    if (!underside.isEmpty()) {
      // With a village the island carries it too, but its rock only reaches half way out.
      double rock = village == null ? radius : radius + village.width() * 0.5;
      int depth = (int) Math.max(minDepth, Math.min(maxDepth, Math.round(rock * depthFactor)));
      for (int k = 1; k <= depth; k++) {
        double r = rock * (1 - k / (depth + 1.0));
        int span = (int) Math.ceil(r);
        for (int x = -span; x <= span; x++)
          for (int z = -span; z <= span; z++)
            if (inDisc(x, z, r)) put(blocks, x, -k, z, underside.get(pick(x, z, k, underside.size())));
      }
    }

    put(blocks, 0, 1, 0, campfire);

    // A bench block just behind every seat, alternating between the configured blocks.
    if (!bench.isEmpty()) {
      // Seat cells stay free, and two benches never share a cell: step outwards until the cell is clear.
      Set<Long> taken = new HashSet<>();
      for (var seat : layout.seats()) taken.add(cell((int) Math.round(seat.x()), (int) Math.round(seat.z())));
      for (int i = 0; i < layout.seats().size(); i++) {
        var seat = layout.seats().get(i);
        for (double step = 1; step <= 4; step += 0.5) {
          double back = (ring + step) / ring;
          int bx = (int) Math.round(seat.x() * back), bz = (int) Math.round(seat.z() * back);
          if (taken.add(cell(bx, bz))) {
            put(blocks, bx, 1, bz, bench.get(i % bench.size()));
            break;
          }
        }
      }
    }

    if (railing != null) {
      for (int[] c : edge) put(blocks, c[0], 1, c[1], railing);
      if (lantern != null) {
        int n = layout.seats().size();
        for (int i = 0; i < n; i += lanternEvery) {
          // Direction between seat i and the next one, so lanterns do not line up with the players.
          double angle = 2 * Math.PI * (i + 0.5) / n, tx = Math.sin(angle), tz = Math.cos(angle);
          int[] best = edge.get(0);
          double bestDot = -2;
          for (int[] c : edge) {
            double len = Math.hypot(c[0], c[1]);
            double dot = len == 0 ? -1 : (c[0] * tx + c[1] * tz) / len;
            if (dot > bestDot) {
              bestDot = dot;
              best = c;
            }
          }
          put(blocks, best[0], 2, best[1], lantern);
        }
      }
    }
    if (village != null) village(blocks, radius, players);
    if (blocks.size() > maxBlocks)
      throw new IllegalArgumentException(
          "Decor for " + players + " players needs " + blocks.size() + " blocks, over the cap of " + maxBlocks);
    return List.copyOf(blocks.values());
  }

  /** Grass, a lane, houses facing the fire, trees and lamp posts on a ring around the platform. */
  private void village(Map<Long, Placement> blocks, double radius, int players) {
    Village v = village;
    double outer = radius + v.width();
    int reach = (int) Math.ceil(outer);
    for (int x = -reach; x <= reach; x++)
      for (int z = -reach; z <= reach; z++) {
        double d = Math.hypot(x, z);
        if (d <= radius || d > outer) continue;
        put(blocks, x, 0, z, d <= radius + 3.5 && d > radius + 1.5 ? v.path() : v.ground());
        // A low fence closes the island's rim.
        if (d > outer - 1) put(blocks, x, 1, z, v.fence());
      }
    // Houses 12 blocks apart at least, trees and lamp posts in between.
    double homes = radius + v.width() * 0.6;
    int n = (int) Math.max(5, Math.min(24, Math.floor(2 * Math.PI * homes / 12)));
    for (int i = 0; i < n; i++) {
      double a = 2 * Math.PI * i / n;
      house(blocks, (int) Math.round(homes * Math.sin(a)), (int) Math.round(homes * Math.cos(a)), v);
      double b = 2 * Math.PI * (i + 0.5) / n;
      tree(blocks, (int) Math.round(homes * Math.sin(b)), (int) Math.round(homes * Math.cos(b)), v);
      int lx = (int) Math.round((radius + 2.5) * Math.sin(b)), lz = (int) Math.round((radius + 2.5) * Math.cos(b));
      put(blocks, lx, 1, lz, v.fence());
      put(blocks, lx, 2, lz, v.fence());
      put(blocks, lx, 3, lz, v.lamp());
    }
  }

  /** A 5x5 cottage with a stepped roof; its door faces the campfire. */
  private static void house(Map<Long, Placement> blocks, int cx, int cz, Village v) {
    boolean doorOnX = Math.abs(cx) > Math.abs(cz);
    int doorX = doorOnX ? (cx > 0 ? -2 : 2) : 0, doorZ = doorOnX ? 0 : (cz > 0 ? -2 : 2);
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++) {
        boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
        put(blocks, cx + dx, 0, cz + dz, v.wall());
        if (!wall) continue;
        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
        for (int y = 1; y <= 3; y++) {
          if (dx == doorX && dz == doorZ && y <= 2) continue; // the doorway
          boolean window = y == 2 && !corner && (dx == 0 || dz == 0);
          put(blocks, cx + dx, y, cz + dz, corner ? v.log() : window ? v.glass() : v.wall());
        }
      }
    for (int layer = 0; layer < 4; layer++) {
      int half = 3 - layer;
      for (int dx = -half; dx <= half; dx++)
        for (int dz = -half; dz <= half; dz++) put(blocks, cx + dx, 4 + layer, cz + dz, v.roof());
    }
  }

  private static void tree(Map<Long, Placement> blocks, int cx, int cz, Village v) {
    for (int dx = -2; dx <= 2; dx++)
      for (int dy = 3; dy <= 6; dy++)
        for (int dz = -2; dz <= 2; dz++)
          if (dx * dx + (dy - 4.5) * (dy - 4.5) * 1.3 + dz * dz <= 5.2) put(blocks, cx + dx, dy, cz + dz, v.leaves());
    for (int y = 1; y <= 5; y++) put(blocks, cx, y, cz, v.log());
  }

  private static boolean inDisc(int x, int z, double radius) {
    return Math.hypot(x, z) <= radius;
  }

  private static int pick(int x, int z, int k, int size) {
    return Math.floorMod(x * 73856093 ^ z * 19349663 ^ k * 83492791, size);
  }

  private static long cell(int x, int z) {
    return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
  }

  private static void put(Map<Long, Placement> blocks, int x, int y, int z, Material m) {
    long key = ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    blocks.put(key, new Placement(x, y, z, m));
  }

  // ---- JSON helpers: every failure names the key that caused it

  private static void only(JsonObject o, String where, String... allowed) {
    Set<String> ok = Set.of(allowed);
    for (String key : o.keySet())
      if (!ok.contains(key)) throw new IllegalArgumentException("Unknown key '" + key + "' in " + where);
  }

  private static JsonObject object(JsonObject o, String key) {
    JsonElement e = o.get(key);
    if (e == null || !e.isJsonObject()) throw new IllegalArgumentException("'" + key + "' must be an object");
    return e.getAsJsonObject();
  }

  private static int integer(JsonObject o, String key, int min, int max) {
    double d = number(o, key, Integer.MIN_VALUE, Integer.MAX_VALUE);
    if (d != Math.rint(d)) throw new IllegalArgumentException("'" + key + "' must be an integer");
    int v = (int) d;
    if (v < min || v > max) throw new IllegalArgumentException("'" + key + "' must be within " + min + ".." + max);
    return v;
  }

  private static double number(JsonObject o, String key, double min, double max) {
    double v;
    try {
      v = o.get(key).getAsDouble();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("'" + key + "' must be a number");
    }
    if (!(v >= min && v <= max)) throw new IllegalArgumentException("'" + key + "' must be within " + min + ".." + max);
    return v;
  }

  private static Material block(JsonObject o, String key) {
    JsonElement e = o.get(key);
    if (e == null || !e.isJsonPrimitive()) throw new IllegalArgumentException("'" + key + "' must be a block name");
    return material(e.getAsString());
  }

  private static List<Material> blocks(JsonObject o, String key) {
    JsonElement e = o.get(key);
    if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty())
      throw new IllegalArgumentException("'" + key + "' must be a non-empty list of block names");
    JsonArray array = e.getAsJsonArray();
    List<Material> out = new ArrayList<>();
    for (JsonElement item : array) {
      if (!item.isJsonPrimitive()) throw new IllegalArgumentException("'" + key + "' holds a non-name entry");
      out.add(material(item.getAsString()));
    }
    return List.copyOf(out);
  }

  /** Accepts "oak_log" or "minecraft:oak_log"; rejects non-blocks, air and forbidden blocks. */
  static Material material(String name) {
    String bare = name.toLowerCase(Locale.ROOT).trim();
    if (bare.startsWith("minecraft:")) bare = bare.substring("minecraft:".length());
    Material m = Material.getMaterial(bare.toUpperCase(Locale.ROOT));
    if (m == null || m.isLegacy() || !m.isBlock())
      throw new IllegalArgumentException("Unknown block '" + name + "'");
    if (m.isAir() || FORBIDDEN.contains(m)) throw new IllegalArgumentException("Block '" + name + "' is not allowed");
    return m;
  }
}
