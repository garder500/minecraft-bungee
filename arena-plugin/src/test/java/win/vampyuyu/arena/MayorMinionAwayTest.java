package win.vampyuyu.arena;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** The elected mayor, the minion the wolves pick, and a player sent away or silenced for the day. */
public class MayorMinionAwayTest {
  private static List<UUID> ids(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(new UUID(21, i + 1));
    return ids;
  }

  private static GameRules.Werewolf rules(Map<String, Integer> cast, String mayor, String minion) {
    return new GameRules.Werewolf(
        cast, 5, 5, 5, true, "FULL", "OPEN", false, false, "POINT", mayor, minion);
  }

  private static WerewolfGame game(int n, Map<String, Integer> cast, String mayor, String minion) {
    return new WerewolfGame(ids(n), 120, 30, 10, new Random(8), rules(cast, mayor, minion));
  }

  private static UUID holder(WerewolfGame g, List<UUID> ids, String role) {
    return ids.stream().filter(p -> g.role(p).equals(role)).findFirst().orElseThrow();
  }

  private static void toPhase(WerewolfGame g, String phase) {
    for (int i = 0; i < 60 && !g.phase().equals(phase); i++) g.advance();
    assertEquals(phase, g.phase());
  }

  // ------------------------------------------------------------------ elected mayor

  @Test
  public void anElectedMayorIsNoCardAndTheFirstDayOpensWithAnElection() {
    var cast = Map.of("WEREWOLF", 1, "SEER", 1, "MAYOR", 1);
    var ids = ids(8);
    var g = game(8, cast, "ELECTED", "DEALT");
    assertTrue("no mayor card in the deal", ids.stream().noneMatch(p -> g.role(p).equals("MAYOR")));
    toPhase(g, "ELECTION");
    assertNull(g.mayor());
    assertFalse(g.night());
    assertTrue(g.abilities(ids.get(0)).containsKey("skip"));
    UUID chosen = ids.get(3);
    for (UUID p : ids) if (!p.equals(chosen)) g.select(p, chosen);
    assertEquals("the election is public: everybody sees the count", 7, g.votes(ids.get(0)).get(chosen).intValue());
    g.drainNotices();
    g.advance();
    assertEquals("DISCUSSION", g.phase());
    assertEquals(chosen, g.mayor());
    assertTrue(
        g.drainNotices().values().stream().anyMatch(n -> n.contains("is elected mayor")));
  }

  @Test
  public void aTiedElectionVotesAgainThenDrawsAndTheMayorsVoteCountsTwice() {
    var cast = Map.of("WEREWOLF", 1, "SEER", 1);
    var ids = ids(8);
    var g = game(8, cast, "ELECTED", "DEALT");
    toPhase(g, "ELECTION");
    UUID a = ids.get(0), b = ids.get(1);
    for (int i = 2; i < 8; i++) g.select(ids.get(i), i % 2 == 0 ? a : b);
    g.advance();
    assertEquals("a tie asks again among the tied", "ELECTION", g.phase());
    assertEquals(2, g.candidates().size());
    assertThrows(IllegalArgumentException.class, () -> g.select(ids.get(2), ids.get(5)));
    for (int i = 2; i < 8; i++) g.select(ids.get(i), i % 2 == 0 ? a : b);
    g.advance(); // still tied: one of them is drawn
    assertEquals("DISCUSSION", g.phase());
    UUID mayor = g.mayor();
    assertTrue(mayor.equals(a) || mayor.equals(b));
    // The mayor's ballot counts twice at the day vote: 2 + 1 beats 2.
    g.advance();
    assertEquals("DAY_VOTE", g.phase());
    List<UUID> others = new ArrayList<>(g.alive());
    others.remove(mayor);
    UUID target = others.get(0), rival = others.get(1);
    g.select(mayor, target);
    g.select(others.get(2), target);
    g.select(others.get(3), rival);
    g.select(others.get(4), rival);
    g.advance();
    assertEquals(target, g.takeVerdict().lynched());
  }

  @Test
  public void whenTheMayorDiesTheVillageElectsAnotherAtDawn() {
    var cast = Map.of("WEREWOLF", 1, "SEER", 1);
    var ids = ids(8);
    var g = game(8, cast, "ELECTED", "DEALT");
    toPhase(g, "ELECTION");
    UUID wolf = holder(g, ids, "WEREWOLF");
    UUID mayor = ids.stream().filter(p -> !p.equals(wolf)).findFirst().orElseThrow();
    for (UUID p : ids) if (!p.equals(mayor)) g.select(p, mayor);
    g.advance();
    assertEquals(mayor, g.mayor());
    toPhase(g, "NIGHT");
    g.select(wolf, mayor);
    for (int i = 0; i < 30 && !g.phase().equals("ELECTION"); i++) g.advance();
    assertEquals("the new day opens with another election", "ELECTION", g.phase());
    assertNull(g.mayor());
  }

  @Test
  public void aDealtMayorStaysTheDefault() {
    var cast = Map.of("WEREWOLF", 1, "SEER", 1, "MAYOR", 1);
    var ids = ids(8);
    var g = game(8, cast, "DEALT", "DEALT");
    assertTrue(ids.stream().anyMatch(p -> g.role(p).equals("MAYOR")));
    toPhase(g, "DISCUSSION");
    assertNull(g.mayor());
  }

  // ------------------------------------------------------------------ minion

  @Test
  public void theWolvesPickAMinionOnTheFirstNightWhoSeemsAVillagerToTheSeer() {
    var cast = Map.of("WEREWOLF", 2, "SEER", 1);
    var ids = ids(9);
    var g = game(9, cast, "DEALT", "PICKED");
    UUID seer = holder(g, ids, "SEER");
    List<UUID> wolves = ids.stream().filter(p -> g.role(p).equals("WEREWOLF")).toList();
    UUID prey = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    UUID minion = ids.stream().filter(p -> g.role(p).equals("VILLAGER") && !p.equals(prey)).findFirst().orElseThrow();
    toPhase(g, "SEER");
    g.select(seer, minion); // the seer looks before the pick: a villager
    toPhase(g, "NIGHT");
    for (UUID w : wolves) g.select(w, prey);
    assertEquals("MINION", g.phase());
    assertEquals(Set.copyOf(wolves), g.actors());
    assertThrows(IllegalArgumentException.class, () -> g.select(wolves.get(0), wolves.get(1)));
    g.drainNotices();
    for (UUID w : wolves) g.select(w, minion);
    assertTrue(g.isMinion(minion));
    var notice = g.drainNotices().get(minion);
    assertTrue(notice, notice.contains("chose you as their minion"));
    assertFalse("the minion does not wake with the pack", g.packMember(minion));
    // The next night the seer sees the minion as a villager, whatever he holds.
    for (int i = 0; i < 40 && !(g.phase().equals("SEER") && g.nightNumber() == 2); i++) g.advance();
    if (g.phase().equals("SEER") && g.actors().contains(seer)) {
      var reply = g.select(seer, minion);
      assertTrue(reply, reply.endsWith("is VILLAGER."));
    }
  }

  @Test
  public void aPickedMinionWinsWithTheWolvesAndNotWithTheVillage() {
    var cast = Map.of("WEREWOLF", 1);
    var ids = ids(5);
    var g = game(5, cast, "DEALT", "PICKED");
    UUID wolf = holder(g, ids, "WEREWOLF");
    List<UUID> villagers = ids.stream().filter(p -> !p.equals(wolf)).toList();
    UUID minion = villagers.get(0);
    toPhase(g, "NIGHT");
    g.select(wolf, villagers.get(1));
    assertEquals("MINION", g.phase());
    g.select(wolf, minion);
    assertTrue(g.isMinion(minion));
    // Everybody else dies: the wolf and his minion are the last two, as many as the rest of the village
    for (UUID p : Set.copyOf(g.alive())) if (!p.equals(wolf) && !p.equals(minion)) g.remove(p);
    assertEquals("WEREWOLVES", g.winner());
    assertTrue(g.winners().containsAll(Set.of(wolf, minion)));
    assertFalse(g.winners().contains(villagers.get(2)));
  }

  @Test
  public void aMinionCardAndAPickedMinionAreNotDealtTogether() {
    var cast = Map.of("WEREWOLF", 2, "MINION", 1);
    assertThrows(IllegalArgumentException.class, () -> rules(cast, "DEALT", "PICKED"));
    // The automatic cast of a big table leaves the minion card out when the wolves pick.
    var big = new WerewolfGame(ids(30), 120, 30, 10, new Random(2), rules(Map.of(), "DEALT", "PICKED"));
    assertFalse(big.composition().containsKey("MINION"));
  }

  @Test
  public void aPickedMinionIsOnTheWolvesSideForTheLoneWolf() {
    var cast = Map.of("WEREWOLF", 1, "LONE_WOLF", 1);
    var ids = ids(6);
    var g = game(6, cast, "DEALT", "PICKED");
    UUID wolf = holder(g, ids, "WEREWOLF"), lone = holder(g, ids, "LONE_WOLF");
    UUID villager = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    UUID prey = ids.stream().filter(p -> g.role(p).equals("VILLAGER") && !p.equals(villager)).findFirst().orElseThrow();
    toPhase(g, "NIGHT");
    g.select(wolf, prey);
    g.select(lone, prey);
    assertEquals("MINION", g.phase());
    g.select(wolf, villager);
    g.select(lone, villager);
    assertTrue(g.isMinion(villager));
    // Last two: the lone wolf and his own pack's minion: the lone wolf does not win alone.
    for (UUID p : Set.copyOf(g.alive())) if (!p.equals(lone) && !p.equals(villager)) g.remove(p);
    assertNotEquals("LONE_WOLF", g.winner());
  }

  // ------------------------------------------------------------------ away and silenced

  @Test
  public void aPlayerSentAwayForTheDaySleepsThroughItAndIsAnnounced() {
    var cast = Map.of("WEREWOLF", 1, "OLD_HAG", 1);
    var ids = ids(8);
    var g = game(8, cast, "DEALT", "DEALT");
    UUID hag = holder(g, ids, "OLD_HAG");
    UUID target = ids.stream().filter(p -> !p.equals(hag) && g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    toPhase(g, "HAG");
    g.select(hag, target); // the last call of the night: the dawn follows at once
    assertEquals("DISCUSSION", g.phase());
    assertTrue(g.exiled(target));
    assertTrue("the exiled sleeps through the day: black screen, nobody in view", g.sleeping(target));
    assertTrue(g.silenced(target));
    assertFalse(g.actors().contains(target));
    var news = g.drainNotices();
    assertTrue(
        String.valueOf(news),
        news.values().stream().anyMatch(n -> n.contains("is away from the village today")));
    var view = WerewolfPresentation.view(g, target);
    assertEquals("Away", view.phase());
    // He is back at nightfall.
    for (int i = 0; i < 20 && !g.night(); i++) g.advance();
    assertFalse(g.exiled(target));
    assertFalse(g.sleeping(target) && !g.night());
  }

  @Test
  public void aSilencedPlayerIsToldAndTheTableSeesIt() {
    var cast = Map.of("WEREWOLF", 1, "SPELLCASTER", 1);
    var ids = ids(8);
    var g = game(8, cast, "DEALT", "DEALT");
    UUID caster = holder(g, ids, "SPELLCASTER");
    UUID target = ids.stream().filter(p -> !p.equals(caster) && g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    toPhase(g, "SPELL");
    g.select(caster, target);
    var told = g.drainNotices().get(target);
    assertTrue(told, told != null && told.contains("silences you"));
    toPhase(g, "DISCUSSION");
    assertTrue(g.silenced(target));
    assertFalse("a silenced player still votes", g.exiled(target));
    assertTrue(g.actors().contains(target));
    assertFalse(g.sleeping(target));
  }

  // ------------------------------------------------------------------ the dawn's hunter and the election

  private static WerewolfGame electedGame(Map<String, Integer> cast, int n) {
    return game(n, cast, "ELECTED", "DEALT");
  }

  @Test
  public void aHunterKilledAtDawnShootsInDaylightBeforeTheElection() {
    var cast = Map.of("WEREWOLF", 1, "HUNTER", 1);
    var ids = ids(8);
    var g = electedGame(cast, 8);
    UUID hunter = holder(g, ids, "HUNTER"), wolf = holder(g, ids, "WEREWOLF");
    toPhase(g, "NIGHT");
    g.select(wolf, hunter);
    for (int i = 0; i < 30 && !g.phase().equals("HUNTER_SHOT"); i++) g.advance();
    assertEquals("HUNTER_SHOT", g.phase());
    assertFalse("the shot is in daylight, whatever comes next", g.night());
    for (UUID p : g.alive()) assertFalse(p.toString(), g.sleeping(p));
    UUID target = ids.stream().filter(p -> g.alive().contains(p) && !p.equals(wolf)).findFirst().orElseThrow();
    g.aim(hunter, target);
    g.use(hunter, "shoot");
    assertEquals("the election follows the shot", "ELECTION", g.phase());
  }

  @Test
  public void aMayorShotByTheDawnsHunterIsReplacedTheSameMorning() {
    var cast = Map.of("WEREWOLF", 1, "HUNTER", 1);
    var ids = ids(8);
    var g = electedGame(cast, 8);
    UUID hunter = holder(g, ids, "HUNTER"), wolf = holder(g, ids, "WEREWOLF");
    toPhase(g, "ELECTION");
    UUID mayor = ids.stream().filter(p -> !p.equals(wolf) && !p.equals(hunter)).findFirst().orElseThrow();
    for (UUID p : ids) if (!p.equals(mayor)) g.select(p, mayor);
    g.advance();
    assertEquals(mayor, g.mayor());
    toPhase(g, "NIGHT");
    g.select(wolf, hunter);
    for (int i = 0; i < 30 && !g.phase().equals("HUNTER_SHOT"); i++) g.advance();
    g.aim(hunter, mayor);
    g.use(hunter, "shoot");
    assertFalse(g.alive().contains(mayor));
    assertEquals("a new mayor is elected the same morning", "ELECTION", g.phase());
  }

  @Test
  public void aDaylightShotKeepsAnExiledPlayerAsleep() {
    var cast = Map.of("WEREWOLF", 1, "HUNTER", 1, "OLD_HAG", 1);
    var ids = ids(8);
    var g = game(8, cast, "DEALT", "DEALT");
    UUID hunter = holder(g, ids, "HUNTER"), wolf = holder(g, ids, "WEREWOLF"), hag = holder(g, ids, "OLD_HAG");
    UUID away = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    toPhase(g, "NIGHT");
    g.select(wolf, hunter);
    toPhase(g, "HAG");
    g.select(hag, away);
    assertEquals("HUNTER_SHOT", g.phase());
    assertTrue(g.sleeping(away));
    UUID other = ids.stream().filter(p -> g.alive().contains(p) && !p.equals(away) && !p.equals(wolf)).findFirst().orElseThrow();
    assertFalse(g.sleeping(other));
    // The hunter cannot aim at the player who is away, and a refused shot keeps the aim.
    assertThrows(IllegalArgumentException.class, () -> g.aim(hunter, away));
    g.aim(hunter, other);
    assertEquals(List.of(other), g.aims(hunter));
  }

  @Test
  public void aSilencedPlayerWhoDiedIsNotAnnounced() {
    var cast = Map.of("WEREWOLF", 1, "SPELLCASTER", 1);
    var ids = ids(8);
    var g = game(8, cast, "DEALT", "DEALT");
    UUID caster = holder(g, ids, "SPELLCASTER"), wolf = holder(g, ids, "WEREWOLF");
    UUID target = ids.stream().filter(p -> g.role(p).equals("VILLAGER")).findFirst().orElseThrow();
    toPhase(g, "NIGHT");
    g.select(wolf, target);
    toPhase(g, "SPELL");
    g.select(caster, target);
    assertFalse(g.alive().contains(target));
    assertTrue(
        g.drainNotices().values().stream().noneMatch(n -> n.contains("is silenced by a spell today")));
  }
}
