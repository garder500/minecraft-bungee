package win.vampyuyu.werewolf;

import java.util.*;
import win.vampyuyu.werewolf.roles.RoleInfo;

/** The Werewolf settings of an arena (its {@code werewolf} option): bounded and validated. */
public record WerewolfRules(
    Map<String, Integer> roles,
    int cupidSeconds,
    int witchSeconds,
    int hunterSeconds,
    Boolean ambienceEnabled,
    String reveal,
    String ballots,
    Boolean dice,
    Boolean amulet,
    String voting,
    String mayor,
    String minion) {
  public WerewolfRules(
      Map<String, Integer> roles,
      int cupidSeconds,
      int witchSeconds,
      int hunterSeconds,
      Boolean ambienceEnabled,
      String reveal,
      String ballots,
      Boolean dice,
      Boolean amulet,
      String voting) {
    this(
        roles, cupidSeconds, witchSeconds, hunterSeconds, ambienceEnabled, reveal, ballots, dice,
        amulet, voting, "DEALT", "DEALT");
  }

  public WerewolfRules(
      Map<String, Integer> roles, int cupidSeconds, int witchSeconds, int hunterSeconds) {
    this(roles, cupidSeconds, witchSeconds, hunterSeconds, true);
  }

  public WerewolfRules(
      Map<String, Integer> roles,
      int cupidSeconds,
      int witchSeconds,
      int hunterSeconds,
      Boolean ambienceEnabled) {
    this(
        roles, cupidSeconds, witchSeconds, hunterSeconds, ambienceEnabled, "FULL", "OPEN",
        false, false, "POINT", "DEALT", "DEALT");
  }

  /** What a death tells the table: FULL role, TEAM only, WOLF or not, or NONE. */
  public static final List<String> REVEALS = List.of("FULL", "TEAM", "WOLF", "NONE");

  /** How ballots are seen: OPEN (running counts, as in a show of hands) or SECRET until the end. */
  public static final List<String> BALLOTS = List.of("OPEN", "SECRET");

  /** How the village eliminates: POINT (everybody points, the most pointed at falls) or NOMINATE. */
  public static final List<String> VOTINGS = List.of("POINT", "NOMINATE");

  /** Where the mayor comes from: DEALT (a card, secret) or ELECTED (by the village, publicly). */
  public static final List<String> MAYORS = List.of("DEALT", "ELECTED");

  /** Where the minion comes from: DEALT (a card) or PICKED (by the wolves on the first night). */
  public static final List<String> MINIONS = List.of("DEALT", "PICKED");

  public WerewolfRules {
    ambienceEnabled = ambienceEnabled == null ? true : ambienceEnabled;
    reveal = reveal == null ? "FULL" : reveal.toUpperCase(Locale.ROOT);
    ballots = ballots == null ? "OPEN" : ballots.toUpperCase(Locale.ROOT);
    mayor = mayor == null ? "DEALT" : mayor.toUpperCase(Locale.ROOT);
    minion = minion == null ? "DEALT" : minion.toUpperCase(Locale.ROOT);
    if (!MAYORS.contains(mayor)) throw new IllegalArgumentException("Mayor must be one of " + MAYORS);
    if (!MINIONS.contains(minion))
      throw new IllegalArgumentException("Minion must be one of " + MINIONS);
    voting = voting == null ? "POINT" : voting.toUpperCase(Locale.ROOT);
    if (!VOTINGS.contains(voting))
      throw new IllegalArgumentException("Voting must be one of " + VOTINGS);
    dice = dice != null && dice;
    amulet = amulet != null && amulet;
    if (!REVEALS.contains(reveal))
      throw new IllegalArgumentException("Reveal must be one of " + REVEALS);
    if (!BALLOTS.contains(ballots))
      throw new IllegalArgumentException("Ballots must be one of " + BALLOTS);
    roles = roles == null ? Map.of() : Map.copyOf(roles);
    for (var e : roles.entrySet()) {
      try {
        WerewolfGame.Role.valueOf(e.getKey());
      } catch (Exception ex) {
        throw new IllegalArgumentException("Unknown Werewolf role");
      }
      if (e.getValue() == null
          || e.getValue() < 0
          || e.getValue() > RoleInfo.of(e.getKey()).max())
        throw new IllegalArgumentException(
            "Too many " + RoleInfo.of(e.getKey()).name() + " (at most " + RoleInfo.of(e.getKey()).max() + ")");
    }
    if (minion.equals("PICKED") && roles.getOrDefault("MINION", 0) > 0)
      throw new IllegalArgumentException(
          "With a picked minion the wolves choose one on the first night: deal no Minion card");
    if (!roles.isEmpty() && wolves(roles) + roles.getOrDefault("VAMPIRE", 0) < 1)
      throw new IllegalArgumentException("Custom roles need at least one werewolf or vampire");
    if (cupidSeconds < 5
        || cupidSeconds > 120
        || witchSeconds < 5
        || witchSeconds > 120
        || hunterSeconds < 5
        || hunterSeconds > 120)
      throw new IllegalArgumentException("Special-role phases must last 5–120 seconds");
  }

  /** Wolves of every kind in a role count map. */
  public static int wolves(Map<String, Integer> roles) {
    int n = 0;
    for (var e : roles.entrySet()) if (WerewolfGame.isWolf(e.getKey())) n += e.getValue();
    return n;
  }

  public static WerewolfRules defaults() {
    return new WerewolfRules(Map.of(), 15, 15, 15);
  }
}
