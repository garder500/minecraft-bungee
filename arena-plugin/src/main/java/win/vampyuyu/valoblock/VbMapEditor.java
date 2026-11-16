package win.vampyuyu.valoblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Edits the markers of a map file (the /vb map tool): spawn points, site, buy zone and barrier corners,
 * orbs and bot waypoints, all in the file's relative coordinates. Pure JSON work; {@link #check()}
 * parses the result exactly like the plugin does, so a bad edit is refused before it is saved.
 */
public final class VbMapEditor {
  private final JsonObject json;

  public VbMapEditor(JsonObject source) {
    this.json = source.deepCopy();
  }

  public JsonObject json() {
    return json;
  }

  private static JsonArray point(double x, double y, double z) {
    JsonArray a = new JsonArray();
    for (double v : new double[] {x, y, z}) a.add(new JsonPrimitive(Math.round(v * 100) / 100.0));
    return a;
  }

  private static JsonArray block(double x, double y, double z) {
    return point(Math.floor(x), Math.floor(y), Math.floor(z));
  }

  /** Sets spawn point number {@code index} (1 based; one past the end adds it). */
  public String spawn(VbSide side, int index, double x, double y, double z) {
    JsonArray a = json.getAsJsonObject("spawns").getAsJsonArray(side.name());
    if (index < 1 || index > a.size() + 1) throw new IllegalArgumentException("Spawn " + index + " of " + side.label() + ": the list has " + a.size());
    if (index > a.size()) a.add(point(x, y, z));
    else a.set(index - 1, point(x, y, z));
    return side.label() + " spawn " + index + " set";
  }

  /** Moves one corner (min or max) of a "site", "buy" or "barrier" box. {@code key} is a site id or a side name. */
  public String corner(String kind, String key, boolean max, double x, double y, double z) {
    JsonObject box = switch (kind) {
      case "site" -> find(json.getAsJsonArray("sites"), "id", key);
      case "buy" -> json.getAsJsonObject("buyZones").getAsJsonObject(side(key));
      case "barrier" -> find(json.getAsJsonArray("barriers"), "side", side(key));
      default -> throw new IllegalArgumentException("Unknown box kind " + kind + " (site, buy, barrier)");
    };
    if (box == null) throw new IllegalArgumentException("No " + kind + " " + key);
    box.add(max ? "max" : "min", block(x, y, z));
    return kind + " " + key + " " + (max ? "max" : "min") + " set";
  }

  private static String side(String key) {
    String k = key.toUpperCase(java.util.Locale.ROOT);
    if (k.startsWith("SEEDB") || k.equals("ATTACK")) return VbSide.SEEDBEARERS.name();
    if (k.startsWith("SEAL") || k.equals("DEFEND")) return VbSide.SEALERS.name();
    throw new IllegalArgumentException("Side must be seedbearers or sealers");
  }

  private static JsonObject find(JsonArray list, String field, String value) {
    if (list == null) return null;
    for (JsonElement e : list) {
      JsonObject o = e.getAsJsonObject();
      if (o.get(field).getAsString().equalsIgnoreCase(value)) return o;
    }
    return null;
  }

  /** Sets orb number {@code index} (1 based, one past the end adds it). */
  public String orb(int index, double x, double y, double z) {
    JsonArray a = json.getAsJsonArray("orbs");
    if (a == null) json.add("orbs", a = new JsonArray());
    if (index < 1 || index > a.size() + 1) throw new IllegalArgumentException("Orb " + index + ": the list has " + a.size());
    if (index > a.size()) a.add(point(x, y, z));
    else a.set(index - 1, point(x, y, z));
    return "orb " + index + " set";
  }

  /** Adds a waypoint node, or moves it when the id exists. */
  public String node(String id, double x, double y, double z) {
    JsonArray nodes = json.getAsJsonObject("waypoints").getAsJsonArray("nodes");
    JsonObject n = find(nodes, "id", id);
    if (n == null) {
      n = new JsonObject();
      n.addProperty("id", id);
      nodes.add(n);
    }
    n.add("pos", point(x, y, z));
    return "node " + id + " set";
  }

  public String link(String a, String b) {
    JsonObject wp = json.getAsJsonObject("waypoints");
    for (String id : new String[] {a, b})
      if (find(wp.getAsJsonArray("nodes"), "id", id) == null) throw new IllegalArgumentException("No node " + id);
    for (JsonElement e : wp.getAsJsonArray("links")) {
      JsonArray l = e.getAsJsonArray();
      if ((l.get(0).getAsString().equals(a) && l.get(1).getAsString().equals(b)) || (l.get(0).getAsString().equals(b) && l.get(1).getAsString().equals(a)))
        return "already linked";
    }
    JsonArray l = new JsonArray();
    l.add(a);
    l.add(b);
    wp.getAsJsonArray("links").add(l);
    return "linked " + a + " and " + b;
  }

  /** Removes a node and every link that touches it (spawn, anchor... references must be fixed by the caller: check() tells). */
  public String removeNode(String id) {
    JsonObject wp = json.getAsJsonObject("waypoints");
    JsonArray nodes = wp.getAsJsonArray("nodes"), keep = new JsonArray(), links = new JsonArray();
    boolean found = false;
    for (JsonElement e : nodes) {
      if (e.getAsJsonObject().get("id").getAsString().equals(id)) found = true;
      else keep.add(e);
    }
    if (!found) throw new IllegalArgumentException("No node " + id);
    for (JsonElement e : wp.getAsJsonArray("links")) {
      JsonArray l = e.getAsJsonArray();
      if (!l.get(0).getAsString().equals(id) && !l.get(1).getAsString().equals(id)) links.add(l);
    }
    wp.add("nodes", keep);
    wp.add("links", links);
    return "node " + id + " removed";
  }

  /** Parses the edited file like the plugin does; throws what is wrong. */
  public VbMap check() {
    return VbMap.parse(json);
  }
}
