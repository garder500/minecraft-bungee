package win.vampyuyu.werewolf;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class NewRolesTest {
  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(9, i + 1));
    return ids;
  }

  private static WerewolfGame game(int n, Map<String, Integer> cast) {
    return new WerewolfGame(ids(n), 5, 5, 5, new Random(3), new WerewolfRules(cast, 5, 5, 5));
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  private static List<UUID> others(WerewolfGame g, List<UUID> ids, String... roles) {
    Set<String> skip = Set.of(roles);
    return ids.stream().filter(p -> !skip.contains(g.role(p))).toList();
  }

  /** Runs the clock to the wolves' turn. */
  private static void toNight(WerewolfGame g) {
    for (int i = 0; i < 20 && !g.phase().equals("NIGHT"); i++) g.advance();
    assertEquals("NIGHT", g.phase());
  }

  @Test
  public void everyWolfRoleWakesWithThePackAndSleepersAreTheRest() {
    var ids = ids(9);
    var g = game(9, Map.of("WEREWOLF", 1, "ALPHA_WOLF", 1, "INFECTOR", 1, "WOLF_CUB", 1, "SEER", 1));
    toNight(g);
    for (UUID p : ids) {
      boolean wolf = WerewolfGame.isWolf(g.role(p));
      assertEquals(g.role(p), wolf, g.actors().contains(p));
      assertEquals(g.role(p), !wolf, g.sleeping(p));
      assertEquals(g.role(p), wolf, g.packMember(p));
    }
    UUID alpha = holder(g, ids, "ALPHA_WOLF");
    assertEquals(3, g.pack(alpha).size());
  }

  @Test
  public void aWolfCannotTargetAnyWolfOfTheSide() {
    var ids = ids(9);
    var g = game(9, Map.of("WEREWOLF", 1, "ALPHA_WOLF", 1, "WOLF_CUB", 1));
    toNight(g);
    UUID alpha = holder(g, ids, "ALPHA_WOLF");
    assertThrows(IllegalArgumentException.class, () -> g.select(alpha, holder(g, ids, "WOLF_CUB")));
  }

  @Test
  public void theAlphasVoteCountsDouble() {
    var ids = ids(9);
    var g = game(9, Map.of("WEREWOLF", 2, "ALPHA_WOLF", 1));
    toNight(g);
    UUID alpha = holder(g, ids, "ALPHA_WOLF");
    List<UUID> wolves = ids.stream().filter(p -> g.role(p).equals("WEREWOLF")).toList();
    List<UUID> prey = others(g, ids, "WEREWOLF", "ALPHA_WOLF");
    // One wolf for A, one for B, the alpha for B: B wins 3 to 1.
    g.select(wolves.get(0), prey.get(0));
    g.select(wolves.get(1), prey.get(1));
    g.select(alpha, prey.get(1));
    // The pack does not agree, so the clock closes the hunt.
    while (g.phase().equals("NIGHT")) g.advance();
    for (int i = 0; i < 5 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertFalse(g.alive().contains(prey.get(1)));
    assertTrue(g.alive().contains(prey.get(0)));
  }

  @Test
  public void aloneAnAlphaVoteDecidesAgainstOneOtherVote() {
    var ids = ids(9);
    var g = game(9, Map.of("WEREWOLF", 1, "ALPHA_WOLF", 1));
    toNight(g);
    UUID alpha = holder(g, ids, "ALPHA_WOLF");
    UUID wolf = holder(g, ids, "WEREWOLF");
    List<UUID> prey = others(g, ids, "WEREWOLF", "ALPHA_WOLF");
    g.select(wolf, prey.get(0));
    g.select(alpha, prey.get(1));
    while (g.phase().equals("NIGHT")) g.advance();
    for (int i = 0; i < 5 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertFalse("the alpha's choice wins the 1-vs-2 tally", g.alive().contains(prey.get(1)));
    assertTrue(g.alive().contains(prey.get(0)));
  }

  @Test
  public void theInfectorTurnsTheVictimIntoAWolfOnceAndTheyWinWithThePack() {
    var ids = ids(9);
    var g = game(9, Map.of("WEREWOLF", 1, "INFECTOR", 1));
    toNight(g);
    UUID infector = holder(g, ids, "INFECTOR");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID victim = others(g, ids, "WEREWOLF", "INFECTOR").getFirst();
    assertTrue(g.abilities(infector).containsKey("infect"));
    assertFalse("only the infector may infect", g.abilities(wolf).containsKey("infect"));
    g.use(infector, "infect");
    assertFalse("one bite per night", g.abilities(infector).containsKey("infect"));
    g.select(wolf, victim);
    g.select(infector, victim); // The pack agrees: the night moves on.
    for (int i = 0; i < 6 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertTrue("the victim lives", g.alive().contains(victim));
    assertEquals("WEREWOLF", g.role(victim));
    assertTrue(g.drainNotices().containsKey(victim));
    // The next night the bite is gone for good.
    for (int i = 0; i < 12 && !g.phase().equals("NIGHT"); i++) g.advance();
    assertEquals("NIGHT", g.phase());
    assertFalse(g.abilities(infector).containsKey("infect"));
  }

  @Test
  public void theWolfCubsDeathDoublesTheNextKill() {
    var ids = ids(10);
    var g = game(10, Map.of("WEREWOLF", 2, "WOLF_CUB", 1));
    UUID cub = holder(g, ids, "WOLF_CUB");
    // The village votes the cub out at once: the next night two players die.
    for (int i = 0; i < 20 && !g.phase().equals("DAY_VOTE"); i++) g.advance();
    assertEquals("DAY_VOTE", g.phase());
    for (UUID p : ids) if (!p.equals(cub)) g.select(p, cub);
    g.advance();
    assertFalse(g.alive().contains(cub));
    toNight(g);
    List<UUID> wolves = ids.stream().filter(p -> g.role(p).equals("WEREWOLF") && g.alive().contains(p)).toList();
    List<UUID> prey = others(g, ids, "WEREWOLF", "WOLF_CUB").stream().filter(g.alive()::contains).toList();
    g.select(wolves.get(0), prey.get(0));
    g.select(wolves.get(1), prey.get(1));
    int before = g.alive().size();
    for (int i = 0; i < 6 && g.phase().equals("NIGHT"); i++) g.advance();
    for (int i = 0; i < 6 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertEquals("two victims", before - 2, g.alive().size());
  }

  @Test
  public void theScapegoatTakesATiedVote() {
    var ids = ids(8);
    var g = game(8, Map.of("WEREWOLF", 2, "SCAPEGOAT", 1));
    UUID goat = holder(g, ids, "SCAPEGOAT");
    for (int i = 0; i < 20 && !g.phase().equals("DAY_VOTE"); i++) g.advance();
    List<UUID> two = others(g, ids, "SCAPEGOAT").subList(0, 2);
    // Everyone but the two candidates splits evenly on them.
    List<UUID> voters = ids.stream().filter(p -> !two.contains(p)).toList();
    for (int i = 0; i < voters.size(); i++) g.select(voters.get(i), two.get(i % 2));
    g.select(two.get(0), two.get(1));
    g.select(two.get(1), two.get(0));
    // 4 v 4 is a tie among the top two.
    Map<UUID, Integer> tally = g.votes(goat);
    int max = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    assertEquals(2, tally.values().stream().filter(v -> v == max).count());
    g.advance();
    assertFalse("the scapegoat is eliminated by the tie", g.alive().contains(goat));
    assertEquals(7, g.alive().size());
  }

  @Test
  public void theBearGrowlsOnlyWhenAWolfSitsNextToTheTamer() {
    var ids = ids(9);
    var g = game(9, Map.of("WEREWOLF", 2, "BEAR_TAMER", 1));
    UUID tamer = holder(g, ids, "BEAR_TAMER");
    // A table where the tamer sits between two villagers, then one where a wolf is beside him.
    List<UUID> villagers = others(g, ids, "BEAR_TAMER", "WEREWOLF");
    List<UUID> wolves = ids.stream().filter(p -> g.role(p).equals("WEREWOLF")).toList();
    List<UUID> quiet = new ArrayList<>(List.of(villagers.get(0), tamer, villagers.get(1)));
    quiet.addAll(wolves);
    quiet.addAll(villagers.subList(2, villagers.size()));
    g.ring(quiet);
    toNight(g);
    for (int i = 0; i < 8 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertTrue("no growl next to villagers", g.drainNotices().values().stream().noneMatch(m -> m.contains("growls")));
    // Second night, wolf beside the tamer.
    var g2 = game(9, Map.of("WEREWOLF", 2, "BEAR_TAMER", 1));
    UUID tamer2 = holder(g2, ids, "BEAR_TAMER");
    List<UUID> w2 = ids.stream().filter(p -> g2.role(p).equals("WEREWOLF")).toList();
    List<UUID> v2 = others(g2, ids, "BEAR_TAMER", "WEREWOLF");
    List<UUID> loud = new ArrayList<>(List.of(w2.get(0), tamer2, v2.get(0)));
    loud.addAll(v2.subList(1, v2.size()));
    loud.add(w2.get(1));
    g2.ring(loud);
    toNight(g2);
    for (int i = 0; i < 8 && !g2.phase().equals("DISCUSSION"); i++) g2.advance();
    assertTrue("the bear growls", g2.drainNotices().values().stream().anyMatch(m -> m.contains("growls")));
  }

  @Test
  public void forcedRolesWinOverTheShuffle() {
    var ids = ids(8);
    Map<UUID, String> forced = Map.of(ids.get(0), "WOLF_CUB", ids.get(1), "SEER");
    var g =
        new WerewolfGame(
            ids, 5, 5, 5, new Random(1), new WerewolfRules(Map.of("WEREWOLF", 2, "SEER", 1), 5, 5, 5), forced);
    assertEquals("WOLF_CUB", g.role(ids.get(0)));
    assertEquals("SEER", g.role(ids.get(1)));
    assertEquals(1, ids.stream().filter(p -> g.role(p).equals("SEER")).count());
  }

  @Test
  public void automaticCastsAddTheNewVillageSpecialsForBigTables() {
    var g = new WerewolfGame(ids(12), 5, 5, 5, new Random(1), WerewolfRules.defaults());
    // Default settings use the classic cast; the new specials arrive with the automatic cast.
    var auto = new WerewolfGame(ids(12), 5, 5, 5, new Random(1), new WerewolfRules(Map.of(), 5, 5, 5));
    assertTrue(auto.composition().containsKey("SCAPEGOAT"));
    assertTrue(auto.composition().containsKey("BEAR_TAMER"));
    assertFalse(g.composition().isEmpty());
  }

  @Test
  public void theWolfSideWinsAndLosesTogether() {
    var ids = ids(5);
    var g = game(5, Map.of("ALPHA_WOLF", 1, "SEER", 1));
    UUID alpha = holder(g, ids, "ALPHA_WOLF");
    assertTrue(WerewolfGame.isWolf("WOLF_CUB"));
    assertFalse(WerewolfGame.isWolf("SCAPEGOAT"));
    // Vote the alpha out on day one: the village wins.
    for (int i = 0; i < 20 && !g.phase().equals("DAY_VOTE"); i++) g.advance();
    for (UUID p : ids) if (!p.equals(alpha)) g.select(p, alpha);
    g.advance();
    assertEquals("VILLAGE", g.winner());
  }
}
