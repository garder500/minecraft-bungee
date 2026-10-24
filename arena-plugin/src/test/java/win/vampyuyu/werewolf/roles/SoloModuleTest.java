package win.vampyuyu.werewolf.roles;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.werewolf.WerewolfBots;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.WerewolfRules;

public class SoloModuleTest {
  private static final int N = 8;

  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(41, i + 1));
    return ids;
  }

  private static WerewolfGame game(List<UUID> ids, int seed, Map<String, Integer> cast) {
    return new WerewolfGame(
        ids, 5, 5, 5, new Random(seed), new WerewolfRules(cast, 5, 5, 5));
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  /** The living players that hold none of the named roles. */
  private static List<UUID> others(WerewolfGame g, List<UUID> ids, String... roles) {
    List<String> skip = List.of(roles);
    return ids.stream()
        .filter(p -> g.alive().contains(p) && !skip.contains(g.role(p)))
        .toList();
  }

  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 20 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** Every living player votes for the target (the target votes for somebody else). */
  private static void lynch(WerewolfGame g, List<UUID> ids, UUID target) {
    toPhase(g, "DAY_VOTE");
    UUID other = g.alive().stream().filter(p -> !p.equals(target)).findFirst().orElseThrow();
    for (UUID p : g.alive()) g.select(p, p.equals(target) ? other : target);
    g.advance();
  }

  /** The wolf kills the victim tonight; the clock then carries the game to the next day. */
  private static void hunt(WerewolfGame g, UUID wolf, UUID victim) {
    toPhase(g, "NIGHT");
    g.select(wolf, victim);
    assertFalse(g.alive().contains(victim));
  }

  private static Map<String, Integer> hoodlumCast() {
    return Map.of("WEREWOLF", 1, "HOODLUM", 1);
  }

  // ------------------------------------------------------------------ hoodlum

  @Test
  public void theHoodlumMarksTwoPlayersWithTwoSelects() {
    var ids = ids(N);
    var g = game(ids, 3, hoodlumCast());
    UUID hoodlum = holder(g, ids, "HOODLUM");
    var pool = others(g, ids, "HOODLUM");
    assertEquals("HOODLUM", g.phase());
    assertEquals(Set.of(hoodlum), g.actors());
    assertTrue(g.abilities(hoodlum).containsKey("pass"));
    g.drainNotices();
    String first = g.select(hoodlum, pool.get(0));
    assertTrue(first, first.toLowerCase().contains("first mark"));
    assertEquals("HOODLUM", g.phase());
    String second = g.select(hoodlum, pool.get(1));
    assertTrue(second.contains(pool.get(0).toString()));
    assertTrue(second.contains(pool.get(1).toString()));
    assertEquals("NIGHT", g.phase());
    // Nobody is told about the marks: the replies are all the hoodlum gets.
    assertTrue(g.drainNotices().isEmpty());
  }

  @Test
  public void theHoodlumIsOnlyCalledOnTheFirstNight() {
    var ids = ids(N);
    var g = game(ids, 3, hoodlumCast());
    UUID hoodlum = holder(g, ids, "HOODLUM");
    UUID wolf = holder(g, ids, "WEREWOLF");
    var pool = others(g, ids, "HOODLUM", "WEREWOLF");
    g.select(hoodlum, pool.get(0));
    g.select(hoodlum, pool.get(1));
    hunt(g, wolf, pool.get(2));
    toPhase(g, "NIGHT");
    assertFalse(g.actors().contains(hoodlum));
    assertThrows(IllegalArgumentException.class, () -> g.select(hoodlum, pool.get(3)));
  }

  @Test
  public void theHoodlumRefusesBadMarks() {
    var ids = ids(N);
    var g = game(ids, 3, hoodlumCast());
    UUID hoodlum = holder(g, ids, "HOODLUM");
    UUID wolf = holder(g, ids, "WEREWOLF");
    var pool = others(g, ids, "HOODLUM");
    assertThrows(IllegalArgumentException.class, () -> g.select(hoodlum, hoodlum));
    assertThrows(IllegalArgumentException.class, () -> g.select(hoodlum, new UUID(9, 9)));
    // Somebody else cannot mark.
    assertThrows(IllegalArgumentException.class, () -> g.select(wolf, pool.get(0)));
    assertEquals("HOODLUM", g.phase());
    g.select(hoodlum, pool.get(0));
    // The same player twice is refused and does not complete the call.
    assertThrows(IllegalArgumentException.class, () -> g.select(hoodlum, pool.get(0)));
    assertEquals("HOODLUM", g.phase());
    g.select(hoodlum, pool.get(1));
    assertEquals("NIGHT", g.phase());
    // Done: no third mark.
    assertThrows(IllegalArgumentException.class, () -> g.select(hoodlum, pool.get(2)));
  }

  @Test
  public void theHoodlumWinsWhenAliveAndBothMarksAreDead() {
    var ids = ids(N);
    var g = game(ids, 3, hoodlumCast());
    UUID hoodlum = holder(g, ids, "HOODLUM");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID victim = others(g, ids, "HOODLUM", "WEREWOLF").get(0);
    g.select(hoodlum, wolf);
    g.select(hoodlum, victim);
    hunt(g, wolf, victim);
    assertEquals("", g.winner());
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertTrue(g.winners().contains(hoodlum));
    assertFalse(g.winners().contains(wolf));
  }

  @Test
  public void theHoodlumLosesWhileOneMarkLives() {
    var ids = ids(N);
    var g = game(ids, 3, hoodlumCast());
    UUID hoodlum = holder(g, ids, "HOODLUM");
    UUID wolf = holder(g, ids, "WEREWOLF");
    var pool = others(g, ids, "HOODLUM", "WEREWOLF");
    g.select(hoodlum, wolf);
    g.select(hoodlum, pool.get(1));
    hunt(g, wolf, pool.get(0));
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertFalse(g.winners().contains(hoodlum));
    assertTrue(g.winners().contains(pool.get(1)));
  }

  @Test
  public void aDeadHoodlumDoesNotWin() {
    var ids = ids(N);
    var g = game(ids, 3, hoodlumCast());
    UUID hoodlum = holder(g, ids, "HOODLUM");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID mark = others(g, ids, "HOODLUM", "WEREWOLF").get(0);
    g.select(hoodlum, wolf);
    g.select(hoodlum, mark);
    hunt(g, wolf, hoodlum);
    lynch(g, ids, mark);
    toPhase(g, "NIGHT");
    hunt(g, wolf, others(g, ids, "WEREWOLF").get(0));
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertFalse(g.alive().contains(hoodlum));
    assertFalse(g.winners().contains(hoodlum));
  }

  @Test
  public void aHoodlumWhoPassesMarksNobodyAndCannotWin() {
    var ids = ids(N);
    var g = game(ids, 3, hoodlumCast());
    UUID hoodlum = holder(g, ids, "HOODLUM");
    UUID wolf = holder(g, ids, "WEREWOLF");
    var pool = others(g, ids, "HOODLUM", "WEREWOLF");
    g.select(hoodlum, pool.get(0));
    g.use(hoodlum, "pass");
    assertEquals("NIGHT", g.phase());
    hunt(g, wolf, pool.get(0));
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertFalse(g.winners().contains(hoodlum));
  }

  // ------------------------------------------------------------------ tanner

  @Test
  public void theTannerWinsWhenLynchedAndTheGameGoesOn() {
    var ids = ids(N);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "TANNER", 1));
    UUID tanner = holder(g, ids, "TANNER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    assertTrue(g.winners().isEmpty());
    lynch(g, ids, tanner);
    assertFalse(g.alive().contains(tanner));
    assertEquals("", g.winner());
    assertNotEquals("FINISHED", g.phase());
    // The village then wins; the tanner wins with them on his own goal.
    toPhase(g, "NIGHT");
    hunt(g, wolf, others(g, ids, "WEREWOLF").get(0));
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertTrue(g.winners().contains(tanner));
  }

  @Test
  public void theTannerWinsEvenWhenTheWolvesWinOrHeDiesAtNight() {
    var ids = ids(N);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "TANNER", 1));
    UUID tanner = holder(g, ids, "TANNER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    hunt(g, wolf, tanner);
    // Keep killing until the wolf wins.
    for (int i = 0; i < 12 && g.winner().isEmpty(); i++) {
      toPhase(g, "NIGHT");
      hunt(g, wolf, others(g, ids, "WEREWOLF").get(0));
    }
    assertEquals("WEREWOLVES", g.winner());
    assertTrue(g.winners().contains(tanner));
    assertTrue(g.winners().contains(wolf));
  }

  @Test
  public void aLivingTannerDoesNotWinWithTheVillage() {
    var ids = ids(N);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "TANNER", 1));
    UUID tanner = holder(g, ids, "TANNER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    hunt(g, wolf, others(g, ids, "WEREWOLF", "TANNER").get(0));
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertTrue(g.alive().contains(tanner));
    assertFalse(g.winners().contains(tanner));
  }

  // ------------------------------------------------------------------ lone wolf

  private static WerewolfGame lone(List<UUID> ids, String... mates) {
    Map<String, Integer> cast = new HashMap<>();
    cast.put("LONE_WOLF", 1);
    for (String m : mates) cast.merge(m, 1, Integer::sum);
    return game(ids, 7, cast);
  }

  /** Kills everybody but the keepers, outside any call. */
  private static void thin(WerewolfGame g, List<UUID> ids, UUID... keep) {
    List<UUID> keepers = List.of(keep);
    for (UUID p : ids)
      if (!keepers.contains(p) && g.alive().contains(p) && g.winner().isEmpty())
        g.kill(p, "OTHER", "test");
    g.advance(); // the win check runs when the clock moves
  }

  @Test
  public void theLoneWolfHuntsWithThePackAndWinsAsTheLastTwoWithAVillager() {
    var ids = ids(N);
    var g = lone(ids);
    UUID wolf = holder(g, ids, "LONE_WOLF");
    UUID villager = others(g, ids, "LONE_WOLF").get(0);
    toPhase(g, "NIGHT");
    assertTrue(g.actors().contains(wolf));
    thin(g, ids, wolf, villager);
    assertEquals("LONE_WOLF", g.winner());
    assertEquals(Set.of(wolf), g.winners());
  }

  @Test
  public void theLoneWolfWinsWhenHeIsTheLastPlayer() {
    var ids = ids(N);
    var g = lone(ids);
    UUID wolf = holder(g, ids, "LONE_WOLF");
    thin(g, ids, wolf);
    assertEquals("LONE_WOLF", g.winner());
    assertEquals(Set.of(wolf), g.winners());
  }

  @Test
  public void theLoneWolfDoesNotWinWithThreeOrMorePlayersLeft() {
    var ids = ids(N);
    var g = lone(ids);
    UUID wolf = holder(g, ids, "LONE_WOLF");
    var villagers = others(g, ids, "LONE_WOLF");
    thin(g, ids, wolf, villagers.get(0), villagers.get(1));
    assertEquals("", g.winner());
  }

  @Test
  public void theLoneWolfAndAPackWolfAreTheWolvesNotALoneWin() {
    var ids = ids(N);
    var g = lone(ids, "WEREWOLF");
    UUID wolf = holder(g, ids, "LONE_WOLF");
    UUID pack = holder(g, ids, "WEREWOLF");
    thin(g, ids, wolf, pack);
    assertEquals("WEREWOLVES", g.winner());
    assertFalse(g.winners().contains(wolf));
    assertTrue(g.winners().contains(pack));
  }

  @Test
  public void theLoneWolfCountsWithTheVillageAtParity() {
    var ids = ids(N);
    var g = lone(ids, "WEREWOLF");
    UUID wolf = holder(g, ids, "LONE_WOLF");
    UUID pack = holder(g, ids, "WEREWOLF");
    var villagers = others(g, ids, "LONE_WOLF", "WEREWOLF");
    thin(g, ids, wolf, pack, villagers.get(0), villagers.get(1));
    assertEquals("", g.winner());
    assertNotEquals("FINISHED", g.phase());
  }

  @Test
  public void theLoneWolfWinsAgainstTheLastVillagerWhenThePackIsGone() {
    var ids = ids(N);
    var g = lone(ids, "WEREWOLF");
    UUID wolf = holder(g, ids, "LONE_WOLF");
    UUID villager = others(g, ids, "LONE_WOLF", "WEREWOLF").get(0);
    thin(g, ids, wolf, villager);
    assertEquals("LONE_WOLF", g.winner());
    assertEquals(Set.of(wolf), g.winners());
  }

  @Test
  public void aSeerSeesTheLoneWolfAsAWerewolf() {
    var ids = ids(N);
    var g = lone(ids, "SEER");
    UUID wolf = holder(g, ids, "LONE_WOLF");
    UUID seer = holder(g, ids, "SEER");
    assertEquals("SEER", g.phase());
    String vision = g.select(seer, wolf);
    assertTrue(vision, vision.endsWith("is WEREWOLF."));
  }

  @Test
  public void aTannerWhoLeavesTheGameDoesNotWin() {
    var ids = ids(N);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "TANNER", 1));
    UUID tanner = holder(g, ids, "TANNER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    g.remove(tanner);
    hunt(g, wolf, others(g, ids, "WEREWOLF").get(0));
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertFalse(g.winners().contains(tanner));
  }

  @Test
  public void theLoneWolfDoesNotWinAlongsideAWolvesHelper() {
    var ids = ids(N);
    var g = lone(ids, "MINION");
    UUID wolf = holder(g, ids, "LONE_WOLF");
    UUID minion = holder(g, ids, "MINION");
    thin(g, ids, wolf, minion);
    assertEquals("", g.winner());
  }

  @Test
  public void theVillageWinsWhenTheLoneWolfDiesAndHeIsNotAWinner() {
    var ids = ids(N);
    var g = lone(ids);
    UUID wolf = holder(g, ids, "LONE_WOLF");
    lynch(g, ids, wolf);
    assertEquals("VILLAGE", g.winner());
    assertFalse(g.winners().contains(wolf));
  }

  @Test
  public void theLoneWolfIsAMemberOfHisOwnWinEvenDead() {
    var ids = ids(N);
    var g = lone(ids);
    UUID wolf = holder(g, ids, "LONE_WOLF");
    var module = new SoloModule();
    assertEquals(Set.of(wolf), module.members(g, "LONE_WOLF"));
    assertTrue(module.members(g, "VILLAGE").isEmpty());
    assertTrue(module.members(g, "WEREWOLVES").isEmpty());
    g.kill(wolf, "OTHER", "test");
    assertEquals(Set.of(wolf), module.members(g, "LONE_WOLF"));
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
  public void theHoodlumBotMarksTwoPlayersByItself() {
    for (int seed = 0; seed < 20; seed++) {
      var ids = ids(N);
      var g = game(ids, seed, hoodlumCast());
      assertEquals("HOODLUM", g.phase());
      String key = g.turnKey();
      var brain = new WerewolfBots(new Random(seed));
      var log = new ArrayList<WerewolfBots.Action>();
      for (long t = 0; t < 10_000 && key.equals(g.turnKey()); t += 1000)
        log.addAll(brain.think(g, new HashSet<>(ids), t, 10_000, false));
      // Only two completed selects can end the call before the clock does.
      assertNotEquals("seed " + seed, "HOODLUM", g.phase());
      assertTrue(log.stream().anyMatch(a -> a.role().equals("HOODLUM")));
    }
  }

  @Test
  public void practiceGamesWithTheSoloRolesFinish() {
    Map<String, Integer> cast =
        Map.of("WEREWOLF", 1, "HOODLUM", 1, "TANNER", 1, "LONE_WOLF", 1, "SEER", 1);
    int hoodlumWins = 0, tannerWins = 0;
    boolean hoodlumActed = false;
    for (int seed = 0; seed < 60; seed++) {
      var ids = ids(9 + seed % 6);
      var botsOnly = game(ids, seed, cast);
      UUID hoodlum = holder(botsOnly, ids, "HOODLUM"), tanner = holder(botsOnly, ids, "TANNER");
      for (var action : play(botsOnly, new HashSet<>(ids), seed))
        hoodlumActed |= action.role().equals("HOODLUM") && action.what().startsWith("takes its");
      if (botsOnly.winners().contains(hoodlum)) hoodlumWins++;
      if (botsOnly.winners().contains(tanner)) tannerWins++;
      var withHuman = game(ids, seed, cast);
      Set<UUID> bots = new HashSet<>(ids);
      bots.remove(ids.getFirst());
      play(withHuman, bots, seed);
    }
    assertTrue(hoodlumActed);
    assertTrue("a bot hoodlum wins sometimes", hoodlumWins > 0);
    assertTrue("a tanner wins sometimes", tannerWins > 0);
  }
}
