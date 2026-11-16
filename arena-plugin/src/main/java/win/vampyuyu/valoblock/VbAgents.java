package win.vampyuyu.valoblock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The agent catalogue: agents/index.json (roster order, roles, ultimate points) and one file per agent, validated as a whole. */
public final class VbAgents {
  public record Role(String name, String color, String text) {}

  public record Ultimate(int perKill, int perOrb, int perRound) {}

  private final Map<String, VbAgent> byId;
  private final List<Role> roles;
  private final Ultimate ultimate;

  private VbAgents(Map<String, VbAgent> byId, List<Role> roles, Ultimate ultimate) {
    this.byId = byId;
    this.roles = roles;
    this.ultimate = ultimate;
  }

  public VbAgent get(String id) {
    return byId.get(id);
  }

  /** In roster order (the menu's row 3). */
  public List<VbAgent> all() {
    return List.copyOf(byId.values());
  }

  public List<String> ids() {
    return List.copyOf(byId.keySet());
  }

  public List<Role> roles() {
    return roles;
  }

  public Ultimate ultimate() {
    return ultimate;
  }

  /** The index lists the agent ids; {@code files} maps each id to its parsed file. */
  public static VbAgents parse(JsonObject index, Map<String, JsonObject> files) {
    List<Role> roles = new ArrayList<>();
    if (!index.has("roles") || !index.get("roles").isJsonArray()) throw new IllegalArgumentException("agents/index.json: missing 'roles'");
    for (JsonElement e : index.getAsJsonArray("roles")) {
      JsonObject r = e.getAsJsonObject();
      roles.add(new Role(str(r, "name", "agents/index.json"), str(r, "color", "agents/index.json"), str(r, "text", "agents/index.json")));
    }
    JsonObject u = index.has("ultimate") ? index.getAsJsonObject("ultimate") : new JsonObject();
    Ultimate ult = new Ultimate((int) num(u, "perKill", 1), (int) num(u, "perOrb", 1), (int) num(u, "perRound", 1));
    if (ult.perKill() < 0 || ult.perOrb() < 0 || ult.perRound() < 0) throw new IllegalArgumentException("agents/index.json: ultimate points cannot be negative");
    Map<String, VbAgent> out = new LinkedHashMap<>();
    for (String id : ids(index)) {
      JsonObject f = files.get(id);
      if (f == null) throw new IllegalArgumentException("Missing data file valoblock/agents/" + id + ".json");
      VbAgent a = agent(id, f, roles);
      out.put(id, a);
    }
    if (out.isEmpty()) throw new IllegalArgumentException("agents/index.json: at least one agent");
    return new VbAgents(out, List.copyOf(roles), ult);
  }

  /** The agent ids of an index file. */
  public static List<String> ids(JsonObject index) {
    if (!index.has("agents") || !index.get("agents").isJsonArray()) throw new IllegalArgumentException("agents/index.json: missing 'agents'");
    List<String> ids = new ArrayList<>();
    for (JsonElement e : index.getAsJsonArray("agents")) {
      String id = e.getAsString();
      if (!id.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("agents/index.json: bad agent id " + id);
      if (ids.contains(id)) throw new IllegalArgumentException("agents/index.json: duplicate agent " + id);
      ids.add(id);
    }
    return ids;
  }

  private static VbAgent agent(String id, JsonObject f, List<Role> roles) {
    String at = "agents/" + id + ".json";
    if (!id.equals(str(f, "id", at))) throw new IllegalArgumentException(at + ": 'id' must be " + id);
    String role = str(f, "role", at);
    if (roles.stream().noneMatch(r -> r.name().equals(role))) throw new IllegalArgumentException(at + ": unknown role " + role);
    JsonObject face = f.getAsJsonObject("face");
    if (face == null) throw new IllegalArgumentException(at + ": missing 'face'");
    List<String> rows = new ArrayList<>();
    for (JsonElement e : face.getAsJsonArray("rows")) rows.add(e.getAsString());
    Map<String, String> palette = new LinkedHashMap<>();
    for (var en : face.getAsJsonObject("palette").entrySet()) palette.put(en.getKey(), en.getValue().getAsString());
    if (rows.size() != 8) throw new IllegalArgumentException(at + ": the face needs 8 rows");
    for (String r : rows) {
      if (r.length() != 8) throw new IllegalArgumentException(at + ": face rows need 8 pixels");
      for (char c : r.toCharArray()) if (!palette.containsKey(String.valueOf(c))) throw new IllegalArgumentException(at + ": face pixel '" + c + "' has no palette colour");
    }
    JsonObject skin = f.has("skin") ? f.getAsJsonObject("skin") : new JsonObject();
    String value = skin.has("value") ? skin.get("value").getAsString() : "";
    String sig = skin.has("signature") ? skin.get("signature").getAsString() : "";
    JsonObject colors = f.has("colors") ? f.getAsJsonObject("colors") : new JsonObject();
    List<VbAbility> abilities = new ArrayList<>();
    for (JsonElement e : f.getAsJsonArray("abilities")) abilities.add(ability(e.getAsJsonObject(), at));
    if (abilities.size() != 4) throw new IllegalArgumentException(at + ": an agent has exactly 4 abilities");
    for (int slot = 4; slot <= 7; slot++) {
      final int s = slot;
      if (abilities.stream().filter(a -> a.slot() == s).count() != 1) throw new IllegalArgumentException(at + ": needs exactly one ability in slot " + slot);
    }
    return new VbAgent(
        id, str(f, "name", at), role, str(f, "roleColor", at), str(f, "realm", at), str(f, "quote", at), str(f, "menuColor", at), palette, rows, value, sig,
        colors.has("glow") ? colors.get("glow").getAsString() : "#ffffff", colors.has("trim") ? colors.get("trim").getAsString() : "#ffffff",
        List.copyOf(abilities));
  }

  private static VbAbility ability(JsonObject a, String at) {
    String id = str(a, "id", at);
    int slot = (int) num(a, "slot", 0);
    if (slot < 4 || slot > 7) throw new IllegalArgumentException(at + " " + id + ": slot must be 4-7");
    int cost = (int) num(a, "cost", 0), ult = (int) num(a, "ultPoints", 0);
    int max = (int) num(a, "maxCharges", 1), charges = (int) num(a, "charges", 1), rk = (int) num(a, "rechargeKills", 0);
    if (cost < 0 || cost > 9000 || max < 1 || max > 5 || charges < 1 || charges > 5 || rk < 0) throw new IllegalArgumentException(at + " " + id + ": invalid cost or charges");
    if (slot == 7 && ult < 1) throw new IllegalArgumentException(at + " " + id + ": an ultimate needs 'ultPoints'");
    Map<String, Double> nums = new LinkedHashMap<>();
    if (a.has("num")) for (var en : a.getAsJsonObject("num").entrySet()) nums.put(en.getKey(), en.getValue().getAsDouble());
    return new VbAbility(slot, id, str(a, "name", at), str(a, "item", at), str(a, "text", at), cost, max, charges, rk, ult, Map.copyOf(nums));
  }

  private static String str(JsonObject o, String key, String at) {
    if (!o.has(key) || !o.get(key).isJsonPrimitive()) throw new IllegalArgumentException(at + ": missing '" + key + "'");
    return o.get(key).getAsString();
  }

  private static double num(JsonObject o, String key, double def) {
    return o.has(key) ? o.get(key).getAsDouble() : def;
  }
}
