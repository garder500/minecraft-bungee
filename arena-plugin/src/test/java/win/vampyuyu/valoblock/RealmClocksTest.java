package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** The timing of the Realm rules: Hourfall's gates, Stormbreak's gusts and wind pads, Rootvault's shafts. */
public class RealmClocksTest {
  private static VbPlan plan(String id) {
    return new VbPlan(VbData.maps().get(id));
  }

  @Test
  public void gatePairsAlternateEvery30SecondsAndTheSouthPairStartsClosed() {
    assertEquals(0, TimeTide.openPair(0));
    assertEquals(1, TimeTide.closedPair(0));
    assertEquals(0, TimeTide.openPair(599));
    assertEquals(1, TimeTide.openPair(600));
    assertEquals(1, TimeTide.openPair(1199));
    assertEquals(0, TimeTide.openPair(1200));
    assertEquals(0, TimeTide.closedPair(600));
  }

  @Test
  public void theBellRingsExactlyThreeSecondsBeforeEachSwapAndNeverTwice() {
    assertFalse(TimeTide.bellBetween(538, 539));
    assertTrue(TimeTide.bellBetween(539, 540));
    assertFalse(TimeTide.bellBetween(540, 541));
    int bells = 0, flips = 0;
    long lastBell = -1;
    for (long t = 1; t <= 6000; t++) {
      if (TimeTide.bellBetween(t - 1, t)) {
        bells++;
        assertEquals("the bell is 60 ticks before a swap", 60, TimeTide.ticksToFlip(t));
        assertTrue(lastBell < 0 || t - lastBell == 600);
        lastBell = t;
      }
      if (TimeTide.flipBetween(t - 1, t)) {
        flips++;
        assertEquals(0, t % 600);
        assertNotEquals(TimeTide.openPair(t - 1), TimeTide.openPair(t));
      }
    }
    assertEquals(10, bells); // 540, 1140, ... 5940
    assertEquals(10, flips); // 600 ... 6000, none at 0
    assertFalse("no swap at the very start", TimeTide.flipBetween(-1, 0));
  }

  @Test
  public void rangeChecksAgreeWithTickByTickChecks() {
    for (long from = 0; from < 1500; from += 37)
      for (long to = from; to < from + 1900; to += 61) {
        boolean bell = false, flip = false, gust = false, warn = false;
        for (long t = from + 1; t <= to; t++) {
          bell |= TimeTide.bellBetween(t - 1, t);
          flip |= TimeTide.flipBetween(t - 1, t);
          gust |= GustClock.gustBetween(t - 1, t);
          warn |= GustClock.announceBetween(t - 1, t);
        }
        assertEquals(bell, TimeTide.bellBetween(from, to));
        assertEquals(flip, TimeTide.flipBetween(from, to));
        assertEquals(gust, GustClock.gustBetween(from, to));
        assertEquals(warn, GustClock.announceBetween(from, to));
      }
  }

  @Test
  public void gatesAreTheFourFCellsOfHourfallInTwoPairsAroundTheTower() {
    VbPlan p = plan("hourfall");
    List<int[]> gates = TimeTide.gates(p);
    assertEquals(4, gates.size());
    assertEquals(0, TimeTide.pairOf(p, gates.get(0)[1]));
    assertEquals(0, TimeTide.pairOf(p, gates.get(1)[1]));
    assertEquals(1, TimeTide.pairOf(p, gates.get(2)[1]));
    assertEquals(1, TimeTide.pairOf(p, gates.get(3)[1]));
    assertEquals(Set.of("n_12_11", "n_17_11"), TimeTide.nodeIds(p, 0));
    for (int[] g : gates) assertEquals("a gate is a one-cell doorway", 'x' == p.doorway(g[0], g[1]) || 'z' == p.doorway(g[0], g[1]), true);
  }

  @Test
  public void everySiteStaysReachableWithEitherGatePairClosed() {
    VbMap m = VbData.maps().get("hourfall");
    VbPlan p = new VbPlan(m);
    Waypoints g = m.waypoints(0, 0, 0);
    for (int pair = 0; pair < 2; pair++) {
      Set<String> closed = TimeTide.nodeIds(p, pair);
      for (VbSide side : VbSide.values())
        for (String spawn : g.spawns(side))
          for (Waypoints.SiteInfo s : g.sites()) {
            List<String> wanted = new ArrayList<>(s.anchors());
            wanted.add(s.stage());
            wanted.add(s.plant());
            for (String t : wanted) assertFalse("pair " + pair + " closed: " + t + " from " + spawn, g.path(spawn, t, closed).isEmpty());
          }
    }
  }

  @Test
  public void aClosedGateMakesTheBotsRotateTheLongWay() {
    VbMap m = VbData.maps().get("hourfall");
    Waypoints g = m.waypoints(0, 0, 0);
    // through the tower: north gate to south gate; with the south pair closed the path must not cross it
    List<String> open = g.path("n_12_10", "n_12_19", Set.of());
    List<String> shut = g.path("n_12_10", "n_12_19", Set.of("n_12_18", "n_17_18"));
    assertFalse(open.isEmpty());
    assertFalse(shut.isEmpty());
    assertFalse(shut.contains("n_12_18"));
    g.setBlocked(Set.of("n_12_11"));
    assertFalse(g.path("n_12_10", "n_12_19").contains("n_12_11"));
    g.setBlocked(Set.of());
    assertEquals(open, g.path("n_12_10", "n_12_19"));
  }

  @Test
  public void gustsBlowEvery45SecondsAnnouncedThreeSecondsBeforeAndAlternate() {
    assertFalse(GustClock.gustBetween(-1, 0));
    assertFalse(GustClock.gustBetween(0, 899));
    assertTrue(GustClock.gustBetween(899, 900));
    assertTrue(GustClock.announceBetween(839, 840));
    assertFalse(GustClock.announceBetween(840, 841));
    assertEquals(0, GustClock.pushing(899));
    assertEquals(1, GustClock.pushing(900));
    assertEquals(1, GustClock.pushing(900 + GustClock.DURATION - 1));
    assertEquals(0, GustClock.pushing(900 + GustClock.DURATION));
    assertEquals(2, GustClock.pushing(1800));
    assertEquals(1, GustClock.sign(1));
    assertEquals(-1, GustClock.sign(2));
    int gusts = 0;
    for (long t = 1; t <= 9000; t++) if (GustClock.gustBetween(t - 1, t)) gusts++;
    assertEquals(10, gusts);
  }

  @Test
  public void gustsPushAcrossOneCellBridgesOnly() {
    VbPlan p = plan("stormbreak");
    assertEquals('x', GustClock.pushAxis(p, 4, 12)); // north-south bridge: pushed east or west
    assertEquals('x', GustClock.pushAxis(p, 25, 17));
    assertEquals('z', GustClock.pushAxis(p, 6, 20)); // east-west bridge: pushed north or south
    assertEquals(0, GustClock.pushAxis(p, 14, 14)); // the Eyrie
    assertEquals(0, GustClock.pushAxis(p, 14, 26)); // the landing
    assertEquals(0, GustClock.pushAxis(p, 6, 14)); // two cells wide
    int bridges = 0;
    for (int r = 0; r < p.rows; r++) for (int c = 0; c < p.cols; c++) if (GustClock.pushAxis(p, c, r) != 0) bridges++;
    assertTrue("bridges: " + bridges, bridges >= 10);
    double[] east = GustClock.push('x', 1), west = GustClock.push('x', 2), south = GustClock.push('z', 1);
    assertTrue(east[0] > 0 && east[1] == 0);
    assertTrue(west[0] < 0);
    assertTrue(south[1] > 0 && south[0] == 0);
    assertEquals(0, GustClock.push((char) 0, 1)[0], 0);
  }

  @Test
  public void everyWindPadLandsOnAWalkableCellInAboutTwoSecondsFollowingTheArc() {
    VbMap m = VbData.maps().get("stormbreak");
    VbPlan p = new VbPlan(m);
    List<WindPads.Pad> pads = WindPads.pads(p);
    assertEquals("every F cell is a pad", p.cellsOf('F').size(), pads.size());
    assertEquals(16, pads.size());
    for (WindPads.Pad pad : pads) {
      assertTrue("landing of " + pad, p.walk(pad.toCol(), pad.toRow()));
      assertNotEquals('F', p.at(pad.toCol(), pad.toRow()));
      double[] from = {(pad.col() + 0.5) * p.cell, 0, (pad.row() + 0.5) * p.cell};
      double[] to = {(pad.toCol() + 0.5) * p.cell, m.standY(p.at(pad.toCol(), pad.toRow())), (pad.toRow() + 0.5) * p.cell};
      WindPads.Flight f = new WindPads.Flight(from, to);
      assertTrue("clears the cliffs", f.apex() >= 10);
      // follow the arc the way the service does: set the velocity each tick, the body moves by it
      double[] pos = from.clone();
      double fastest = 0;
      for (int t = 0; t < WindPads.FLIGHT_TICKS; t++) {
        assertFalse(f.done(t));
        double[] v = f.velocity(t, pos);
        fastest = Math.max(fastest, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
        for (int i = 0; i < 3; i++) pos[i] += v[i];
        assertFalse("never in the sea", WindPads.drowned(pos[1]));
      }
      assertTrue(f.done(WindPads.FLIGHT_TICKS));
      assertTrue("speed " + fastest, fastest < 3.5); // never clamped, so the landing is exact
      for (int i = 0; i < 3; i++) assertEquals(to[i], pos[i], 1e-6);
    }
    assertEquals(40, WindPads.FLIGHT_TICKS); // 2 s
  }

  @Test
  public void padsAreUsedStandingOnThemAndTheSeaIsBelowTheFloor() {
    VbPlan p = plan("stormbreak");
    List<WindPads.Pad> pads = WindPads.pads(p);
    assertNotNull(WindPads.padAt(p, pads, 12.5 * 5, 0, 4.5 * 5));
    assertNull("in the air above a pad", WindPads.padAt(p, pads, 12.5 * 5, 3, 4.5 * 5));
    assertNull(WindPads.padAt(p, pads, 11.5 * 5, 0, 4.5 * 5));
    assertTrue(WindPads.drowned(-1));
    assertFalse(WindPads.drowned(0));
    assertFalse(WindPads.drowned(-0.5));
  }

  @Test
  public void rootShaftsSitAboveFCellsAndStopAtTheBranch() {
    VbPlan p = plan("rootvault");
    List<RootDrops.Root> roots = RootDrops.roots(p);
    assertEquals(4, roots.size());
    RootDrops.Root r = roots.get(0);
    double cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
    assertTrue(RootDrops.inShaft(roots, cx, 5, cz));
    assertFalse("standing on the floor", RootDrops.inShaft(roots, cx, 0, cz));
    assertFalse("above the branch", RootDrops.inShaft(roots, cx, 12, cz));
    assertFalse("beside the shaft", RootDrops.inShaft(roots, r.maxX() + 3, 5, cz));
    assertEquals(2, roots.stream().filter(x -> x.col() < 15).count()); // two per site
  }

  @Test
  public void everyRootHasABranchFromTheCanopyLoop() {
    VbPlan p = plan("rootvault");
    for (int[] f : p.cellsOf('F')) {
      List<int[]> b = RootDrops.branch(p, f);
      assertFalse(b.isEmpty());
      int[] last = b.get(b.size() - 1);
      assertArrayEquals(f, last);
      int[] first = b.get(0);
      boolean touchesLoop = false;
      for (int[] d : VbPlan.DIRS) touchesLoop |= p.at(first[0] + d[0], first[1] + d[1]) == 'h';
      assertTrue("branch starts next to the loop", touchesLoop);
    }
  }
}
