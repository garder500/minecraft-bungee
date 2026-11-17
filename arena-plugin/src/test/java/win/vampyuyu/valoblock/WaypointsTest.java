package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

public class WaypointsTest {
  private final Waypoints g = VbData.waypoints();

  private static JsonObject json(String s) {
    return JsonParser.parseString(s).getAsJsonObject();
  }

  private static final String BOXES = "";
  private static final List<VbSite> SITES = List.of(new VbSite("A", "Site A", new double[] {0, 0, 0}, new double[] {1, 1, 1}));

  private static String graph(String links, String extra) {
    return "{\"spawns\":{\"SEEDBEARERS\":[\"s\"],\"SEALERS\":[\"d\"]},"
        + "\"nodes\":[{\"id\":\"s\",\"pos\":[0,0,0]},{\"id\":\"m\",\"pos\":[10,0,0]},{\"id\":\"d\",\"pos\":[20,0,0]},{\"id\":\"p\",\"pos\":[10,0,10]}],"
        + "\"links\":" + links + ","
        + "\"siteData\":{\"A\":{\"weight\":1,\"anchors\":[\"p\"],\"stage\":\"m\",\"plant\":\"p\"" + extra + "}}}";
  }

  @Test
  public void theShippedGraphLoadsAndEverySiteHasData() {
    for (VbSite box : VbData.sites()) assertNotNull(g.site(box.id()));
    assertFalse(g.spawns(VbSide.SEEDBEARERS).isEmpty());
    assertFalse(g.spawns(VbSide.SEALERS).isEmpty());
  }

  @Test
  public void everySpawnReachesEverySiteNode() {
    for (VbSide side : VbSide.values())
      for (String spawn : g.spawns(side))
        for (Waypoints.SiteInfo s : g.sites()) {
          assertFalse(g.path(spawn, s.plant()).isEmpty());
          assertFalse(g.path(spawn, s.stage()).isEmpty());
          for (String a : s.anchors()) assertFalse(g.path(spawn, a).isEmpty());
        }
  }

  @Test
  public void aPathFollowsTheLinksAndStartsAndEndsAtTheNodes() {
    List<String> p = g.path("as1", "a_site");
    assertEquals("as1", p.get(0));
    assertEquals("a_site", p.get(p.size() - 1));
    assertTrue(p.contains("hub"));
    for (int i = 0; i + 1 < p.size(); i++) assertFalse(g.path(p.get(i), p.get(i + 1)).isEmpty());
  }

  @Test
  public void theShortestRouteIsChosen() {
    Waypoints w = Waypoints.parse(json(graph("[[\"s\",\"m\"],[\"m\",\"d\"],[\"m\",\"p\"],[\"s\",\"p\"]]", "")), SITES);
    assertEquals(List.of("s", "p"), w.path("s", "p")); // direct link (14.1) beats s-m-p (20)
    assertEquals(List.of("s", "m", "d"), w.path("s", "d"));
  }

  @Test
  public void routeStartsFromTheNearestNode() {
    List<double[]> r = g.route(-3, 65, -39, "a_site");
    assertEquals(g.node("as1").x(), r.get(0)[0], 0.001);
    assertEquals(g.node("a_site").z(), r.get(r.size() - 1)[2], 0.001);
  }

  @Test
  public void anUnreachableSiteNodeIsRefused() {
    try {
      Waypoints.parse(json(graph("[[\"s\",\"m\"],[\"m\",\"d\"]]", "")), SITES);
      fail("p is not linked");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("cannot be reached"));
    }
  }

  @Test
  public void aLinkToAnUnknownNodeIsRefused() {
    try {
      Waypoints.parse(json(graph("[[\"s\",\"zz\"]]", "")), SITES);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("zz"));
    }
  }

  @Test
  public void aSiteWithoutDataIsRefused() {
    List<VbSite> two = List.of(SITES.get(0), new VbSite("B", "B", new double[] {0, 0, 0}, new double[] {1, 1, 1}));
    try {
      Waypoints.parse(json(graph("[[\"s\",\"m\"],[\"m\",\"d\"],[\"m\",\"p\"]]", "")), two);
      fail();
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("site B"));
    }
  }

  @Test
  public void aGraphLinkedProperlyIsAccepted() {
    Waypoints w = Waypoints.parse(json(graph("[[\"s\",\"m\"],[\"m\",\"d\"],[\"m\",\"p\"]]", ",\"smokes\":[{\"node\":\"m\",\"at\":[1,2,3]}],\"peeks\":[\"p\"]")), SITES);
    assertEquals(1, w.site("A").smokes().size());
    assertEquals(List.of("p"), w.site("A").peeks());
  }

  @Test
  public void botsJsonIsValidAndTheDesignLevelsAreThere() {
    BotConfig c = VbData.bots();
    assertEquals("veteran", c.defaultLevel());
    assertEquals(450, c.level("recruit").reactionMs());
    assertEquals(300, c.level("veteran").reactionMs());
    assertEquals(200, c.level("elite").reactionMs());
    assertEquals(3900, c.fullBuyAt());
    assertEquals("smoke", c.kind("rot_cloud"));
    assertEquals("none", c.kind("unknown_ability"));
    try {
      c.level("god");
      fail();
    } catch (IllegalArgumentException expected) {
      // fine
    }
  }

  @Test
  public void everyAbilityNamedInBotsJsonExists() {
    for (String id : VbData.bots().abilityKinds().keySet())
      assertTrue(id, VbData.agents().all().stream().flatMap(a -> a.abilities().stream()).anyMatch(ab -> ab.id().equals(id)));
  }
}
