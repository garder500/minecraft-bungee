package win.vampyuyu.werewolf.roles;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;
import win.vampyuyu.werewolf.WerewolfBots;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.WerewolfRules;

public class SocialModuleTest {
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

  private static List<UUID> holders(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).toList();
  }

  private static UUID plain(WerewolfGame g, List<UUID> ids) {
    return ids.stream().filter(p -> g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
  }

  /** Runs the clock until the phase is reached (nobody acts, so nobody dies but by a rule). */
  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 60 && !g.phase().equals(phase) && g.winner().isEmpty(); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  /** Runs the clock out of the current phase, then to the next time the phase opens. */
  private static void toNextTime(WerewolfGame g, String phase) {
    g.advance();
    toPhase(g, phase);
  }

  // ------------------------------------------------------------------ spellcaster

  @Test
  public void spellcasterSilencesOnePlayerForTheDay() {
    var ids = ids(1, 9);
    var g = game(ids, 1, Map.of("WEREWOLF", 1, "SPELLCASTER", 1));
    UUID caster = holder(g, ids, "SPELLCASTER");
    UUID target = plain(g, ids);
    toPhase(g, "SPELL");
    assertTrue(g.actors().contains(caster));
    g.drainNotices();
    String reply = g.select(caster, target);
    assertTrue(reply, reply.contains(target.toString()));
    assertTrue(g.silenced(target));
    assertFalse(g.silenced(caster));
    toPhase(g, "DISCUSSION");
    assertTrue(g.silenced(target));
    assertTrue("a silenced player still votes", g.actors().contains(target));
    // He is told in private, and the table is told who is silent today.
    var news = g.drainNotices();
    assertTrue(news.get(target), news.get(target).contains("silences you"));
    assertTrue(news.get(caster), news.get(caster).contains("is silenced by a spell today"));
  }

  @Test
  public void spellcasterMayPickHimselfButNeverTheSamePlayerTwice() {
    var ids = ids(2, 9);
    var g = game(ids, 2, Map.of("WEREWOLF", 1, "SPELLCASTER", 1));
    UUID caster = holder(g, ids, "SPELLCASTER");
    UUID target = plain(g, ids);
    toPhase(g, "SPELL");
    g.select(caster, target);
    toNextTime(g, "SPELL");
    assertFalse("the silence ends at nightfall", g.silenced(target));
    assertThrows(IllegalArgumentException.class, () -> g.select(caster, target));
    assertEquals("SPELL", g.phase());
    g.select(caster, caster);
    assertTrue(g.silenced(caster));
    toNextTime(g, "SPELL");
    assertThrows(IllegalArgumentException.class, () -> g.select(caster, caster));
  }

  @Test
  public void spellcasterCannotPickTheDeadOrActOutOfTurn() {
    var ids = ids(3, 9);
    var g = game(ids, 3, Map.of("WEREWOLF", 1, "SPELLCASTER", 1));
    UUID caster = holder(g, ids, "SPELLCASTER");
    UUID villager = plain(g, ids);
    toPhase(g, "SPELL");
    assertThrows(IllegalArgumentException.class, () -> g.select(villager, caster));
    assertThrows(IllegalArgumentException.class, () -> g.select(caster, new UUID(0, 0)));
    toPhase(g, "DISCUSSION");
    assertThrows(IllegalArgumentException.class, () -> g.select(caster, villager));
  }

  // ------------------------------------------------------------------ old hag

  @Test
  public void hagSendsAPlayerAwayAndOnlyThatPlayerIsTold() {
    var ids = ids(4, 9);
    var g = game(ids, 4, Map.of("WEREWOLF", 1, "OLD_HAG", 1));
    UUID hag = holder(g, ids, "OLD_HAG");
    UUID target = plain(g, ids);
    toPhase(g, "HAG");
    g.drainNotices();
    g.select(hag, target);
    // The hag's call is the last of the night: the dawn follows at once, and the whole table is told
    // who was sent away (that is visible in the game), the player himself first.
    Map<UUID, String> notices = g.drainNotices();
    assertTrue(notices.get(target), notices.get(target).contains("sent you away"));
    for (String text : notices.values()) assertTrue(text, text.contains("is away from the village today"));
    assertTrue(g.exiled(target));
    assertTrue(g.silenced(target));
    toPhase(g, "DISCUSSION");
    assertTrue(g.exiled(target));
    assertFalse("an exiled player is out of the day", g.actors().contains(target));
    toPhase(g, "HAG");
    assertFalse("the exile ends at nightfall", g.exiled(target));
  }

  @Test
  public void hagCannotSendHerselfAway() {
    var ids = ids(5, 9);
    var g = game(ids, 5, Map.of("WEREWOLF", 1, "OLD_HAG", 1));
    UUID hag = holder(g, ids, "OLD_HAG");
    toPhase(g, "HAG");
    assertThrows(IllegalArgumentException.class, () -> g.select(hag, hag));
    assertEquals("HAG", g.phase());
    assertFalse(g.exiled(hag));
    g.select(hag, plain(g, ids));
    assertNotEquals("HAG", g.phase());
  }

  @Test
  public void anExiledPlayerCannotBeVotedFor() {
    var ids = ids(6, 9);
    var g = game(ids, 6, Map.of("WEREWOLF", 1, "OLD_HAG", 1));
    UUID hag = holder(g, ids, "OLD_HAG");
    UUID target = plain(g, ids);
    toPhase(g, "HAG");
    g.select(hag, target);
    toPhase(g, "DAY_VOTE");
    UUID voter = ids.stream().filter(p -> !p.equals(target)).findFirst().orElseThrow();
    assertThrows(IllegalArgumentException.class, () -> g.select(voter, target));
  }

  // ------------------------------------------------------------------ ghost

  @Test
  public void ghostBecomesAGhostAtTheFirstDawnAndKeepsHisCall() {
    var ids = ids(7, 9);
    var g = game(ids, 7, Map.of("WEREWOLF", 1, "GHOST", 1));
    UUID ghost = holder(g, ids, "GHOST");
    toPhase(g, "DISCUSSION");
    assertFalse(g.alive().contains(ghost));
    toPhase(g, "GHOST");
    assertTrue("a dead ghost is still called", g.actors().contains(ghost));
  }

  @Test
  public void ghostLetterReachesTheLivingAtDawn() {
    var ids = ids(8, 9);
    var g = game(ids, 8, Map.of("WEREWOLF", 1, "GHOST", 1));
    UUID ghost = holder(g, ids, "GHOST");
    toPhase(g, "DISCUSSION");
    toPhase(g, "GHOST");
    g.drainNotices();
    assertTrue(g.chat(ghost, " t "));
    assertNotEquals("the letter ended his call", "GHOST", g.phase());
    toPhase(g, "DISCUSSION");
    Map<UUID, String> notices = g.drainNotices();
    for (UUID p : g.alive())
      assertEquals("A ghostly letter appears in the mist: T", notices.get(p));
    assertFalse("the dead ghost is not told", notices.containsKey(ghost));
  }

  @Test
  public void ghostMessagesThatAreNotOneLetterAreNotSwallowed() {
    var ids = ids(9, 9);
    var g = game(ids, 9, Map.of("WEREWOLF", 1, "GHOST", 1));
    UUID ghost = holder(g, ids, "GHOST");
    UUID other = plain(g, ids);
    toPhase(g, "GHOST");
    for (String text : List.of("hello", "ab", "", "  ", "7", "!", "é", other.toString()))
      assertFalse(text, g.chat(ghost, text));
    assertEquals("GHOST", g.phase());
    assertFalse("only the ghost may haunt", g.chat(other, "a"));
    assertTrue(g.chat(ghost, "Z"));
  }

  @Test
  public void ghostChatIsOrdinaryOutsideHisCall() {
    var ids = ids(10, 9);
    var g = game(ids, 10, Map.of("WEREWOLF", 1, "GHOST", 1));
    UUID ghost = holder(g, ids, "GHOST");
    toPhase(g, "DISCUSSION");
    assertFalse(g.chat(ghost, "a"));
    toPhase(g, "DAY_VOTE");
    assertFalse(g.chat(ghost, "a"));
  }

  @Test
  public void noLetterMeansNoNotice() {
    var ids = ids(11, 9);
    var g = game(ids, 11, Map.of("WEREWOLF", 1, "GHOST", 1));
    toPhase(g, "GHOST");
    g.drainNotices();
    toPhase(g, "DISCUSSION");
    for (String text : g.drainNotices().values()) assertFalse(text, text.contains("ghostly"));
  }

  @Test
  public void ghostDeathCountsForTheWinCheck() {
    var ids = ids(12, 4);
    var g = game(ids, 12, Map.of("WEREWOLF", 1, "GHOST", 1, "OLD_MAN", 1));
    toPhase(g, "DISCUSSION");
    assertEquals("the ghost's fate does not end the game yet", "", g.winner());
    assertEquals(3, g.alive().size());
  }

  // ------------------------------------------------------------------ masons

  @Test
  public void masonsLearnEachOtherAndNobodyElseIsTold() {
    var ids = ids(13, 10);
    var g = game(ids, 13, Map.of("WEREWOLF", 2, "MASON", 3));
    List<UUID> masons = holders(g, ids, "MASON");
    Map<UUID, String> notices = g.drainNotices();
    for (UUID mason : masons) {
      String text = notices.get(mason);
      assertNotNull(text);
      for (UUID other : masons) assertEquals(!other.equals(mason), text.contains(other.toString()));
    }
    for (UUID p : ids) if (!masons.contains(p)) assertNull(g.role(p), notices.get(p));
  }

  @Test
  public void aLoneMasonIsToldSo() {
    var ids = ids(14, 9);
    var g = game(ids, 14, Map.of("WEREWOLF", 1, "MASON", 1));
    UUID mason = holder(g, ids, "MASON");
    assertEquals("You are the only mason in the village.", g.drainNotices().get(mason));
  }

  @Test
  public void masonsAreOnlyToldOnTheFirstNight() {
    var ids = ids(15, 9);
    var g = game(ids, 15, Map.of("WEREWOLF", 1, "MASON", 2));
    g.drainNotices();
    toPhase(g, "DISCUSSION");
    toPhase(g, "DAY_VOTE");
    toNextTime(g, "NIGHT");
    for (String text : g.drainNotices().values()) assertFalse(text, text.contains("mason"));
  }

  // ------------------------------------------------------------------ minion

  @Test
  public void minionLearnsTheWolvesAndTheyAreNotToldAboutHim() {
    var ids = ids(16, 10);
    var g = game(ids, 16, Map.of("WEREWOLF", 2, "MINION", 1));
    UUID minion = holder(g, ids, "MINION");
    List<UUID> wolves = holders(g, ids, "WEREWOLF");
    Map<UUID, String> notices = g.drainNotices();
    String text = notices.get(minion);
    assertNotNull(text);
    for (UUID wolf : wolves) assertTrue(text.contains(wolf.toString()));
    for (UUID p : ids) if (!p.equals(minion)) assertNull(g.role(p), notices.get(p));
    assertFalse("the minion does not wake with the pack", g.actors().contains(minion));
  }

  // ------------------------------------------------------------------ old man

  @Test
  public void oldManDiesOnTheNightAfterTheWolfCount() {
    var ids = ids(17, 9);
    var g = game(ids, 17, Map.of("WEREWOLF", 2, "OLD_MAN", 1));
    UUID oldMan = holder(g, ids, "OLD_MAN");
    toPhase(g, "DISCUSSION"); // dawn of night 1
    assertTrue(g.alive().contains(oldMan));
    toNextTime(g, "DISCUSSION"); // dawn of night 2
    assertTrue(g.alive().contains(oldMan));
    toNextTime(g, "DISCUSSION"); // dawn of night 3 = two wolves + 1
    assertFalse(g.alive().contains(oldMan));
  }

  @Test
  public void oldManDiesSoonerWhenAWolfIsLynched() {
    var ids = ids(20, 9);
    var g = game(ids, 20, Map.of("WEREWOLF", 2, "OLD_MAN", 1));
    UUID oldMan = holder(g, ids, "OLD_MAN");
    UUID wolf = holders(g, ids, "WEREWOLF").getFirst();
    toPhase(g, "DISCUSSION"); // dawn of night 1: 2 wolves, he lives
    assertTrue(g.alive().contains(oldMan));
    toPhase(g, "DAY_VOTE");
    for (UUID voter : new ArrayList<>(g.actors())) if (!voter.equals(wolf)) g.select(voter, wolf);
    if (g.phase().equals("DAY_VOTE")) g.advance();
    toPhase(g, "DISCUSSION"); // dawn of night 2: one wolf left, 2 >= 1 + 1
    assertEquals("dawn 2 has passed", 3, g.nightNumber());
    assertFalse("the lynched wolf is dead", g.alive().contains(wolf));
    assertFalse("old man dies at dawn 2", g.alive().contains(oldMan));
  }

  @Test
  public void ghostLetterAndBearGrowlBothArriveTheSameDawn() {
    var ids = ids(21, 9);
    var g = game(ids, 21, Map.of("WEREWOLF", 1, "GHOST", 1, "BEAR_TAMER", 1));
    UUID ghost = holder(g, ids, "GHOST");
    UUID tamer = holder(g, ids, "BEAR_TAMER");
    UUID wolf = holder(g, ids, "WEREWOLF");
    List<UUID> ring = new ArrayList<>(List.of(wolf, tamer));
    for (UUID p : ids) if (!ring.contains(p)) ring.add(p);
    g.ring(ring);
    toPhase(g, "DISCUSSION");
    toPhase(g, "GHOST");
    g.drainNotices();
    assertTrue(g.chat(ghost, "q"));
    toPhase(g, "DISCUSSION");
    var notices = g.drainNotices();
    for (UUID p : g.alive()) {
      String all = notices.get(p);
      assertNotNull(all);
      assertTrue(all, all.contains("A ghostly letter appears in the mist: Q"));
      assertTrue(all, all.contains("The bear growls!"));
    }
  }

  @Test
  public void oldManWithASingleWolfDiesOnTheSecondNight() {
    var ids = ids(18, 9);
    var g = game(ids, 18, Map.of("WEREWOLF", 1, "OLD_MAN", 1));
    UUID oldMan = holder(g, ids, "OLD_MAN");
    toPhase(g, "DISCUSSION");
    assertTrue(g.alive().contains(oldMan));
    toNextTime(g, "DISCUSSION");
    assertFalse(g.alive().contains(oldMan));
  }

  @Test
  public void oldAgeCanGiveTheWolvesTheGame() {
    var ids = ids(19, 4);
    var g = game(ids, 19, Map.of("WEREWOLF", 1, "GHOST", 1, "OLD_MAN", 1));
    toPhase(g, "DISCUSSION");
    assertEquals(3, g.alive().size());
    UUID oldMan = holder(g, ids, "OLD_MAN");
    for (int i = 0; i < 40 && g.winner().isEmpty(); i++) g.advance();
    assertFalse(g.alive().contains(oldMan));
    assertEquals("WEREWOLVES", g.winner());
  }

  // ------------------------------------------------------------------ bots

  private static void play(WerewolfGame game, Set<UUID> bots, int seed) {
    WerewolfBots brain = new WerewolfBots(new Random(seed));
    List<WerewolfBots.Action> log = new ArrayList<>();
    for (int turn = 0; turn < 200 && game.winner().isEmpty(); turn++) {
      String key = game.turnKey();
      for (long t = 0; t < 10_000 && key.equals(game.turnKey()); t += 1000)
        log.addAll(brain.think(game, bots, t, 10_000, false));
      if (key.equals(game.turnKey())) log.addAll(brain.think(game, bots, 10_000, 10_000, true));
      if (key.equals(game.turnKey())) game.advance();
    }
    assertFalse("The game should end, seed " + seed, game.winner().isEmpty());
    // Bots keep away from exiled players: any refusal fails.
    for (WerewolfBots.Action action : log)
      assertFalse(
          action.toString(),
          action.what().startsWith("was refused"));
  }

  private static final Map<String, Integer> CAST =
      Map.of(
          "WEREWOLF", 2, "SPELLCASTER", 1, "OLD_HAG", 1, "GHOST", 1, "MASON", 2, "MINION", 1,
          "OLD_MAN", 1, "SEER", 1);

  @Test
  public void aPracticeGameWithTheSocialRolesFinishes() {
    for (int seed = 0; seed < 60; seed++) {
      var ids = ids(seed, 12);
      play(game(ids, seed, CAST), new HashSet<>(ids), seed);
    }
  }

  @Test
  public void aPracticeGameWithAnIdleHumanFinishes() {
    for (int seed = 0; seed < 30; seed++) {
      var ids = ids(seed, 11);
      Set<UUID> bots = new HashSet<>(ids);
      bots.remove(ids.getFirst());
      play(game(ids, seed, CAST), bots, seed);
    }
  }
}
