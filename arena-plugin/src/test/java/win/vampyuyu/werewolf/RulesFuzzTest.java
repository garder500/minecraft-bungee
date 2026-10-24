package win.vampyuyu.werewolf;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.werewolf.roles.RoleInfo;

/**
 * Plays hundreds of random tables with random casts and random variants, every seat a practice bot,
 * and checks the only things that must always hold: the game ends, no bot is ever refused, and the
 * winners are real players. It is the safety net under the whole role set.
 */
public class RulesFuzzTest {
  private static final List<String> KILLERS =
      List.of("WEREWOLF", "ALPHA_WOLF", "INFECTOR", "WOLF_CUB", "LONE_WOLF", "VAMPIRE");

  private static List<UUID> ids(int seed, int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(seed, i + 1));
    return ids;
  }

  /** A random legal cast for n players. */
  private static Map<String, Integer> cast(Random r, int n) {
    Map<String, Integer> cast = new LinkedHashMap<>();
    int killers = 1 + r.nextInt(Math.max(1, Math.min(3, (n - 1) / 3)));
    for (int i = 0; i < killers; i++) {
      String killer = i == 0 ? "WEREWOLF" : KILLERS.get(r.nextInt(KILLERS.size()));
      cast.merge(killer, 1, Integer::sum);
    }
    cast.replaceAll((role, count) -> Math.min(count, RoleInfo.of(role).max()));
    List<String> specials = new ArrayList<>();
    for (WerewolfGame.Role role : WerewolfGame.Role.values()) {
      String name = role.name();
      if (name.equals("VILLAGER") || KILLERS.contains(name)) continue;
      for (int i = 0; i < RoleInfo.of(name).max() && i < 3; i++) specials.add(name);
    }
    Collections.shuffle(specials, r);
    int budget = Math.min(n - killers - 1, 2 + r.nextInt(Math.max(1, n)));
    for (String role : specials) {
      if (cast.values().stream().mapToInt(Integer::intValue).sum() >= killers + budget) break;
      if (cast.getOrDefault(role, 0) < RoleInfo.of(role).max()) cast.merge(role, 1, Integer::sum);
    }
    return cast;
  }

  private static String describe(int seed, Map<String, Integer> cast, WerewolfRules rules) {
    return "seed " + seed + " cast " + cast + " voting " + rules.voting() + " mayor " + rules.mayor()
        + " minion " + rules.minion() + " reveal "
        + rules.reveal() + " ballots " + rules.ballots() + " dice " + rules.dice()
        + " amulet " + rules.amulet();
  }

  @Test
  public void randomTablesAlwaysFinishWithoutARefusedBot() {
    Set<String> rolesSeen = new HashSet<>();
    for (int seed = 0; seed < Integer.getInteger("fuzz.seeds", 400); seed++) {
      Random r = new Random(seed * 7919L + 13);
      int n = 6 + r.nextInt(25);
      Map<String, Integer> cast = cast(r, n);
      String minionMode = r.nextInt(3) == 0 ? "PICKED" : "DEALT";
      if (minionMode.equals("PICKED")) cast.remove("MINION"); // the wolves pick: no card
      var rules =
          new WerewolfRules(
              cast, 5, 5, 5, true,
              List.of("FULL", "TEAM", "WOLF", "NONE").get(r.nextInt(4)),
              r.nextInt(4) == 0 ? "SECRET" : "OPEN",
              r.nextInt(3) == 0,
              r.nextInt(3) == 0,
              r.nextInt(3) == 0 ? "NOMINATE" : "POINT",
              r.nextInt(3) == 0 ? "ELECTED" : "DEALT",
              minionMode);
      String label = describe(seed, cast, rules);
      var ids = ids(seed, n);
      WerewolfGame game;
      try {
        game = new WerewolfGame(ids, 5, 5, 5, new Random(seed), rules);
      } catch (IllegalArgumentException illegal) {
        continue; // a random cast that cannot be dealt (too many killers for the table)
      }
      for (UUID p : ids) rolesSeen.add(game.role(p));
      Set<UUID> bots = new HashSet<>(ids);
      var brain = new WerewolfBots(new Random(seed));
      List<String> trail = new ArrayList<>();
      for (int turn = 0; turn < 700 && game.winner().isEmpty(); turn++) {
        String key = game.turnKey();
        String phase = game.phase();
        List<WerewolfBots.Action> log = new ArrayList<>();
        for (long t = 0; t < 10_000 && key.equals(game.turnKey()); t += 1000)
          log.addAll(brain.think(game, bots, t, 10_000, false));
        if (key.equals(game.turnKey())) log.addAll(brain.think(game, bots, 10_000, 10_000, true));
        for (var action : log) {
          trail.add(phase + " " + action);
          assertFalse(label + ": " + action + "\n" + tail(trail), action.what().startsWith("was refused"));
        }
        if (key.equals(game.turnKey())) game.advance();
      }
      assertFalse("the game must end: " + label + "\n" + tail(trail), game.winner().isEmpty());
      for (UUID winner : game.winners()) assertTrue(label, ids.contains(winner));
    }
    // The random casts reached a good part of the role set.
    assertTrue("roles reached: " + rolesSeen, rolesSeen.size() >= 30);
  }

  private static String tail(List<String> trail) {
    return String.join("\n", trail.subList(Math.max(0, trail.size() - 12), trail.size()));
  }
}
