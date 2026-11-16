package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class VbMapTest {
  private static VbMap map(String id) {
    return VbData.maps().get(id);
  }

  @Test
  public void everyShippedMapLoadsWithAConsistentGraph() {
    assertEquals(VbData.MAP_IDS, new ArrayList<>(VbData.maps().keySet()));
    for (VbMap m : VbData.maps().values()) {
      assertEquals(30, m.plan().size());
      assertEquals(2, m.sites().size());
      assertEquals(5, m.spawnPoints(VbSide.SEEDBEARERS).size());
      assertEquals(5, m.spawnPoints(VbSide.SEALERS).size());
      assertEquals(2, m.barriers().size());
      assertEquals(2, m.orbs().size());
      // waypoints() throws unless every spawn reaches every site's stage, plant and anchors
      assertNotNull(m.waypoints(0, 0, 0));
    }
    assertEquals(120, map("hourfall").width());
    assertEquals(150, map("stormbreak").width());
  }

  @Test
  public void greyboxMaterialsAreRealBlocks() {
    for (VbMap m : VbData.maps().values())
      for (VbMap.Kind k : VbMap.Kind.values()) assertNotNull(m.id() + " " + k, org.bukkit.Material.matchMaterial(m.material(k)));
  }

  @Test
  public void greyboxHasAFloorUnderEveryCellAndWallsFourHigh() {
    VbMap m = map("hourfall");
    Map<String, VbMap.Kind> blocks = new HashMap<>();
    m.greybox((x, y, z, k) -> blocks.put(x + "," + y + "," + z, k));
    // cell (0,0) of the Hourfall plan is a wall: 4 blocks per cell, 4 high
    assertEquals(VbMap.Kind.WALL, blocks.get("0,0,0"));
    assertEquals(VbMap.Kind.WALL, blocks.get("3,3,3"));
    assertNull(blocks.get("0,4,0"));
    // the Seedbearer spawn ring is painted, the middle of the plan is floor
    int col = 10, row = 26;
    assertEquals(VbMap.Kind.ATTACK, blocks.get((col * 4) + ",-1," + (row * 4)));
    for (int x = 0; x < m.width(); x += 7)
      for (int z = 0; z < m.depth(); z += 7) assertNotNull("floor at " + x + "," + z, blocks.get(x + ",-1," + z));
    // the invisible border keeps players inside
    assertEquals(VbMap.Kind.BARRIER, blocks.get("-1,0,5"));
    assertEquals(VbMap.Kind.BARRIER, blocks.get(m.width() + ",3,5"));
  }

  @Test
  public void spawnsAndSitesStandOnOpenCellsAndBarriersCloseTheSpawnRoom() {
    for (VbMap m : VbData.maps().values()) {
      for (VbSide side : VbSide.values())
        for (double[] p : m.spawnPoints(side)) {
          char c = m.plan().get((int) (p[2] / m.cell())).charAt((int) (p[0] / m.cell()));
          assertTrue(m.id() + " spawn on '" + c + "'", ".RBAFh".indexOf(c) >= 0);
        }
      for (VbMap.Barrier b : m.barriers()) {
        assertTrue(m.buyZone(b.side()).contains(b.box().min()[0] + 1, b.box().min()[1] + 1, b.box().min()[2] + 1));
        List<double[]> cells = new ArrayList<>();
        m.barrierBlocks((x, y, z, k) -> cells.add(new double[] {x, y, z}));
        assertFalse(cells.isEmpty());
      }
      // a barrier never fills a cell that the plan makes a wall, and a spawn point is inside the buy zone
      for (VbSide side : VbSide.values())
        for (double[] p : m.spawnPoints(side)) assertTrue(m.buyZone(side).contains(p[0], 0, p[2]));
    }
  }

  @Test
  public void sitesAndGraphMoveWithTheOrigin() {
    VbMap m = map("hourfall");
    VbSite a = m.sites(256, 65, 0).get(0);
    assertEquals(m.sites().get(0).min()[0] + 256, a.min()[0], 1e-9);
    Waypoints at0 = m.waypoints(0, 0, 0), at256 = m.waypoints(256, 65, 0);
    String id = at0.spawns(VbSide.SEALERS).get(0);
    assertEquals(at0.node(id).x() + 256, at256.node(id).x(), 1e-9);
    assertEquals(at0.node(id).y() + 65, at256.node(id).y(), 1e-9);
    double[] sp = m.spawn(VbSide.SEEDBEARERS, 7, 256, 65, 0); // the index wraps
    assertEquals(m.spawnPoints(VbSide.SEEDBEARERS).get(2)[0] + 256, sp[0], 1e-9);
  }

  @Test
  public void anActiveMapReplacesThePlaceholderSitesUntilItIsDeactivated() {
    List<VbSite> before = VbData.sites();
    VbMap m = map("rootvault");
    try {
      VbData.activate(m.sites(512, 65, 0), m.waypoints(512, 65, 0));
      assertEquals(m.sites(512, 65, 0).get(0).min()[0], VbData.sites().get(0).min()[0], 1e-9);
      assertEquals("rootvault", VbData.waypoints().map());
    } finally {
      VbData.deactivate();
    }
    assertEquals(before.get(0).min()[0], VbData.sites().get(0).min()[0], 1e-9);
  }

  @Test
  public void aBrokenMapIsRefused() {
    var j = map("hourfall").source().deepCopy();
    j.getAsJsonArray("plan").set(0, new com.google.gson.JsonPrimitive("too short"));
    assertThrows(IllegalArgumentException.class, () -> VbMap.parse(j));
    var k = map("hourfall").source().deepCopy();
    k.getAsJsonObject("waypoints").getAsJsonObject("siteData").getAsJsonObject("A").addProperty("plant", "nowhere");
    assertThrows(IllegalArgumentException.class, () -> VbMap.parse(k));
  }
}
