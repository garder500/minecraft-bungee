package win.vampyuyu.valoblock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bot levels, buy rules, movement and ability numbers (valoblock/bots.json). Pure data. */
public record BotConfig(
    String defaultLevel,
    Map<String, Level> levels,
    int fullBuyAt,
    List<String> primaryOrder,
    int saveShieldMin,
    double speed,
    double groupRadius,
    double stageTimeoutSeconds,
    int rotateCount,
    double gunfireSiteRadius,
    double pickupRadius,
    double sealRadius,
    double sightRange,
    double ultRange,
    int ultMinEnemies,
    int ultDamage,
    double smokeSeconds,
    double smokeRadius,
    double flashRange,
    double flashSeconds,
    Map<String, String> abilityKinds) {

  /** A skill level: how long a bot takes to react to a newly seen enemy, how wide its aim wanders, how much of the recoil it cancels. */
  public record Level(String id, int reactionMs, double aimErrorDeg, double recoilControl) {
    public Level {
      if (reactionMs < 0 || aimErrorDeg < 0 || recoilControl < 0 || recoilControl > 1)
        throw new IllegalArgumentException("Bot level " + id + ": reactionMs/aimErrorDeg must be >= 0, recoilControl 0-1");
    }
  }

  public BotConfig {
    if (!levels.containsKey(defaultLevel)) throw new IllegalArgumentException("bots.json: defaultLevel " + defaultLevel + " is not a level");
    if (fullBuyAt < 0 || primaryOrder.isEmpty()) throw new IllegalArgumentException("bots.json: invalid buy rules");
    if (speed <= 0 || groupRadius <= 0 || rotateCount < 0) throw new IllegalArgumentException("bots.json: invalid move numbers");
    levels = Map.copyOf(levels);
    abilityKinds = Map.copyOf(abilityKinds);
  }

  public Level level(String id) {
    Level l = levels.get(id == null ? "" : id.toLowerCase(java.util.Locale.ROOT));
    if (l == null) throw new IllegalArgumentException("Unknown bot level " + id + " (" + String.join(", ", levels.keySet()) + ")");
    return l;
  }

  public Level defaultLevelObject() {
    return levels.get(defaultLevel);
  }

  /** "smoke", "flash" or "none" for an ability id (the ultimate is handled by slot). */
  public String kind(String abilityId) {
    return abilityKinds.getOrDefault(abilityId, "none");
  }

  public static BotConfig parse(JsonObject j) {
    JsonObject lv = VbRules.obj(j, "levels"), buy = VbRules.obj(j, "buy"), move = VbRules.obj(j, "move"), combat = VbRules.obj(j, "combat");
    Map<String, Level> levels = new LinkedHashMap<>();
    for (var e : lv.entrySet()) {
      JsonObject l = e.getValue().getAsJsonObject();
      levels.put(e.getKey(), new Level(e.getKey(), VbRules.i(l, "reactionMs"), VbRules.d(l, "aimErrorDeg"), VbRules.d(l, "recoilControl")));
    }
    Map<String, String> kinds = new LinkedHashMap<>();
    JsonObject k = VbRules.obj(j, "abilityKinds");
    for (var e : k.entrySet()) {
      String v = e.getValue().getAsString();
      if (!List.of("smoke", "flash", "none").contains(v)) throw new IllegalArgumentException("bots.json: ability kind " + v + " (smoke, flash or none)");
      kinds.put(e.getKey(), v);
    }
    JsonElement dl = j.get("defaultLevel");
    if (dl == null) throw new IllegalArgumentException("bots.json: missing defaultLevel");
    return new BotConfig(
        dl.getAsString(), levels, VbRules.i(buy, "fullBuyAt"),
        buy.getAsJsonArray("primaryOrder").asList().stream().map(JsonElement::getAsString).toList(),
        VbRules.i(buy, "saveShieldMin"), VbRules.d(move, "speed"), VbRules.d(move, "groupRadius"),
        VbRules.d(move, "stageTimeoutSeconds"), VbRules.i(move, "rotateCount"), VbRules.d(move, "gunfireSiteRadius"),
        VbRules.d(move, "pickupRadius"), VbRules.d(move, "sealRadius"), VbRules.d(combat, "sightRange"),
        VbRules.d(combat, "ultRange"), VbRules.i(combat, "ultMinEnemies"), VbRules.i(combat, "ultDamage"),
        VbRules.d(combat, "smokeSeconds"), VbRules.d(combat, "smokeRadius"), VbRules.d(combat, "flashRange"),
        VbRules.d(combat, "flashSeconds"), kinds);
  }
}
