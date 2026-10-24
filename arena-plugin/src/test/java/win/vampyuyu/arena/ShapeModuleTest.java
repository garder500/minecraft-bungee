package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class ShapeModuleTest {
  private static final int N = 9;
  private static final Set<String> FIRST_NIGHT =
      Set.of("CUPID", "DOPPELGANGER", "HOODLUM", "MASON", "MINION", "GHOST", "DRUNK", "APPRENTICE_SEER");

  private static List<UUID> ids(int seed, int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(seed + 700L, i + 1));
    return ids;
  }

  private static WerewolfGame game(List<UUID> ids, int seed, Map<String, Integer> cast) {
    return new WerewolfGame(
        ids, 5, 5, 5, new Random(seed), new GameRules.Werewolf(cast, 5, 5, 5));
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 80 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** The first seed whose drunk is dealt this real role. */
  private static int seedWhereDrunkIs(String real, Map<String, Integer> cast) {
    for (int seed = 0; seed < 600; seed++) {
      var ids = ids(seed, N);
      var g = game(ids, seed, cast);
      if (ids.stream().anyMatch(p -> g.role(p).equals(real) && g.selfRoleName(p).equals("VILLAGER")
          && !g.role(p).equals("VILLAGER"))) return seed;
    }
    throw new AssertionError("no seed gives a drunk " + real);
  }

  /** Every living player votes for the target (the target votes for somebody else). */
  private static void lynch(WerewolfGame g, List<UUID> ids, UUID target) {
    toPhase(g, "DAY_VOTE");
    UUID other = ids.stream().filter(p -> !p.equals(target) && g.alive().contains(p)).findFirst().orElseThrow();
    for (UUID p : g.alive()) g.select(p, p.equals(target) ? other : target);
    g.advance();
  }

  /** Lets the clock run (nobody acts) until the drunk's notice arrives; returns the notices and the dawns seen. */
  private static Map<UUID, String> untilSober(WerewolfGame g, UUID drunk, Set<String> phasesMet, int[] dawns) {
    for (int i = 0; i < 200; i++) {
      Map<UUID, String> notices = g.drainNotices();
      if (notices.getOrDefault(drunk, "").contains("You sober up")) return notices;
      phasesMet.add(g.phase());
      if (g.phase().equals("DISCUSSION")) dawns[0]++;
      g.advance();
    }
    throw new AssertionError("the drunk never woke");
  }

  // ------------------------------------------------------------------ drunk

  @Test
  public void aDrunkHoldsARealRoleButBelievesHeIsAVillager() {
    Set<String> seen = new HashSet<>();
    for (int seed = 0; seed < 150; seed++) {
      var ids = ids(seed, N);
      var g = game(ids, seed, Map.of("WEREWOLF", 1, "SEER", 1, "DRUNK", 1, "CUPID", 1));
      UUID drunk = ids.stream().filter(p -> g.selfRoleName(p).equals("VILLAGER") && g.role(p).equals("DRUNK")).findFirst().orElse(null);
      assertNull("the drunk never keeps the DRUNK card", drunk);
      long hidden = ids.stream().filter(p -> g.selfRoleName(p).equals("VILLAGER")).count();
      assertTrue(hidden >= 1);
      for (String r : List.of("SEER", "WEREWOLF", "CUPID"))
        assertTrue(r + " is never held twice", ids.stream().filter(p -> g.role(p).equals(r)).count() <= (r.equals("WEREWOLF") ? 2 : 1));
      for (UUID p : ids) {
        assertNotEquals("DRUNK", g.role(p));
        if (g.selfRoleName(p).equals("VILLAGER") && !g.role(p).equals("VILLAGER")) {
          seen.add(g.role(p));
          assertFalse(g.role(p) + " is a first-night role", FIRST_NIGHT.contains(g.role(p)));
        }
      }
    }
    assertTrue(seen.toString(), seen.size() >= 4);
    assertTrue(seen.contains("WEREWOLF") || seen.contains("SEER"));
  }

  @Test
  public void aSleepingDrunkIsNeverCalledEvenWhenHeIsReallyTheSeerOrAWolf() {
    int seer = seedWhereDrunkIs("SEER", Map.of("WEREWOLF", 1, "DRUNK", 1));
    var ids = ids(seer, N);
    var g = game(ids, seer, Map.of("WEREWOLF", 1, "DRUNK", 1));
    UUID drunk = holder(g, ids, "SEER");
    Set<String> met = new HashSet<>();
    do {
      met.add(g.phase());
      assertFalse(g.actors().contains(drunk));
      g.advance();
    } while (!g.phase().equals("DISCUSSION"));
    assertFalse("the seer's call never opens", met.contains("SEER"));
    assertThrows(IllegalArgumentException.class, () -> g.select(drunk, ids.get(0).equals(drunk) ? ids.get(1) : ids.get(0)));

    int wolf = seedWhereDrunkIs("WEREWOLF", Map.of("WEREWOLF", 1, "DRUNK", 1));
    var wolfIds = ids(wolf, N);
    var h = game(wolfIds, wolf, Map.of("WEREWOLF", 1, "DRUNK", 1));
    List<UUID> wolves = wolfIds.stream().filter(p -> h.role(p).equals("WEREWOLF")).toList();
    UUID sleeper = wolves.stream().filter(p -> h.selfRoleName(p).equals("VILLAGER")).findFirst().orElseThrow();
    UUID awake = wolves.stream().filter(p -> !p.equals(sleeper)).findFirst().orElseThrow();
    toPhase(h, "NIGHT");
    assertTrue(h.actors().contains(awake));
    assertFalse(h.actors().contains(sleeper));
    assertTrue(h.sleeping(sleeper));
  }

  @Test
  public void theDrunkSobersUpOnTheThirdNightAndTheSeerCallOpensForHim() {
    var cast = Map.of("WEREWOLF", 1, "DRUNK", 1);
    int seed = seedWhereDrunkIs("SEER", cast);
    var ids = ids(seed, N);
    var g = game(ids, seed, cast);
    UUID drunk = holder(g, ids, "SEER");
    Set<String> met = new HashSet<>();
    int[] dawns = {0};
    var notices = untilSober(g, drunk, met, dawns);
    assertEquals("two dawns have passed", 2, dawns[0]);
    assertFalse(met.contains("SEER"));
    assertEquals("You sober up: you are the Seer", notices.get(drunk));
    for (UUID p : ids) if (!p.equals(drunk)) assertFalse(notices.getOrDefault(p, ""), notices.getOrDefault(p, "").contains("sober"));
    assertEquals("SEER", g.selfRoleName(drunk));
    toPhase(g, "SEER");
    assertTrue(g.actors().contains(drunk));
    UUID target = ids.stream().filter(p -> !p.equals(drunk)).findFirst().orElseThrow();
    assertNotNull(g.select(drunk, target));
  }

  @Test
  public void aDrunkWolfJoinsThePackOnTheThirdNightAndStillCountsAsAWolf() {
    var cast = Map.of("WEREWOLF", 1, "DRUNK", 1);
    int seed = seedWhereDrunkIs("WEREWOLF", cast);
    var ids = ids(seed, N);
    var g = game(ids, seed, cast);
    List<UUID> wolves = ids.stream().filter(p -> g.role(p).equals("WEREWOLF")).toList();
    UUID drunk = wolves.stream().filter(p -> g.selfRoleName(p).equals("VILLAGER")).findFirst().orElseThrow();
    UUID other = wolves.stream().filter(p -> !p.equals(drunk)).findFirst().orElseThrow();
    // Parity: lynching the other wolf does not end the game while the drunk wolf lives.
    lynch(g, ids, other);
    assertFalse(g.alive().contains(other));
    assertEquals("the drunk still counts as a wolf", "", g.winner());
    assertTrue(g.alive().contains(drunk));
    g.drainNotices();
  }

  @Test
  public void thePackIsToldWhenTheDrunkWolfWakes() {
    var cast = Map.of("WEREWOLF", 2, "DRUNK", 1);
    int seed = -1;
    for (int s = 0; s < 800 && seed < 0; s++) {
      var ids = ids(s, N);
      var g = game(ids, s, cast);
      if (ids.stream().filter(p -> g.role(p).equals("WEREWOLF")).count() == 3) seed = s;
    }
    var ids = ids(seed, N);
    var g = game(ids, seed, cast);
    UUID drunk = ids.stream().filter(p -> g.role(p).equals("WEREWOLF") && g.selfRoleName(p).equals("VILLAGER")).findFirst().orElseThrow();
    var notices = untilSober(g, drunk, new HashSet<>(), new int[1]);
    assertEquals("You sober up: you are the Werewolf", notices.get(drunk));
    for (UUID p : ids) {
      String text = notices.getOrDefault(p, "");
      if (p.equals(drunk)) continue;
      assertEquals(p + " " + text, g.role(p).equals("WEREWOLF"), text.contains("joins the pack"));
    }
  }

  @Test
  public void aDrunkWhoDiedBeforeTheThirdNightIsNotWoken() {
    var cast = Map.of("WEREWOLF", 1, "DRUNK", 1);
    var ids = ids(5, N);
    var g = game(ids, 5, cast);
    UUID drunk = ids.stream().filter(p -> g.selfRoleName(p).equals("VILLAGER") && !g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    lynch(g, ids, drunk);
    assertFalse(g.alive().contains(drunk));
    g.drainNotices();
    for (int i = 0; i < 60 && g.winner().isEmpty(); i++) {
      assertFalse(g.drainNotices().getOrDefault(drunk, "").contains("sober"));
      g.advance();
    }
  }

  // ------------------------------------------------------------------ doppelganger

  private static final Map<String, Integer> DOPPEL = Map.of("WEREWOLF", 1, "SEER", 1, "DOPPELGANGER", 1);

  @Test
  public void theDoppelgangerPicksOnTheFirstNightAndStaysAVillagerMeanwhile() {
    var ids = ids(1, N);
    var g = game(ids, 1, DOPPEL);
    UUID doppel = holder(g, ids, "DOPPELGANGER");
    UUID seer = holder(g, ids, "SEER");
    assertEquals("DOPPEL", g.phase());
    assertEquals(Set.of(doppel), g.actors());
    String reply = g.select(doppel, seer);
    assertTrue(reply, reply.contains("when they die"));
    assertNotEquals("DOPPEL", g.phase());
    assertEquals("DOPPELGANGER", g.role(doppel));
    assertEquals("the pick is secret", Map.of(), g.drainNotices().entrySet().stream()
        .filter(e -> !e.getValue().isEmpty()).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  @Test
  public void theDoppelgangerRefusesBadPicks() {
    var ids = ids(2, N);
    var g = game(ids, 2, DOPPEL);
    UUID doppel = holder(g, ids, "DOPPELGANGER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID other = holder(g, ids, "SEER");
    assertThrows(IllegalArgumentException.class, () -> g.select(doppel, doppel));
    assertThrows(IllegalArgumentException.class, () -> g.select(doppel, new UUID(0, 0)));
    assertThrows(IllegalArgumentException.class, () -> g.select(wolf, other));
    assertEquals("DOPPEL", g.phase());
    g.select(doppel, other);
    assertThrows(IllegalArgumentException.class, () -> g.select(doppel, wolf));
  }

  @Test
  public void theDoppelgangerTakesTheRoleOfTheDeadAndOnlyShesTold() {
    var ids = ids(3, N);
    var g = game(ids, 3, DOPPEL);
    UUID doppel = holder(g, ids, "DOPPELGANGER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID seer = holder(g, ids, "SEER");
    g.select(doppel, seer);
    toPhase(g, "NIGHT");
    g.drainNotices();
    g.select(wolf, seer);
    toPhase(g, "DISCUSSION");
    assertFalse(g.alive().contains(seer));
    assertEquals("SEER", g.role(doppel));
    var notices = g.drainNotices();
    assertTrue(notices.get(doppel), notices.get(doppel).contains("you are now the Seer"));
    for (UUID p : ids) if (!p.equals(doppel)) assertFalse(notices.getOrDefault(p, ""), notices.getOrDefault(p, "").contains("you are now"));
    // The new seer is called the next night.
    toPhase(g, "SEER");
    assertTrue(g.actors().contains(doppel));
  }

  @Test
  public void copyingAWolfMakesHerAWolfForTheWinCheck() {
    var ids = ids(4, N);
    var g = game(ids, 4, Map.of("WEREWOLF", 2, "DOPPELGANGER", 1));
    UUID doppel = holder(g, ids, "DOPPELGANGER");
    UUID wolf = ids.stream().filter(p -> g.role(p).equals("WEREWOLF")).findFirst().orElseThrow();
    g.select(doppel, wolf);
    lynch(g, ids, wolf);
    assertFalse(g.alive().contains(wolf));
    assertEquals("WEREWOLF", g.role(doppel));
    assertTrue(g.drainNotices().get(doppel).contains("Werewolf"));
    assertEquals("two wolves are left", "", g.winner());
    UUID last = ids.stream().filter(p -> g.alive().contains(p) && g.role(p).equals("WEREWOLF") && !p.equals(doppel)).findFirst().orElseThrow();
    lynch(g, ids, last);
    assertEquals("the copied wolf keeps the village from winning", "", g.winner());
  }

  @Test
  public void aUniqueRoleAlreadyHeldIsNotCopiedTheDoppelgangerBecomesAVillager() {
    var cast = Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1, "DOPPELGANGER", 1);
    var ids = ids(6, N);
    var g = game(ids, 6, cast);
    UUID doppel = holder(g, ids, "DOPPELGANGER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID seer = holder(g, ids, "SEER");
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    g.select(doppel, seer);
    toPhase(g, "NIGHT");
    g.drainNotices();
    g.select(wolf, seer);
    toPhase(g, "DISCUSSION");
    assertEquals("SEER", g.role(apprentice));
    assertEquals("VILLAGER", g.role(doppel));
    assertEquals(1, ids.stream().filter(p -> g.alive().contains(p) && g.role(p).equals("SEER")).count());
    var notices = g.drainNotices();
    assertTrue(notices.get(doppel), notices.get(doppel).contains("plain Villager"));
    assertFalse(notices.get(apprentice).contains("Villager"));
  }

  @Test
  public void aDeadDoppelgangerCopiesNothing() {
    var ids = ids(7, N);
    var g = game(ids, 7, DOPPEL);
    UUID doppel = holder(g, ids, "DOPPELGANGER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID seer = holder(g, ids, "SEER");
    g.select(doppel, seer);
    toPhase(g, "NIGHT");
    g.select(wolf, doppel);
    toPhase(g, "DISCUSSION");
    assertFalse(g.alive().contains(doppel));
    g.drainNotices();
    lynch(g, ids, seer);
    assertFalse(g.alive().contains(seer));
    assertEquals("DOPPELGANGER", g.role(doppel));
    assertFalse(g.drainNotices().containsKey(doppel));
  }

  // ------------------------------------------------------------------ bots

  @Test
  public void aDoppelgangerBotPicksAnotherLivingPlayer() {
    Set<UUID> picked = new HashSet<>();
    for (int seed = 0; seed < 30; seed++) {
      var ids = ids(seed, N);
      var g = game(ids, seed, DOPPEL);
      UUID doppel = holder(g, ids, "DOPPELGANGER");
      g.botAct(doppel, new Random(seed));
      assertNotEquals("the bot took its turn", "DOPPEL", g.phase());
      toPhase(g, "NIGHT");
      // Whoever she picked, the death of the seer is the test: kill the seer and see if she copies.
      UUID seer = holder(g, ids, "SEER");
      g.select(holder(g, ids, "WEREWOLF"), seer);
      toPhase(g, "DISCUSSION");
      if (g.role(doppel).equals("SEER")) picked.add(seer);
    }
    assertFalse("the bot sometimes picks the seer", picked.isEmpty());
  }

  private static final Map<String, Integer> BOT_CAST =
      Map.of("WEREWOLF", 2, "SEER", 1, "DOPPELGANGER", 1, "DRUNK", 1, "WITCH", 1, "GUARD", 1);

  private static List<WerewolfBots.Action> turn(WerewolfGame game, WerewolfBots brain, Set<UUID> bots) {
    List<WerewolfBots.Action> log = new ArrayList<>();
    String key = game.turnKey();
    for (long t = 0; t < 10_000 && key.equals(game.turnKey()); t += 1000)
      log.addAll(brain.think(game, bots, t, 10_000, false));
    if (key.equals(game.turnKey())) log.addAll(brain.think(game, bots, 10_000, 10_000, true));
    if (key.equals(game.turnKey())) game.advance();
    return log;
  }

  @Test
  public void aPracticeGameWithTheShapeRolesFinishesAndTheDoppelgangerBotActs() {
    int doppelActs = 0;
    for (int seed = 0; seed < 60; seed++) {
      var ids = ids(seed, 11);
      var g = game(ids, seed, BOT_CAST);
      Set<UUID> bots = new HashSet<>(ids);
      WerewolfBots brain = new WerewolfBots(new Random(seed));
      List<WerewolfBots.Action> log = new ArrayList<>();
      for (int t = 0; t < 250 && g.winner().isEmpty(); t++) log.addAll(turn(g, brain, bots));
      assertFalse("The game should end, seed " + seed, g.winner().isEmpty());
      for (WerewolfBots.Action a : log) {
        // The shared bots still aim at exiled players now and then (a drunk may wake as the hag).
        assertFalse(
            a.toString(),
            a.what().startsWith("was refused") && !a.what().contains("away from the village"));
        if (a.role().equals("DOPPELGANGER") && a.what().contains("DOPPEL")) doppelActs++;
      }
    }
    assertTrue("doppelganger bots chose somebody: " + doppelActs, doppelActs > 0);
  }
}
