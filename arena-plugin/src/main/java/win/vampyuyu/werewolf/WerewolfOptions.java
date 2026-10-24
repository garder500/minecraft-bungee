package win.vampyuyu.werewolf;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import win.vampyuyu.arena.ArenaConfig;

/**
 * The Werewolf side of an arena's {@linkplain ArenaConfig#options() options}: the rules and the
 * phase timers, parsed once per configuration. Files keep these flat beside the generic fields:
 * {@code werewolf}, {@code discussionSeconds}, {@code voteSeconds}, {@code nightSeconds}. A {@code
 * places} key written by older versions is ignored here and kept untouched in the file (the seats
 * now come from the sky platform).
 */
public record WerewolfOptions(
    WerewolfRules rules, int discussionSeconds, int voteSeconds, int nightSeconds) {
  private static final Gson GSON = new Gson();

  public static final int DISCUSSION = 60, VOTE = 30, NIGHT = 30;

  /** The options of a Werewolf arena; throws IllegalArgumentException when they cannot be played. */
  public static WerewolfOptions of(ArenaConfig c) {
    return c.options().view(WerewolfOptions.class, json -> parse(c, json));
  }

  private static WerewolfOptions parse(ArenaConfig c, JsonObject json) {
    WerewolfRules rules =
        json.has("werewolf") ? GSON.fromJson(json.get("werewolf"), WerewolfRules.class) : null;
    rules = rules == null ? WerewolfRules.defaults() : rules;
    if ("werewolf".equals(c.preset()) && !rules.roles().isEmpty()) {
      if (WerewolfRules.wolves(rules.roles()) * 2 >= c.minPlayers())
        throw new IllegalArgumentException(
            "Wolves must be in the minority at the minimum player count");
      if (rules.roles().values().stream().mapToInt(Integer::intValue).sum() > c.minPlayers())
        throw new IllegalArgumentException("Minimum players must cover the configured role counts");
    }
    int discussion = number(json, "discussionSeconds", DISCUSSION);
    int vote = number(json, "voteSeconds", VOTE);
    int night = number(json, "nightSeconds", NIGHT);
    if (c.minPlayers() < 4)
      throw new IllegalArgumentException(
          "Invalid duration or player limits (Werewolf needs at least 4 players)");
    if (discussion < 5 || discussion > 600 || vote < 5 || vote > 300 || night < 5 || night > 300)
      throw new IllegalArgumentException("Invalid phase durations");
    // Werewolf is a social deduction game; PvP must not bypass the secret ballot.
    if (c.allowPvP()) throw new IllegalArgumentException("Werewolf requires PvP disabled");
    return new WerewolfOptions(rules, discussion, vote, night);
  }

  private static int number(JsonObject json, String key, int fallback) {
    return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
  }
}
