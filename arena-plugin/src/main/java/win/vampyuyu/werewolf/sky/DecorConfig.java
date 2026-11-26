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
 * {@link #models} lists the display models (moon, mill sails, belfry bell and vane) that go with it.
 *
 * <p>The players stay seated around the fire for the whole game, so what matters is what a seat sees:
 * nothing solid stands inside the seat ring (only the floor and the campfire), standing stones and
 * the hamlet's skyline (two-storey houses, a belfry, a mill, an old oak) rise behind the players
 * opposite, and lit windows, lanterns and soul lanterns carry the night.
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

  /**
   * One block of the decor, relative to the campfire centre. {@code state} holds block properties
   * (e.g. "facing=north,half=bottom" for stairs, "level=15" for a light block), or null for the defaults.
   */
  public record Placement(int dx, int dy, int dz, Material material, String state) {
    public Placement(int dx, int dy, int dz, Material material) {
      this(dx, dy, dz, material, null);
    }
  }

  /**
   * One display model of the decor: the pack item {@code vampyuyu:werewolf/<item>}, its offset from the
   * campfire centre (blocks, floor level), the direction its front faces (Minecraft yaw), its scale, and
   * an optional spin (degrees a second about its own x, y or z axis).
   */
  public record Model(
      String item,
      double dx,
      double dy,
      double dz,
      float yaw,
      float scale,
      char spinAxis,
      float spinPerSecond,
      boolean glow,
      boolean billboard,
      float viewRange) {}

  /** The hamlet around the platform: houses, landmarks, trees and lamps on a grassy ring {@code width} blocks wide. */
  record Village(int width, Material ground, Material path, Material wall, Material log, Material roof,
      Material glass, Material leaves, Material fence, Material lamp, Material upper, Material window,
      Material roofStairs, Material stone, Material spire, boolean landmarks, boolean models) {}

  private final int maxBlocks;
  private Village village;
  private final Material floorInner, floorOuter, floorAccent, hearth, campfire, railing, lantern;
  private Material menhir, menhirTop;
  private final double innerMargin;
  private final List<Material> bench, underside;
  private final int lanternEvery, minDepth, maxDepth;
  private final double depthFactor;
  private boolean shell;

  private DecorConfig(
      int maxBlocks,
      Material floorInner,
      Material floorOuter,
      Material floorAccent,
      Material hearth,
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
    this.floorAccent = floorAccent;
    this.hearth = hearth;
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

  /** Loads the decor: overrides/werewolf/decor/default.json when present, else the jar's copy. */
  public static DecorConfig loadDefault() {
    try (InputStream in = win.vampyuyu.werewolf.DataFiles.open(DecorConfig.class, DEFAULT_RESOURCE)) {
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

  /**
   * Every key added after the first version is optional, so an older override file still parses.
   *
   * @throws IllegalArgumentException on malformed JSON, unknown keys, unknown or forbidden blocks.
   */
  public static DecorConfig parse(String json) {
    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Decor is not a valid JSON object: " + e.getMessage(), e);
    }
    only(root, "decor", "version", "maxBlocks", "floor", "campfire", "bench", "railing", "lanterns", "underside", "village", "menhirs");
    if (root.has("version") && integer(root, "version", 1, 1) != 1)
      throw new IllegalArgumentException("Unsupported decor version " + root.get("version"));
    int cap = root.has("maxBlocks") ? integer(root, "maxBlocks", 1, HARD_CAP) : 8000;

    JsonObject floor = object(root, "floor");
    only(floor, "floor", "inner", "outer", "innerMargin", "accent", "hearth");
    Material inner = block(floor, "inner"), outer = block(floor, "outer");
    Material accent = floor.has("accent") ? block(floor, "accent") : null;
    Material hearth = floor.has("hearth") ? block(floor, "hearth") : null;
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
    boolean shell = false;
    if (root.has("underside")) {
      JsonObject o = object(root, "underside");
      only(o, "underside", "blocks", "depthFactor", "minDepth", "maxDepth", "shell");
      under = blocks(o, "blocks");
      if (o.has("depthFactor")) factor = number(o, "depthFactor", 0.1, 3);
      if (o.has("minDepth")) minD = integer(o, "minDepth", 1, 40);
      if (o.has("maxDepth")) maxD = integer(o, "maxDepth", 1, 40);
      if (o.has("shell")) shell = bool(o, "shell");
      if (minD > maxD) throw new IllegalArgumentException("underside.minDepth is above maxDepth");
    }
    DecorConfig config =
        new DecorConfig(cap, inner, outer, accent, hearth, margin, fireBlock, bench, rail, lamp, every, under, factor, minD, maxD);
    config.shell = shell;
    if (root.has("menhirs")) {
      JsonObject o = object(root, "menhirs");
      only(o, "menhirs", "block", "top");
      config.menhir = block(o, "block");
      config.menhirTop = o.has("top") ? block(o, "top") : null;
    }
    if (root.has("village")) {
      JsonObject o = object(root, "village");
      only(o, "village", "width", "ground", "path", "wall", "log", "roof", "glass", "leaves", "fence", "lamp",
          "upper", "window", "roofStairs", "stone", "spire", "landmarks", "models");
      int width = o.has("width") ? integer(o, "width", 0, 24) : 16;
      Material stairs = o.has("roofStairs") ? block(o, "roofStairs") : Material.DARK_OAK_STAIRS;
      if (!stairs.name().endsWith("_STAIRS"))
        throw new IllegalArgumentException("village.roofStairs must be a stairs block, not " + stairs.name().toLowerCase(Locale.ROOT));
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
                o.has("lamp") ? block(o, "lamp") : Material.LANTERN,
                o.has("upper") ? block(o, "upper") : null,
                o.has("window") ? block(o, "window") : null,
                stairs,
                o.has("stone") ? block(o, "stone") : Material.STONE_BRICKS,
                o.has("spire") ? block(o, "spire") : Material.DEEPSLATE_TILES,
                !o.has("landmarks") || bool(o, "landmarks"),
                !o.has("models") || bool(o, "models"));
    }
    // Fail at load time rather than at the first game: the biggest platform must fit under the cap.
    config.generate(PlatformLayout.MAX_PLAYERS);
    return config;
  }

  // ------------------------------------------------------------------ the decor

  /** Every block of the decor for this many players; throws when the config exceeds its own cap. */
  public List<Placement> generate(int players) {
    PlatformLayout layout = PlatformLayout.of(players);
    double radius = layout.platformRadius(), ring = layout.ringRadius();
    int reach = (int) Math.ceil(radius);
    Map<Long, Placement> blocks = new LinkedHashMap<>();

    // Floor: a disc, planks near the fire and a different block on the outer walkway. With an accent,
    // eight spokes and a circle mark the floor like a sundial; with a hearth, a dark round under the fire.
    List<int[]> edge = new ArrayList<>();
    double boundary = ring + innerMargin;
    for (int x = -reach; x <= reach; x++)
      for (int z = -reach; z <= reach; z++) {
        if (!inDisc(x, z, radius)) continue;
        double d = Math.hypot(x, z);
        Material m = d <= boundary ? floorInner : floorOuter;
        if (floorAccent != null && d > 1.6 && (spoke(x, z, 8) || Math.abs(d - boundary) < 0.5)) m = floorAccent;
        if (hearth != null && d <= 1.6) m = hearth;
        put(blocks, x, 0, z, m);
        if (!inDisc(x + 1, z, radius) || !inDisc(x - 1, z, radius) || !inDisc(x, z + 1, radius) || !inDisc(x, z - 1, radius))
          edge.add(new int[] {x, z});
      }

    // Underside: the floating island, narrowing layer by layer.
    if (!underside.isEmpty()) {
      if (shell && village != null) keel(blocks, radius + village.width());
      else {
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
    }

    put(blocks, 0, 1, 0, campfire);

    // A bench block just behind every seat, alternating between the configured blocks.
    Set<Long> taken = new HashSet<>();
    for (var seat : layout.seats()) taken.add(cell((int) Math.round(seat.x()), (int) Math.round(seat.z())));
    if (!bench.isEmpty()) {
      // Seat cells stay free, and two benches never share a cell: step outwards until the cell is clear.
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
      for (int[] c : edge) {
        put(blocks, c[0], 1, c[1], railing);
        taken.add(cell(c[0], c[1]));
      }
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
    if (menhir != null) menhirs(blocks, layout, taken);
    if (village != null) village(blocks, radius, players);
    connect(blocks);
    if (blocks.size() > maxBlocks)
      throw new IllegalArgumentException(
          "Decor for " + players + " players needs " + blocks.size() + " blocks, over the cap of " + maxBlocks);
    return List.copyOf(blocks.values());
  }

  /** The display models of the decor for this many players (none without a village or with models off). */
  public List<Model> models(int players) {
    if (village == null || !village.models() || !village.landmarks()) return List.of();
    double radius = PlatformLayout.of(players).platformRadius(), outer = radius + village.width();
    List<Model> out = new ArrayList<>();
    // The red moon, beside the belfry (not behind its spire) and close enough to stay inside a short view distance.
    out.add(new Model("moon", outer * 0.45, 30, -(outer + 16), 0, 11, 'y', 0, true, true, 6));
    for (Landmark l : landmarks(radius)) {
      double x = l.x(), z = l.z();
      float facing = (float) Math.toDegrees(Math.atan2(x, -z)); // towards the fire
      switch (l.kind()) {
        case BELFRY -> {
          out.add(new Model("belfry_crown_bell", x, BELFRY_CHAMBER + 1.6, z, facing, 3, 'y', 0, false, false, 3));
          out.add(new Model("belfry_crown_vane", x, BELFRY_TIP + 1, z, facing, 3.5f, 'y', 9, false, false, 4));
        }
        case MILL -> {
          double len = Math.hypot(x, z), ux = -x / len, uz = -z / len;
          out.add(new Model("mill_sails", x + ux * 3.6, MILL_HUB, z + uz * 3.6, facing, 4.5f, 'z', 24, false, false, 4));
        }
        default -> {}
      }
    }
    return List.copyOf(out);
  }

  // ---------------------------------------------------------------- the island

  /**
   * A hollow keel under the whole island: each layer is a rough ring a couple of blocks thick (the
   * inside is never seen), the last one closes it; roots and chained lanterns hang from the rim and a
   * few stone fangs point down.
   */
  private void keel(Map<Long, Placement> blocks, double outer) {
    int depth = (int) Math.max(minDepth, Math.min(maxDepth, Math.round(outer * depthFactor)));
    int span = (int) Math.ceil(outer + 2);
    double prev = outer;
    for (int k = 1; k <= depth; k++) {
      double t = k / (depth + 1.0);
      double base = outer * Math.pow(1 - t, 0.8);
      double thick = Math.max(1.3, prev - base + 1.1);
      prev = base;
      for (int x = -span; x <= span; x++)
        for (int z = -span; z <= span; z++) {
          double d = Math.hypot(x, z), a = Math.atan2(x, z);
          // Rough, but always under the grass: nothing pokes out past the rim.
          double r = Math.min(outer - 0.3, base * (1 + 0.07 * Math.sin(3 * a + k * 0.7) + 0.04 * Math.sin(7 * a - k * 1.3)));
          if (d > r || (k < depth && d <= r - thick)) continue;
          put(blocks, x, -k, z, underside.get(pick(x, z, k, underside.size())));
        }
    }
    // Fangs: short stone columns under the keel ending in a dripstone tip.
    int fangs = 7;
    for (int i = 0; i < fangs; i++) {
      double a = 2 * Math.PI * i / fangs + 0.4, r = outer * (0.18 + 0.1 * (i % 3));
      int x = (int) Math.round(r * Math.sin(a)), z = (int) Math.round(r * Math.cos(a));
      // The fang hangs from the lowest keel block of its column (the keel is hollow: scan the whole depth).
      int top = 0;
      for (int y = -1; y >= -depth - 1; y--) if (blocks.containsKey(key(x, y, z))) top = y;
      if (top == 0) continue;
      int len = 2 + (i * 5) % 5;
      for (int j = 0; j < len; j++) put(blocks, x, top - 1 - j, z, underside.get(pick(x, z, j, underside.size())));
      put(blocks, x, top - 1 - len, z, Material.POINTED_DRIPSTONE, "vertical_direction=down,thickness=tip");
    }
    // Roots under the overhanging rim, and chains carrying lanterns.
    // Roots hang where the grass overhangs the first keel layer (its rough edge leaves a lip here and there).
    for (int x = -span; x <= span; x++)
      for (int z = -span; z <= span; z++) {
        double d = Math.hypot(x, z);
        if (d > outer || d < outer * 0.8 || blocks.containsKey(key(x, -1, z))) continue;
        if (Math.floorMod(x * 31 + z * 17 + x * z, 3) == 0) put(blocks, x, -1, z, Material.HANGING_ROOTS);
      }
    for (int i = 0; i < 10; i++) {
      double a = 2 * Math.PI * (i + 0.25) / 10;
      int x = (int) Math.round((outer - 1.5) * Math.sin(a)), z = (int) Math.round((outer - 1.5) * Math.cos(a));
      int len = 2 + (i * 3) % 4;
      boolean free = true;
      for (int j = 1; j <= len + 1; j++) free &= !blocks.containsKey(key(x, -j, z));
      if (!free) continue;
      for (int j = 1; j <= len; j++) put(blocks, x, -j, z, Material.IRON_CHAIN);
      put(blocks, x, -len - 1, z, Material.LANTERN, "hanging=true");
    }
  }

  /** Standing stones just inside the rail, between the seats: low, so they frame the players without hiding them. */
  private void menhirs(Map<Long, Placement> blocks, PlatformLayout layout, Set<Long> taken) {
    int n = layout.seats().size();
    int count = Math.max(4, Math.min(12, (n + 1) / 2));
    double r = layout.platformRadius() - 1.6;
    for (int i = 0; i < count; i++) {
      double a = 2 * Math.PI * (i + 0.5) / count;
      // Between two seats: nudge the angle to the middle of the nearest gap.
      double gap = 2 * Math.PI / n;
      a = (Math.floor(a / gap) + 0.5) * gap;
      int x = (int) Math.round(r * Math.sin(a)), z = (int) Math.round(r * Math.cos(a));
      if (!taken.add(cell(x, z))) continue;
      int h = 2 + i % 2;
      for (int y = 1; y <= h; y++) put(blocks, x, y, z, menhir);
      if (menhirTop != null) put(blocks, x, h + 1, z, menhirTop);
    }
  }

  // ---------------------------------------------------------------- the hamlet

  enum Kind { BELFRY, MILL, OAK }

  record Landmark(Kind kind, int x, int z, int index) {}

  static final int BELFRY_CHAMBER = 15, BELFRY_TIP = 28, MILL_HUB = 11;

  /** Where the three landmarks stand: the belfry opposite seat 0, the mill and the old oak a third of a turn away. */
  private List<Landmark> landmarks(double radius) {
    if (village == null || !village.landmarks()) return List.of();
    int n = homeCount(radius);
    double homes = homesRadius(radius);
    List<Landmark> out = new ArrayList<>();
    Kind[] kinds = {Kind.BELFRY, Kind.MILL, Kind.OAK};
    for (int k = 0; k < 3; k++) {
      double want = Math.PI + k * 2 * Math.PI / 3;
      int i = (int) Math.round(want / (2 * Math.PI) * n) % n;
      double a = 2 * Math.PI * i / n;
      double r = homes + (kinds[k] == Kind.BELFRY ? 1.5 : kinds[k] == Kind.MILL ? 1 : 0.5);
      out.add(new Landmark(kinds[k], (int) Math.round(r * Math.sin(a)), (int) Math.round(r * Math.cos(a)), i));
    }
    return out;
  }

  private double homesRadius(double radius) {
    return radius + village.width() * 0.6;
  }

  private int homeCount(double radius) {
    return (int) Math.max(5, Math.min(24, Math.floor(2 * Math.PI * homesRadius(radius) / 12)));
  }

  /** Grass, a lane, houses facing the fire, the landmarks, trees, lamp posts and flowers on a ring around the platform. */
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
    double homes = homesRadius(radius);
    int n = homeCount(radius);
    Map<Integer, Landmark> marks = new java.util.HashMap<>();
    for (Landmark l : landmarks(radius)) marks.put(l.index(), l);
    for (int i = 0; i < n; i++) {
      double a = 2 * Math.PI * i / n;
      int hx = (int) Math.round(homes * Math.sin(a)), hz = (int) Math.round(homes * Math.cos(a));
      Landmark l = marks.get(i);
      // A lane from the ring path to the door.
      for (double r = radius + 3.5; r < homes - 2.5; r += 0.5)
        put(blocks, (int) Math.round(r * Math.sin(a)), 0, (int) Math.round(r * Math.cos(a)), v.path());
      if (l == null) house(blocks, hx, hz, v, i);
      else
        switch (l.kind()) {
          case BELFRY -> belfry(blocks, l.x(), l.z(), v);
          case MILL -> mill(blocks, l.x(), l.z(), v);
          case OAK -> oak(blocks, l.x(), l.z(), v);
        }
      double b = 2 * Math.PI * (i + 0.5) / n;
      boolean nextToOak = l != null && l.kind() == Kind.OAK || marks.containsKey((i + 1) % n) && marks.get((i + 1) % n).kind() == Kind.OAK;
      if (!nextToOak) tree(blocks, (int) Math.round((homes + 1.5) * Math.sin(b)), (int) Math.round((homes + 1.5) * Math.cos(b)), v, i);
      int lx = (int) Math.round((radius + 2.5) * Math.sin(b)), lz = (int) Math.round((radius + 2.5) * Math.cos(b));
      for (int y = 1; y <= 3; y++) put(blocks, lx, y, lz, v.fence());
      put(blocks, lx, 4, lz, v.lamp());
      // Flowers and fireflies in the gardens between the houses.
      for (int k = 0; k < 6; k++) {
        double fr = homes - 4 + (k * 7 % 9), fa = b + (k - 2.5) * 0.06;
        int fx = (int) Math.round(fr * Math.sin(fa)), fz = (int) Math.round(fr * Math.cos(fa));
        if (Math.hypot(fx, fz) > outer - 1.5 || Math.hypot(fx, fz) <= radius + 3.5) continue;
        if (blocks.containsKey(key(fx, 1, fz))) continue;
        Material[] garden = {Material.FIREFLY_BUSH, Material.WILDFLOWERS, Material.SHORT_GRASS, Material.BUSH, Material.FIREFLY_BUSH, Material.CORNFLOWER};
        put(blocks, fx, 1, fz, garden[(i + k) % garden.length]);
      }
    }
  }

  /** Direction names, the way stairs and doors face, for a step (dx, dz). */
  private static String facing(int dx, int dz) {
    if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? "east" : "west";
    return dz > 0 ? "south" : "north";
  }

  /**
   * A half-timbered house: a stone or plank ground floor, a jettied plaster storey with lit windows, a
   * steep hipped roof of stairs and a smoking chimney; every third one is a single-storey cottage.
   * Its door faces the campfire, a lantern hangs over it.
   */
  private static void house(Map<Long, Placement> blocks, int cx, int cz, Village v, int index) {
    boolean doorOnX = Math.abs(cx) > Math.abs(cz);
    int doorX = doorOnX ? (cx > 0 ? -2 : 2) : 0, doorZ = doorOnX ? 0 : (cz > 0 ? -2 : 2);
    boolean tall = index % 3 != 2 && v.upper() != null;
    Material window = v.window() != null ? v.window() : v.glass();
    String lit = window == Material.REDSTONE_LAMP ? "lit=true" : null;
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++) {
        boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
        put(blocks, cx + dx, 0, cz + dz, v.wall());
        if (!wall) continue;
        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
        for (int y = 1; y <= 3; y++) {
          if (dx == doorX && dz == doorZ && y <= 2) continue; // the doorway
          boolean isWindow = y == 2 && !corner && (dx == 0 || dz == 0);
          if (isWindow) put(blocks, cx + dx, y, cz + dz, window, lit);
          else put(blocks, cx + dx, y, cz + dz, corner ? v.log() : v.wall());
        }
      }
    // A lantern lights the doorway, hanging from the jetty (or the eaves).
    int ox = Integer.signum(doorX), oz = Integer.signum(doorZ);
    int top = 3;
    if (tall) {
      // The jetty: a beam floor one block wider, then the plaster storey with timber posts.
      for (int dx = -3; dx <= 3; dx++)
        for (int dz = -3; dz <= 3; dz++) {
          boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
          put(blocks, cx + dx, 4, cz + dz, rim ? v.log() : v.wall(), rim ? axis(v.log(), Math.abs(dz) == 3 && Math.abs(dx) < 3 ? "x" : Math.abs(dx) == 3 && Math.abs(dz) < 3 ? "z" : null) : null);
          if (!rim) continue;
          boolean post = Math.abs(dx) == 3 && Math.abs(dz) == 3 || dx == 0 || dz == 0;
          for (int y = 5; y <= 6; y++) {
            boolean isWindow = y == 5 && (Math.abs(dx) == 1 || Math.abs(dz) == 1) && !(Math.abs(dx) == 3 && Math.abs(dz) == 3);
            if (isWindow) put(blocks, cx + dx, y, cz + dz, window, lit);
            else put(blocks, cx + dx, y, cz + dz, post ? v.log() : v.upper());
          }
        }
      put(blocks, cx + ox * 3, 3, cz + oz * 3, Material.LANTERN, "hanging=true");
      top = 6;
    }
    // The roof: rings of stairs climbing to a ridge, corners filled, overhanging the walls by one.
    int half = tall ? 4 : 3;
    for (int layer = 0; half - layer >= 0; layer++) {
      int h = half - layer, y = top + 1 + layer;
      for (int dx = -h; dx <= h; dx++)
        for (int dz = -h; dz <= h; dz++) {
          boolean rim = Math.abs(dx) == h || Math.abs(dz) == h;
          if (!rim) continue;
          if (h == 0 || Math.abs(dx) == h && Math.abs(dz) == h) put(blocks, cx + dx, y, cz + dz, v.roof());
          else {
            // A stair climbs towards the middle: its high side faces inwards.
            String face = Math.abs(dx) == h ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
            put(blocks, cx + dx, y, cz + dz, v.roofStairs(), "facing=" + face + ",half=bottom,shape=straight");
          }
        }
      if (h == 0) break;
    }
    if (!tall) put(blocks, cx + ox * 3, 3, cz + oz * 3, Material.LANTERN, "hanging=true");
    // A chimney through the back slope, smoking.
    int chx = cx - ox * 2 + (doorOnX ? 0 : 1), chz = cz - oz * 2 + (doorOnX ? 1 : 0);
    for (int y = top + 1; y <= top + half + 1; y++) put(blocks, chx, y, chz, v.stone());
    put(blocks, chx, top + half + 2, chz, Material.CAMPFIRE, "lit=true,signal_fire=false");
  }

  /**
   * The belfry, the village's tallest landmark: a stone tower with lit lancet windows, an open bell
   * chamber (the bell is a display model) lit from inside, and a slate spire carrying a wolf weathervane.
   */
  private static void belfry(Map<Long, Placement> blocks, int cx, int cz, Village v) {
    int r = 3;
    for (int dx = -r; dx <= r; dx++)
      for (int dz = -r; dz <= r; dz++) {
        boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
        boolean wall = Math.abs(dx) == r || Math.abs(dz) == r;
        put(blocks, cx + dx, 0, cz + dz, v.stone());
        if (!wall) continue;
        for (int y = 1; y < BELFRY_CHAMBER; y++) {
          boolean slit = !corner && (dx == 0 || dz == 0) && (y == 5 || y == 6 || y == 10 || y == 11);
          if (slit) put(blocks, cx + dx, y, cz + dz, v.window() != null ? v.window() : v.glass(), v.window() == Material.REDSTONE_LAMP ? "lit=true" : null);
          else put(blocks, cx + dx, y, cz + dz, corner ? Material.CHISELED_STONE_BRICKS : v.stone());
        }
        // Buttresses: the corners step out at the foot.
        if (corner) for (int y = 1; y <= 4; y++) put(blocks, cx + dx + Integer.signum(dx), y, cz + dz + Integer.signum(dz), v.stone());
      }
    // Chamber floor, four open arches (corner pillars only), a cornice, then the spire.
    for (int dx = -r; dx <= r; dx++)
      for (int dz = -r; dz <= r; dz++) put(blocks, cx + dx, BELFRY_CHAMBER - 1, cz + dz, v.stone());
    for (int y = BELFRY_CHAMBER; y <= BELFRY_CHAMBER + 3; y++)
      for (int sx : new int[] {-r, r})
        for (int sz : new int[] {-r, r}) put(blocks, cx + sx, y, cz + sz, v.stone());
    put(blocks, cx, BELFRY_CHAMBER, cz, Material.LIGHT, "level=15");
    for (int sx : new int[] {-r, r})
      for (int sz : new int[] {-r, r}) put(blocks, cx + sx - Integer.signum(sx), BELFRY_CHAMBER + 3, cz + sz - Integer.signum(sz), Material.LANTERN, "hanging=true");
    int base = BELFRY_CHAMBER + 4;
    for (int dx = -r - 1; dx <= r + 1; dx++)
      for (int dz = -r - 1; dz <= r + 1; dz++)
        if (Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1 || (Math.abs(dx) <= r && Math.abs(dz) <= r))
          put(blocks, cx + dx, base, cz + dz, Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1 ? v.stone() : v.spire());
    // The spire: a slate pyramid, two layers per step, hollow inside.
    int y = base + 1;
    for (int h = r; h >= 0; h--) {
      int layers = h == 0 ? BELFRY_TIP - y + 1 : 2;
      for (int rep = 0; rep < layers && y <= BELFRY_TIP; rep++, y++)
        for (int dx = -h; dx <= h; dx++)
          for (int dz = -h; dz <= h; dz++)
            if (Math.abs(dx) == h || Math.abs(dz) == h) put(blocks, cx + dx, y, cz + dz, v.spire());
    }
  }

  /**
   * The mill: a round stone foot, a timber body, a pointed cap; its sails (a spinning display model) face
   * the fire. A lit window looks out under the sails.
   */
  private static void mill(Map<Long, Placement> blocks, int cx, int cz, Village v) {
    for (int y = 0; y <= MILL_HUB + 1; y++) {
      double r = 3.4 - y * 0.08;
      int span = 4;
      for (int dx = -span; dx <= span; dx++)
        for (int dz = -span; dz <= span; dz++) {
          double d = Math.hypot(dx, dz);
          if (d > r || (y > 0 && d <= r - 1.2)) continue;
          Material m = y == 0 ? v.stone() : y <= 4 ? Material.COBBLESTONE : v.upper() != null ? v.upper() : v.wall();
          if (y > 4 && (Math.abs(dx) + Math.abs(dz) == 3) && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) m = v.log();
          put(blocks, cx + dx, y, cz + dz, m);
        }
    }
    // Facing the fire: a lit window under the hub, a beam carrying the sails.
    double len = Math.hypot(cx, cz);
    int fx = (int) Math.round(-cx / len * 3), fz = (int) Math.round(-cz / len * 3);
    Material window = v.window() != null ? v.window() : v.glass();
    put(blocks, cx + fx, 7, cz + fz, window, window == Material.REDSTONE_LAMP ? "lit=true" : null);
    put(blocks, cx + fx, 3, cz + fz, window, window == Material.REDSTONE_LAMP ? "lit=true" : null);
    put(blocks, cx + fx, 1, cz + fz, Material.AIR);
    put(blocks, cx + fx, 2, cz + fz, Material.AIR);
    // The cap: a cone of stairs-coloured roof blocks.
    for (int layer = 0; layer <= 5; layer++) {
      double r = 3.6 - layer * 0.7;
      int y = MILL_HUB + 2 + layer;
      for (int dx = -4; dx <= 4; dx++)
        for (int dz = -4; dz <= 4; dz++) {
          double d = Math.hypot(dx, dz);
          if (d <= r && d > r - 1.3 || (r < 1 && dx == 0 && dz == 0)) put(blocks, cx + dx, y, cz + dz, v.roof());
        }
    }
  }

  /** The old oak: a thick trunk with roots, four crooked limbs, a broad crown hung with lanterns. */
  private static void oak(Map<Long, Placement> blocks, int cx, int cz, Village v) {
    Material log = Material.DARK_OAK_LOG;
    for (int y = 1; y <= 11; y++)
      for (int dx = 0; dx <= 1; dx++)
        for (int dz = 0; dz <= 1; dz++) put(blocks, cx + dx, y, cz + dz, log);
    int[][] roots = {{-1, 0}, {2, 1}, {0, 2}, {1, -1}, {-1, 1}, {2, 0}};
    for (int[] r : roots) put(blocks, cx + r[0], 1, cz + r[1], log);
    put(blocks, cx - 2, 1, cz, log, "axis=x");
    put(blocks, cx + 3, 1, cz + 1, log, "axis=x");
    int[][] limbs = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
    List<int[]> crowns = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      int sx = limbs[i][0], sz = limbs[i][1];
      int x = cx + (sx > 0 ? 1 : 0), z = cz + (sz > 0 ? 1 : 0), y = 7 + i % 2;
      for (int s = 0; s < 4; s++) {
        x += sx;
        z += sz;
        if (s % 2 == 1) y++;
        put(blocks, x, y, z, log);
      }
      crowns.add(new int[] {x, y + 2, z});
    }
    crowns.add(new int[] {cx, 14, cz});
    Set<Long> leaves = new HashSet<>();
    for (int[] c : crowns)
      for (int dx = -5; dx <= 5; dx++)
        for (int dy = -3; dy <= 3; dy++)
          for (int dz = -5; dz <= 5; dz++) {
            double e = dx * dx / 20.0 + dy * dy / 7.5 + dz * dz / 20.0;
            if (e > 1 || e < 0.42) continue; // a shell: the inside is never seen
            int x = c[0] + dx, y = c[1] + dy, z = c[2] + dz;
            if (blocks.containsKey(key(x, y, z))) continue;
            put(blocks, x, y, z, v.leaves(), persistent(v.leaves()));
            leaves.add(key(x, y, z));
          }
    // Lanterns hang under the crown, one per limb.
    for (int[] c : crowns.subList(0, 4)) {
      int y = c[1] - 4;
      while (y < c[1] && !leaves.contains(key(c[0], y + 1, c[2]))) y++;
      if (!blocks.containsKey(key(c[0], y, c[2]))) put(blocks, c[0], y, c[2], Material.LANTERN, "hanging=true");
    }
  }

  private static void tree(Map<Long, Placement> blocks, int cx, int cz, Village v, int index) {
    int top = 5 + index % 3;
    for (int dx = -2; dx <= 2; dx++)
      for (int dy = top - 2; dy <= top + 1; dy++)
        for (int dz = -2; dz <= 2; dz++)
          if (dx * dx + (dy - top + 0.5) * (dy - top + 0.5) * 1.3 + dz * dz <= 5.2)
            put(blocks, cx + dx, dy, cz + dz, v.leaves(), persistent(v.leaves()));
    for (int y = 1; y <= top; y++) put(blocks, cx, y, cz, v.log());
  }

  /**
   * Fences, walls and panes are written without physics, so they would stand as lone posts: give each
   * one the connections to its neighbours of the same family (and walls their post).
   */
  private static void connect(Map<Long, Placement> blocks) {
    String[] sides = {"north", "east", "south", "west"};
    int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    for (var e : List.copyOf(blocks.entrySet())) {
      Placement p = e.getValue();
      String kind = family(p.material());
      if (kind == null || p.state() != null) continue;
      StringBuilder state = new StringBuilder();
      for (int i = 0; i < 4; i++) {
        Placement n = blocks.get(key(p.dx() + steps[i][0], p.dy(), p.dz() + steps[i][1]));
        boolean joined = n != null && kind.equals(family(n.material()));
        if (state.length() > 0) state.append(',');
        state.append(sides[i]).append('=').append(kind.equals("wall") ? (joined ? "low" : "none") : joined);
      }
      if (kind.equals("wall")) state.append(",up=true");
      blocks.put(e.getKey(), new Placement(p.dx(), p.dy(), p.dz(), p.material(), state.toString()));
    }
  }

  private static String family(Material m) {
    String n = m.name();
    if (n.endsWith("_FENCE")) return n.contains("NETHER_BRICK") ? "nether_fence" : "fence";
    if (n.endsWith("_WALL") && !n.contains("SIGN") && !n.contains("BANNER") && !n.contains("HEAD") && !n.contains("SKULL") && !n.contains("TORCH") && !n.contains("FAN")) return "wall";
    if (n.endsWith("_PANE") || n.equals("IRON_BARS")) return "pane";
    return null;
  }

  /** A log's axis, or null for a block that has none (a config may put planks where logs go). */
  private static String axis(Material m, String axis) {
    if (axis == null) return null;
    String n = m.name();
    return n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") ? "axis=" + axis : null;
  }

  /** Leaves placed by the decor never decay, even far from a log. */
  private static String persistent(Material m) {
    return m.name().endsWith("_LEAVES") ? "persistent=true" : null;
  }

  /** Whether (x, z) lies on one of {@code count} spokes from the centre. */
  private static boolean spoke(int x, int z, int count) {
    double a = Math.atan2(x, z), step = 2 * Math.PI / count;
    double off = a - Math.round(a / step) * step;
    return Math.abs(Math.hypot(x, z) * Math.sin(off)) < 0.5;
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

  private static long key(int x, int y, int z) {
    return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
  }

  private static void put(Map<Long, Placement> blocks, int x, int y, int z, Material m) {
    put(blocks, x, y, z, m, null);
  }

  /** AIR removes what an earlier step put there (a doorway cut in a wall). */
  private static void put(Map<Long, Placement> blocks, int x, int y, int z, Material m, String state) {
    if (m == Material.AIR) blocks.remove(key(x, y, z));
    else blocks.put(key(x, y, z), new Placement(x, y, z, m, state));
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

  private static boolean bool(JsonObject o, String key) {
    JsonElement e = o.get(key);
    if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())
      throw new IllegalArgumentException("'" + key + "' must be true or false");
    return e.getAsBoolean();
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
