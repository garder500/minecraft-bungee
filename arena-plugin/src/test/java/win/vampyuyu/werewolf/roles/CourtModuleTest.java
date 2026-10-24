package win.vampyuyu.werewolf.roles;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.werewolf.WerewolfBots;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.WerewolfJournal;
import win.vampyuyu.werewolf.WerewolfRules;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;

public class CourtModuleTest {
  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(31, i + 1));
    return ids;
  }

  private static WerewolfGame game(List<UUID> ids, int seed, Map<String, Integer> cast) {
    return new WerewolfGame(
        ids, 5, 5, 5, new Random(seed), new WerewolfRules(cast, 5, 5, 5));
  }

  private static WerewolfGame nominated(List<UUID> ids, int seed, Map<String, Integer> cast) {
    var rules =
        new WerewolfRules(cast, 5, 5, 5, true, "FULL", "OPEN", false, false, "NOMINATE");
    return new WerewolfGame(ids, 120, 30, 10, new Random(seed), rules);
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  /** Living villagers (plain ones), in seat order. */
  private static List<UUID> villagers(WerewolfGame g, List<UUID> ids) {
    return ids.stream()
        .filter(p -> g.role(p).equals("VILLAGER") && g.alive().contains(p))
        .toList();
  }

  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 30 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** Every living player but the target votes for the target. */
  private static void allVote(WerewolfGame g, UUID target) {
    for (UUID p : g.alive()) if (!p.equals(target)) g.select(p, target);
  }

  /** Opens the day vote (point mode) and has everybody vote for the target; leaves it open. */
  private static void ballotOn(WerewolfGame g, UUID target) {
    toPhase(g, "DAY_VOTE");
    allVote(g, target);
  }

  /** Nominated voting: nominates, seconds, and leaves the game at the start of the trial. */
  private static void toTrial(WerewolfGame g, UUID accused, UUID nominator, UUID seconder) {
    toPhase(g, "DISCUSSION");
    g.select(nominator, accused);
    g.select(seconder, accused);
    assertEquals("DEFENSE", g.phase());
    g.advance();
    assertEquals("TRIAL", g.phase());
  }

  // ------------------------------------------------------------------ mayor

  @Test
  public void theMayorsBallotCountsTwice() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MAYOR", 1));
    UUID mayor = holder(g, ids, "MAYOR");
    List<UUID> v = villagers(g, ids);
    toPhase(g, "DAY_VOTE");
    g.select(mayor, v.get(0));
    g.select(v.get(1), v.get(2));
    g.advance();
    assertFalse("two ballots against one", g.alive().contains(v.get(0)));
    assertTrue(g.alive().contains(v.get(2)));
  }

  @Test
  public void withoutTheMayorTheSameBallotsTie() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1));
    List<UUID> v = villagers(g, ids);
    toPhase(g, "DAY_VOTE");
    g.select(v.get(0), v.get(2));
    g.select(v.get(1), v.get(3));
    g.advance();
    assertEquals("a tie asks for a revote", "DAY_VOTE", g.phase());
    assertTrue(g.alive().contains(v.get(2)) && g.alive().contains(v.get(3)));
  }

  @Test
  public void aDeadMayorCountsOnce() {
    var module = new CourtModule();
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MAYOR", 1));
    UUID mayor = holder(g, ids, "MAYOR"), wolf = holder(g, ids, "WEREWOLF");
    assertEquals(1, module.weight(g, mayor)); // not in a day vote now
    toPhase(g, "DAY_VOTE");
    assertEquals(2, module.weight(g, mayor));
    assertEquals(1, module.weight(g, wolf));
    g.remove(mayor);
    assertFalse(g.living().contains(mayor));
    assertEquals("a dead mayor weighs once", 1, module.weight(g, mayor));
  }

  @Test
  public void theMayorsThumbCountsTwiceAtTheTrial() {
    var ids = ids(8);
    var g = nominated(ids, 3, Map.of("WEREWOLF", 1, "MAYOR", 1));
    UUID mayor = holder(g, ids, "MAYOR");
    List<UUID> v = villagers(g, ids);
    UUID accused = v.get(0);
    toTrial(g, accused, v.get(1), v.get(2));
    List<UUID> others = new ArrayList<>(g.actors());
    others.remove(mayor);
    // The mayor and three others are down (5 of 9 counted), the last three are up.
    g.use(mayor, "die");
    for (int i = 0; i < 3; i++) g.use(others.get(i), "die");
    for (int i = 3; i < others.size(); i++) g.use(others.get(i), "live");
    assertFalse("5 of 9 is more than half", g.alive().contains(accused));
  }

  @Test
  public void withoutTheMayorFourThumbsOfEightSpareTheAccused() {
    var ids = ids(8);
    var g = nominated(ids, 3, Map.of("WEREWOLF", 1));
    List<UUID> v = villagers(g, ids);
    UUID accused = v.get(0);
    toTrial(g, accused, v.get(1), v.get(2));
    List<UUID> jurors = new ArrayList<>(g.actors());
    for (int i = 0; i < 4; i++) g.use(jurors.get(i), "die");
    for (int i = 4; i < jurors.size(); i++) g.use(jurors.get(i), "live");
    assertTrue(g.alive().contains(accused));
  }

  // ------------------------------------------------------------------ pacifist

  @Test
  public void thePacifistsBallotNeverCounts() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "PACIFIST", 1));
    UUID pacifist = holder(g, ids, "PACIFIST");
    List<UUID> v = villagers(g, ids);
    toPhase(g, "DAY_VOTE");
    g.select(pacifist, v.get(0));
    assertEquals("his ballot is accepted", v.get(0), g.ballot(pacifist));
    g.select(v.get(1), v.get(2));
    g.advance();
    assertTrue("the pacifist's ballot is ignored", g.alive().contains(v.get(0)));
    assertFalse(g.alive().contains(v.get(2)));
  }

  @Test
  public void aLonePacifistBallotEliminatesNobody() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "PACIFIST", 1));
    UUID pacifist = holder(g, ids, "PACIFIST");
    int living = g.alive().size();
    toPhase(g, "DAY_VOTE");
    g.select(pacifist, villagers(g, ids).get(0));
    g.advance();
    assertEquals(living, g.alive().size());
  }

  @Test
  public void thePacifistsThumbNeverCountsAtTheTrial() {
    var ids = ids(6);
    var g = nominated(ids, 3, Map.of("WEREWOLF", 1, "PACIFIST", 1));
    UUID pacifist = holder(g, ids, "PACIFIST");
    List<UUID> v = villagers(g, ids);
    UUID accused = v.get(0);
    toTrial(g, accused, v.get(1), v.get(2));
    List<UUID> others = new ArrayList<>(g.actors());
    others.remove(pacifist);
    assertEquals(4, others.size());
    // Right rule: 2 down of 4 counted is not more than half, spared. Counting the pacifist's
    // thumb it would be 3 of 5, condemned.
    g.use(pacifist, "die");
    for (int i = 0; i < 2; i++) g.use(others.get(i), "die");
    for (int i = 2; i < others.size(); i++) g.use(others.get(i), "live");
    assertTrue("2 down of 4 counted spares him", g.alive().contains(accused));
  }

  // ------------------------------------------------------------------ idiot

  @Test
  public void theIdiotWhoDidNotVoteIsGivenARandomBallotAgainstAnother() {
    for (int seed = 0; seed < 20; seed++) {
      var ids = ids(8);
      var g = game(ids, seed, Map.of("WEREWOLF", 1, "IDIOT", 1));
      UUID idiot = holder(g, ids, "IDIOT");
      toPhase(g, "DAY_VOTE");
      assertNull(g.ballot(idiot));
      g.advance();
      assertEquals("seed " + seed, 7, g.alive().size());
      assertTrue("never himself, seed " + seed, g.alive().contains(idiot));
    }
  }

  @Test
  public void theIdiotAlsoVotesWhenHeSkips() {
    var ids = ids(8);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "IDIOT", 1));
    UUID idiot = holder(g, ids, "IDIOT");
    toPhase(g, "DAY_VOTE");
    g.use(idiot, "skip");
    g.advance();
    assertEquals(7, g.alive().size());
  }

  @Test
  public void theIdiotKeepsABallotHeCast() {
    var ids = ids(8);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "IDIOT", 1));
    UUID idiot = holder(g, ids, "IDIOT");
    UUID target = villagers(g, ids).get(2);
    toPhase(g, "DAY_VOTE");
    g.select(idiot, target);
    g.advance();
    assertFalse(g.alive().contains(target));
  }

  @Test
  public void anotherRoleWhoDidNotVoteIsLeftOut() {
    var ids = ids(8);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "MAYOR", 1));
    toPhase(g, "DAY_VOTE");
    g.advance();
    assertEquals(8, g.alive().size());
  }

  @Test
  public void theIdiotsMissingThumbIsDownAtTheTrial() {
    var ids = ids(8);
    var g = nominated(ids, 3, Map.of("WEREWOLF", 1, "IDIOT", 1));
    UUID idiot = holder(g, ids, "IDIOT");
    List<UUID> v = villagers(g, ids);
    UUID accused = v.get(0);
    toTrial(g, accused, v.get(1), v.get(2));
    List<UUID> others = new ArrayList<>(g.actors());
    others.remove(idiot);
    // 4 down + 2 up + the idiot who says nothing: his thumb is down, 5 of 8 condemns.
    for (int i = 0; i < 4; i++) g.use(others.get(i), "die");
    g.use(others.get(4), "live");
    assertEquals("TRIAL", g.phase());
    g.use(others.get(5), "live");
    assertEquals("the idiot is the last thumb missing", "TRIAL", g.phase());
    g.advance();
    assertFalse(g.alive().contains(accused));
  }

  @Test
  public void withoutTheIdiotTheSameThumbsSpareTheAccused() {
    var ids = ids(8);
    var g = nominated(ids, 3, Map.of("WEREWOLF", 1));
    List<UUID> v = villagers(g, ids);
    UUID accused = v.get(0);
    toTrial(g, accused, v.get(1), v.get(2));
    List<UUID> jurors = new ArrayList<>(g.actors());
    for (int i = 0; i < 4; i++) g.use(jurors.get(i), "die");
    g.use(jurors.get(4), "live");
    g.use(jurors.get(5), "live");
    g.advance();
    assertTrue(g.alive().contains(accused));
  }

  // ------------------------------------------------------------------ prince

  @Test
  public void theVillageCannotEliminateThePrinceTheFirstTime() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "PRINCE", 1));
    UUID prince = holder(g, ids, "PRINCE");
    ballotOn(g, prince);
    g.drainNotices();
    g.advance();
    assertTrue(g.alive().contains(prince));
    var notices = g.drainNotices();
    assertEquals("the whole village is told", g.alive(), notices.keySet());
    for (String text : notices.values()) assertTrue(text, text.contains("is the Prince"));
    assertNull(g.takeVerdict().lynched());
  }

  @Test
  public void theSecondVoteTakesThePrince() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "PRINCE", 1));
    UUID prince = holder(g, ids, "PRINCE");
    ballotOn(g, prince);
    g.advance();
    assertTrue(g.alive().contains(prince));
    toPhase(g, "DAY_VOTE");
    allVote(g, prince);
    g.advance();
    assertFalse(g.alive().contains(prince));
  }

  @Test
  public void thePrinceIsOnlySparedFromTheVote() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 2, "PRINCE", 1));
    UUID prince = holder(g, ids, "PRINCE"), wolf = holder(g, ids, "WEREWOLF");
    var module = new CourtModule();
    assertFalse(module.saves(g, new WerewolfGame.Kill(prince, "WOLVES", "x")));
    assertFalse(module.saves(g, new WerewolfGame.Kill(prince, "POISON", "x")));
    assertFalse(module.saves(g, new WerewolfGame.Kill(wolf, "VOTE", "x")));
    assertTrue(module.saves(g, new WerewolfGame.Kill(prince, "VOTE", "x")));
    assertFalse("only once", module.saves(g, new WerewolfGame.Kill(prince, "VOTE", "x")));
  }

  @Test
  public void thePrinceIsSparedAtTheTrialToo() {
    var ids = ids(8);
    var g = nominated(ids, 3, Map.of("WEREWOLF", 1, "PRINCE", 1));
    UUID prince = holder(g, ids, "PRINCE");
    List<UUID> v = villagers(g, ids);
    toTrial(g, prince, v.get(0), v.get(1));
    for (UUID juror : new ArrayList<>(g.actors())) g.use(juror, "die");
    assertTrue(g.alive().contains(prince));
  }

  @Test
  public void aCondemnedPrinceOpensNoMartyrWindow() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "PRINCE", 1, "MARTYR", 1));
    UUID prince = holder(g, ids, "PRINCE"), martyr = holder(g, ids, "MARTYR");
    ballotOn(g, prince);
    g.advance();
    assertNotEquals("MARTYR", g.phase());
    assertTrue(g.alive().contains(prince));
    assertTrue("the martyr lives", g.alive().contains(martyr));
    assertTrue(g.abilities(martyr).isEmpty());
    // Revealed: the next time the prince is condemned, the martyr is asked.
    toPhase(g, "DAY_VOTE");
    allVote(g, prince);
    g.advance();
    assertEquals("MARTYR", g.phase());
  }

  @Test
  public void sparesOnlyAnUnrevealedLivingPrinceFromTheVote() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 2, "PRINCE", 1));
    UUID prince = holder(g, ids, "PRINCE"), wolf = holder(g, ids, "WEREWOLF");
    var module = new CourtModule();
    assertFalse(module.spares(g, new WerewolfGame.Kill(prince, "WOLVES", "x")));
    assertFalse(module.spares(g, new WerewolfGame.Kill(wolf, "VOTE", "x")));
    assertTrue(module.spares(g, new WerewolfGame.Kill(prince, "VOTE", "x")));
    assertTrue("no side effect", module.spares(g, new WerewolfGame.Kill(prince, "VOTE", "x")));
    assertTrue(module.saves(g, new WerewolfGame.Kill(prince, "VOTE", "x")));
    assertFalse(module.spares(g, new WerewolfGame.Kill(prince, "VOTE", "x")));
  }

  @Test
  public void theMartyrPhaseNamesTheMartyrOnlyToHim() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR");
    UUID condemned = villagers(g, ids).get(0);
    ballotOn(g, condemned);
    g.advance();
    assertEquals("MARTYR", g.phase());
    for (UUID viewer : ids) {
      String boss = WerewolfPresentation.boss(g, viewer, 10).text();
      String center = WerewolfPresentation.center(g, viewer, 10).text();
      if (viewer.equals(martyr)) {
        assertTrue(boss, boss.contains("Martyr"));
      } else {
        assertFalse(boss, boss.contains("Martyr") || boss.contains("Marty"));
        assertFalse(center, center.contains("Martyr") || center.contains("Marty"));
      }
    }
  }

  @Test
  public void theMartyrBotsChanceDoesNotDependOnTheCondemnedRole() {
    int[] hits = new int[2];
    for (int k = 0; k < 2; k++) {
      for (int seed = 0; seed < 400; seed++) {
        var ids = ids(8);
        var g = game(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
        UUID martyr = holder(g, ids, "MARTYR");
        UUID condemned = k == 0 ? holder(g, ids, "WEREWOLF") : villagers(g, ids).get(0);
        ballotOn(g, condemned);
        g.advance();
        assertEquals("MARTYR", g.phase());
        new CourtModule().bot(g, WerewolfGame.Phase.MARTYR, martyr, new Random(seed));
        if (!g.alive().contains(martyr)) hits[k]++;
      }
    }
    assertEquals("same draws, same outcome whoever is condemned", hits[0], hits[1]);
    assertTrue("about a quarter: " + hits[0], hits[0] > 60 && hits[0] < 140);
  }

  // ------------------------------------------------------------------ martyr

  @Test
  public void theMartyrMayTakeTheCondemnedPlayersPlace() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR");
    UUID condemned = villagers(g, ids).get(0);
    ballotOn(g, condemned);
    g.advance();
    assertEquals("MARTYR", g.phase());
    assertEquals(Set.of(martyr), g.actors());
    assertEquals(Set.of("sacrifice", "let"), g.abilities(martyr).keySet());
    assertTrue(g.abilities(martyr).get("sacrifice").contains(condemned.toString()));
    g.drainNotices();
    String reply = g.use(martyr, "sacrifice");
    assertTrue(reply, reply.contains("gave your life"));
    assertTrue(g.alive().contains(condemned));
    assertFalse(g.alive().contains(martyr));
    assertEquals(martyr, g.takeVerdict().lynched());
    assertNotEquals("MARTYR", g.phase());
  }

  @Test
  public void theMartyrMayLetItPassAndTheCondemnedDies() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR");
    UUID condemned = villagers(g, ids).get(0);
    ballotOn(g, condemned);
    g.advance();
    assertEquals("You let it pass.", g.use(martyr, "let"));
    assertFalse(g.alive().contains(condemned));
    assertTrue(g.alive().contains(martyr));
  }

  @Test
  public void aMartyrWhoSaysNothingLetsItPassAtTheDeadline() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
    UUID condemned = villagers(g, ids).get(0);
    ballotOn(g, condemned);
    g.advance();
    assertEquals("MARTYR", g.phase());
    g.advance();
    assertFalse(g.alive().contains(condemned));
    assertEquals(7, g.alive().size());
  }

  @Test
  public void theMartyrActsOnlyInHisPhaseAndNobodyElseCan() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR"), wolf = holder(g, ids, "WEREWOLF");
    UUID condemned = villagers(g, ids).get(0);
    toPhase(g, "DAY_VOTE");
    assertThrows(IllegalArgumentException.class, () -> g.use(martyr, "sacrifice"));
    allVote(g, condemned);
    g.advance();
    assertEquals("MARTYR", g.phase());
    assertThrows(IllegalArgumentException.class, () -> g.use(wolf, "sacrifice"));
    assertThrows(IllegalArgumentException.class, () -> g.use(condemned, "sacrifice"));
    assertThrows(IllegalArgumentException.class, () -> g.use(martyr, "pass"));
    assertThrows(IllegalArgumentException.class, () -> g.use(martyr, "stir"));
    assertThrows(IllegalArgumentException.class, () -> g.select(martyr, wolf));
    assertEquals("nothing happened", "MARTYR", g.phase());
    assertEquals(8, g.alive().size());
  }

  @Test
  public void noMartyrPhaseWhenTheMartyrHimselfIsCondemnedOrDead() {
    var ids = ids(8);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR");
    ballotOn(g, martyr);
    g.advance();
    assertNotEquals("MARTYR", g.phase());
    assertFalse(g.alive().contains(martyr));
    // With the martyr gone, a later verdict goes straight through.
    toPhase(g, "DAY_VOTE");
    UUID next = villagers(g, ids).get(0);
    allVote(g, next);
    g.advance();
    assertNotEquals("MARTYR", g.phase());
    assertFalse(g.alive().contains(next));
  }

  @Test
  public void aSacrificeCanGiveTheWolvesTheGame() {
    var ids = ids(5);
    var g = game(ids, 3, Map.of("WEREWOLF", 2, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR");
    UUID wolf = holder(g, ids, "WEREWOLF");
    ballotOn(g, wolf);
    g.advance();
    assertEquals("MARTYR", g.phase());
    g.use(martyr, "sacrifice");
    assertTrue(g.alive().contains(wolf));
    assertFalse("two wolves against two villagers", g.winner().isEmpty());
  }

  @Test
  public void withoutTheSacrificeTheWolfFallsAndTheGameGoesOn() {
    var ids = ids(5);
    var g = game(ids, 3, Map.of("WEREWOLF", 2, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR");
    UUID wolf = holder(g, ids, "WEREWOLF");
    ballotOn(g, wolf);
    g.advance();
    g.use(martyr, "let");
    assertFalse(g.alive().contains(wolf));
    assertTrue(g.winner().isEmpty());
  }

  @Test
  public void theMartyrPhaseAlsoOpensAfterATrial() {
    var ids = ids(8);
    var g = nominated(ids, 3, Map.of("WEREWOLF", 1, "MARTYR", 1));
    UUID martyr = holder(g, ids, "MARTYR");
    List<UUID> v = villagers(g, ids);
    UUID accused = v.get(0);
    toTrial(g, accused, v.get(1), v.get(2));
    for (UUID juror : new ArrayList<>(g.actors())) g.use(juror, "die");
    assertEquals("MARTYR", g.phase());
    g.use(martyr, "sacrifice");
    assertTrue(g.alive().contains(accused));
    assertFalse(g.alive().contains(martyr));
  }

  // ------------------------------------------------------------------ troublemaker

  @Test
  public void theTroublemakerStirsOnceAndTheNextDayEliminatesTwo() {
    var ids = ids(9);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "TROUBLEMAKER", 1));
    UUID trouble = holder(g, ids, "TROUBLEMAKER");
    toPhase(g, "TROUBLE");
    assertEquals(Set.of(trouble), g.actors());
    assertEquals(Set.of("stir", "pass"), g.abilities(trouble).keySet());
    g.drainNotices();
    String reply = g.use(trouble, "stir");
    assertTrue(reply, reply.contains("two players"));
    assertNotEquals("TROUBLE", g.phase());
    for (String text : g.drainNotices().values())
      assertFalse("nobody learns who stirred: " + text, text.contains(trouble.toString()));
    List<UUID> v = villagers(g, ids);
    ballotOn(g, v.get(0));
    g.advance();
    assertFalse(g.alive().contains(v.get(0)));
    assertEquals("a second vote follows at once", "DAY_VOTE", g.phase());
    assertTrue(g.drainNotices().values().stream().anyMatch(t -> t.startsWith("Trouble!")));
    allVote(g, v.get(1));
    g.advance();
    assertFalse(g.alive().contains(v.get(1)));
    assertTrue(g.night());
  }

  @Test
  public void withoutStirringADayHasOneVerdict() {
    var ids = ids(9);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "TROUBLEMAKER", 1));
    UUID trouble = holder(g, ids, "TROUBLEMAKER");
    toPhase(g, "TROUBLE");
    g.use(trouble, "pass");
    List<UUID> v = villagers(g, ids);
    ballotOn(g, v.get(0));
    g.advance();
    assertFalse(g.alive().contains(v.get(0)));
    assertTrue(g.night());
  }

  @Test
  public void passKeepsThePowerAndStirringIsOncePerGame() {
    var ids = ids(9);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "TROUBLEMAKER", 1));
    UUID trouble = holder(g, ids, "TROUBLEMAKER");
    toPhase(g, "TROUBLE");
    g.use(trouble, "pass");
    toPhase(g, "DAY_VOTE");
    toPhase(g, "TROUBLE");
    assertTrue("still called after passing", g.actors().contains(trouble));
    g.use(trouble, "stir");
    assertThrows(IllegalArgumentException.class, () -> g.use(trouble, "stir"));
    int nights = 0;
    for (int i = 0; i < 60 && nights < 3 && g.winner().isEmpty(); i++) {
      g.advance();
      assertNotEquals("spent for good: his call is skipped", "TROUBLE", g.phase());
      if (g.phase().equals("NIGHT")) nights++;
    }
    assertTrue("later nights were played", nights > 0);
  }

  @Test
  public void stirIsRefusedOutsideTheCallAndToEveryoneElse() {
    var ids = ids(9);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "TROUBLEMAKER", 1));
    UUID trouble = holder(g, ids, "TROUBLEMAKER"), wolf = holder(g, ids, "WEREWOLF");
    UUID villager = villagers(g, ids).get(0);
    assertThrows(IllegalArgumentException.class, () -> g.use(trouble, "stir")); // not his call
    toPhase(g, "TROUBLE");
    assertThrows(IllegalArgumentException.class, () -> g.use(wolf, "stir"));
    assertThrows(IllegalArgumentException.class, () -> g.use(villager, "stir"));
    assertThrows(IllegalArgumentException.class, () -> g.use(trouble, "sacrifice"));
    assertThrows(IllegalArgumentException.class, () -> g.select(trouble, villager));
    assertEquals("TROUBLE", g.phase());
    assertTrue(g.abilities(wolf).isEmpty());
  }

  @Test
  public void aStirStandsEvenIfTheTroublemakerIsTheFirstToFall() {
    var ids = ids(9);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "TROUBLEMAKER", 1));
    UUID trouble = holder(g, ids, "TROUBLEMAKER");
    toPhase(g, "TROUBLE");
    g.use(trouble, "stir");
    ballotOn(g, trouble);
    g.advance();
    assertFalse(g.alive().contains(trouble));
    assertEquals("the second vote still follows", "DAY_VOTE", g.phase());
    UUID next = villagers(g, ids).get(0);
    allVote(g, next);
    g.advance();
    assertFalse(g.alive().contains(next));
    assertTrue(g.night());
  }

  // ------------------------------------------------------------------ bots

  private static List<UUID> botIds(int seed) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 11; i++) ids.add(new UUID(40 + seed, i + 1));
    return ids;
  }

  @Test
  public void aPracticeGameWithTheCourtFinishesAndTheBotsUseTheirPowers() {
    Map<String, Integer> cast =
        Map.of(
            "WEREWOLF", 2, "MAYOR", 1, "IDIOT", 1, "PACIFIST", 1, "PRINCE", 1, "MARTYR", 1,
            "TROUBLEMAKER", 1);
    int sacrifices = 0, stirs = 0, turns = 0;
    for (int seed = 0; seed < 150; seed++) {
      List<UUID> ids = botIds(seed);
      var g = game(ids, seed, cast);
      List<String> lines = new ArrayList<>();
      g.journal(new WerewolfJournal("g" + seed, Map.of(), lines::add));
      var brain = new WerewolfBots(new Random(seed));
      Set<UUID> bots = new HashSet<>(ids);
      for (int t = 0; t < 200 && g.winner().isEmpty(); t++) {
        String key = g.turnKey();
        List<WerewolfBots.Action> log = new ArrayList<>();
        for (long ms = 0; ms < 10_000 && key.equals(g.turnKey()); ms += 1000)
          log.addAll(brain.think(g, bots, ms, 10_000, false));
        if (key.equals(g.turnKey())) log.addAll(brain.think(g, bots, 10_000, 10_000, true));
        if (key.equals(g.turnKey())) g.advance();
        for (var action : log) {
          assertFalse(action.toString(), action.what().startsWith("was refused"));
          if (action.what().contains("MARTYR turn") || action.what().contains("TROUBLE turn"))
            turns++;
        }
      }
      assertFalse("The game should end, seed " + seed, g.winner().isEmpty());
      for (String line : lines) {
        if (line.contains("takes the place of")) sacrifices++;
        if (line.contains("stirs up trouble")) stirs++;
      }
    }
    assertTrue("bots take their turns", turns > 0);
    assertTrue("a martyr bot sacrificed itself", sacrifices > 0);
    assertTrue("a troublemaker bot stirred", stirs > 0);
  }
}
