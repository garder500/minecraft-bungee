package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import org.junit.Test;

public class VbMapEditorTest {
  private VbMapEditor editor() {
    return new VbMapEditor(VbData.maps().get("hourfall").source());
  }

  @Test
  public void editsAreAppliedToACopyAndStillParse() {
    VbMapEditor e = editor();
    e.spawn(VbSide.SEALERS, 1, 62.0, 0, 6.0);
    e.orb(1, 60, 0, 60);
    e.corner("site", "A", true, 30.7, 3, 45.2);
    e.corner("buy", "sealers", false, 40, -1, 0);
    VbMap m = e.check();
    assertEquals(62.0, m.spawnPoints(VbSide.SEALERS).get(0)[0], 1e-9);
    assertEquals(60.0, m.orbs().get(0)[0], 1e-9);
    assertEquals(30.0, m.sites().get(0).max()[0], 1e-9); // block corners are floored
    assertNotEquals("the shipped map is not touched", 62.0, VbData.maps().get("hourfall").spawnPoints(VbSide.SEALERS).get(0)[0], 1e-9);
  }

  @Test
  public void addsNodesLinksAndRemovesThemWithTheirLinks() {
    VbMapEditor e = editor();
    String spawn = VbData.maps().get("hourfall").source().getAsJsonObject("waypoints").getAsJsonObject("spawns").getAsJsonArray("SEALERS").get(0).getAsString();
    e.node("tunnel", 60, 0, 60);
    assertEquals("linked tunnel and " + spawn, e.link("tunnel", spawn));
    assertEquals("already linked", e.link(spawn, "tunnel"));
    assertThrows(IllegalArgumentException.class, () -> e.link("tunnel", "nope"));
    e.removeNode("tunnel");
    e.check();
    assertThrows(IllegalArgumentException.class, () -> e.removeNode("tunnel"));
  }

  @Test
  public void anEditThatBreaksTheMapIsRefusedByTheCheck() {
    VbMapEditor e = editor();
    String plant = VbData.maps().get("hourfall").source().getAsJsonObject("waypoints").getAsJsonObject("siteData").getAsJsonObject("A").get("plant").getAsString();
    e.removeNode(plant);
    assertThrows(IllegalArgumentException.class, e::check);
  }

  @Test
  public void badArgumentsAreExplained() {
    VbMapEditor e = editor();
    assertThrows(IllegalArgumentException.class, () -> e.spawn(VbSide.SEALERS, 9, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> e.corner("zone", "A", true, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> e.corner("site", "Z", true, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> e.corner("buy", "nobody", true, 0, 0, 0));
  }
}
