package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Links between waypoints against the blocks the themed build places, and the pruning that keeps every site reachable. */
public class LinkCheckTest {
  private static Waypoints.Node node(String id, double x, double y, double z) {
    return new Waypoints.Node(id, x, y, z);
  }

  @Test
  public void aWallOnTheLineBlocksAndAnOpenLineDoesNot() {
    Set<String> solid = new HashSet<>(List.of("5,1,0", "5,2,0"));
    LinkCheck.Solid s = (x, y, z) -> solid.contains(x + "," + y + "," + z);
    assertTrue(LinkCheck.blocked(node("a", 0.5, 0, 0.5), node("b", 9.5, 0, 0.5), s));
    assertFalse(LinkCheck.blocked(node("a", 0.5, 0, 2.5), node("b", 9.5, 0, 2.5), s));
    // the floor under the feet is not an obstacle
    LinkCheck.Solid floor = (x, y, z) -> y < 0;
    assertFalse(LinkCheck.blocked(node("a", 0.5, 0, 0.5), node("b", 9.5, 0, 0.5), floor));
  }

  @Test
  public void pruningNeverDisconnectsASpawnFromASite() {
    for (String id : VbData.MAP_IDS) {
      VbMap m = VbData.maps().get(id);
      VbCanvas c = VbThemes.canvas(m);
      Waypoints g = m.waypoints(0, 0, 0);
      int before = g.linkList().size();
      List<String> report = g.pruneLinks(l -> LinkCheck.blocked(g.node(l[0]), g.node(l[1]), c::solid));
      assertNull(id, g.unreachable());
      long dropped = report.stream().filter(r -> r.startsWith("dropped")).count();
      assertEquals(id, before - dropped, g.linkList().size());
      // after pruning no kept link is blocked unless it was reported as kept
      long stillBlocked = g.linkList().stream().filter(l -> LinkCheck.blocked(g.node(l[0]), g.node(l[1]), c::solid)).count();
      assertEquals(id, report.stream().filter(r -> r.startsWith("kept")).count(), stillBlocked);
      System.out.println("LINKDIAG " + id + " links=" + before + " dropped=" + dropped + " kept-blocked=" + stillBlocked + " " + report.stream().filter(r -> r.startsWith("kept")).toList());
      // which graph nodes sit inside a site box, and can both sides walk to them
      for (VbSite box : m.sites(0, 0, 0)) {
        for (Waypoints.Node n : g.nodes()) {
          if (n.x() < box.min()[0] || n.x() > box.max()[0] || n.z() < box.min()[2] || n.z() > box.max()[2]) continue;
          for (VbSide side : VbSide.values()) {
            boolean ok = false;
            for (String sp : g.spawns(side)) ok |= !g.path(sp, n.id(), Set.of()).isEmpty();
            if (!ok) System.out.println("LINKDIAG UNREACHABLE " + id + " site " + box.id() + " node " + n.id() + " y=" + n.y() + " from " + side);
          }
        }
      }
    }
  }
}
