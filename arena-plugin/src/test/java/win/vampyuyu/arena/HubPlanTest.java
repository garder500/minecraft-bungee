package win.vampyuyu.arena;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.Test;
import win.vampyuyu.werewolf.WerewolfPreset;

/** The waiting hub plans: safe to walk (no way into the void), clear spawn, inside the arena, connected rails. */
public class HubPlanTest {
  @Test
  public void keysRoundTripNegativeCoordinates() {
    for (int[] c : new int[][] {{-40, -30, 17}, {0, 0, 0}, {39, 45, -40}, {-1, -1, -1}}) {
      long k = HubPlan.key(c[0], c[1], c[2]);
      assertEquals(c[0], HubPlan.kx(k));
      assertEquals(c[1], HubPlan.ky(k));
      assertEquals(c[2], HubPlan.kz(k));
    }
  }

  @Test
  public void panesFencesAndWallsConnectToTheirNeighbours() {
    HubPlan p = new HubPlan();
    p.set(0, 1, 0, Material.WHITE_STAINED_GLASS_PANE);
    p.set(1, 1, 0, Material.WHITE_STAINED_GLASS_PANE);
    p.set(0, 1, -1, Material.STONE);
    p.set(5, 1, 5, Material.SPRUCE_FENCE);
    Map<Long, HubPlan.Block> r = p.resolved();
    assertEquals("[north=true,south=false,west=false,east=true]", r.get(HubPlan.key(0, 1, 0)).state());
    assertEquals("[north=false,south=false,west=true,east=false]", r.get(HubPlan.key(1, 1, 0)).state());
    assertEquals("[north=false,south=false,west=false,east=false]", r.get(HubPlan.key(5, 1, 5)).state());
    assertNull("plain blocks keep their default state", r.get(HubPlan.key(0, 1, -1)).state());
  }

  @Test
  public void stagingDeckIsSafeAndFitsItsArena() {
    check("ValoBlock", StagingDeck.plan(), WaitingHubs.valoblockConfig(new JsonObject()), WaitingHubs.VALOBLOCK_Y);
  }

  @Test
  public void moonlitVillageIsSafeAndFitsItsArena() {
    check(
        "Werewolf",
        MoonlitVillage.plan(),
        WaitingHubs.werewolfConfig(new WerewolfPreset(null).defaultOptions()),
        WaitingHubs.WEREWOLF_Y);
  }

  @Test
  public void hubsAreSpectacularNotBoxes() {
    // a skyline: something tall stands well above the players, and something hangs well below the floor
    for (HubPlan p : new HubPlan[] {StagingDeck.plan(), MoonlitVillage.plan()}) {
      int top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
      for (long k : p.resolved().keySet()) {
        top = Math.max(top, HubPlan.ky(k));
        bottom = Math.min(bottom, HubPlan.ky(k));
      }
      assertTrue("tallest block y=" + top, top >= 20);
      assertTrue("deepest block y=" + bottom, bottom <= -12);
    }
  }

  @Test
  public void hubDisplaysAreTheBlockbenchModels() {
    for (var spec : WaitingHubs.valoblockDisplays())
      if (spec.item() != null) assertTrue(spec.item(), spec.item().startsWith("vb_"));
    for (var spec : WaitingHubs.werewolfDisplays())
      if (spec.item() != null) assertTrue(spec.item(), spec.item().startsWith("ww_"));
  }

  private static void check(String hub, HubPlan plan, ArenaConfig c, int floorPlusOne) {
    int fy = floorPlusOne - 1;
    var b = c.bounds();
    Map<Long, HubPlan.Block> blocks = plan.resolved();
    assertTrue(hub + ": " + blocks.size() + " blocks", blocks.size() < 40000);
    for (long k : blocks.keySet()) {
      double x = WaitingHubs.HUB_X + HubPlan.kx(k) + 0.5, y = fy + HubPlan.ky(k), z = WaitingHubs.HUB_Z + HubPlan.kz(k) + 0.5;
      assertTrue(hub + ": block outside the arena at " + HubPlan.kx(k) + "," + HubPlan.ky(k) + "," + HubPlan.kz(k),
          b.contains(x, y, z));
    }
    int sx = (int) Math.floor(c.spawn().x() - WaitingHubs.HUB_X);
    int sz = (int) Math.floor(c.spawn().z() - WaitingHubs.HUB_Z);
    int sl = (int) Math.floor(c.spawn().y()) - fy;
    assertEquals(hub + ": players stand on y = 1", 1, sl);
    assertTrue(hub + ": no floor under the spawn", support(plan.material(sx, 0, sz)));
    assertTrue(hub + ": spawn blocked", free(plan.material(sx, 1, sz)) && free(plan.material(sx, 2, sz)));
    // every standing place a player can walk, climb or drop to from the spawn has ground under it
    ArrayDeque<int[]> todo = new ArrayDeque<>();
    Set<Long> seen = new HashSet<>();
    todo.add(new int[] {sx, sl, sz});
    seen.add(HubPlan.key(sx, sl, sz));
    while (!todo.isEmpty()) {
      int[] at = todo.poll();
      int x = at[0], l = at[1], z = at[2];
      assertTrue(hub + ": walked out to " + x + "," + z, Math.abs(x) < WaitingHubs.HALF - 2 && Math.abs(z) < WaitingHubs.HALF - 2);
      for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        int nx = x + d[0], nz = z + d[1];
        int nl = -999;
        if (free(plan.material(nx, l, nz)) && free(plan.material(nx, l + 1, nz))) {
          nl = l;
          while (free(plan.material(nx, nl - 1, nz))) {
            nl--;
            assertTrue(hub + ": a player can step off into the void at " + nx + "," + nz + " (from " + x + "," + l + "," + z + ")",
                nl > -40);
          }
        } else if (climbable(plan.material(nx, l, nz))
            && free(plan.material(nx, l + 1, nz))
            && free(plan.material(nx, l + 2, nz))
            && free(plan.material(x, l + 2, z))) {
          nl = l + 1;
        }
        if (nl != -999 && seen.add(HubPlan.key(nx, nl, nz))) todo.add(new int[] {nx, nl, nz});
      }
    }
    assertTrue(hub + ": the walkable area is tiny (" + seen.size() + ")", seen.size() > 300);
  }

  /** A player's body passes through it (doors open: adventure players may use them). */
  static boolean free(Material m) {
    if (m.isAir() || m == Material.LIGHT) return true;
    String n = m.name();
    return n.endsWith("_DOOR") || n.equals("SHORT_GRASS") || n.equals("FERN") || n.equals("LILY_OF_THE_VALLEY")
        || n.equals("BLUE_ORCHID") || n.equals("FIREFLY_BUSH") || n.equals("HANGING_ROOTS") || n.endsWith("_CARPET");
  }

  static boolean support(Material m) {
    return !free(m);
  }

  /** A player can jump on top of it (fences, walls and panes are 1.5 high: no). */
  static boolean climbable(Material m) {
    return support(m) && HubPlan.family(m) == HubPlan.Family.NONE;
  }
}
