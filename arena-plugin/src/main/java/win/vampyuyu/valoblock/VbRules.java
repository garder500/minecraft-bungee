package win.vampyuyu.valoblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Match rules, timers and the Shards economy (valoblock/match.json). Pure data. */
public record VbRules(
    int teamSize,
    int winScore,
    int swapAfter,
    int agentSelectSeconds,
    int buySeconds,
    int combatSeconds,
    int fuseSeconds,
    int roundEndSeconds,
    double plantHoldSeconds,
    double sealSeconds,
    double sealCheckpointSeconds,
    int maxHealth,
    List<Integer> shields,
    Economy economy) {

  public record Economy(
      int pistolCredits,
      int roundWon,
      List<Integer> roundLost,
      int kill,
      int plantTeam,
      int cap,
      int overtimeCredits) {
    /** Loss bonus after {@code streak} consecutive lost rounds (1 = the first loss). */
    public int lossBonus(int streak) {
      if (streak < 1) return 0;
      return roundLost.get(Math.min(streak, roundLost.size()) - 1);
    }
  }

  public VbRules {
    if (teamSize < 1 || teamSize > 10) throw new IllegalArgumentException("teamSize must be 1-10");
    if (winScore < 1 || swapAfter < 1 || swapAfter >= winScore * 2)
      throw new IllegalArgumentException("Invalid winScore/swapAfter");
    if (winScore != swapAfter + 1)
      throw new IllegalArgumentException("winScore must be swapAfter + 1 (first to 7, swap after 6)");
    for (int s : new int[] {agentSelectSeconds, buySeconds, combatSeconds, fuseSeconds, roundEndSeconds})
      if (s < 1 || s > 3600) throw new IllegalArgumentException("Phase timers must be 1-3600 seconds");
    if (plantHoldSeconds <= 0 || sealSeconds <= 0 || sealCheckpointSeconds < 0 || sealCheckpointSeconds >= sealSeconds)
      throw new IllegalArgumentException("Invalid plant/seal hold times");
    if (maxHealth < 1) throw new IllegalArgumentException("maxHealth must be positive");
    if (shields.isEmpty() || shields.stream().anyMatch(v -> v < 0)) throw new IllegalArgumentException("Invalid shields");
    if (economy.roundLost.isEmpty() || economy.cap < economy.pistolCredits)
      throw new IllegalArgumentException("Invalid economy");
    shields = List.copyOf(shields);
  }

  public static VbRules parse(JsonObject j) {
    JsonObject e = obj(j, "economy");
    return new VbRules(
        i(j, "teamSize"), i(j, "winScore"), i(j, "swapAfter"), i(j, "agentSelectSeconds"), i(j, "buySeconds"),
        i(j, "combatSeconds"), i(j, "fuseSeconds"), i(j, "roundEndSeconds"), d(j, "plantHoldSeconds"),
        d(j, "sealSeconds"), d(j, "sealCheckpointSeconds"), i(j, "maxHealth"), ints(j, "shields"),
        new Economy(
            i(e, "pistolCredits"), i(e, "roundWon"), ints(e, "roundLost"), i(e, "kill"), i(e, "plantTeam"),
            i(e, "cap"), i(e, "overtimeCredits")));
  }

  static JsonObject obj(JsonObject j, String key) {
    if (!j.has(key) || !j.get(key).isJsonObject()) throw new IllegalArgumentException("Missing object '" + key + "'");
    return j.getAsJsonObject(key);
  }

  static int i(JsonObject j, String key) {
    return (int) d(j, key);
  }

  static double d(JsonObject j, String key) {
    if (!j.has(key) || !j.get(key).isJsonPrimitive() || !j.get(key).getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException("Missing number '" + key + "'");
    return j.get(key).getAsDouble();
  }

  static List<Integer> ints(JsonObject j, String key) {
    if (!j.has(key) || !j.get(key).isJsonArray()) throw new IllegalArgumentException("Missing list '" + key + "'");
    JsonArray a = j.getAsJsonArray(key);
    return a.asList().stream().map(x -> x.getAsInt()).toList();
  }
}
