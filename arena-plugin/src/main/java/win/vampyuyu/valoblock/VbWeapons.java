package win.vampyuyu.valoblock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The weapon catalogue (valoblock/weapons.json), validated as a whole like the other ValoBlock data. */
public final class VbWeapons {
  private final Map<String, VbWeapon> byId;
  private final String defaultSidearm;
  private final String melee;

  private VbWeapons(Map<String, VbWeapon> byId, String defaultSidearm, String melee) {
    this.byId = byId;
    this.defaultSidearm = defaultSidearm;
    this.melee = melee;
  }

  public VbWeapon get(String id) {
    return byId.get(id);
  }

  public List<VbWeapon> all() {
    return List.copyOf(byId.values());
  }

  public VbWeapon defaultSidearm() {
    return byId.get(defaultSidearm);
  }

  public VbWeapon melee() {
    return byId.get(melee);
  }

  public static VbWeapons parse(JsonObject j) {
    double legsFactor = num(j, "legsFactor", 0.85, "weapons.json");
    if (legsFactor <= 0 || legsFactor > 1) throw new IllegalArgumentException("weapons.json: legsFactor must be in (0, 1]");
    if (!j.has("weapons") || !j.get("weapons").isJsonArray()) throw new IllegalArgumentException("weapons.json: missing 'weapons'");
    Map<String, VbWeapon> out = new LinkedHashMap<>();
    for (JsonElement e : j.getAsJsonArray("weapons")) {
      VbWeapon w = weapon(e.getAsJsonObject(), legsFactor);
      if (out.put(w.id(), w) != null) throw new IllegalArgumentException("weapons.json: duplicate weapon " + w.id());
    }
    String sidearm = str(j, "defaultSidearm", "weapons.json");
    String melee = str(j, "melee", "weapons.json");
    VbWeapon s = out.get(sidearm), m = out.get(melee);
    if (s == null || s.slot() != VbWeapon.Slot.SIDEARM || !s.free()) throw new IllegalArgumentException("weapons.json: defaultSidearm must be a free sidearm");
    if (m == null || m.slot() != VbWeapon.Slot.MELEE) throw new IllegalArgumentException("weapons.json: melee must be a melee item");
    List<String> cells = new ArrayList<>();
    for (VbWeapon w : out.values()) {
      if (w.menuRow() < 0) continue;
      String cell = w.menuRow() + "," + w.menuCol();
      if (cells.contains(cell)) throw new IllegalArgumentException("weapons.json: two items in menu cell " + cell);
      cells.add(cell);
    }
    return new VbWeapons(out, sidearm, melee);
  }

  private static VbWeapon weapon(JsonObject w, double legsFactor) {
    String id = str(w, "id", "weapons.json");
    String at = "weapons.json " + id;
    if (!id.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("weapons.json: bad id " + id);
    VbWeapon.Slot slot;
    VbWeapon.Mode mode;
    try {
      slot = VbWeapon.Slot.valueOf(str(w, "slot", at).toUpperCase(Locale.ROOT));
      mode = VbWeapon.Mode.valueOf(w.has("mode") ? w.get("mode").getAsString().toUpperCase(Locale.ROOT) : "SEMI");
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(at + ": unknown slot or mode");
    }
    int price = (int) num(w, "price", 0, at);
    int body = (int) num(w, "body", 0, at);
    int head = (int) num(w, "head", 0, at);
    int legs = w.has("legs") ? (int) num(w, "legs", 0, at) : (int) Math.round(body * legsFactor);
    int mag = (int) num(w, "magazine", 0, at);
    double rate = num(w, "rate", 0, at);
    int shield = (int) num(w, "shield", 0, at);
    int[] menu = {-1, -1};
    if (w.has("menu") && w.get("menu").isJsonArray()) {
      menu[0] = w.getAsJsonArray("menu").get(0).getAsInt();
      menu[1] = w.getAsJsonArray("menu").get(1).getAsInt();
      if (menu[0] < 0 || menu[0] > 4 || menu[1] < 0 || menu[1] > 8) throw new IllegalArgumentException(at + ": menu cell out of the 5x9 grid (row 6 is for abilities)");
    }
    if (price < 0 || price > 9000) throw new IllegalArgumentException(at + ": price must be 0-9000");
    if (slot == VbWeapon.Slot.SHIELD && shield <= 0) throw new IllegalArgumentException(at + ": a shield needs a positive 'shield' value");
    if ((slot == VbWeapon.Slot.PRIMARY || slot == VbWeapon.Slot.SIDEARM) && (mag < 1 || rate <= 0 || body < 1 || head < body))
      throw new IllegalArgumentException(at + ": a gun needs magazine, rate, body damage and head >= body");
    int pellets = (int) num(w, "pellets", 1, at);
    int burst = (int) num(w, "burst", 1, at);
    if (pellets < 1 || pellets > 20 || burst < 1 || burst > 10) throw new IllegalArgumentException(at + ": invalid pellets or burst");
    double fs = num(w, "falloffStart", 40, at), fe = num(w, "falloffEnd", 90, at), fm = num(w, "falloffMin", 1, at);
    if (fe < fs || fm < 0 || fm > 1) throw new IllegalArgumentException(at + ": invalid falloff");
    JsonObject r = w.has("recoil") ? w.getAsJsonObject("recoil") : new JsonObject();
    VbWeapon.Recoil recoil = new VbWeapon.Recoil(num(r, "pitch", 0, at), num(r, "yaw", 0, at), (int) num(r, "climb", 1, at), num(r, "drift", 0, at));
    return new VbWeapon(
        id, str(w, "name", at), price, str(w, "kind", at), str(w, "metal", at), str(w, "metalName", at), str(w, "texture", at), slot,
        w.has("base") ? w.get("base").getAsString() : "STICK", menu[0], menu[1], head, body, legs, pellets, rate, mode, burst,
        num(w, "burstGap", 0.06, at), mag, num(w, "reload", 2, at), num(w, "spread", 0, at), num(w, "sneakMult", 1, at),
        num(w, "moveMult", 1, at), num(w, "sprayGrow", 0, at), w.has("firstShotPerfect") && w.get("firstShotPerfect").getAsBoolean(), fs, fe, fm,
        num(w, "range", 120, at), (int) num(w, "penetration", 0, at), num(w, "penetrationFactor", 1, at), num(w, "spinUp", 0, at),
        num(w, "slowOnHit", 0, at), num(w, "zoom", 1, at), num(w, "scopedSlow", 0, at), recoil, shield, (int) num(w, "backstab", 0, at),
        num(w, "reach", 3, at), num(w, "speed", 1, at), w.has("killSound") ? w.get("killSound").getAsString() : "", str(w, "note", at));
  }

  private static String str(JsonObject o, String key, String at) {
    if (!o.has(key) || !o.get(key).isJsonPrimitive()) throw new IllegalArgumentException(at + ": missing '" + key + "'");
    return o.get(key).getAsString();
  }

  private static double num(JsonObject o, String key, double def, String at) {
    if (!o.has(key)) return def;
    try {
      return o.get(key).getAsDouble();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(at + ": '" + key + "' must be a number");
    }
  }
}
