package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class InsightModuleTest {
  private static List<UUID> ids(int seed, int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(seed, i + 1));
    return ids;
  }

  private static WerewolfGame game(List<UUID> ids, int seed, Map<String, Integer> cast) {
    return new WerewolfGame(
        ids, 5, 5, 5, new Random(seed), new GameRules.Werewolf(cast, 5, 5, 5));
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  /** Runs the clock until the given phase opens. */
  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 80 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** Runs the clock until the next day starts, recording every phase met on the way. */
  private static Set<String> toDay(WerewolfGame g) {
    Set<String> met = new HashSet<>();
    for (int i = 0; i < 80 && !g.phase().equals("DISCUSSION"); i++) {
      met.add(g.phase());
      g.advance();
    }
    assertEquals("DISCUSSION", g.phase());
    return met;
  }

  /** Runs the clock from the day to the next night's wolf hunt, recording the phases met. */
  private static Set<String> toNextHunt(WerewolfGame g) {
    Set<String> met = new HashSet<>();
    g.advance();
    for (int i = 0; i < 80 && !g.phase().equals("NIGHT"); i++) {
      met.add(g.phase());
      g.advance();
    }
    assertEquals("NIGHT", g.phase());
    return met;
  }

  /** Runs the clock from one dawn to the next, recording the phases met (the whole night). */
  private static Set<String> wholeNight(WerewolfGame g) {
    Set<String> met = new HashSet<>();
    g.advance();
    for (int i = 0; i < 80 && !g.phase().equals("DISCUSSION"); i++) {
      met.add(g.phase());
      g.advance();
    }
    assertEquals("DISCUSSION", g.phase());
    return met;
  }

  private static boolean mentions(Map<UUID, String> notices, UUID who, String text) {
    return notices.getOrDefault(who, "").contains(text);
  }

  // ------------------------------------------------------------------ aura seer

  @Test
  public void theAuraSeerReadsSpecialRolesAndPlainOnes() {
    var ids = ids(1, 9);
    var g = game(ids, 1, Map.of("WEREWOLF", 1, "SEER", 1, "AURA_SEER", 1, "CURSED", 1));
    UUID aura = holder(g, ids, "AURA_SEER");
    UUID seer = holder(g, ids, "SEER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID cursed = holder(g, ids, "CURSED");
    UUID villager = holder(g, ids, "VILLAGER");
    toPhase(g, "AURA");
    assertTrue(g.actors().contains(aura));
    assertTrue(g.abilities(aura).containsKey("pass"));
    String reply = g.select(aura, seer);
    assertTrue(reply, reply.contains("special"));
    assertNotEquals("AURA", g.phase());
    for (UUID plain : List.of(wolf, cursed, villager)) {
      toNextAura(g);
      reply = g.select(aura, plain);
      assertTrue(reply, reply.contains("plain"));
    }
  }

  /** Runs the clock to the aura seer's call of the next night. */
  private static void toNextAura(WerewolfGame g) {
    toNextHunt(g);
    toPhase(g, "AURA");
  }

  @Test
  public void theAuraSeerCannotReadHimselfOrTheDeadOrActOutOfTurn() {
    var ids = ids(2, 9);
    var g = game(ids, 2, Map.of("WEREWOLF", 1, "AURA_SEER", 1));
    UUID aura = holder(g, ids, "AURA_SEER");
    UUID other = holder(g, ids, "VILLAGER");
    assertThrows(IllegalArgumentException.class, () -> g.select(aura, other));
    toPhase(g, "AURA");
    assertThrows(IllegalArgumentException.class, () -> g.select(aura, aura));
    assertThrows(IllegalArgumentException.class, () -> g.select(other, aura));
    assertEquals("AURA", g.phase()); // the refusals did not spend the call
    g.select(aura, other);
    assertThrows(IllegalArgumentException.class, () -> g.select(aura, other));
  }

  @Test
  public void passingEndsTheAuraCallAndDeadAuraSeersAreNotCalled() {
    var ids = ids(3, 9);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "AURA_SEER", 1));
    UUID aura = holder(g, ids, "AURA_SEER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    toPhase(g, "NIGHT");
    g.select(wolf, aura);
    toPhase(g, "AURA");
    g.use(aura, "pass");
    assertNotEquals("AURA", g.phase());
    toDay(g);
    assertFalse(g.alive().contains(aura));
    assertFalse(wholeNight(g).contains("AURA"));
  }

  @Test
  public void anAuraReplyReachesOnlyTheCaller() {
    var ids = ids(4, 9);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "AURA_SEER", 1));
    UUID aura = holder(g, ids, "AURA_SEER");
    toPhase(g, "AURA");
    g.drainNotices();
    g.select(aura, holder(g, ids, "WEREWOLF"));
    assertTrue(g.drainNotices().isEmpty());
  }

  // ------------------------------------------------------------------ P.I.

  @Test
  public void thePiLearnsWhetherAWolfIsAmongThePlayerAndHerNeighbours() {
    var ids = ids(5, 11);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "PI", 1));
    UUID pi = holder(g, ids, "PI");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID beside = g.neighbour(wolf, 1);
    if (beside.equals(pi)) beside = g.neighbour(wolf, -1);
    toPhase(g, "PI");
    assertTrue(g.abilities(pi).containsKey("pass"));
    String reply = g.select(pi, beside);
    assertTrue(reply, reply.contains("at least one werewolf"));
    assertFalse("The wolf is never named", reply.contains(wolf.toString()));
    assertNotEquals("PI", g.phase());
  }

  @Test
  public void thePiReadsNoWolfWhenNoneIsNear() {
    var ids = ids(6, 11);
    var g = game(ids, 6, Map.of("WEREWOLF", 1, "PI", 1));
    UUID pi = holder(g, ids, "PI");
    UUID target =
        ids.stream()
            .filter(p -> !WerewolfGame.wolf(WerewolfGame.Role.valueOf(g.role(p))))
            .filter(p -> !g.role(g.neighbour(p, -1)).equals("WEREWOLF"))
            .filter(p -> !g.role(g.neighbour(p, 1)).equals("WEREWOLF"))
            .findFirst()
            .orElseThrow();
    toPhase(g, "PI");
    String reply = g.select(pi, target);
    assertTrue(reply, reply.contains("no werewolf"));
  }

  @Test
  public void theWolfOnTheTargetItselfCounts() {
    var ids = ids(7, 9);
    var g = game(ids, 7, Map.of("WEREWOLF", 1, "PI", 1));
    toPhase(g, "PI");
    String reply = g.select(holder(g, ids, "PI"), holder(g, ids, "WEREWOLF"));
    assertTrue(reply, reply.contains("at least one werewolf"));
  }

  @Test
  public void thePiWorksOnlyOncePerGame() {
    var ids = ids(8, 9);
    var g = game(ids, 8, Map.of("WEREWOLF", 1, "PI", 1));
    UUID pi = holder(g, ids, "PI");
    toPhase(g, "PI");
    g.select(pi, holder(g, ids, "VILLAGER"));
    toDay(g);
    assertFalse(wholeNight(g).contains("PI"));
    assertFalse(g.alive().isEmpty());
  }

  @Test
  public void aPiWhoPassesKeepsThePower() {
    var ids = ids(9, 9);
    var g = game(ids, 9, Map.of("WEREWOLF", 1, "PI", 1));
    UUID pi = holder(g, ids, "PI");
    toPhase(g, "PI");
    g.use(pi, "pass");
    toDay(g);
    assertTrue(g.alive().contains(pi));
    toNextHunt(g);
    toPhase(g, "PI");
    assertTrue(g.actors().contains(pi));
    assertNotNull(g.select(pi, holder(g, ids, "VILLAGER")));
  }

  @Test
  public void aPiThatTimesOutKeepsThePowerToo() {
    var ids = ids(10, 9);
    var g = game(ids, 10, Map.of("WEREWOLF", 1, "PI", 1));
    UUID pi = holder(g, ids, "PI");
    toPhase(g, "PI");
    g.advance(); // the clock ends the call
    toDay(g);
    assertTrue(g.alive().contains(pi));
    toNextHunt(g);
    toPhase(g, "PI");
    assertTrue(g.actors().contains(pi));
  }

  @Test
  public void thePiRefusesADeadTargetAndOthersCannotUseTheCall() {
    var ids = ids(11, 9);
    var g = game(ids, 11, Map.of("WEREWOLF", 1, "PI", 1));
    UUID pi = holder(g, ids, "PI");
    UUID other = holder(g, ids, "VILLAGER");
    toPhase(g, "PI");
    assertThrows(IllegalArgumentException.class, () -> g.select(other, pi));
    assertThrows(IllegalArgumentException.class, () -> g.select(pi, new UUID(0, 0)));
    assertEquals("PI", g.phase());
  }

  @Test
  public void aDeadPlayerCannotBePickedByTheAuraSeerThePiOrTheSorcerer() {
    var ids = ids(40, 11);
    var g = game(ids, 40, Map.of("WEREWOLF", 1, "AURA_SEER", 1, "PI", 1, "SORCERER", 1));
    UUID victim = holder(g, ids, "VILLAGER");
    toPhase(g, "NIGHT");
    g.select(holder(g, ids, "WEREWOLF"), victim);
    toDay(g);
    assertFalse(g.alive().contains(victim));
    toNextHunt(g);
    for (String role : List.of("AURA_SEER", "PI", "SORCERER")) {
      toPhase(g, role.equals("AURA_SEER") ? "AURA" : role);
      UUID actor = holder(g, ids, role);
      assertThrows(role, IllegalArgumentException.class, () -> g.select(actor, victim));
      assertNotEquals("NIGHT", g.phase()); // the refusal did not spend the call
      assertTrue(role, g.actors().contains(actor));
      g.use(actor, "pass");
    }
  }

  @Test
  public void thePiAndTheSorcererCannotPickThemselves() {
    var ids = ids(41, 9);
    var g = game(ids, 41, Map.of("WEREWOLF", 1, "PI", 1, "SORCERER", 1));
    UUID pi = holder(g, ids, "PI");
    UUID sorcerer = holder(g, ids, "SORCERER");
    toPhase(g, "PI");
    assertThrows(IllegalArgumentException.class, () -> g.select(pi, pi));
    assertEquals("PI", g.phase());
    g.select(pi, sorcerer); // the investigation was not spent by the refusal
    toPhase(g, "SORCERER");
    assertThrows(IllegalArgumentException.class, () -> g.select(sorcerer, sorcerer));
    assertEquals("SORCERER", g.phase());
    assertTrue(g.select(sorcerer, pi).contains("not the Seer"));
  }

  // ------------------------------------------------------------------ sorcerer

  @Test
  public void theSorcererLearnsWhetherAPlayerIsTheSeer() {
    var ids = ids(12, 9);
    var g = game(ids, 12, Map.of("WEREWOLF", 1, "SEER", 1, "SORCERER", 1));
    UUID sorcerer = holder(g, ids, "SORCERER");
    UUID seer = holder(g, ids, "SEER");
    toPhase(g, "SORCERER");
    assertTrue(g.abilities(sorcerer).containsKey("pass"));
    g.drainNotices();
    String reply = g.select(sorcerer, seer);
    assertTrue(reply, reply.endsWith("is the Seer."));
    assertTrue(g.drainNotices().isEmpty());
    toNextHunt(g);
    toPhase(g, "SORCERER");
    reply = g.select(sorcerer, holder(g, ids, "VILLAGER"));
    assertTrue(reply, reply.endsWith("is not the Seer."));
  }

  @Test
  public void theSorcererIsCalledEveryNightAndRefusesStrangers() {
    var ids = ids(13, 9);
    var g = game(ids, 13, Map.of("WEREWOLF", 1, "SEER", 1, "SORCERER", 1));
    UUID sorcerer = holder(g, ids, "SORCERER");
    UUID villager = holder(g, ids, "VILLAGER");
    toPhase(g, "SORCERER");
    assertThrows(IllegalArgumentException.class, () -> g.select(villager, sorcerer));
    g.use(sorcerer, "pass");
    toDay(g);
    toNextHunt(g);
    toPhase(g, "SORCERER");
    assertTrue(g.actors().contains(sorcerer));
  }

  @Test
  public void aSorcererDoesNotWakeWithThePackAndTheSeerSeesAVillager() {
    var ids = ids(14, 9);
    var g = game(ids, 14, Map.of("WEREWOLF", 1, "SEER", 1, "SORCERER", 1));
    UUID sorcerer = holder(g, ids, "SORCERER");
    UUID seer = holder(g, ids, "SEER");
    toPhase(g, "SEER");
    assertTrue(g.select(seer, sorcerer).contains("VILLAGER"));
    toPhase(g, "NIGHT");
    assertFalse(g.actors().contains(sorcerer));
  }

  @Test
  public void theWolvesWinCheckIgnoresTheSorcererWhenTheLastWolfIsLynched() {
    var ids = ids(15, 7);
    var g = game(ids, 15, Map.of("WEREWOLF", 1, "SORCERER", 1));
    UUID wolf = holder(g, ids, "WEREWOLF");
    toPhase(g, "DAY_VOTE");
    for (UUID p : ids) if (!p.equals(wolf)) g.select(p, wolf);
    g.advance();
    assertEquals("VILLAGE", g.winner());
  }

  // ------------------------------------------------------------------ apprentice seer

  @Test
  public void theApprenticeBecomesTheSeerWhenTheSeerDies() {
    var ids = ids(16, 11);
    var g = game(ids, 16, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID seer = holder(g, ids, "SEER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    toPhase(g, "SEER");
    assertFalse(g.actors().contains(apprentice));
    g.drainNotices();
    toPhase(g, "NIGHT");
    g.select(wolf, seer);
    toDay(g);
    assertFalse(g.alive().contains(seer));
    assertEquals("SEER", g.role(apprentice));
    Map<UUID, String> notices = g.drainNotices();
    assertTrue(notices.toString(), mentions(notices, apprentice, "you are the Seer now"));
    for (var notice : notices.entrySet())
      if (!notice.getKey().equals(apprentice))
        assertFalse(notice.getValue(), notice.getValue().contains("Seer now"));
    toPhase(g, "SEER");
    assertTrue(g.actors().contains(apprentice));
    assertTrue(g.abilities(apprentice).containsKey("pass"));
    assertTrue(g.select(apprentice, wolf).contains("WEREWOLF"));
  }

  @Test
  public void theApprenticeIsCalledFromTheNextNightOnly() {
    var ids = ids(17, 11);
    var g = game(ids, 17, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    toPhase(g, "NIGHT");
    g.select(wolf, holder(g, ids, "SEER"));
    toDay(g);
    assertEquals("SEER", g.role(apprentice));
    assertTrue(wholeNight(g).contains("SEER"));
  }

  @Test
  public void theApprenticeIsNotCalledAndDoesNotChangeWhileTheSeerLives() {
    var ids = ids(18, 11);
    var g = game(ids, 18, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    toPhase(g, "SEER");
    assertFalse(g.actors().contains(apprentice));
    assertThrows(
        IllegalArgumentException.class, () -> g.select(apprentice, holder(g, ids, "VILLAGER")));
    toDay(g);
    assertEquals("APPRENTICE_SEER", g.role(apprentice));
  }

  @Test
  public void theSeerAndTheApprenticeDyingTheSameDawnLeaveNoSeer() {
    var ids = ids(19, 11);
    var g = game(ids, 19, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1, "WITCH", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID seer = holder(g, ids, "SEER");
    UUID witch = holder(g, ids, "WITCH");
    toPhase(g, "NIGHT");
    g.select(holder(g, ids, "WEREWOLF"), seer);
    assertEquals("WITCH", g.phase());
    g.aim(witch, apprentice);
    g.use(witch, "poison");
    toDay(g);
    assertFalse(g.alive().contains(seer));
    assertFalse(g.alive().contains(apprentice));
    assertFalse(wholeNight(g).contains("SEER"));
  }

  @Test
  public void theApprenticeDyingFirstIsNotPromoted() {
    var ids = ids(20, 11);
    var g = game(ids, 20, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1, "WITCH", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID seer = holder(g, ids, "SEER");
    UUID witch = holder(g, ids, "WITCH");
    UUID wolf = holder(g, ids, "WEREWOLF");
    toPhase(g, "NIGHT");
    g.select(wolf, holder(g, ids, "VILLAGER"));
    g.aim(witch, apprentice);
    g.use(witch, "poison");
    toDay(g);
    assertFalse(g.alive().contains(apprentice));
    g.drainNotices();
    toNextHunt(g);
    g.select(wolf, seer);
    toDay(g);
    assertFalse(g.alive().contains(seer));
    assertEquals("APPRENTICE_SEER", g.role(apprentice));
    assertFalse(g.drainNotices().containsKey(apprentice));
  }

  @Test
  public void aDeadApprenticeIsNeverToldHeIsTheSeer() {
    var ids = ids(42, 11);
    var g = game(ids, 42, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1, "WITCH", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID seer = holder(g, ids, "SEER");
    UUID witch = holder(g, ids, "WITCH");
    toPhase(g, "NIGHT");
    g.select(holder(g, ids, "WEREWOLF"), seer);
    g.aim(witch, apprentice);
    g.use(witch, "poison");
    g.drainNotices();
    toDay(g);
    assertFalse(g.alive().contains(seer));
    assertFalse(g.alive().contains(apprentice));
    assertEquals("APPRENTICE_SEER", g.role(apprentice));
    assertFalse(mentions(g.drainNotices(), apprentice, "Seer"));
  }

  @Test
  public void theApprenticeKeepsHisNoticeWhenTheBearGrowlsTheSameDawn() {
    var ids = ids(43, 11);
    var g = game(ids, 43, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1, "BEAR_TAMER", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID seer = holder(g, ids, "SEER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID tamer = holder(g, ids, "BEAR_TAMER");
    List<UUID> ring = new ArrayList<>(ids);
    ring.remove(tamer);
    ring.remove(wolf);
    ring.add(0, wolf);
    ring.add(0, tamer);
    g.ring(ring);
    toPhase(g, "NIGHT");
    g.drainNotices();
    g.select(wolf, seer);
    toDay(g);
    assertFalse(g.alive().contains(seer));
    String text = g.drainNotices().getOrDefault(apprentice, "");
    assertTrue(text, text.contains("The bear growls"));
    assertTrue(text, text.contains("you are the Seer now"));
    assertEquals("SEER", g.role(apprentice));
  }

  @Test
  public void theApprenticeKeepsHisNoticeWhenTheSeerIsCondemnedWithATroublePending() {
    var ids = ids(44, 11);
    var g = game(ids, 44, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID seer = holder(g, ids, "SEER");
    toPhase(g, "DAY_VOTE");
    g.extraLynches(1);
    g.drainNotices();
    for (UUID p : ids) if (!p.equals(seer)) g.select(p, seer);
    g.advance();
    assertFalse(g.alive().contains(seer));
    assertEquals("SEER", g.role(apprentice));
    String text = g.drainNotices().getOrDefault(apprentice, "");
    assertTrue(text, text.contains("Trouble!"));
    assertTrue(text, text.contains("you are the Seer now"));
  }

  @Test
  public void theSeerSeesTheApprenticeAsAVillagerAndTheAuraSeerAsSpecial() {
    var ids = ids(45, 11);
    var g = game(ids, 45, Map.of("WEREWOLF", 1, "SEER", 1, "AURA_SEER", 1, "APPRENTICE_SEER", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    toPhase(g, "SEER");
    String vision = g.select(holder(g, ids, "SEER"), apprentice);
    assertTrue(vision, vision.endsWith("is VILLAGER."));
    toPhase(g, "AURA");
    String aura = g.select(holder(g, ids, "AURA_SEER"), apprentice);
    assertTrue(aura, aura.contains("special"));
  }

  @Test
  public void theApprenticeTakesOverWhenTheSeerIsLynched() {
    var ids = ids(21, 11);
    var g = game(ids, 21, Map.of("WEREWOLF", 1, "SEER", 1, "APPRENTICE_SEER", 1));
    UUID apprentice = holder(g, ids, "APPRENTICE_SEER");
    UUID seer = holder(g, ids, "SEER");
    toPhase(g, "DAY_VOTE");
    for (UUID p : ids) if (!p.equals(seer)) g.select(p, seer);
    g.advance();
    assertFalse(g.alive().contains(seer));
    assertEquals("SEER", g.role(apprentice));
  }

  // ------------------------------------------------------------------ lycan

  @Test
  public void theSeerSeesTheLycanAsAWerewolf() {
    var ids = ids(22, 9);
    var g = game(ids, 22, Map.of("WEREWOLF", 1, "SEER", 1, "LYCAN", 1));
    UUID seer = holder(g, ids, "SEER");
    UUID lycan = holder(g, ids, "LYCAN");
    toPhase(g, "SEER");
    String vision = g.select(seer, lycan);
    assertTrue(vision, vision.endsWith("is WEREWOLF."));
    assertEquals("LYCAN", g.role(lycan));
    assertFalse(g.actors().contains(lycan));
  }

  @Test
  public void theAuraSeerReadsALycanAsSpecial() {
    var ids = ids(23, 9);
    var g = game(ids, 23, Map.of("WEREWOLF", 1, "AURA_SEER", 1, "LYCAN", 1));
    toPhase(g, "AURA");
    String reply = g.select(holder(g, ids, "AURA_SEER"), holder(g, ids, "LYCAN"));
    assertTrue(reply, reply.contains("special"));
  }

  // ------------------------------------------------------------------ bots

  private static final Map<String, Integer> CAST =
      Map.of(
          "WEREWOLF", 2,
          "SEER", 1,
          "APPRENTICE_SEER", 1,
          "AURA_SEER", 1,
          "PI", 1,
          "SORCERER", 1,
          "LYCAN", 1,
          "WITCH", 1,
          "GUARD", 1);

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
  public void aPracticeGameWithTheInsightRolesFinishesAndEveryRoleActs() {
    Set<String> seen = new HashSet<>();
    for (int seed = 0; seed < 60; seed++) {
      List<UUID> ids = ids(seed, 12);
      for (WerewolfBots.Action action : play(game(ids, seed, CAST), new HashSet<>(ids), seed))
        seen.add(action.role() + " " + action.what().split(" ")[0]);
    }
    for (String role : List.of("AURA_SEER", "PI", "SORCERER"))
      assertTrue(role + " in " + seen, seen.contains(role + " takes"));
  }

  @Test
  public void aPracticeGameWithAnIdleHumanAmongTheInsightRolesFinishes() {
    for (int seed = 100; seed < 130; seed++) {
      List<UUID> ids = ids(seed, 12);
      Set<UUID> bots = new HashSet<>(ids);
      bots.remove(ids.getFirst());
      play(game(ids, seed, CAST), bots, seed);
    }
  }

  @Test
  public void aPiBotUsesItsPowerOnSomeNightsOnly() {
    boolean waited = false, used = false;
    for (int seed = 0; seed < 40; seed++) {
      List<UUID> ids = ids(seed, 9);
      var g = game(ids, seed, Map.of("WEREWOLF", 1, "PI", 1));
      toPhase(g, "PI");
      g.botAct(holder(g, ids, "PI"), new Random(seed));
      assertNotEquals("PI", g.phase());
      toDay(g);
      if (wholeNight(g).contains("PI")) waited = true;
      else used = true;
    }
    assertTrue(waited);
    assertTrue(used);
  }

  @Test
  public void anAuraBotAndASorcererBotPickAnotherPlayerEveryNight() {
    for (int seed = 0; seed < 40; seed++) {
      List<UUID> ids = ids(seed, 9);
      var g = game(ids, seed, Map.of("WEREWOLF", 1, "AURA_SEER", 1, "SORCERER", 1));
      var random = new Random(seed);
      var brain = new InsightModule(); // remembers the picks; the game applies them
      Map<String, UUID> previous = new HashMap<>();
      for (int night = 0; night < 3; night++) {
        for (String role : List.of("AURA_SEER", "SORCERER")) {
          toPhase(g, role.equals("AURA_SEER") ? "AURA" : role);
          UUID bot = holder(g, ids, role);
          brain.bot(g, WerewolfGame.Phase.valueOf(g.phase()), bot, random);
          assertNotEquals(role, g.phase());
          UUID pick = brain.lastPick(bot);
          assertNotNull(pick);
          assertNotEquals(bot, pick);
          assertNotEquals("seed " + seed + " " + role, previous.put(role, pick), pick);
        }
        toDay(g);
        toNextHunt(g);
      }
    }
  }
}
