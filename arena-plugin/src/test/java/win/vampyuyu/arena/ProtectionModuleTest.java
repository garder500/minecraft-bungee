package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class ProtectionModuleTest {
  private static final int N = 8;

  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(21, i + 1));
    return ids;
  }

  private static WerewolfGame game(List<UUID> ids, int seed, Map<String, Integer> cast) {
    return new WerewolfGame(
        ids, 5, 5, 5, new Random(seed), new GameRules.Werewolf(cast, 5, 5, 5));
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  private static List<UUID> villagers(WerewolfGame g, List<UUID> ids) {
    return ids.stream().filter(p -> g.role(p).equals("VILLAGER")).toList();
  }

  /** Runs the clock until the given phase opens (nobody votes in the days it crosses). */
  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 20 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** Every living player votes for the target (the target votes for somebody else). */
  private static void lynch(WerewolfGame g, List<UUID> ids, UUID target) {
    toPhase(g, "DAY_VOTE");
    UUID other = ids.stream().filter(p -> !p.equals(target)).findFirst().orElseThrow();
    for (UUID p : g.alive()) g.select(p, p.equals(target) ? other : target);
    g.advance();
  }

  // ------------------------------------------------------------------ priest

  @Test
  public void thePriestBlessesOnceAndTheBlessingStopsTheNextKill() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "PRIEST", 1));
    UUID priest = holder(g, ids, "PRIEST"), wolf = holder(g, ids, "WEREWOLF");
    UUID blessed = villagers(g, ids).get(0);
    assertEquals("PRIEST", g.phase());
    assertTrue(g.actors().contains(priest));
    g.select(priest, blessed);
    assertEquals("NIGHT", g.phase());
    g.drainNotices();
    g.select(wolf, blessed);
    assertTrue(g.alive().contains(blessed));
    var notices = g.drainNotices();
    assertEquals(Set.of(blessed), notices.keySet());
    assertTrue(notices.get(blessed).contains("blessing"));
    // The priest is not woken again, and the blessing is spent.
    toPhase(g, "NIGHT");
    assertFalse(g.actors().contains(priest));
    g.select(wolf, blessed);
    assertFalse(g.alive().contains(blessed));
  }

  @Test
  public void thePriestRefusesHimselfAndOnlyHeIsCalled() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "PRIEST", 1));
    UUID priest = holder(g, ids, "PRIEST"), wolf = holder(g, ids, "WEREWOLF");
    UUID villager = villagers(g, ids).get(0);
    assertThrows(IllegalArgumentException.class, () -> g.select(priest, priest));
    assertThrows(IllegalArgumentException.class, () -> g.select(wolf, villager));
    assertThrows(IllegalArgumentException.class, () -> g.select(villager, villager));
    assertEquals("PRIEST", g.phase());
    assertFalse(g.actors().contains(wolf));
    g.select(priest, villager);
    assertThrows(IllegalArgumentException.class, () -> g.select(priest, villagers(g, ids).get(1)));
  }

  @Test
  public void thePriestMayGoBackToSleepAndBlessLater() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "PRIEST", 1));
    UUID priest = holder(g, ids, "PRIEST");
    g.use(priest, "pass");
    assertEquals("NIGHT", g.phase());
    toPhase(g, "PRIEST");
    assertTrue(g.actors().contains(priest));
  }

  @Test
  public void aBlessingAlsoStopsTheVillageVote() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "PRIEST", 1));
    UUID priest = holder(g, ids, "PRIEST"), wolf = holder(g, ids, "WEREWOLF");
    List<UUID> villagers = villagers(g, ids);
    UUID blessed = villagers.get(0);
    g.select(priest, blessed);
    g.select(wolf, villagers.get(1));
    g.drainNotices();
    lynch(g, ids, blessed);
    assertTrue(g.alive().contains(blessed));
    assertEquals(Set.of(blessed), g.drainNotices().keySet());
    // Spent: the next vote takes them.
    lynch(g, ids, blessed);
    assertFalse(g.alive().contains(blessed));
  }

  @Test
  public void theBlessingOutlivesThePriest() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "PRIEST", 1));
    UUID priest = holder(g, ids, "PRIEST"), wolf = holder(g, ids, "WEREWOLF");
    UUID blessed = villagers(g, ids).get(0);
    g.select(priest, blessed);
    g.select(wolf, priest);
    assertFalse(g.alive().contains(priest));
    toPhase(g, "NIGHT");
    g.drainNotices();
    g.select(wolf, blessed);
    assertTrue(g.alive().contains(blessed));
    assertTrue(g.drainNotices().get(blessed).contains("blessing"));
  }

  @Test
  public void aBlessingDoesNotStopOldAgeOrGrief() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "PRIEST", 1));
    UUID priest = holder(g, ids, "PRIEST");
    UUID blessed = villagers(g, ids).get(0);
    var module = new ProtectionModule();
    module.select(g, WerewolfGame.Phase.PRIEST, priest, blessed);
    assertFalse(module.saves(g, new WerewolfGame.Kill(blessed, "OTHER", "old age")));
    assertTrue(module.saves(g, new WerewolfGame.Kill(blessed, "POISON", "poison")));
  }

  // ------------------------------------------------------------------ tough guy

  @Test
  public void theToughGuySurvivesTheWolvesAndDiesAtTheNextDawn() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "TOUGH_GUY", 1));
    UUID tough = holder(g, ids, "TOUGH_GUY"), wolf = holder(g, ids, "WEREWOLF");
    UUID other = villagers(g, ids).get(0);
    assertEquals("NIGHT", g.phase());
    g.select(wolf, tough);
    assertTrue(g.alive().contains(tough));
    var notices = g.drainNotices();
    assertEquals(Set.of(tough), notices.keySet());
    assertTrue(notices.get(tough).contains("wounded"));
    toPhase(g, "NIGHT");
    g.select(wolf, other);
    assertFalse(g.alive().contains(tough));
    assertFalse(g.alive().contains(other));
  }

  @Test
  public void theWoundedToughGuyDiesEvenWhenTheWolvesLeaveHimBe() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "TOUGH_GUY", 1));
    UUID tough = holder(g, ids, "TOUGH_GUY"), wolf = holder(g, ids, "WEREWOLF");
    g.select(wolf, tough);
    toPhase(g, "NIGHT");
    g.select(wolf, tough); // wounded already: the second attack is not survived
    assertFalse(g.alive().contains(tough));
  }

  @Test
  public void theToughGuyOnlyResistsTheWolves() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "TOUGH_GUY", 1));
    UUID tough = holder(g, ids, "TOUGH_GUY");
    var module = new ProtectionModule();
    var vote = new WerewolfGame.Kill(tough, "VOTE", "vote");
    var delayed = new WerewolfGame.Kill(tough, "DELAYED", "wounds");
    assertFalse(module.saves(g, vote));
    assertFalse(module.saves(g, delayed));
    assertTrue(module.saves(g, new WerewolfGame.Kill(tough, "WOLVES2", "cub")));
    assertFalse(module.saves(g, new WerewolfGame.Kill(tough, "WOLVES", "again")));
  }

  @Test
  public void theVillageVoteKillsTheToughGuyAtOnce() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "TOUGH_GUY", 1));
    UUID tough = holder(g, ids, "TOUGH_GUY");
    lynch(g, ids, tough);
    assertFalse(g.alive().contains(tough));
  }

  // ------------------------------------------------------------------ diseased

  @Test
  public void theWolvesCannotHuntAfterEatingTheDiseased() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 2, "DISEASED", 1));
    UUID diseased = holder(g, ids, "DISEASED");
    List<UUID> wolves = ids.stream().filter(p -> g.role(p).equals("WEREWOLF")).toList();
    UUID prey = villagers(g, ids).get(0);
    for (UUID w : wolves) g.select(w, diseased);
    assertFalse(g.alive().contains(diseased));
    var notices = g.drainNotices();
    assertEquals(Set.copyOf(wolves), notices.keySet());
    assertTrue(notices.get(wolves.get(0)).contains("sick"));
    toPhase(g, "NIGHT");
    assertTrue(g.drainNotices().get(wolves.get(0)).contains("sick"));
    for (UUID w : wolves) g.select(w, prey);
    assertTrue("Nobody can be hunted", g.alive().contains(prey));
    toPhase(g, "NIGHT");
    for (UUID w : wolves) g.select(w, prey);
    assertFalse(g.alive().contains(prey));
  }

  @Test
  public void theDiseasedDyingByTheVillageVoteCallsNothingOff() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "DISEASED", 1));
    UUID diseased = holder(g, ids, "DISEASED"), wolf = holder(g, ids, "WEREWOLF");
    UUID prey = villagers(g, ids).get(0);
    lynch(g, ids, diseased);
    assertFalse(g.alive().contains(diseased));
    toPhase(g, "NIGHT");
    g.select(wolf, prey);
    assertFalse(g.alive().contains(prey));
  }

  @Test
  public void theWolvesWinAtParityOnceTheDiseasedAndAVillagerAreGone() {
    var ids = ids(4);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "DISEASED", 1));
    UUID diseased = holder(g, ids, "DISEASED"), wolf = holder(g, ids, "WEREWOLF");
    g.select(wolf, diseased);
    assertEquals("", g.winner());
    UUID villager = villagers(g, ids).get(0);
    lynch(g, ids, villager);
    assertEquals("WEREWOLVES", g.winner());
  }

  // ------------------------------------------------------------------ cursed

  @Test
  public void theCursedSurvivesAndTurnsIntoAWerewolfTheNextNight() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "CURSED", 1));
    UUID cursed = holder(g, ids, "CURSED"), wolf = holder(g, ids, "WEREWOLF");
    UUID prey = villagers(g, ids).get(0);
    g.select(wolf, cursed);
    assertTrue(g.alive().contains(cursed));
    assertEquals("CURSED", g.role(cursed));
    var first = g.drainNotices();
    assertEquals(Set.of(cursed), first.keySet());
    assertTrue(first.get(cursed).contains("bit"));
    toPhase(g, "NIGHT");
    assertEquals("WEREWOLF", g.role(cursed));
    assertTrue(g.actors().contains(cursed));
    var second = g.drainNotices();
    assertEquals(Set.of(cursed, wolf), second.keySet());
    assertTrue(second.get(cursed).contains("werewolf"));
    assertTrue(second.get(wolf).contains(cursed.toString()));
    // He hunts with the pack now: both must agree.
    g.select(wolf, prey);
    assertEquals("NIGHT", g.phase());
    g.select(cursed, prey);
    assertFalse(g.alive().contains(prey));
  }

  @Test
  public void aCursedWhoDiesBeforeTheNextNightStaysCursed() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "CURSED", 1));
    UUID cursed = holder(g, ids, "CURSED"), wolf = holder(g, ids, "WEREWOLF");
    g.select(wolf, cursed);
    lynch(g, ids, cursed);
    assertFalse(g.alive().contains(cursed));
    toPhase(g, "NIGHT");
    assertEquals("CURSED", g.role(cursed));
    assertEquals(Set.of(wolf), g.actors());
  }

  @Test
  public void theVillageWinsIfItVotesOutTheLastWolfBeforeTheCursedTurns() {
    var ids = ids(5);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "CURSED", 1));
    UUID cursed = holder(g, ids, "CURSED"), wolf = holder(g, ids, "WEREWOLF");
    g.select(wolf, cursed);
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertTrue(g.alive().contains(cursed));
  }

  @Test
  public void theCursedIsNotSavedFromTheSecondWolfKillOrOtherDeaths() {
    var ids = ids(N);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "CURSED", 1));
    UUID cursed = holder(g, ids, "CURSED");
    var module = new ProtectionModule();
    assertFalse(module.saves(g, new WerewolfGame.Kill(cursed, "WOLVES2", "cub")));
    assertFalse(module.saves(g, new WerewolfGame.Kill(cursed, "VOTE", "vote")));
    assertFalse(module.saves(g, new WerewolfGame.Kill(cursed, "POISON", "poison")));
    assertTrue(module.saves(g, new WerewolfGame.Kill(cursed, "WOLVES", "wolves")));
  }

  // ------------------------------------------------------------------ bots

  private static List<WerewolfBots.Action> turn(
      WerewolfGame game, WerewolfBots brain, Set<UUID> bots) {
    List<WerewolfBots.Action> log = new ArrayList<>();
    String key = game.turnKey();
    for (long t = 0; t < 10_000 && key.equals(game.turnKey()); t += 1000)
      log.addAll(brain.think(game, bots, t, 10_000, false));
    if (key.equals(game.turnKey())) log.addAll(brain.think(game, bots, 10_000, 10_000, true));
    if (key.equals(game.turnKey())) game.advance();
    return log;
  }

  private static List<WerewolfBots.Action> play(WerewolfGame game, Set<UUID> bots, int seed) {
    WerewolfBots brain = new WerewolfBots(new Random(seed));
    List<WerewolfBots.Action> log = new ArrayList<>();
    for (int turn = 0; turn < 200 && game.winner().isEmpty(); turn++)
      log.addAll(turn(game, brain, bots));
    assertFalse("The game should end, seed " + seed, game.winner().isEmpty());
    for (WerewolfBots.Action action : log)
      assertFalse(action.toString(), action.what().startsWith("was refused"));
    return log;
  }

  @Test
  public void practiceGamesWithTheProtectionRolesFinish() {
    Map<String, Integer> cast =
        Map.of("WEREWOLF", 2, "PRIEST", 1, "TOUGH_GUY", 1, "DISEASED", 1, "CURSED", 1, "SEER", 1);
    boolean priestActed = false;
    for (int seed = 0; seed < 60; seed++) {
      var ids = ids(8 + seed % 6);
      var botsOnly = game(ids, seed, cast);
      for (var action : play(botsOnly, new HashSet<>(ids), seed))
        priestActed |= action.role().equals("PRIEST") && action.what().startsWith("takes its");
      // One idle human: the clock handles whatever they hold.
      var withHuman = game(ids, seed, cast);
      Set<UUID> bots = new HashSet<>(ids);
      bots.remove(ids.getFirst());
      play(withHuman, bots, seed);
    }
    assertTrue(priestActed);
  }
}
