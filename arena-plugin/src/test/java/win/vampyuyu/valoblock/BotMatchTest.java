package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;

/** Filling a match with bots, their agents, the practice flag and late humans. */
public class BotMatchTest {
  private final VbRules rules = VbData.rules();

  private VbMatch match(int humans, long seed) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < humans; i++) ids.add(UUID.randomUUID());
    return new VbMatch(rules, ids, new Random(seed));
  }

  private long botsIn(VbMatch m, int squad) {
    return m.squad(squad).stream().filter(m::isBot).count();
  }

  @Test
  public void oneHumanIsEnoughNineBots() {
    VbMatch m = match(1, 1);
    assertFalse(m.hasBots());
    m.advance();
    assertTrue(m.hasBots());
    assertEquals(9, m.bots().size());
    assertEquals(5, m.squad(0).size());
    assertEquals(5, m.squad(1).size());
    assertEquals(10, m.alive().size());
  }

  @Test
  public void humansAreSplitFirstThenEachTeamIsToppedUpToFive() {
    for (int humans = 1; humans <= 10; humans++) {
      VbMatch m = match(humans, humans);
      int h0 = m.squad(0).size(), h1 = m.squad(1).size();
      assertTrue("balanced split of " + humans, Math.abs(h0 - h1) <= 1);
      m.advance();
      assertEquals(5, m.squad(0).size());
      assertEquals(5, m.squad(1).size());
      assertEquals(10 - humans, m.bots().size());
      assertEquals(5 - h0, botsIn(m, 0));
      assertEquals(5 - h1, botsIn(m, 1));
    }
  }

  @Test
  public void tenHumansNeedNoBots() {
    VbMatch m = match(10, 5);
    m.advance();
    assertFalse(m.hasBots());
  }

  @Test
  public void everyBotHasAnAgentAndNoTeamRepeatsOne() {
    for (long seed = 0; seed < 30; seed++) {
      VbMatch m = match(1, seed);
      m.advance();
      for (int squad = 0; squad < 2; squad++) {
        List<String> agents = m.squad(squad).stream().map(m::agent).toList();
        assertFalse(agents.contains(""));
        assertEquals(agents.size(), new HashSet<>(agents).size());
      }
    }
  }

  @Test
  public void aBotNeverTakesTheAgentAHumanLocked() {
    for (long seed = 0; seed < 30; seed++) {
      VbMatch m = match(2, seed);
      UUID human = m.squad(0).get(0);
      m.lockAgent(human, "vesper");
      m.advance();
      for (UUID id : m.squad(0)) if (!id.equals(human)) assertNotEquals("vesper", m.agent(id));
    }
  }

  @Test
  public void botsFillMissingRolesFirst() {
    // Squad 0 has a human Weaver (murk); with the 8 agents of 4 roles, the four bots of the squad must cover the other three roles first.
    for (long seed = 0; seed < 40; seed++) {
      VbMatch m = match(2, seed);
      UUID human = m.squad(0).get(0);
      m.lockAgent(human, "murk");
      m.advance();
      Set<String> roles = m.squad(0).stream().map(id -> VbData.agents().get(m.agent(id)).role()).collect(Collectors.toSet());
      assertEquals("all four roles covered, seed " + seed, 4, roles.size());
    }
  }

  @Test
  public void aTeamWithoutAWeaverGetsOne() {
    VbAgents agents = VbData.agents();
    for (long seed = 0; seed < 50; seed++) {
      List<String> free = new ArrayList<>(List.of("cinder", "gale", "murk", "vesper"));
      String pick = BotAgentPicker.pick(free, List.of("rime", "thorn", "nacre"), agents, new Random(seed));
      // Warden (rime, thorn) and Seer (nacre) are held: the missing roles are Vanguard and Weaver.
      assertTrue(List.of("cinder", "gale", "murk").contains(pick));
    }
    String weaver = BotAgentPicker.pick(List.of("cinder", "murk"), List.of("rime", "nacre", "gale"), agents, new Random(1));
    assertEquals("murk", weaver);
    assertNull(BotAgentPicker.pick(List.of(), List.of(), agents, new Random(1)));
  }

  @Test
  public void everyoneCoveredPicksAnyFreeAgent() {
    String pick = BotAgentPicker.pick(List.of("cinder"), List.of("cinder", "rime", "nacre", "murk"), VbData.agents(), new Random(1));
    assertEquals("cinder", pick);
  }

  @Test
  public void aLateHumanTakesABotSeatWithItsShardsAndAgent() {
    VbMatch m = match(1, 9);
    m.advance(); // buy 1
    m.advance(); // combat
    m.forceRound(0);
    // Everything every bot holds before the next buy phase.
    Map<UUID, String> agent = new HashMap<>();
    Map<UUID, Integer> credits = new HashMap<>();
    for (UUID id : m.bots()) {
      agent.put(id, m.agent(id));
      credits.put(id, m.credits(id));
    }
    UUID late = UUID.randomUUID();
    List<UUID> replaced = new ArrayList<>();
    m.setEvents(new VbEvents() {
      @Override
      public void botReplaced(VbMatch match, UUID b, UUID h) {
        replaced.add(b);
      }
    });
    m.queueSeat(late);
    assertEquals(1, m.pendingSeats());
    assertFalse(m.participants().contains(late)); // not before the next buy phase
    m.advance(); // round end -> next buy phase
    assertEquals(VbPhase.BUY, m.vbPhase());
    assertEquals(0, m.pendingSeats());
    assertEquals(1, replaced.size());
    UUID gone = replaced.get(0);
    assertTrue(agent.containsKey(gone));
    assertTrue(m.participants().contains(late));
    assertFalse(m.participants().contains(gone));
    assertFalse(m.isBot(late));
    assertEquals(8, m.bots().size());
    assertEquals(10, m.participants().size());
    assertEquals(agent.get(gone), m.agent(late));
    assertEquals(credits.get(gone).intValue(), m.credits(late));
  }

  @Test
  public void aLateHumanGoesToTheSquadWithFewerHumans() {
    VbMatch m = match(3, 2); // 2 humans vs 1, or 1 vs 2
    m.advance();
    int h0 = (int) m.squad(0).stream().filter(id -> !m.isBot(id)).count();
    int h1 = (int) m.squad(1).stream().filter(id -> !m.isBot(id)).count();
    UUID late = UUID.randomUUID();
    m.queueSeat(late);
    m.advance(); // combat
    m.forceRound(0);
    m.advance();
    assertEquals(h0 <= h1 ? 0 : 1, m.squadOf(late));
  }

  @Test
  public void nobodyElseCanTakeASeatTwiceOrWithoutBots() {
    VbMatch m = match(10, 4);
    m.advance();
    assertNull(m.replaceBot(UUID.randomUUID()));
    UUID in = m.participants().iterator().next();
    try {
      m.replaceBot(in);
      fail("already in the match");
    } catch (IllegalArgumentException expected) {
      // fine
    }
  }

  @Test
  public void aMatchWhoseHumansAllLeftEnds() {
    VbMatch m = match(2, 6);
    List<UUID> humans = new ArrayList<>(m.participants());
    m.advance();
    assertEquals(VbPhase.BUY, m.vbPhase());
    for (UUID h : humans) m.remove(h);
    assertEquals(VbPhase.END, m.vbPhase());
  }
}
