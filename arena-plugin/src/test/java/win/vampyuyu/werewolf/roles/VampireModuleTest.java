package win.vampyuyu.werewolf.roles;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.werewolf.WerewolfBots;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.WerewolfRules;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;

public class VampireModuleTest {
  private static List<UUID> ids(int seed, int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(seed, i + 1));
    return ids;
  }

  private static WerewolfGame game(List<UUID> ids, int seed, Map<String, Integer> cast) {
    return new WerewolfGame(
        ids, 5, 5, 5, new Random(seed), new WerewolfRules(cast, 5, 5, 5));
  }

  private static List<UUID> holders(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).toList();
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return holders(g, ids, role).getFirst();
  }

  /** Runs the clock until the game is in this phase. */
  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 40 && !g.phase().equals(phase) && g.winner().isEmpty(); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** Runs the clock to the day vote, or to the end of the game. */
  private static void toVote(WerewolfGame g) {
    for (int i = 0; i < 40 && !g.phase().equals("DAY_VOTE") && g.winner().isEmpty(); i++)
      g.advance();
  }

  /** The vampires agree on a victim: the call ends. */
  private static void feast(WerewolfGame g, List<UUID> vampires, UUID victim) {
    toPhase(g, "VAMPIRE");
    for (UUID v : vampires) if (g.alive().contains(v)) g.select(v, victim);
    assertNotEquals("VAMPIRE", g.phase());
  }

  private static UUID firstPlain(WerewolfGame g, List<UUID> ids) {
    return ids.stream()
        .filter(p -> g.role(p).equals("VILLAGER") && g.alive().contains(p))
        .findFirst()
        .orElseThrow();
  }

  @Test
  public void theVictimDiesWhenTheDayVoteOpensAndTheVillageIsTold() {
    var ids = ids(1, 8);
    var g = game(ids, 1, Map.of("VAMPIRE", 2));
    var vampires = holders(g, ids, "VAMPIRE");
    UUID victim = firstPlain(g, ids);
    feast(g, vampires, victim);
    toPhase(g, "DISCUSSION");
    assertTrue("alive until the vote opens", g.alive().contains(victim));
    g.drainNotices();
    g.advance();
    assertEquals("DAY_VOTE", g.phase());
    assertFalse(g.alive().contains(victim));
    var notices = g.drainNotices();
    for (UUID p : g.alive()) assertTrue(notices.get(p).contains(victim.toString()));
    assertFalse("the dead are not told", notices.containsKey(victim));
  }

  @Test
  public void aSplitFeastDrainsNobodyAndAVampireCannotPickAVampire() {
    var ids = ids(2, 8);
    var g = game(ids, 2, Map.of("VAMPIRE", 2));
    var vampires = holders(g, ids, "VAMPIRE");
    toPhase(g, "VAMPIRE");
    assertThrows(
        IllegalArgumentException.class, () -> g.select(vampires.get(0), vampires.get(1)));
    List<UUID> prey = ids.stream().filter(p -> !vampires.contains(p)).toList();
    g.select(vampires.get(0), prey.get(0));
    g.select(vampires.get(1), prey.get(1));
    assertEquals("VAMPIRE", g.phase());
    toPhase(g, "DAY_VOTE");
    assertEquals(8, g.alive().size());
  }

  @Test
  public void onlyVampiresActAndOnlyTheyKnowTheFeast() {
    var ids = ids(3, 8);
    var g = game(ids, 3, Map.of("VAMPIRE", 2));
    var vampires = holders(g, ids, "VAMPIRE");
    toPhase(g, "VAMPIRE");
    assertEquals(new HashSet<>(vampires), g.actors());
    UUID villager = firstPlain(g, ids);
    assertThrows(
        IllegalArgumentException.class, () -> g.select(villager, vampires.get(0)));
    assertTrue(g.pack(villager).isEmpty());
    assertEquals(List.of(vampires.get(1)), g.pack(vampires.get(0)));
    g.select(vampires.get(0), villager);
    assertEquals(Map.of(villager, 1), g.votes(vampires.get(1)));
    assertTrue("the village does not see the ballots", g.votes(villager).isEmpty());
    assertTrue(g.sleeping(villager));
  }

  @Test
  public void wolvesCannotKillAVampire() {
    var ids = ids(4, 9);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "VAMPIRE", 1));
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID vampire = holder(g, ids, "VAMPIRE");
    toPhase(g, "NIGHT");
    g.select(wolf, vampire);
    toPhase(g, "DAY_VOTE");
    assertTrue(g.alive().contains(vampire));
    assertEquals(9, g.alive().size());
  }

  @Test
  public void aWolfAndAVampireHuntingTheSamePlayerKillHimOnce() {
    var ids = ids(5, 9);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "VAMPIRE", 1));
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID vampire = holder(g, ids, "VAMPIRE");
    UUID prey = firstPlain(g, ids);
    toPhase(g, "NIGHT");
    g.select(wolf, prey);
    feast(g, List.of(vampire), prey);
    toPhase(g, "DAY_VOTE");
    assertFalse(g.alive().contains(prey));
    assertEquals(8, g.alive().size());
  }

  @Test
  public void aDyingHunterStrikesTheNearestVampireInsteadOfShooting() {
    for (int seed = 0; seed < 20; seed++) {
      var ids = ids(10 + seed, 9);
      var g = game(ids, seed, Map.of("VAMPIRE", 2, "HUNTER", 1));
      UUID hunter = holder(g, ids, "HUNTER");
      var vampires = holders(g, ids, "VAMPIRE");
      UUID expected = nearest(ids, hunter, vampires);
      feast(g, vampires, hunter);
      toPhase(g, "DAY_VOTE");
      assertFalse(g.alive().contains(hunter));
      assertFalse("no shot for a vampire hunter, seed " + seed, g.phase().equals("HUNTER_SHOT"));
      assertFalse(g.alive().contains(expected));
      UUID other = vampires.get(0).equals(expected) ? vampires.get(1) : vampires.get(0);
      assertTrue(g.alive().contains(other));
      assertTrue(g.drainNotices().get(other).contains(expected.toString()));
    }
  }

  /** Reference implementation: seat distance around the ring, lower seat first on a tie. */
  private static UUID nearest(List<UUID> ids, UUID from, List<UUID> candidates) {
    UUID best = null;
    int bestDistance = Integer.MAX_VALUE, n = ids.size(), at = ids.indexOf(from);
    for (UUID p : ids) {
      if (!candidates.contains(p)) continue;
      int gap = Math.abs(ids.indexOf(p) - at);
      int distance = Math.min(gap, n - gap);
      if (distance < bestDistance) {
        best = p;
        bestDistance = distance;
      }
    }
    return best;
  }

  @Test
  public void withoutAVampireTheHunterKeepsHisShot() {
    var ids = ids(6, 9);
    var g = game(ids, 6, Map.of("WEREWOLF", 1, "VAMPIRE", 1, "HUNTER", 1));
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID vampire = holder(g, ids, "VAMPIRE");
    UUID hunter = holder(g, ids, "HUNTER");
    // Night one: the vampire feeds on a villager, then the village votes the vampire out.
    feast(g, List.of(vampire), firstPlain(g, ids));
    toPhase(g, "DAY_VOTE");
    for (UUID p : g.alive()) g.select(p, p.equals(vampire) ? wolf : vampire);
    g.advance();
    assertFalse(g.alive().contains(vampire));
    assertTrue(g.winner().isEmpty());
    toPhase(g, "NIGHT");
    g.select(wolf, hunter);
    toPhase(g, "HUNTER_SHOT");
    UUID target = firstPlain(g, ids);
    g.select(hunter, target);
    assertFalse("the hunter's shot is fired", g.alive().contains(target));
  }

  @Test
  public void aHunterWhoDiesByTheVoteAlsoStrikesAVampire() {
    var ids = ids(7, 9);
    var g = game(ids, 7, Map.of("VAMPIRE", 1, "HUNTER", 1));
    UUID hunter = holder(g, ids, "HUNTER");
    UUID vampire = holder(g, ids, "VAMPIRE");
    UUID victim = firstPlain(g, ids);
    feast(g, List.of(vampire), victim);
    toPhase(g, "DAY_VOTE");
    for (UUID p : g.alive()) if (!p.equals(hunter)) g.select(p, hunter);
    g.advance();
    assertFalse(g.alive().contains(hunter));
    assertFalse(g.alive().contains(vampire));
    assertEquals("VILLAGE", g.winner());
  }

  @Test
  public void vampiresWinWhenOnlyVampiresAreLeft() {
    var ids = ids(8, 5);
    var g = game(ids, 8, Map.of("VAMPIRE", 2));
    var vampires = holders(g, ids, "VAMPIRE");
    for (int night = 0; night < 3; night++) {
      assertTrue(g.winner().isEmpty());
      feast(g, vampires, firstPlain(g, ids));
      toVote(g);
    }
    assertEquals("VAMPIRES", g.winner());
    assertEquals(new HashSet<>(vampires), g.winners());
  }

  @Test
  public void deadVampiresStillShareTheVictory() {
    var ids = ids(9, 7);
    var g = game(ids, 9, Map.of("VAMPIRE", 3));
    var vampires = holders(g, ids, "VAMPIRE");
    UUID fallen = vampires.get(0);
    UUID villager = firstPlain(g, ids);
    // The village votes one vampire out on the first day.
    feast(g, vampires, villager);
    toPhase(g, "DAY_VOTE");
    for (UUID p : g.alive()) g.select(p, p.equals(fallen) ? vampires.get(1) : fallen);
    g.advance();
    assertFalse(g.alive().contains(fallen));
    while (g.winner().isEmpty()) {
      feast(g, vampires, firstPlain(g, ids));
      toVote(g);
    }
    assertEquals("VAMPIRES", g.winner());
    assertTrue(g.winners().containsAll(vampires));
  }

  @Test
  public void aWolfCannotWinWhileAVampireLives() {
    var ids = ids(11, 5);
    var g = game(ids, 11, Map.of("WEREWOLF", 1, "VAMPIRE", 1));
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID vampire = holder(g, ids, "VAMPIRE");
    // Night one: the vampire drains a villager.
    feast(g, List.of(vampire), firstPlain(g, ids));
    toPhase(g, "DAY_VOTE");
    assertEquals(4, g.alive().size());
    // Night two: the wolf eats another one, leaving wolf, vampire and one villager.
    toPhase(g, "NIGHT");
    g.select(wolf, firstPlain(g, ids));
    toVote(g);
    assertEquals(Set.of(wolf, vampire, firstPlain(g, ids)), g.alive());
    assertTrue("1 wolf >= 1 other, yet a vampire lives", g.winner().isEmpty());
  }

  // ---------------------------------------------------------------- bots

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

  private static void play(WerewolfGame game, Set<UUID> bots, int seed) {
    WerewolfBots brain = new WerewolfBots(new Random(seed));
    List<WerewolfBots.Action> log = new ArrayList<>();
    for (int turn = 0; turn < 300 && game.winner().isEmpty(); turn++)
      log.addAll(turn(game, brain, bots));
    assertFalse("The game should end, seed " + seed, game.winner().isEmpty());
    for (WerewolfBots.Action action : log)
      assertFalse(action.toString(), action.what().startsWith("was refused"));
  }

  @Test
  public void aPracticeGameWithVampiresFinishes() {
    Map<String, Integer> cast =
        Map.of("VAMPIRE", 2, "WEREWOLF", 1, "SEER", 1, "HUNTER", 1, "WITCH", 1);
    for (int seed = 0; seed < 60; seed++) {
      List<UUID> ids = ids(seed, 10);
      play(game(ids, seed, cast), new HashSet<>(ids), seed);
      List<UUID> mixed = ids(seed, 9);
      Set<UUID> bots = new HashSet<>(mixed);
      bots.remove(mixed.getFirst());
      play(game(mixed, seed, Map.of("VAMPIRE", 2, "HUNTER", 1)), bots, seed);
    }
  }

  @Test
  public void botVampiresFollowAHumanVampire() {
    for (int seed = 0; seed < 30; seed++) {
      List<UUID> ids = ids(seed, 10);
      var g = game(ids, seed, Map.of("VAMPIRE", 3));
      var vampires = holders(g, ids, "VAMPIRE");
      UUID human = vampires.getFirst();
      Set<UUID> bots = new HashSet<>(ids);
      bots.remove(human);
      toPhase(g, "VAMPIRE");
      UUID prey = ids.stream().filter(p -> !vampires.contains(p)).findFirst().orElseThrow();
      g.select(human, prey);
      var brain = new WerewolfBots(new Random(seed));
      brain.think(g, bots, 10_000, 10_000, true);
      assertNotEquals("the feast ends, seed " + seed, "VAMPIRE", g.phase());
      toPhase(g, "DAY_VOTE");
      assertFalse(g.alive().contains(prey));
    }
  }

  @Test
  public void theHookItselfMakesBotVampiresFollowAHumanVampire() {
    for (int seed = 0; seed < 30; seed++) {
      List<UUID> ids = ids(seed, 10);
      var g = game(ids, seed, Map.of("VAMPIRE", 3));
      var vampires = holders(g, ids, "VAMPIRE");
      UUID human = vampires.getFirst();
      toPhase(g, "VAMPIRE");
      UUID prey = ids.stream().filter(p -> !vampires.contains(p)).findFirst().orElseThrow();
      g.select(human, prey);
      var bots = vampires.subList(1, vampires.size());
      for (UUID bot : bots.subList(0, bots.size() - 1)) {
        g.botAct(bot, new Random(seed));
        assertEquals("seed " + seed, prey, g.ballot(bot));
      }
      // The last vote makes the feast unanimous: the call closes on the human's prey.
      g.botAct(bots.getLast(), new Random(seed));
      assertNotEquals("VAMPIRE", g.phase());
      toPhase(g, "DAY_VOTE");
      assertFalse("seed " + seed, g.alive().contains(prey));
    }
  }

  @Test
  public void aVampireIsAnnouncedAsAVampire() {
    String line = WerewolfPresentation.deathLine("X", "VAMPIRE", "FULL");
    assertTrue(line, line.contains("a Vampire"));
  }
}
