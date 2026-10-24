package win.vampyuyu.werewolf.roles;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.werewolf.WerewolfBots;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.WerewolfRules;

public class CultModuleTest {
  private static List<UUID> ids(int seed, int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(seed, i + 1));
    return ids;
  }

  private static WerewolfGame game(List<UUID> ids, int seed, Map<String, Integer> cast) {
    return new WerewolfGame(
        ids, 5, 5, 5, new Random(seed), new WerewolfRules(cast, 5, 5, 5));
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  private static List<UUID> villagers(WerewolfGame g, List<UUID> ids) {
    return ids.stream().filter(p -> g.role(p).equals("VILLAGER")).toList();
  }

  /** Runs the clock until the phase named is reached. */
  private static void to(WerewolfGame g, String phase) {
    for (int i = 0; i < 30 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** The game's own cult module, to look at its members. */
  @SuppressWarnings("unchecked")
  private static CultModule cultOf(WerewolfGame g) throws Exception {
    var f = WerewolfGame.class.getDeclaredField("modules");
    f.setAccessible(true);
    for (RoleModule m : (List<RoleModule>) f.get(g)) if (m instanceof CultModule c) return c;
    throw new AssertionError("no cult module");
  }

  /** Four players: a cult leader, a werewolf and two villagers. */
  private static final Map<String, Integer> SMALL = Map.of("WEREWOLF", 1, "CULT_LEADER", 1);

  @Test
  public void theLeaderRecruitsInHisCallAndOnlyHeIsTold() {
    var ids = ids(1, 4);
    var g = game(ids, 1, SMALL);
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID a = villagers(g, ids).get(0);
    to(g, "CULT");
    assertEquals(Set.of(leader), g.actors());
    g.drainNotices();
    String reply = g.select(leader, a);
    assertTrue(reply.contains(a.toString()));
    assertFalse("the call ends once the leader is done", g.phase().equals("CULT"));
    assertFalse("recruits are not told", g.drainNotices().containsKey(a));
  }

  @Test
  public void theLeaderIsCalledEveryNight() {
    var ids = ids(2, 6);
    var g = game(ids, 2, SMALL);
    UUID leader = holder(g, ids, "CULT_LEADER");
    List<UUID> vs = villagers(g, ids);
    to(g, "CULT");
    g.select(leader, vs.get(0));
    to(g, "DAY_VOTE");
    to(g, "CULT");
    assertTrue(g.actors().contains(leader));
    g.select(leader, vs.get(1));
  }

  @Test
  public void refusals() {
    var ids = ids(3, 5);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "CULT_LEADER", 1));
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID a = villagers(g, ids).get(0);
    to(g, "CULT");
    assertThrows(IllegalArgumentException.class, () -> g.select(leader, leader));
    assertThrows(
        "only the leader recruits", IllegalArgumentException.class, () -> g.select(a, wolf));
    g.select(leader, a);
    to(g, "CULT"); // next night
    assertThrows(
        "already a member", IllegalArgumentException.class, () -> g.select(leader, a));
  }

  @Test
  public void aDeadPlayerCannotBeRecruited() {
    var ids = ids(4, 5);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "CULT_LEADER", 1));
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID prey = villagers(g, ids).get(0);
    to(g, "NIGHT");
    g.select(wolf, prey);
    to(g, "DAY_VOTE");
    assertFalse(g.alive().contains(prey));
    to(g, "CULT");
    assertThrows(IllegalArgumentException.class, () -> g.select(leader, prey));
  }

  @Test
  public void theModuleItselfRefusesADeadTarget() throws Exception {
    var ids = ids(4, 5);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "CULT_LEADER", 1));
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID prey = villagers(g, ids).get(0);
    to(g, "NIGHT");
    g.select(wolf, prey);
    to(g, "DAY_VOTE");
    assertFalse(g.alive().contains(prey));
    CultModule module = cultOf(g);
    var e =
        assertThrows(
            IllegalArgumentException.class,
            () -> module.select(g, WerewolfGame.Phase.CULT, leader, prey));
    assertEquals("Select a living player", e.getMessage());
    assertFalse(module.members(g, "CULT").contains(prey));
  }

  @Test
  public void theCultWinsWhenEveryLivingPlayerIsAMember() {
    var ids = ids(5, 4);
    var g = game(ids, 5, SMALL);
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    List<UUID> vs = villagers(g, ids);
    UUID a = vs.get(0), b = vs.get(1);
    to(g, "NIGHT");
    g.select(wolf, b); // the wolf kills the second villager
    to(g, "CULT");
    g.select(leader, a);
    to(g, "DAY_VOTE");
    assertEquals(Set.of(leader, wolf, a), g.alive());
    g.select(leader, wolf);
    g.select(a, wolf);
    g.advance();
    assertEquals("CULT", g.winner());
    assertFalse(g.alive().contains(wolf));
    assertEquals(Set.of(leader, a), g.winners());
  }

  @Test
  public void aRecruitedWolfCountsAsAMember() {
    var ids = ids(6, 4);
    var g = game(ids, 6, SMALL);
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    List<UUID> vs = villagers(g, ids);
    to(g, "CULT");
    g.select(leader, wolf);
    to(g, "DAY_VOTE");
    // Lynch a villager, leaving the leader, the wolf and a villager.
    g.select(leader, vs.get(0));
    g.select(wolf, vs.get(0));
    g.advance();
    assertEquals("", g.winner());
    to(g, "CULT");
    g.select(leader, vs.get(1)); // every living player is now a member
    for (int i = 0; i < 10 && g.winner().isEmpty(); i++) g.advance();
    assertEquals("CULT", g.winner());
    assertTrue(g.winners().containsAll(Set.of(leader, wolf, vs.get(1))));
  }

  @Test
  public void anOutsiderStillAliveStopsTheCultWin() {
    var ids = ids(7, 4);
    var g = game(ids, 7, SMALL);
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    List<UUID> vs = villagers(g, ids);
    UUID a = vs.get(0), b = vs.get(1);
    to(g, "CULT");
    g.select(leader, a);
    to(g, "DAY_VOTE");
    g.select(leader, b);
    g.select(a, b);
    g.advance();
    assertFalse(g.alive().contains(b));
    assertTrue("the wolf lives and is not in the cult", g.alive().contains(wolf));
    assertNotEquals("CULT", g.winner());
  }

  @Test
  public void theCultKeepsItsMembersWhenTheLeaderDiesAndNobodyReplacesHim() throws Exception {
    var ids = ids(8, 5);
    var g = game(ids, 8, Map.of("WEREWOLF", 1, "CULT_LEADER", 1));
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID a = villagers(g, ids).get(0);
    to(g, "CULT");
    g.select(leader, a);
    to(g, "NIGHT");
    g.select(wolf, leader);
    to(g, "DAY_VOTE");
    assertFalse(g.alive().contains(leader));
    assertTrue(g.alive().contains(a));
    assertEquals("", g.winner());
    // No call for a dead leader, now or later.
    for (int i = 0; i < 12 && g.winner().isEmpty(); i++) {
      g.advance();
      assertNotEquals("CULT", g.phase());
    }
    assertTrue(cultOf(g).members(g, "CULT").containsAll(Set.of(leader, a)));
  }

  @Test
  public void recruitsOfALeaderWhoLivesAloneDoNotGiveTheWinWhileOutsidersLive() throws Exception {
    var ids = ids(9, 5);
    var g = game(ids, 9, Map.of("WEREWOLF", 1, "CULT_LEADER", 1));
    UUID leader = holder(g, ids, "CULT_LEADER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID a = villagers(g, ids).get(0);
    to(g, "CULT");
    g.select(leader, a);
    to(g, "NIGHT");
    to(g, "DAY_VOTE");
    g.select(leader, a);
    g.select(wolf, a);
    g.advance();
    assertFalse("the recruit is dead", g.alive().contains(a));
    assertTrue("the leader lives", g.alive().contains(leader));
    assertTrue(cultOf(g).members(g, "CULT").containsAll(Set.of(leader, a)));
    assertTrue("outsiders live", g.alive().contains(wolf));
    assertEquals("", g.winner());
  }

  @Test
  public void aTableWithoutALeaderNeverGivesTheCultTheWin() {
    var ids = ids(10, 6);
    var g = game(ids, 10, Map.of("WEREWOLF", 1));
    UUID wolf = holder(g, ids, "WEREWOLF");
    to(g, "DAY_VOTE");
    for (UUID p : ids) if (!p.equals(wolf)) g.select(p, wolf);
    g.advance();
    assertEquals("VILLAGE", g.winner());
  }

  // ------------------------------------------------------------------ bots

  private static void turn(WerewolfGame game, WerewolfBots brain, Set<UUID> bots) {
    String key = game.turnKey();
    for (long t = 0; t < 10_000 && key.equals(game.turnKey()); t += 1000)
      brain.think(game, bots, t, 10_000, false);
    if (key.equals(game.turnKey())) brain.think(game, bots, 10_000, 10_000, true);
    if (key.equals(game.turnKey())) game.advance();
  }

  @Test
  public void aPracticeGameWithACultLeaderFinishes() {
    boolean cultWon = false;
    for (int seed = 0; seed < 60; seed++) {
      List<UUID> ids = ids(seed, 6 + seed % 7);
      var game = game(ids, seed, Map.of("WEREWOLF", 1, "CULT_LEADER", 1, "SEER", 1));
      Set<UUID> bots = new HashSet<>(ids);
      var brain = new WerewolfBots(new Random(seed));
      for (int t = 0; t < 200 && game.winner().isEmpty(); t++) turn(game, brain, bots);
      assertFalse("The game should end, seed " + seed, game.winner().isEmpty());
      cultWon |= game.winner().equals("CULT");
    }
    assertTrue("the bot leader wins at least once in 60 games", cultWon);
  }

  @Test
  public void theBotLeaderRecruitsALivingOutsider() {
    var ids = ids(11, 7);
    var g = game(ids, 11, Map.of("WEREWOLF", 1, "CULT_LEADER", 1));
    UUID leader = holder(g, ids, "CULT_LEADER");
    to(g, "CULT");
    var brain = new WerewolfBots(new Random(11));
    var actions = brain.think(g, Set.of(leader), 10_000, 10_000, true);
    assertFalse(actions.isEmpty());
    assertFalse(actions.toString(), actions.toString().contains("was refused"));
    assertNotEquals("CULT", g.phase());
  }
}
