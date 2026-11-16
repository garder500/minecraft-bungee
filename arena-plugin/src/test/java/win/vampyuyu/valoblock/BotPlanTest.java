package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class BotPlanTest {
  private final Waypoints g = VbData.waypoints();
  private final BotConfig cfg = VbData.bots();

  private List<UUID> ids(int n) {
    List<UUID> l = new ArrayList<>();
    for (int i = 0; i < n; i++) l.add(UUID.randomUUID());
    return l;
  }

  @Test
  public void siteChoiceFollowsTheWeights() {
    Random r = new Random(11);
    int a = 0, n = 4000;
    for (int i = 0; i < n; i++) if (BotPlan.pickSite(g, r).equals("A")) a++;
    double expected = g.site("A").weight() / (g.site("A").weight() + g.site("B").weight());
    assertEquals(expected, a / (double) n, 0.04);
  }

  @Test
  public void defendersSpreadByWeightWithOneAnchorEach() {
    BotPlan.Defense d = BotPlan.Defense.allocate(g, ids(5), new Random(1));
    assertEquals(3, d.holding("A")); // weights 0.55 / 0.45 of 5 = 2.75 / 2.25
    assertEquals(2, d.holding("B"));
  }

  @Test
  public void everyDefenderHasAnAnchorOfItsSite() {
    List<UUID> bots = ids(5);
    BotPlan.Defense d = BotPlan.Defense.allocate(g, bots, new Random(2));
    for (UUID b : bots) {
      assertNotNull(d.siteOf(b));
      assertTrue(g.site(d.siteOf(b)).anchors().contains(d.anchor(b)));
    }
  }

  @Test
  public void gunfireSendsTheNearestDefendersButNeverEmptiesASite() {
    List<UUID> bots = ids(5);
    BotPlan.Defense d = BotPlan.Defense.allocate(g, bots, new Random(3));
    Map<UUID, double[]> pos = new HashMap<>();
    for (UUID b : bots) {
      Waypoints.Node n = g.node(d.anchor(b));
      pos.put(b, new double[] {n.x(), n.y(), n.z()});
    }
    int b0 = d.holding("B");
    List<UUID> sent = d.rotate("A", pos, 5); // ask for more than can leave
    assertEquals("B keeps one defender", 1, d.holding("B"));
    assertEquals(b0 - 1, sent.size());
    for (UUID s : sent) assertEquals("A", d.siteOf(s));
    // Rotated bots do not go back and forth.
    assertTrue(d.rotate("B", pos, 5).stream().noneMatch(sent::contains));
  }

  @Test
  public void rotateCountLimitsHowManyLeave() {
    List<UUID> bots = ids(5);
    BotPlan.Defense d = BotPlan.Defense.allocate(g, bots, new Random(4));
    Map<UUID, double[]> pos = new HashMap<>();
    for (UUID b : bots) {
      Waypoints.Node n = g.node(d.anchor(b));
      pos.put(b, new double[] {n.x(), n.y(), n.z()});
    }
    assertEquals(1, d.rotate("B", pos, 1).size());
    assertEquals(3, d.holding("B"));
    assertTrue("nobody has a position, nobody goes", BotPlan.Defense.allocate(g, ids(5), new Random(4)).rotate("B", new HashMap<>(), 2).isEmpty());
  }

  @Test
  public void aGunshotIsPlacedAtTheNearestSiteWithinRange() {
    Waypoints.Node a = g.node(g.site("A").plant());
    assertEquals("A", BotPlan.siteNear(g, a.x() + 3, a.y(), a.z(), 30));
    assertNull(BotPlan.siteNear(g, 500, 65, 500, 30));
  }

  @Test
  public void theGroupIsReleasedWhenAllAreCloseOrAfterTheWait() {
    BotPlan.Attack at = new BotPlan.Attack("A", 1000);
    Waypoints.Node stage = g.node(g.site("A").stage());
    List<double[]> far = List.of(new double[] {stage.x(), stage.y(), stage.z()}, new double[] {stage.x() + 40, stage.y(), stage.z()});
    assertFalse(at.update(2000, g, cfg, far));
    List<double[]> near = List.of(new double[] {stage.x(), stage.y(), stage.z()}, new double[] {stage.x() + 3, stage.y(), stage.z()});
    assertTrue(at.update(3000, g, cfg, near));
    assertTrue(at.update(4000, g, cfg, far)); // stays released

    BotPlan.Attack slow = new BotPlan.Attack("B", 0);
    assertFalse(slow.update(13_000, g, cfg, far));
    assertTrue(slow.update(14_000, g, cfg, far)); // 14 s timeout
  }
}
