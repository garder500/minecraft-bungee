package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.Test;

public class SealRulesTest {
  private static SealRules.Candidate c(UUID id, VbSide side, boolean alive, double dist, double angle, boolean scoped, boolean holding) {
    return new SealRules.Candidate(id, side, alive, dist, angle, scoped, holding);
  }

  private static SealRules.Candidate ok(UUID id) {
    return c(id, VbSide.SEALERS, true, 1.0, 10, false, true);
  }

  @Test
  public void onlyALivingSealerInRangeAimingAndHoldingMay() {
    UUID a = UUID.randomUUID();
    assertTrue(SealRules.mayHold(ok(a)));
    assertFalse("seedbearer", SealRules.mayHold(c(a, VbSide.SEEDBEARERS, true, 1, 10, false, true)));
    assertFalse("dead", SealRules.mayHold(c(a, VbSide.SEALERS, false, 1, 10, false, true)));
    assertFalse("too far", SealRules.mayHold(c(a, VbSide.SEALERS, true, 2.6, 10, false, true)));
    assertTrue("edge", SealRules.mayHold(c(a, VbSide.SEALERS, true, 2.5, 35, false, true)));
    assertFalse("looking away", SealRules.mayHold(c(a, VbSide.SEALERS, true, 1, 36, false, true)));
    assertFalse("scoped sniper", SealRules.mayHold(c(a, VbSide.SEALERS, true, 1, 10, true, true)));
    assertFalse("not holding", SealRules.mayHold(c(a, VbSide.SEALERS, true, 1, 10, false, false)));
  }

  @Test
  public void theFirstHolderKeepsItAndOthersWaitTheirTurn() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    assertEquals(a, SealRules.pick(null, List.of(ok(a), ok(b))));
    assertEquals("b keeps it although a is first in the list", b, SealRules.pick(b, List.of(ok(a), ok(b))));
    assertEquals("b let go: a takes over", a, SealRules.pick(b, List.of(ok(a), c(b, VbSide.SEALERS, true, 1, 10, false, false))));
    assertNull(SealRules.pick(a, List.of(c(a, VbSide.SEALERS, true, 9, 10, false, true))));
    assertNull(SealRules.pick(null, List.of()));
  }

  @Test
  public void reachNeedsASealerAliveAndNear() {
    assertTrue(SealRules.inReach(VbSide.SEALERS, true, 2.5));
    assertFalse(SealRules.inReach(VbSide.SEALERS, true, 3));
    assertFalse(SealRules.inReach(VbSide.SEEDBEARERS, true, 1));
    assertFalse(SealRules.inReach(VbSide.SEALERS, false, 1));
  }

  @Test
  public void angleAndFuseTicks() {
    assertEquals(0, SealRules.angle(0, 0, 1, 0, 0, 5), 1e-6);
    assertEquals(90, SealRules.angle(0, 0, 1, 5, 0, 0), 1e-6);
    assertEquals(0, SealRules.angle(0, 0, 1, 0, 0, 0), 1e-6);
    assertTrue(SealRules.tickInterval(0, 40) > SealRules.tickInterval(20, 40));
    assertTrue(SealRules.tickInterval(20, 40) > SealRules.tickInterval(40, 40));
    assertEquals(0.15, SealRules.tickInterval(99, 40), 1e-9);
  }

  @Test
  public void releaseDropsToTheCheckpointOnlyAfterReachingIt() {
    VbMatch m = planted();
    UUID s = sealer(m);
    double cp = m.rules().sealCheckpointSeconds();
    m.holdSeal(s, cp - 0.5);
    m.holdSeal(null, 0.05);
    assertEquals("before the checkpoint a release resets", 0, m.sealProgress(), 1e-9);
    m.holdSeal(s, cp + 0.5);
    m.holdSeal(null, 0.05);
    assertEquals("after it, back to the checkpoint", cp, m.sealProgress(), 1e-9);
    assertTrue(m.holdSeal(s, m.rules().sealSeconds()));
  }

  @Test
  public void aSeedbearerCannotSeal() {
    VbMatch m = planted();
    UUID bearer = m.participants().stream().filter(i -> m.side(i) == VbSide.SEEDBEARERS).findFirst().get();
    assertFalse(m.holdSeal(bearer, 100));
    assertEquals(VbPhase.PLANTED, m.vbPhase());
  }

  private static UUID sealer(VbMatch m) {
    return m.participants().stream().filter(i -> m.side(i) == VbSide.SEALERS).findFirst().get();
  }

  private VbMatch planted() {
    List<UUID> ids = new java.util.ArrayList<>();
    for (int i = 0; i < 10; i++) ids.add(UUID.randomUUID());
    VbMatch m = new VbMatch(VbData.rules(), ids, new Random(7));
    m.advance();
    m.advance();
    assertTrue(m.holdPlant(m.seedCarrier(), VbData.sites().get(0), m.rules().plantHoldSeconds() + 1));
    assertEquals(VbPhase.PLANTED, m.vbPhase());
    return m;
  }
}
