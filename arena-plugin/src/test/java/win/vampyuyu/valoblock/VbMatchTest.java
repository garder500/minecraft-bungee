package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class VbMatchTest {
  private final VbRules rules = VbData.rules();
  private final List<UUID> humans = new ArrayList<>();

  private VbMatch match(int players) {
    humans.clear();
    for (int i = 0; i < players; i++) humans.add(UUID.randomUUID());
    return new VbMatch(rules, humans, new Random(7));
  }

  /** Match with 10 players already in the first buy phase. */
  private VbMatch buyPhase() {
    VbMatch m = match(10);
    m.advance();
    assertEquals("BUY", m.phase());
    return m;
  }

  private VbMatch combat() {
    VbMatch m = buyPhase();
    m.advance();
    assertEquals("COMBAT", m.phase());
    return m;
  }

  private UUID first(VbMatch m, VbSide side) {
    return m.alive().stream().filter(id -> m.side(id) == side).sorted().findFirst().orElseThrow();
  }

  private List<UUID> of(VbMatch m, VbSide side) {
    return m.alive().stream().filter(id -> m.side(id) == side).toList();
  }

  private void killAll(VbMatch m, VbSide side, UUID killer) {
    for (UUID id : of(m, side)) m.damage(id, killer, 1000);
  }

  /** Plays one round to the end with the given squad winning by elimination, back to the next BUY (or END). */
  private void playRound(VbMatch m, int squad) {
    if (m.vbPhase() == VbPhase.BUY) m.advance();
    VbSide loserSide = m.score().sideOf(1 - squad, m.round());
    VbSide winnerSide = loserSide.other();
    killAll(m, loserSide, first(m, winnerSide));
    assertEquals("ROUND_END", m.phase());
    m.advance();
  }

  @Test
  public void startsInAgentSelectAndTimersMatchTheDesign() {
    VbMatch m = match(4);
    assertEquals("AGENT_SELECT", m.phase());
    assertEquals(60, m.seconds());
    m.advance();
    assertEquals(25, m.seconds());
    m.advance();
    assertEquals(100, m.seconds());
    assertEquals("", m.winner());
  }

  @Test
  public void humansAreSplitBalancedAndBotsFillBothTeamsToFive() {
    VbMatch m = match(3);
    assertEquals(2, m.squad(0).size() + m.squad(1).size() - 1);
    m.advance();
    assertEquals(5, m.squad(0).size());
    assertEquals(5, m.squad(1).size());
    assertEquals(7, m.bots().size());
    assertEquals(10, m.alive().size());
  }

  @Test
  public void oneHumanPlaysAgainstNineBots() {
    VbMatch m = match(1);
    m.advance();
    assertEquals(9, m.bots().size());
  }

  @Test
  public void tooManyHumansAreRefused() {
    assertThrows(IllegalArgumentException.class, () -> match(11));
  }

  @Test
  public void agentsAreUniquePerTeamAndOnlyDuringSelect() {
    VbMatch m = match(4);
    UUID a = m.squad(0).get(0), b = m.squad(0).get(1), c = m.squad(1).get(0);
    m.lockAgent(a, "vesper");
    assertThrows(IllegalArgumentException.class, () -> m.lockAgent(b, "vesper"));
    m.lockAgent(c, "vesper"); // other team may
    m.advance();
    assertThrows(IllegalStateException.class, () -> m.lockAgent(b, "nacre"));
  }

  @Test
  public void lockInIsFinalAndRefusesUnknownAgents() {
    VbMatch m = match(4);
    UUID a = m.squad(0).get(0);
    assertThrows(IllegalArgumentException.class, () -> m.lockAgent(a, "nobody"));
    m.lockAgent(a, "cinder");
    assertEquals("cinder", m.agent(a));
    assertThrows(IllegalStateException.class, () -> m.lockAgent(a, "gale"));
    assertTrue(m.takenByTeammate(m.squad(0).get(1), "cinder"));
    assertFalse(m.freeAgents(0).contains("cinder"));
    assertTrue(m.freeAgents(1).contains("cinder"));
  }

  @Test
  public void whenSelectTimeRunsOutEveryoneGetsADistinctAgentOfTheirTeam() {
    VbMatch m = match(3);
    List<String> locked = new ArrayList<>();
    m.setEvents(new VbEvents() {
      @Override
      public void agentLocked(VbMatch match, UUID player, String agent) {
        locked.add(player + ":" + agent);
      }
    });
    UUID early = m.squad(0).get(0);
    m.lockAgent(early, "thorn");
    m.advance(); // select over: the rest get random free agents, bots too
    assertEquals(10, m.participants().size());
    for (int squad = 0; squad < 2; squad++) {
      Set<String> seen = new HashSet<>();
      for (UUID id : m.squad(squad)) {
        assertFalse("a player has an agent", m.agent(id).isEmpty());
        assertTrue("one agent per team", seen.add(m.agent(id)));
      }
    }
    assertEquals("thorn", m.agent(early));
    assertEquals(10, locked.size());
  }

  @Test
  public void healAndDrainRespectTheLimits() {
    VbMatch m = combat();
    UUID id = m.squad(0).get(0);
    assertEquals(0, m.heal(id, 30)); // already full
    m.damage(id, m.squad(1).get(0), 40);
    assertEquals(60, m.health(id));
    assertEquals(25, m.heal(id, 25));
    assertEquals(15, m.heal(id, 50));
    assertEquals(99, m.drain(id, 500)); // never below 1
    assertEquals(1, m.health(id));
    assertFalse(m.damage(id, null, 0));
  }

  @Test
  public void everyoneStartsWithPistolCreditsAndFullHealth() {
    VbMatch m = buyPhase();
    for (UUID id : m.participants()) {
      assertEquals(800, m.credits(id));
      assertEquals(100, m.health(id));
      assertEquals(0, m.shield(id));
    }
    assertNotNull(m.seedCarrier());
    assertEquals(VbSide.SEEDBEARERS, m.side(m.seedCarrier()));
  }

  @Test
  public void buyingOnlyInBuyPhaseAndOnlyWithEnoughShards() {
    VbMatch m = buyPhase();
    UUID p = first(m, VbSide.SEALERS);
    assertTrue(m.buy(p, 300));
    assertEquals(500, m.credits(p));
    assertFalse(m.buy(p, 501));
    assertEquals(500, m.credits(p));
    assertTrue(m.buyShield(p, 25, 200));
    assertEquals(25, m.shield(p));
    assertFalse("a shield no better than the current one", m.buyShield(p, 25, 0));
    assertFalse("only the configured values", m.buyShield(p, 30, 0));
    m.advance();
    assertFalse(m.buy(p, 1));
  }

  @Test
  public void shieldAbsorbsFirstThenHealthAndOneLife() {
    VbMatch m = buyPhase();
    UUID victim = first(m, VbSide.SEALERS), killer = first(m, VbSide.SEEDBEARERS);
    assertTrue(m.buyShield(victim, 50, 0));
    m.advance();
    assertFalse(m.damage(victim, killer, 40));
    assertEquals(10, m.shield(victim));
    assertEquals(100, m.health(victim));
    assertFalse(m.damage(victim, killer, 40));
    assertEquals(0, m.shield(victim));
    assertEquals(70, m.health(victim));
    assertTrue(m.damage(victim, killer, 70));
    assertFalse(m.alive().contains(victim));
    assertFalse("dead stays dead", m.damage(victim, killer, 10));
    assertEquals(1, m.kills(killer));
  }

  @Test
  public void noDamageInBuyPhaseAndNoFriendlyFire() {
    VbMatch m = buyPhase();
    List<UUID> bearers = of(m, VbSide.SEEDBEARERS);
    assertFalse(m.damage(of(m, VbSide.SEALERS).get(0), bearers.get(0), 50));
    assertEquals(100, m.health(of(m, VbSide.SEALERS).get(0)));
    m.advance();
    m.damage(bearers.get(1), bearers.get(0), 50);
    assertEquals(100, m.health(bearers.get(1)));
  }

  @Test
  public void killPaysTwoHundredAndCapsAtNineThousand() {
    VbMatch m = combat();
    UUID killer = first(m, VbSide.SEEDBEARERS);
    m.damage(of(m, VbSide.SEALERS).get(0), killer, 1000);
    assertEquals(1000, m.credits(killer));
  }

  @Test
  public void killingTheLastSealerWinsTheRoundForSeedbearers() {
    VbMatch m = combat();
    int bearers = m.score().squadOf(VbSide.SEEDBEARERS, 1);
    killAll(m, VbSide.SEALERS, first(m, VbSide.SEEDBEARERS));
    assertEquals("ROUND_END", m.phase());
    assertEquals(1, m.score().wins(bearers));
    assertEquals(VbMatch.Reason.ELIMINATION, m.results().get(0).reason());
    assertEquals(6, m.seconds());
  }

  @Test
  public void timeRunningOutIsASealerWin() {
    VbMatch m = combat();
    m.advance();
    assertEquals(VbMatch.Reason.TIME, m.results().get(0).reason());
    assertEquals(VbSide.SEALERS, m.results().get(0).winnerSide());
  }

  @Test
  public void plantNeedsTheCarrierInsideASiteForTheFullHold() {
    VbMatch m = combat();
    VbSite site = VbData.sites().get(0);
    UUID carrier = m.seedCarrier();
    UUID other = of(m, VbSide.SEEDBEARERS).stream().filter(id -> !id.equals(carrier)).findFirst().orElseThrow();
    assertFalse(m.holdPlant(other, site, 10));
    assertFalse(m.holdPlant(carrier, null, 10));
    assertFalse(m.holdPlant(carrier, site, 3.9));
    assertFalse("releasing resets the plant", m.holdPlant(null, null, 0.05));
    assertEquals(0, m.plantProgress(), 1e-9);
    assertFalse(m.holdPlant(carrier, site, 3.9));
    assertTrue(m.holdPlant(carrier, site, 0.2));
    assertEquals("PLANTED", m.phase());
    assertEquals(40, m.seconds());
    assertSame(site, m.plantedSite());
    assertNull(m.seedCarrier());
  }

  @Test
  public void plantPaysTheWholeTeamThreeHundred() {
    VbMatch m = combat();
    UUID carrier = m.seedCarrier();
    m.holdPlant(carrier, VbData.sites().get(0), 10);
    for (UUID id : m.participants())
      assertEquals(m.side(id) == VbSide.SEEDBEARERS ? 1100 : 800, m.credits(id));
  }

  @Test
  public void aDeadCarrierDropsTheSeedAndAnotherBearerCanTakeIt() {
    VbMatch m = combat();
    UUID carrier = m.seedCarrier();
    m.damage(carrier, first(m, VbSide.SEALERS), 1000);
    assertNull(m.seedCarrier());
    UUID other = first(m, VbSide.SEEDBEARERS);
    assertTrue(m.pickUpSeed(other));
    assertEquals(other, m.seedCarrier());
    assertFalse(m.holdPlant(carrier, VbData.sites().get(0), 10));
  }

  @Test
  public void fuseOpeningWinsForSeedbearers() {
    VbMatch m = combat();
    m.holdPlant(m.seedCarrier(), VbData.sites().get(0), 10);
    m.advance();
    assertEquals("ROUND_END", m.phase());
    assertEquals(VbMatch.Reason.FUSE, m.results().get(0).reason());
    assertEquals(VbSide.SEEDBEARERS, m.results().get(0).winnerSide());
  }

  @Test
  public void sealingNeedsSevenSecondsWithCheckpointAtThreeAndAHalf() {
    VbMatch m = combat();
    m.holdPlant(m.seedCarrier(), VbData.sites().get(0), 10);
    UUID sealer = first(m, VbSide.SEALERS);
    UUID bearer = first(m, VbSide.SEEDBEARERS);
    assertFalse("a Seedbearer cannot seal", m.holdSeal(bearer, 100));
    assertFalse(m.holdSeal(sealer, 3.0));
    m.holdSeal(null, 0.05);
    assertEquals(0, m.sealProgress(), 1e-9);
    assertFalse(m.holdSeal(sealer, 3.6));
    m.holdSeal(null, 0.05);
    assertEquals(3.5, m.sealProgress(), 1e-9);
    assertFalse(m.holdSeal(sealer, 3.4));
    assertTrue(m.holdSeal(sealer, 0.2));
    assertEquals("ROUND_END", m.phase());
    assertEquals(VbMatch.Reason.SEALED, m.results().get(0).reason());
    assertEquals(VbSide.SEALERS, m.results().get(0).winnerSide());
  }

  @Test
  public void afterThePlantAllSeedbearersDeadDoesNotEndTheRoundButAllSealersDeadDoes() {
    VbMatch m = combat();
    m.holdPlant(m.seedCarrier(), VbData.sites().get(0), 10);
    UUID sealer = first(m, VbSide.SEALERS);
    killAll(m, VbSide.SEEDBEARERS, sealer);
    assertEquals("PLANTED", m.phase());
    assertFalse(m.holdSeal(sealer, 1));
    killAll(m, VbSide.SEALERS, null);
    // Sealers all died by "the world" after the bearers: the sealers' team has nobody left.
    assertEquals("ROUND_END", m.phase());
    assertEquals(VbSide.SEEDBEARERS, m.results().get(0).winnerSide());
  }

  @Test
  public void roundWinPaysThreeThousandAndLossBonusEscalatesAndResetsOnAWin() {
    VbMatch m = buyPhase();
    UUID a = m.squad(0).get(0), b = m.squad(1).get(0);
    // Squad 0 loses rounds 1, 2, 3, 4 then wins round 5.
    m.advance();
    forceAndNext(m, 1);
    assertEquals("round 1 winner paid 800 + 3000 (cap)", 3800, m.credits(b));
    assertEquals("first loss", 800 + 1900, m.credits(a));
    forceAndNext(m, 1);
    assertEquals(2700 + 2400, m.credits(a));
    forceAndNext(m, 1);
    assertEquals(5100 + 2900, m.credits(a));
    forceAndNext(m, 1);
    assertEquals("third and later losses stay at 2900", 8000 + 2900 > 9000 ? 9000 : 10900, m.credits(a));
    assertEquals(4, m.lossStreak(0));
    forceAndNext(m, 0);
    assertEquals(0, m.lossStreak(0));
    assertEquals(1, m.lossStreak(1));
  }

  private void forceAndNext(VbMatch m, int squad) {
    if (m.vbPhase() == VbPhase.BUY) m.advance();
    m.forceRound(squad);
    if (m.vbPhase() == VbPhase.ROUND_END) m.advance();
  }

  @Test
  public void creditsNeverExceedTheCap() {
    VbMatch m = buyPhase();
    UUID winner = m.squad(1).get(0);
    for (int i = 0; i < 4; i++) forceAndNext(m, 1);
    assertEquals(9000, m.credits(winner));
  }

  @Test
  public void survivorsKeepShieldAndLoadoutDeadOnesLoseThem() {
    VbMatch m = buyPhase();
    UUID survivor = first(m, VbSide.SEALERS);
    UUID dead = of(m, VbSide.SEALERS).stream().filter(id -> !id.equals(survivor)).findFirst().orElseThrow();
    m.buyShield(survivor, 50, 0);
    m.buyShield(dead, 50, 0);
    m.advance();
    m.damage(dead, first(m, VbSide.SEEDBEARERS), 1000);
    m.forceRound(m.score().squadOf(VbSide.SEALERS, 1));
    m.advance();
    assertEquals("BUY", m.phase());
    assertEquals(2, m.round());
    assertTrue(m.keepsLoadout(survivor));
    assertFalse(m.keepsLoadout(dead));
    assertTrue("unused bought abilities survive a death", m.keepsAbilities(dead));
    assertTrue(m.keepsAbilities(survivor));
    assertEquals(50, m.shield(survivor));
    assertEquals(0, m.shield(dead));
    assertEquals("everyone respawns", 10, m.alive().size());
    assertEquals(100, m.health(dead));
  }

  @Test
  public void sidesSwapAndEconomyResetsForTheSecondHalf() {
    VbMatch m = buyPhase();
    UUID p = m.squad(0).get(0);
    VbSide before = m.side(p);
    for (int i = 0; i < 6; i++) forceAndNext(m, 0);
    assertEquals(7, m.round());
    assertEquals(before.other(), m.side(p));
    assertEquals(800, m.credits(p));
    assertEquals(0, m.lossStreak(1));
    assertFalse("pistol round: nothing is kept", m.keepsLoadout(p));
    assertFalse("pistol round: bought abilities are cleared too", m.keepsAbilities(p));
    assertNotNull(m.seedCarrier());
    assertEquals(VbSide.SEEDBEARERS, m.side(m.seedCarrier()));
  }

  @Test
  public void firstToSevenEndsTheMatchAfterTheRoundEndPause() {
    VbMatch m = buyPhase();
    for (int i = 0; i < 6; i++) forceAndNext(m, 0);
    m.advance(); // BUY -> COMBAT
    m.forceRound(0);
    assertEquals("ROUND_END", m.phase());
    assertEquals("", m.winner());
    m.advance();
    assertEquals("END", m.phase());
    assertEquals("Team A", m.winner());
    assertEquals(new HashSet<>(humans.stream().filter(id -> m.squadOf(id) == 0).toList()), m.winners());
  }

  @Test
  public void overtimeEndToEnd() {
    VbMatch m = buyPhase();
    for (int i = 0; i < 6; i++) {
      forceAndNext(m, 0);
      forceAndNext(m, 1);
    }
    assertEquals(13, m.round());
    for (UUID id : m.participants()) assertEquals(5000, m.credits(id));
    forceAndNext(m, 0);
    assertEquals(14, m.round());
    forceAndNext(m, 1);
    assertEquals("7-7: sudden death", 15, m.round());
    m.advance();
    m.forceRound(1);
    m.advance();
    assertEquals("END", m.phase());
    assertEquals("Team B", m.winner());
  }

  @Test
  public void leavingMidCombatCountsAsADeathAndTheLastLeaverForfeitsTheMatch() {
    VbMatch m = combat();
    List<UUID> sealers = of(m, VbSide.SEALERS);
    for (UUID id : sealers.subList(0, 4)) m.remove(id);
    assertEquals("COMBAT", m.phase());
    assertEquals(6, m.alive().size());
    m.remove(sealers.get(4));
    assertEquals("END", m.phase());
    assertEquals("Team " + VbMatch.teamName(m.score().squadOf(VbSide.SEEDBEARERS, 1)), m.winner());
  }

  @Test
  public void aSquadThatAllLeftForfeits() {
    VbMatch m = match(2);
    m.remove(m.squad(0).get(0));
    assertEquals("END", m.phase());
    assertEquals("Team B", m.winner());
  }

  @Test
  public void timeRunningOutDeclaresTheLeaderOrADraw() {
    VbMatch m = buyPhase();
    m.finish();
    assertEquals("DRAW", m.winner());
    assertEquals(Set.of(), m.winners());
    VbMatch n = buyPhase();
    n.advance();
    n.forceRound(1);
    n.finish();
    assertEquals("Team B", n.winner());
  }

  @Test
  public void roleIsTheSideAndVotingIsRefused() {
    VbMatch m = buyPhase();
    assertEquals("SEEDBEARERS", m.role(m.seedCarrier()));
    assertThrows(IllegalArgumentException.class, () -> m.select(m.seedCarrier(), m.seedCarrier()));
  }

  @Test
  public void eventsAreReported() {
    VbMatch m = match(10);
    List<String> log = new ArrayList<>();
    m.setEvents(
        new VbEvents() {
          public void phaseChanged(VbMatch x, VbPhase from, VbPhase to) {
            log.add(from + ">" + to);
          }

          public void roundEnded(VbMatch x, VbMatch.RoundResult r) {
            log.add("end" + r.round());
          }

          public void seedPlanted(VbMatch x, VbSite s, UUID p) {
            log.add("plant" + s.id());
          }
        });
    m.advance();
    m.advance();
    m.holdPlant(m.seedCarrier(), VbData.sites().get(1), 10);
    m.advance();
    assertEquals(
        List.of("AGENT_SELECT>BUY", "BUY>COMBAT", "COMBAT>PLANTED", "plantB", "PLANTED>ROUND_END", "end1"), log);
  }

  @Test
  public void everyoneIsAliveAgainWhenARoundStarts() {
    VbMatch m = buyPhase();
    List<Integer> aliveAtStart = new ArrayList<>();
    m.setEvents(new VbEvents() {
      @Override
      public void roundStarted(VbMatch match, int round) {
        aliveAtStart.add(match.alive().size());
      }
    });
    m.advance();
    killAll(m, VbSide.SEALERS, first(m, VbSide.SEEDBEARERS));
    assertTrue("the dead stay out until the next round", m.alive().size() < 10);
    m.advance(); // round end -> next buy phase
    assertEquals(List.of(10), aliveAtStart);
    assertEquals("everyone is alive() again, so the engine does not re-spectate them", 10, m.alive().size());
  }

  @Test
  public void theSeedCarrierLeavingInTheBuyPhasePassesTheSeedOn() {
    VbMatch m = buyPhase();
    UUID carrier = m.seedCarrier();
    assertNotNull(carrier);
    m.remove(carrier);
    assertTrue(m.hasLeft(carrier));
    assertNotNull(m.seedCarrier());
    assertNotEquals(carrier, m.seedCarrier());
    assertEquals(VbSide.SEEDBEARERS, m.side(m.seedCarrier()));
    assertTrue(m.alive().contains(m.seedCarrier()));
  }

  @Test
  public void theSeedCarrierLeavingInCombatPassesTheSeedOnToo() {
    VbMatch m = combat();
    UUID carrier = m.seedCarrier();
    m.remove(carrier);
    assertNotNull(m.seedCarrier());
    assertNotEquals(carrier, m.seedCarrier());
  }

  @Test
  public void theLastSeedbearerLeavingLeavesNoCarrier() {
    VbMatch m = buyPhase();
    for (UUID id : of(m, VbSide.SEEDBEARERS)) if (!id.equals(m.seedCarrier())) m.remove(id);
    UUID last = m.seedCarrier();
    m.remove(last);
    assertNull(m.seedCarrier());
  }

  @Test
  public void aStaleClockAdvanceDoesNotSkipTheFuse() {
    VbMatch m = combat();
    // The clock read COMBAT as due; meanwhile the plant finished.
    assertTrue(m.holdPlant(m.seedCarrier(), VbData.sites().get(0), 100));
    assertEquals("PLANTED", m.phase());
    m.advance("COMBAT"); // stale: the fuse is not skipped
    assertEquals("PLANTED", m.phase());
    assertEquals(0, m.results().size());
    m.advance("PLANTED");
    assertEquals("ROUND_END", m.phase());
    assertEquals(VbMatch.Reason.FUSE, m.results().get(0).reason());
  }
}
