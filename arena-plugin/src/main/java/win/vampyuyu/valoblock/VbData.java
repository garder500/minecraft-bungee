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
  public static final List<String> FILES = List.of(MATCH, SITES);

  private static volatile VbRules rules;
  private static volatile List<VbSite> sites;

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
    return sites;
  }

  public static synchronized void reload() {
    VbRules newRules = VbRules.parse(read(MATCH));
    List<VbSite> newSites = parseSites(read(SITES));
    rules = newRules;
    sites = newSites;
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
    return FILES.stream().filter(f -> DataFiles.override(f) != null).map(f -> f.substring(1)).toList();
  }
}
