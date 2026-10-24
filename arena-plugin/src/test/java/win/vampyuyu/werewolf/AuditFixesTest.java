package win.vampyuyu.werewolf;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;

/** Regression tests for what the independent audit of the rules found. */
public class AuditFixesTest {
  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(11, i + 1));
    return ids;
  }

  private static WerewolfGame game(int n, Map<String, Integer> cast, WerewolfRules variant) {
    return new WerewolfGame(ids(n), 120, 30, 10, new Random(5), variant);
  }

  private static WerewolfRules rules(
      Map<String, Integer> cast, String reveal, boolean amulet, String voting) {
    return new WerewolfRules(cast, 5, 5, 5, true, reveal, "OPEN", false, amulet, voting);
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 40 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  @Test
  public void theAmuletSavesOnlyOncePerDawnAndPerDay() {
    var cast = Map.of("WEREWOLF", 1, "WITCH", 1);
    var ids = ids(8);
    var g = game(8, cast, rules(cast, "FULL", true, "POINT"));
    UUID holder =
        g.drainNotices().entrySet().stream()
            .filter(e -> e.getValue().contains("Amulet of Protection"))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    UUID wolf = holder(g, ids, "WEREWOLF"), witch = holder(g, ids, "WITCH");
    toPhase(g, "NIGHT");
    g.select(wolf, holder);
    toPhase(g, "WITCH");
    g.select(witch, holder); // the death potion on the same player
    toPhase(g, "DISCUSSION");
    assertFalse("the amulet saved the wolves' attack, not the poison too", g.alive().contains(holder));
  }

  @Test
  public void aSpentTrialIsCountedWhenTheLastJurorLeaves() {
    var cast = Map.of("WEREWOLF", 1, "SEER", 1);
    var ids = ids(8);
    var g = game(8, cast, rules(cast, "FULL", false, "NOMINATE"));
    toPhase(g, "DISCUSSION");
    List<UUID> villagers = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).toList();
    UUID accused = villagers.get(0);
    g.select(villagers.get(1), accused);
    g.select(villagers.get(2), accused);
    g.advance(); // the defense
    assertEquals("TRIAL", g.phase());
    List<UUID> jury = new ArrayList<>(g.actors());
    UUID last = jury.removeLast();
    for (UUID juror : jury) g.use(juror, "die");
    assertEquals("TRIAL", g.phase());
    g.remove(last); // the only juror still to vote walks out
    assertNotEquals("the trial does not wait for a juror who left", "TRIAL", g.phase());
  }

  @Test
  public void aDayThatEndsWithoutAVoteSpendsTheExtraLynching() {
    var cast = Map.of("WEREWOLF", 1, "SEER", 1);
    var g = game(8, cast, rules(cast, "FULL", false, "NOMINATE"));
    toPhase(g, "DISCUSSION");
    g.extraLynches(1);
    for (int i = 0; i < 12 && !g.night(); i++) g.advance(); // nobody is nominated
    assertTrue(g.night());
    for (int i = 0; i < 20 && g.night(); i++) g.advance();
    // the next day has a normal single elimination
    List<UUID> villagers = new ArrayList<>(g.alive());
    villagers.removeIf(p -> g.role(p).equals("WEREWOLF"));
    UUID accused = villagers.get(0);
    g.select(villagers.get(1), accused);
    g.select(villagers.get(2), accused);
    g.advance();
    for (UUID juror : g.actors()) g.use(juror, "die");
    assertFalse(g.alive().contains(accused));
    assertTrue("a single elimination only: the night comes", g.night());
  }

  @Test
  public void aSparedPlayerIsNamedInTheVerdict() {
    var cast = Map.of("WEREWOLF", 1, "PRINCE", 1);
    var ids = ids(8);
    var g = game(8, cast, rules(cast, "FULL", false, "POINT"));
    UUID prince = holder(g, ids, "PRINCE");
    toPhase(g, "DAY_VOTE");
    for (UUID p : ids) if (!p.equals(prince)) g.select(p, prince);
    g.advance();
    var verdict = g.takeVerdict();
    assertNull(verdict.lynched());
    assertEquals(prince, verdict.spared());
    assertTrue(g.alive().contains(prince));
  }

  @Test
  public void aBlessedPrinceIsStillRevealedWhenTheVillageCondemnsHim() {
    var cast = Map.of("WEREWOLF", 1, "PRINCE", 1, "PRIEST", 1);
    var ids = ids(9);
    var g = game(9, cast, rules(cast, "FULL", false, "POINT"));
    UUID prince = holder(g, ids, "PRINCE"), priest = holder(g, ids, "PRIEST");
    toPhase(g, "PRIEST");
    g.select(priest, prince);
    toPhase(g, "DAY_VOTE");
    g.drainNotices();
    for (UUID p : ids) if (!p.equals(prince)) g.select(p, prince);
    g.advance();
    assertTrue(g.alive().contains(prince));
    assertTrue(
        "the first condemnation reveals the prince, whatever else protects him",
        g.drainNotices().values().stream().anyMatch(n -> n.contains("is the Prince")));
  }

  @Test
  public void theCastAsDealtKeepsADrunksCardAndHidesHisRealRole() {
    var cast = Map.of("WEREWOLF", 2, "DRUNK", 1);
    var g = game(9, cast, rules(cast, "FULL", false, "POINT"));
    assertEquals(1, (int) g.composition().getOrDefault("DRUNK", 0));
    assertEquals(2, (int) g.composition().getOrDefault("WEREWOLF", 0));
  }

  @Test
  public void aHiddenRevealHidesTheClockOfASleepersNight() {
    var ids = ids(8);
    var cast = Map.of("WEREWOLF", 1, "SEER", 1);
    var g = game(8, cast, rules(cast, "NONE", false, "POINT"));
    UUID villager = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    var boss = WerewolfPresentation.boss(g, villager, 20);
    assertFalse(boss.timer());
    assertFalse(boss.text().contains("20 s"));
    var full = game(8, cast, rules(cast, "FULL", false, "POINT"));
    assertTrue(WerewolfPresentation.boss(full, villager, 20).timer());
  }

  @Test
  public void twoSoloRolesInLoveAreTwoSidesAndWinTogetherAtTheEnd() {
    var cast = Map.of("WEREWOLF", 1, "CUPID", 1, "LONE_WOLF", 1, "TANNER", 1);
    var ids = ids(8);
    var g = game(8, cast, rules(cast, "FULL", false, "POINT"));
    UUID cupid = holder(g, ids, "CUPID"), lone = holder(g, ids, "LONE_WOLF"), tanner = holder(g, ids, "TANNER");
    assertEquals("CUPID", g.phase());
    g.select(cupid, lone);
    g.select(cupid, tanner);
    for (UUID p : Set.copyOf(g.alive())) if (!p.equals(lone) && !p.equals(tanner)) g.remove(p);
    assertEquals("LOVERS", g.winner());
    assertTrue(g.winners().containsAll(Set.of(lone, tanner)));
  }
}
