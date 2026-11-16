package win.vampyuyu.valoblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import win.vampyuyu.werewolf.DataFiles;

/**
 * ValoBlock data files: shipped in the jar under /valoblock/, overridable from
 * {@code <plugin data>/overrides/valoblock/} (see {@link DataFiles}). {@link #reload()} reads
 * everything first and swaps it in only when all of it is valid: a broken file throws and the old
 * data stays.
 */
public final class VbData {
  public static final String MATCH = "/valoblock/match.json";
  public static final String SITES = "/valoblock/sites.json";
  public static final String WEAPONS = "/valoblock/weapons.json";
  public static final String BOTS = "/valoblock/bots.json";
  public static final String MAP = "/valoblock/map.json";
  public static final String AGENTS_INDEX = "/valoblock/agents/index.json";
  /** The maps that ship (valoblock/maps/&lt;id&gt;.json); the first is the default. */
  public static final List<String> MAP_IDS = List.of("hourfall", "rootvault", "stormbreak");
  public static final List<String> FILES;

  static {
    List<String> f = new ArrayList<>(List.of(MATCH, SITES, WEAPONS, AGENTS_INDEX, BOTS, MAP));
    for (String id : MAP_IDS) f.add(mapFile(id));
    FILES = List.copyOf(f);
  }

  /** Resource path of one map file. */
  public static String mapFile(String id) {
    return "/valoblock/maps/" + id + ".json";
  }

  /** Resource path of one agent file. */
  public static String agentFile(String id) {
    return "/valoblock/agents/" + id + ".json";
  }

  private static volatile VbRules rules;
  private static volatile List<VbSite> sites;
  private static volatile VbWeapons weapons;
  private static volatile VbAgents agents;
  private static volatile BotConfig bots;
  private static volatile Waypoints waypoints;
  private static volatile java.util.Map<String, VbMap> maps = java.util.Map.of();
  // The map of the running match, at its slot: absolute coordinates. Null: the placeholder files above.
  private static volatile List<VbSite> activeSites;
  private static volatile Waypoints activeWaypoints;

  private VbData() {}

  public static VbRules rules() {
    VbRules r = rules;
    if (r == null) {
      reload();
      r = rules;
    }
    return r;
  }

  public static List<VbSite> sites() {
    rules();
    List<VbSite> a = activeSites;
    return a != null ? a : sites;
  }

  /** The maps by id (relative coordinates). */
  public static java.util.Map<String, VbMap> maps() {
    rules();
    return maps;
  }

  /** The running match's map was built at an origin: sites and bot graph now follow it. */
  public static void activate(List<VbSite> mapSites, Waypoints graph) {
    activeSites = mapSites;
    activeWaypoints = graph;
  }

  /** Back to the placeholder files. */
  public static void deactivate() {
    activeSites = null;
    activeWaypoints = null;
  }

  public static VbWeapons weapons() {
    rules();
    return weapons;
  }

  public static VbAgents agents() {
    rules();
    return agents;
  }

  public static BotConfig bots() {
    rules();
    return bots;
  }

  /** The waypoint graph bots walk on (a real map replaces the placeholder in a later slice). */
  public static Waypoints waypoints() {
    rules();
    Waypoints a = activeWaypoints;
    return a != null ? a : waypoints;
  }

  public static synchronized void reload() {
    VbRules newRules = VbRules.parse(read(MATCH));
    List<VbSite> newSites = parseSites(read(SITES));
    VbWeapons newWeapons = VbWeapons.parse(read(WEAPONS));
    JsonObject index = read(AGENTS_INDEX);
    java.util.Map<String, JsonObject> files = new java.util.LinkedHashMap<>();
    for (String id : VbAgents.ids(index)) files.put(id, read(agentFile(id)));
    VbAgents newAgents = VbAgents.parse(index, files);
    BotConfig newBots = BotConfig.parse(read(BOTS));
    Waypoints newWaypoints = Waypoints.parse(read(MAP), newSites);
    java.util.Map<String, VbMap> newMaps = new java.util.LinkedHashMap<>();
    for (String id : MAP_IDS) {
      VbMap m = VbMap.parse(read(mapFile(id)));
      if (!m.id().equals(id)) throw new IllegalArgumentException("maps/" + id + ".json: its id is " + m.id());
      newMaps.put(id, m);
    }
    for (String f : newBots.primaryOrder())
      if (newWeapons.get(f) == null || !newWeapons.get(f).isGun()) throw new IllegalArgumentException("bots.json: primaryOrder names an unknown gun " + f);
    maps = java.util.Collections.unmodifiableMap(newMaps);
    rules = newRules;
    bots = newBots;
    waypoints = newWaypoints;
    sites = newSites;
    weapons = newWeapons;
    agents = newAgents;
  }

  static List<VbSite> parseSites(JsonObject j) {
    if (!j.has("sites") || !j.get("sites").isJsonArray()) throw new IllegalArgumentException("sites.json: missing 'sites'");
    List<VbSite> out = new ArrayList<>();
    for (JsonElement e : j.getAsJsonArray("sites")) {
      JsonObject s = e.getAsJsonObject();
      String id = s.get("id").getAsString();
      if (out.stream().anyMatch(o -> o.id().equals(id))) throw new IllegalArgumentException("sites.json: duplicate site " + id);
      out.add(new VbSite(id, s.has("name") ? s.get("name").getAsString() : id, triple(s, "min"), triple(s, "max")));
    }
    if (out.isEmpty()) throw new IllegalArgumentException("sites.json: at least one site");
    return List.copyOf(out);
  }

  private static double[] triple(JsonObject s, String key) {
    JsonArray a = s.getAsJsonArray(key);
    if (a == null || a.size() != 3) throw new IllegalArgumentException("sites.json: '" + key + "' needs 3 numbers");
    return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
  }

  private static JsonObject read(String resource) {
    try (InputStream in = DataFiles.open(VbData.class, resource)) {
      if (in == null) throw new IllegalArgumentException("Missing data file " + resource.substring(1));
      return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (IOException | RuntimeException e) {
      if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
      throw new IllegalArgumentException(resource.substring(1) + ": " + e.getMessage(), e);
    }
  }

  /** The data files read from the override folder, for /vb reload. */
  public static List<String> overrides() {
    List<String> all = new ArrayList<>(FILES);
    VbAgents a = agents;
    if (a != null) for (String id : a.ids()) all.add(agentFile(id));
    return all.stream().filter(f -> DataFiles.override(f) != null).map(f -> f.substring(1)).toList();
  }
}
