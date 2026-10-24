package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** The variants of the game: nominated voting, secret ballots, what a death reveals, dice, amulet. */
public class VariantsTest {
  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(7, i + 1));
    return ids;
  }

  private static GameRules.Werewolf rules(
      String reveal, String ballots, boolean dice, boolean amulet, String voting) {
    return new GameRules.Werewolf(
        Map.of("WEREWOLF", 1, "SEER", 1), 5, 5, 5, true, reveal, ballots, dice, amulet, voting);
  }

  private static WerewolfGame game(int n, GameRules.Werewolf rules) {
    return new WerewolfGame(ids(n), 120, 30, 10, new Random(4), rules);
  }

  private static void toDay(WerewolfGame g) {
    for (int i = 0; i < 20 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertEquals("DISCUSSION", g.phase());
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  // ------------------------------------------------------------------ nominated voting

  @Test
  public void aSecondedNominationGoesToTrialAndMoreThanHalfDownEliminates() {
    var ids = ids(8);
    var g = game(8, rules("FULL", "OPEN", false, false, "NOMINATE"));
    toDay(g);
    UUID wolf = holder(g, ids, "WEREWOLF");
    List<UUID> villagers = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).toList();
    UUID accused = villagers.get(0), nominator = villagers.get(1), seconder = villagers.get(2);
    g.select(nominator, accused);
    assertEquals("DISCUSSION", g.phase());
    assertEquals(accused, g.nominee());
    assertThrows(IllegalArgumentException.class, () -> g.select(nominator, accused));
    assertThrows(IllegalArgumentException.class, () -> g.select(accused, accused));
    g.select(seconder, accused);
    assertEquals("DEFENSE", g.phase());
    assertEquals(Set.of(accused), g.actors());
    g.advance();
    assertEquals("TRIAL", g.phase());
    assertFalse(g.actors().contains(accused));
    assertTrue(g.abilities(wolf).containsKey("die"));
    assertTrue(g.abilities(accused).isEmpty());
    for (UUID juror : g.actors()) g.use(juror, "die");
    assertEquals("the verdict falls once every thumb is cast", "NIGHT", g.night() ? "NIGHT" : g.phase());
    assertFalse(g.alive().contains(accused));
    assertEquals(accused, g.takeVerdict().lynched());
  }

  @Test
  public void aSparedNominationReturnsToTheDebateAndAnIdleDayEndsWithoutDeath() {
    var ids = ids(8);
    var g = game(8, rules("FULL", "OPEN", false, false, "NOMINATE"));
    toDay(g);
    List<UUID> villagers = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).toList();
    UUID accused = villagers.get(0);
    g.select(villagers.get(1), accused);
    g.select(villagers.get(2), accused);
    g.advance(); // defense
    for (UUID juror : g.actors()) g.use(juror, "live");
    assertEquals("DISCUSSION", g.phase());
    assertTrue(g.alive().contains(accused));
    assertNull(g.nominee());
    int living = g.alive().size();
    for (int i = 0; i < 10 && !g.night(); i++) g.advance();
    assertTrue("the day ends when the time is spent", g.night());
    assertEquals(living, g.alive().size());
    assertNull(g.takeVerdict().lynched());
  }

  // ------------------------------------------------------------------ secret ballots

  @Test
  public void secretBallotsHideTheCountUntilTheVoteCloses() {
    var ids = ids(8);
    var g = game(8, rules("FULL", "SECRET", false, false, "POINT"));
    toDay(g);
    g.advance();
    assertEquals("DAY_VOTE", g.phase());
    UUID a = ids.get(0), b = ids.get(1), c = ids.get(2);
    g.select(a, c);
    g.select(b, c);
    assertFalse(g.tallyVisible(a));
    assertTrue(g.votes(a).isEmpty());
    assertEquals(c, g.ballot(a)); // a voter always sees their own ballot
    g.drainNotices();
    g.advance();
    assertTrue(
        g.drainNotices().values().stream().anyMatch(n -> n.contains("The ballots are opened")));
  }

  // ------------------------------------------------------------------ reveal

  @Test
  public void aDeathRevealsOnlyWhatTheArenaAllows() {
    assertEquals(
        "X has died — they were the Seer", WerewolfPresentation.deathLine("X", "SEER", "FULL"));
    assertEquals(
        "X has died — they were on the village's side",
        WerewolfPresentation.deathLine("X", "SEER", "TEAM"));
    assertEquals(
        "X has died — they were on the wolves' side",
        WerewolfPresentation.deathLine("X", "WEREWOLF", "TEAM"));
    assertEquals(
        "X has died — they were not a werewolf",
        WerewolfPresentation.deathLine("X", "SEER", "WOLF"));
    assertEquals(
        "X has died — they were a werewolf", WerewolfPresentation.deathLine("X", "WEREWOLF", "WOLF"));
    assertEquals("X has died", WerewolfPresentation.deathLine("X", "SEER", "NONE"));
  }

  @Test
  public void withAHiddenRevealTheDeadRoleIsStillCalledAndPlayedOutBriefly() {
    var ids = ids(8);
    var g = game(8, rules("NONE", "OPEN", false, false, "POINT"));
    UUID seer = holder(g, ids, "SEER");
    toDay(g);
    g.advance();
    for (UUID p : ids) if (!p.equals(seer)) g.select(p, seer);
    g.advance();
    assertFalse(g.alive().contains(seer));
    // Nobody can tell the seer is gone: the call still opens, for a few seconds only.
    assertEquals("SEER", g.phase());
    assertTrue(g.actors().isEmpty());
    assertTrue(g.seconds() <= 6);
    g.advance();
    assertEquals("NIGHT", g.phase());
  }

  @Test
  public void withAFullRevealTheDeadRoleIsSkipped() {
    var ids = ids(8);
    var g = game(8, rules("FULL", "OPEN", false, false, "POINT"));
    UUID seer = holder(g, ids, "SEER");
    toDay(g);
    g.advance();
    for (UUID p : ids) if (!p.equals(seer)) g.select(p, seer);
    g.advance();
    assertEquals("NIGHT", g.phase());
  }

  // ------------------------------------------------------------------ amulet and dice

  @Test
  public void theAmuletSavesItsHolderOnceAndCanBePassedOn() {
    var ids = ids(8);
    var g = game(8, rules("FULL", "OPEN", false, true, "POINT"));
    UUID holder =
        g.drainNotices().entrySet().stream()
            .filter(e -> e.getValue().contains("Amulet of Protection"))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    assertFalse("the amulet never starts with a wolf", g.role(holder).equals("WEREWOLF"));
    // The wolves hunt the holder: the amulet saves them.
    UUID wolf = holder(g, ids, "WEREWOLF");
    for (int i = 0; i < 10 && !g.phase().equals("NIGHT"); i++) g.advance();
    g.select(wolf, holder);
    for (int i = 0; i < 20 && g.phase().equals("NIGHT"); i++) g.advance();
    assertTrue("the amulet saved the holder", g.alive().contains(holder));
    // The amulet's call is the last one of the night.
    for (int i = 0; i < 20 && !g.phase().equals("AMULET") && !g.phase().equals("DISCUSSION"); i++)
      g.advance();
    assertEquals("AMULET", g.phase());
    UUID next = ids.stream().filter(p -> !p.equals(holder) && !p.equals(wolf)).findFirst().orElseThrow();
    g.select(holder, next);
    assertEquals("DISCUSSION", g.phase());
  }

  @Test
  public void aUnusedSavedAmuletShattersAtDawn() {
    var ids = ids(8);
    var g = game(8, rules("FULL", "OPEN", false, true, "POINT"));
    UUID holder =
        g.drainNotices().entrySet().stream()
            .filter(e -> e.getValue().contains("Amulet of Protection"))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow();
    UUID wolf = holder(g, ids, "WEREWOLF");
    for (int i = 0; i < 10 && !g.phase().equals("NIGHT"); i++) g.advance();
    g.select(wolf, holder);
    for (int i = 0; i < 20 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertTrue(g.alive().contains(holder));
    // Hunted again the next night, the holder dies: the amulet shattered.
    for (int i = 0; i < 20 && !g.phase().equals("NIGHT"); i++) g.advance();
    g.select(wolf, holder);
    for (int i = 0; i < 20 && !g.phase().equals("DISCUSSION"); i++) g.advance();
    assertFalse(g.alive().contains(holder));
  }

  @Test
  public void theDailyDiceNeverStallTheGame() {
    for (int seed = 0; seed < 60; seed++) {
      var ids = ids(9);
      var g =
          new WerewolfGame(
              ids, 5, 5, 5, new Random(seed), rules("FULL", "OPEN", true, false, "POINT"));
      Set<UUID> bots = new HashSet<>(ids);
      var brain = new WerewolfBots(new Random(seed));
      for (int turn = 0; turn < 300 && g.winner().isEmpty(); turn++) {
        String key = g.turnKey();
        for (long t = 0; t < 10_000 && key.equals(g.turnKey()); t += 1000)
          brain.think(g, bots, t, 10_000, false);
        if (key.equals(g.turnKey())) brain.think(g, bots, 10_000, 10_000, true);
        if (key.equals(g.turnKey())) g.advance();
      }
      assertFalse("the game should end, seed " + seed, g.winner().isEmpty());
    }
  }

  @Test
  public void nominatedGamesWithBotsFinish() {
    for (int seed = 0; seed < 60; seed++) {
      var ids = ids(9);
      var g =
          new WerewolfGame(
              ids, 5, 5, 5, new Random(seed), rules("NONE", "OPEN", false, false, "NOMINATE"));
      Set<UUID> bots = new HashSet<>(ids);
      var brain = new WerewolfBots(new Random(seed));
      for (int turn = 0; turn < 600 && g.winner().isEmpty(); turn++) {
        String key = g.turnKey();
        for (long t = 0; t < 10_000 && key.equals(g.turnKey()); t += 1000)
          for (var action : brain.think(g, bots, t, 10_000, false))
            assertFalse(action.toString(), action.what().startsWith("was refused"));
        if (key.equals(g.turnKey())) brain.think(g, bots, 10_000, 10_000, true);
        if (key.equals(g.turnKey())) g.advance();
      }
      assertFalse("the game should end, seed " + seed, g.winner().isEmpty());
    }
  }
}
