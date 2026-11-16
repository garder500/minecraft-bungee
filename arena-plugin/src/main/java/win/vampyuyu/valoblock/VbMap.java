package win.vampyuyu.valoblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * One ValoBlock map (valoblock/maps/&lt;id&gt;.json): the 30x30 plan the greybox is built from and the
 * markers (spawns, buy zones, barriers, sites, orbs, bot waypoints). Pure: no world access. Every
 * coordinate in the file is in blocks from the map's min corner (x, z) and from the standing feet (y),
 * so the same file works wherever the map is built; {@link #sites}, {@link #waypoints} and
 * {@link #spawn} translate them to an origin.
 */
public final class VbMap {
  /** What a block of the greybox is made of: the plugin turns a kind into a material with the palette. */
  public enum Kind {
    FLOOR,
    WALL,
    SITE,
    ATTACK,
    DEFEND,
    FEATURE,
    HIGH,
    CRATE,
    FOLIAGE,
    WATER,
    BARRIER
  }

  /** Receives the blocks of the greybox, in coordinates relative to the origin (y 0 = feet level). */
  public interface Sink {
    void set(int x, int y, int z, Kind kind);
  }

  public record Box(double[] min, double[] max) {
    public boolean contains(double x, double y, double z) {
      return x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2];
    }
  }

  public record Barrier(VbSide side, Box box) {}

  private final String id, name, realm, feature;
  private final int cell, wallHeight;
  private final Map<String, String> palette;
  private final List<String> plan;
  private final Map<VbSide, List<double[]>> spawns;
  private final Map<VbSide, Box> buyZones;
  private final List<Barrier> barriers;
  private final List<VbSite> sites;
  private final List<double[]> orbs;
  private final JsonObject waypoints;
  private final JsonObject source;

  private VbMap(JsonObject j) {
    source = j;
    id = text(j, "id");
    name = text(j, "name");
    realm = j.has("realm") ? j.get("realm").getAsString() : "";
    feature = j.has("feature") ? j.get("feature").getAsString() : "";
    cell = (int) VbRules.d(j, "cell");
    wallHeight = (int) VbRules.d(j, "wallHeight");
    if (cell < 1 || cell > 16) throw new IllegalArgumentException(id + ": cell must be 1 to 16");
    if (wallHeight < 2 || wallHeight > 12) throw new IllegalArgumentException(id + ": wallHeight must be 2 to 12");
    palette = new LinkedHashMap<>();
    for (var e : VbRules.obj(j, "palette").entrySet()) palette.put(e.getKey(), e.getValue().getAsString());
    for (Kind k : Kind.values())
      if (k != Kind.BARRIER && !palette.containsKey(k.name().toLowerCase(Locale.ROOT)) && !palette.containsKey(paletteKey(k)))
        throw new IllegalArgumentException(id + ": palette lacks '" + paletteKey(k) + "'");
    List<String> rows = new ArrayList<>();
    JsonArray p = j.getAsJsonArray("plan");
    if (p == null || p.isEmpty()) throw new IllegalArgumentException(id + ": missing 'plan'");
    for (JsonElement e : p) rows.add(e.getAsString());
    int w = rows.get(0).length();
    for (String r : rows) {
      if (r.length() != w) throw new IllegalArgumentException(id + ": plan rows must all be " + w + " cells wide");
      for (char c : r.toCharArray())
        if ("#.RBAFhcgw".indexOf(c) < 0) throw new IllegalArgumentException(id + ": unknown plan character '" + c + "'");
    }
    plan = List.copyOf(rows);
    spawns = new EnumMap<>(VbSide.class);
    buyZones = new EnumMap<>(VbSide.class);
    JsonObject sp = VbRules.obj(j, "spawns"), bz = VbRules.obj(j, "buyZones");
    for (VbSide s : VbSide.values()) {
      JsonArray a = sp.getAsJsonArray(s.name());
      if (a == null || a.isEmpty()) throw new IllegalArgumentException(id + ": no spawn point for " + s.name());
      List<double[]> pts = new ArrayList<>();
      for (JsonElement e : a) pts.add(triple(e, id + ": spawn"));
      spawns.put(s, List.copyOf(pts));
      JsonObject b = bz.getAsJsonObject(s.name());
      if (b == null) throw new IllegalArgumentException(id + ": no buy zone for " + s.name());
      buyZones.put(s, box(b));
    }
    List<Barrier> bl = new ArrayList<>();
    JsonArray ba = j.getAsJsonArray("barriers");
    if (ba != null)
      for (JsonElement e : ba) {
        JsonObject b = e.getAsJsonObject();
        VbSide side;
        try {
          side = VbSide.valueOf(text(b, "side"));
        } catch (IllegalArgumentException ex) {
          throw new IllegalArgumentException(id + ": barrier names an unknown side");
        }
        bl.add(new Barrier(side, box(b)));
      }
    barriers = List.copyOf(bl);
    sites = VbData.parseSites(j);
    orbs = new ArrayList<>();
    JsonArray oa = j.getAsJsonArray("orbs");
    if (oa != null) for (JsonElement e : oa) orbs.add(triple(e, id + ": orb"));
    waypoints = VbRules.obj(j, "waypoints");
    // Fail now, not at the first match: the graph must be consistent with the sites and reachable.
    waypoints(0, 0, 0);
    double wd = width(), dd = depth();
    for (var list : spawns.values())
      for (double[] s : list)
        if (s[0] < 0 || s[2] < 0 || s[0] > wd || s[2] > dd) throw new IllegalArgumentException(id + ": a spawn point is outside the plan");
    for (VbSite s : sites)
      if (s.min()[0] < 0 || s.min()[2] < 0 || s.max()[0] > wd || s.max()[2] > dd) throw new IllegalArgumentException(id + ": site " + s.id() + " is outside the plan");
  }

  public static VbMap parse(JsonObject j) {
    return new VbMap(j);
  }

  /** The plan's own JSON, for the editor. */
  public JsonObject source() {
    return source;
  }

  private static String paletteKey(Kind k) {
    return switch (k) {
      case ATTACK -> "attack";
      case DEFEND -> "defend";
      default -> k.name().toLowerCase(Locale.ROOT);
    };
  }

  /** The material name of a kind (Bukkit's Material name), or null for BARRIER (always the barrier block). */
  public String material(Kind k) {
    return k == Kind.BARRIER ? "BARRIER" : palette.get(paletteKey(k));
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String realm() {
    return realm;
  }

  public String feature() {
    return feature;
  }

  public List<String> plan() {
    return plan;
  }

  public int cell() {
    return cell;
  }

  public int wallHeight() {
    return wallHeight;
  }

  /** Blocks along x. */
  public int width() {
    return plan.get(0).length() * cell;
  }

  /** Blocks along z. */
  public int depth() {
    return plan.size() * cell;
  }

  public List<double[]> orbs() {
    return orbs;
  }

  public List<Barrier> barriers() {
    return barriers;
  }

  public Box buyZone(VbSide side) {
    return buyZones.get(side);
  }

  public List<double[]> spawnPoints(VbSide side) {
    return spawns.get(side);
  }

  /** One spawn point (the index wraps) at an origin. */
  public double[] spawn(VbSide side, int index, double ox, double oy, double oz) {
    List<double[]> l = spawns.get(side);
    double[] p = l.get(Math.floorMod(index, l.size()));
    return new double[] {p[0] + ox, p[1] + oy, p[2] + oz};
  }

  /** The seed sites at an origin. */
  public List<VbSite> sites(double ox, double oy, double oz) {
    List<VbSite> out = new ArrayList<>();
    for (VbSite s : sites)
      out.add(new VbSite(s.id(), s.name(), new double[] {s.min()[0] + ox, s.min()[1] + oy, s.min()[2] + oz}, new double[] {s.max()[0] + ox, s.max()[1] + oy, s.max()[2] + oz}));
    return out;
  }

  public List<VbSite> sites() {
    return sites(0, 0, 0);
  }

  /** The bot graph at an origin (validated against the sites: unknown nodes or unreachable sites throw). */
  public Waypoints waypoints(double ox, double oy, double oz) {
    JsonObject copy = waypoints.deepCopy();
    for (JsonElement e : copy.getAsJsonArray("nodes")) shift(e.getAsJsonObject().getAsJsonArray("pos"), ox, oy, oz);
    for (var site : VbRules.obj(copy, "siteData").entrySet()) {
      JsonObject s = site.getValue().getAsJsonObject();
      if (s.has("smokes"))
        for (JsonElement sm : s.getAsJsonArray("smokes")) shift(sm.getAsJsonObject().getAsJsonArray("at"), ox, oy, oz);
    }
    try {
      return Waypoints.parse(copy, sites(ox, oy, oz));
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(id + ": " + e.getMessage(), e);
    }
  }

  private static void shift(JsonArray a, double x, double y, double z) {
    double[] d = {x, y, z};
    for (int i = 0; i < 3; i++) a.set(i, new com.google.gson.JsonPrimitive(a.get(i).getAsDouble() + d[i]));
  }

  /** The barrier blocks (buy phase): every cell of each barrier box. */
  public void barrierBlocks(Sink sink) {
    for (Barrier b : barriers) fill(b.box(), sink);
  }

  private static void fill(Box b, Sink sink) {
    for (int x = (int) Math.floor(b.min()[0]); x < (int) Math.ceil(b.max()[0]); x++)
      for (int y = (int) Math.floor(b.min()[1]); y < (int) Math.ceil(b.max()[1]); y++)
        for (int z = (int) Math.floor(b.min()[2]); z < (int) Math.ceil(b.max()[2]); z++) sink.set(x, y, z, Kind.BARRIER);
  }

  /**
   * The greybox: floor everywhere (4 blocks per cell by default), walls {@code wallHeight} high, crates and
   * foliage 2 high, high ground 1 high, a water pit for the sea and an invisible border all round.
   * The floor top is y -1 (feet level is 0).
   */
  public void greybox(Sink sink) {
    for (int row = 0; row < plan.size(); row++)
      for (int col = 0; col < plan.get(row).length(); col++) {
        char c = plan.get(row).charAt(col);
        for (int dx = 0; dx < cell; dx++)
          for (int dz = 0; dz < cell; dz++) {
            int x = col * cell + dx, z = row * cell + dz;
            switch (c) {
              case 'w' -> {
                sink.set(x, -5, z, Kind.WALL);
                for (int y = -4; y <= -2; y++) sink.set(x, y, z, Kind.WATER);
              }
              case '#' -> {
                sink.set(x, -1, z, Kind.FLOOR);
                for (int y = 0; y < wallHeight; y++) sink.set(x, y, z, Kind.WALL);
              }
              case 'h' -> {
                sink.set(x, -1, z, Kind.FLOOR);
                sink.set(x, 0, z, Kind.HIGH);
              }
              case 'c' -> {
                sink.set(x, -1, z, Kind.FLOOR);
                sink.set(x, 0, z, Kind.CRATE);
                sink.set(x, 1, z, Kind.CRATE);
              }
              case 'g' -> {
                sink.set(x, -1, z, Kind.FLOOR);
                sink.set(x, 0, z, Kind.FOLIAGE);
                sink.set(x, 1, z, Kind.FOLIAGE);
              }
              case 'A' -> sink.set(x, -1, z, Kind.SITE);
              case 'R' -> sink.set(x, -1, z, Kind.ATTACK);
              case 'B' -> sink.set(x, -1, z, Kind.DEFEND);
              case 'F' -> sink.set(x, -1, z, Kind.FEATURE);
              default -> sink.set(x, -1, z, Kind.FLOOR);
            }
          }
      }
    int w = width(), d = depth();
    for (int y = -1; y < 8; y++) {
      for (int x = -1; x <= w; x++) {
        sink.set(x, y, -1, Kind.BARRIER);
        sink.set(x, y, d, Kind.BARRIER);
      }
      for (int z = 0; z < d; z++) {
        sink.set(-1, y, z, Kind.BARRIER);
        sink.set(w, y, z, Kind.BARRIER);
      }
    }
  }

  /** The most the map can reach below, above and to the sides (for clearing). */
  public int lowestY() {
    return -5;
  }

  private static String text(JsonObject o, String key) {
    if (!o.has(key)) throw new IllegalArgumentException("map: missing '" + key + "'");
    return o.get(key).getAsString();
  }

  private static double[] triple(JsonElement e, String what) {
    if (!e.isJsonArray() || e.getAsJsonArray().size() != 3) throw new IllegalArgumentException(what + " needs 3 numbers");
    JsonArray a = e.getAsJsonArray();
    return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
  }

  private static Box box(JsonObject o) {
    double[] a = triple(o.get("min"), "box min"), b = triple(o.get("max"), "box max");
    double[] lo = new double[3], hi = new double[3];
    for (int i = 0; i < 3; i++) {
      lo[i] = Math.min(a[i], b[i]);
      hi[i] = Math.max(a[i], b[i]);
    }
    return new Box(lo, hi);
  }
}
