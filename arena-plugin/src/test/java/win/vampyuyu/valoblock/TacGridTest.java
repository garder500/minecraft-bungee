package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class TacGridTest {
  private static VbMap map(String id) {
    return VbData.maps().get(id);
  }

  @Test
  public void theGridSitsOnTheFiveByFiveMiddleSlotsOfTheChest() {
    assertEquals(2, TacGrid.slot(0, 0));
    assertEquals(6, TacGrid.slot(4, 0));
    assertEquals(4 * 9 + 6, TacGrid.slot(4, 4));
    for (int gy = 0; gy < 5; gy++)
      for (int gx = 0; gx < 5; gx++) assertArrayEquals(new int[] {gx, gy}, TacGrid.gridOf(TacGrid.slot(gx, gy)));
    assertNull(TacGrid.gridOf(0));
    assertNull(TacGrid.gridOf(8));
    assertNull(TacGrid.gridOf(TacGrid.BACK));
    assertNull(TacGrid.gridOf(TacGrid.CANCEL));
    assertEquals(9, TacGrid.teamSlot(1));
  }

  @Test
  public void overviewCellsAreSixByTheSixPlanCellsAndZoomCellsAreFinerStill() {
    VbMap h = map("hourfall"); // cell 4 blocks
    assertArrayEquals(new double[] {3, 3}, TacGrid.centreCells(h, TacGrid.OVERVIEW, 0, 0), 1e-9);
    assertArrayEquals(new double[] {12, 12}, TacGrid.centre(h, TacGrid.OVERVIEW, 0, 0), 1e-9);
    assertArrayEquals(new double[] {108, 108}, TacGrid.centre(h, TacGrid.OVERVIEW, 4, 4), 1e-9);
    // zone 7 = column 2, row 1: it starts at cell (12, 6); a fine slot is 1.2 cells
    assertArrayEquals(new double[] {12.6, 6.6}, TacGrid.centreCells(h, 7, 0, 0), 1e-9);
    assertArrayEquals(new double[] {17.4, 6.6}, TacGrid.centreCells(h, 7, 4, 0), 1e-9);
    assertArrayEquals(new double[] {69.6, 26.4}, TacGrid.centre(h, 7, 4, 0), 1e-9);
    VbMap s = map("stormbreak"); // cell 5 blocks
    assertArrayEquals(new double[] {15, 15}, TacGrid.centre(s, TacGrid.OVERVIEW, 0, 0), 1e-9);
    assertArrayEquals(new double[] {87, 33}, TacGrid.centre(s, 7, 4, 0), 1e-9);
  }

  @Test
  public void everyCentreOfEveryMapAndLevelLiesInsideItsViewAndBackToItsCell() {
    for (VbMap m : VbData.maps().values()) {
      for (int gy = 0; gy < 5; gy++)
        for (int gx = 0; gx < 5; gx++) {
          double[] c = TacGrid.centre(m, TacGrid.OVERVIEW, gx, gy);
          assertTrue(c[0] > 0 && c[0] < m.width() && c[1] > 0 && c[1] < m.depth());
          assertArrayEquals(m.id(), new int[] {gx, gy}, TacGrid.cellOf(m, TacGrid.OVERVIEW, c[0], c[1]));
          assertEquals(TacGrid.zone(gx, gy), TacGrid.zoneOf(m, c[0], c[1]));
        }
      for (int zone = 0; zone < 25; zone++)
        for (int gy = 0; gy < 5; gy++)
          for (int gx = 0; gx < 5; gx++) {
            double[] c = TacGrid.centre(m, zone, gx, gy);
            assertEquals(m.id() + " zone " + zone, zone, TacGrid.zoneOf(m, c[0], c[1]));
            assertArrayEquals(m.id() + " zone " + zone, new int[] {gx, gy}, TacGrid.cellOf(m, zone, c[0], c[1]));
          }
    }
  }

  @Test
  public void aPointOfAnotherZoneIsNotInTheZoomedView() {
    VbMap h = map("hourfall");
    assertNull(TacGrid.cellOf(h, 0, 100, 100));
    assertNotNull(TacGrid.cellOf(h, 24, 100, 100));
  }

  @Test
  public void aClickOnAWallMovesToTheNearestStandingCellOrNothing() {
    VbMap h = map("hourfall");
    // (0,0) is wall deep in the corner: nothing to stand on within a cell
    assertNull(TacGrid.snap(h, 2, 2, 1.0));
    // the cell (10, 1) of the plan is the Sealer spawn pocket's floor; a click in the wall just left of it lands on it
    double[] s = TacGrid.snap(h, 9 * 4 + 3.9, 1 * 4 + 2, 1.0);
    assertNotNull(s);
    assertEquals(10 * 4 + 2, s[0], 1e-9);
    // a walkable cell keeps the exact point
    double[] same = TacGrid.snap(h, 20 * 4 + 1.3, 20 * 4 + 1.7, 1.0);
    assertEquals(81.3, same[0], 1e-9);
    assertEquals(81.7, same[1], 1e-9);
    // the high ground of Rootvault stands six blocks up, the floor none
    VbMap r = map("rootvault");
    int row = 8, col = 12; // an h cell in the plan
    assertEquals('h', r.plan().get(row).charAt(col));
    assertEquals(6, TacGrid.snap(r, (col + 0.5) * 4, (row + 0.5) * 4, 1.0)[2], 1e-9);
    assertEquals(0, TacGrid.snap(r, (13 + 0.5) * 4, (3 + 0.5) * 4, 1.0)[2], 1e-9);
    assertEquals(1, TacGrid.snap(map("stormbreak"), (2 + 0.5) * 5, (5 + 0.5) * 5, 1.0)[2], 1e-9);
  }

  @Test
  public void everyZoneOfEveryMapHasSomethingToPickAndEverySiteIsNamedByItsZone() {
    for (VbMap m : VbData.maps().values()) {
      int walkable = 0;
      for (int zone = 0; zone < 25; zone++) {
        boolean any = false;
        for (int gy = 0; gy < 5; gy++) for (int gx = 0; gx < 5; gx++) if (TacGrid.pick(m, zone, gx, gy, 1.0) != null) any = true;
        assertEquals(m.id() + " zone " + zone, any, TacGrid.zoneReachable(m, zone, 0, 0, 1e9));
        if (any) walkable++;
      }
      assertTrue(m.id(), walkable >= 8);
      java.util.List<String> all = new java.util.ArrayList<>();
      for (int zone = 0; zone < 25; zone++) all.addAll(TacGrid.features(m, zone));
      for (VbSite site : m.sites()) assertTrue(all.contains("Site " + site.id() + ": " + site.name()));
      assertTrue(all.contains("Seedbearer spawn") && all.contains("Sealer spawn"));
    }
  }

  @Test
  public void reachLimitsWhichZonesCanBePicked() {
    VbMap h = map("hourfall");
    assertTrue(TacGrid.zoneReachable(h, 12, 60, 60, 30));
    assertFalse(TacGrid.zoneReachable(h, 0, 60, 110, 30));
    assertTrue(TacGrid.zoneReachable(h, 22, 60, 110, 30));
  }
}
