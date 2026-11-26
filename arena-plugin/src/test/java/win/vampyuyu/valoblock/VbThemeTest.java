package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.*;
import org.bukkit.Material;
import org.junit.Test;

/** The themed builds: deterministic, real blocks, walkable from every graph node, lit, and the plan's heights respected. */
public class VbThemeTest {
  private static VbMap map(String id) {
    return VbData.maps().get(id);
  }

  private static final Map<String, VbCanvas> CACHE = new HashMap<>();

  private static VbCanvas canvas(String id) {
    return CACHE.computeIfAbsent(id, k -> VbThemes.canvas(map(k)));
  }

  @Test
  public void everyShippedMapHasAThemeAndTheBuildIsDeterministic() {
    for (String id : VbData.MAP_IDS) {
      assertTrue(id, VbThemes.hasTheme(id));
      StringBuilder a = new StringBuilder(), b = new StringBuilder();
      canvas(id).emit((x, y, z, d) -> a.append(x).append(',').append(y).append(',').append(z).append('=').append(d).append(';'));
      VbThemes.canvas(map(id)).emit((x, y, z, d) -> b.append(x).append(',').append(y).append(',').append(z).append('=').append(d).append(';'));
      assertEquals(id, a.toString(), b.toString());
      assertTrue(canvas(id).size() > 20000);
    }
  }

  @Test
  public void everyBlockIsARealMaterialInsideTheClearBox() {
    for (String id : VbData.MAP_IDS) {
      VbMap m = map(id);
      Set<String> bad = new TreeSet<>();
      canvas(id).emit((x, y, z, d) -> {
        if (Material.matchMaterial(VbCanvas.material(d)) == null) bad.add(d);
        boolean play = x >= -1 && x <= m.width() && z >= -1 && z <= m.depth();
        // inside the border: the clear box; beyond it (or under the floor): the scenery box, never on the border line itself
        // (scenery may also hang high above the play: clouds, the World Tree's crown)
        if (play && y >= -1 && y < SceneryKit.ABOVE_PLAY) assertTrue(id + " " + x + "," + y + "," + z, y <= m.highestY());
        else {
          int s = VbMap.SCENERY_MARGIN;
          assertTrue(id + " scenery " + x + "," + y + "," + z, x >= -s && x < m.width() + s && z >= -s && z < m.depth() + s && y >= VbMap.SCENERY_LOW_Y && y <= VbMap.SCENERY_HIGH_Y);
          if (y >= 0 && y < SceneryKit.ABOVE_PLAY && !d.equals("barrier")) assertTrue(id + " scenery touches the border at " + x + "," + y + "," + z, x < -2 || x > m.width() + 1 || z < -2 || z > m.depth() + 1);
        }
        if (d.contains("leaves")) assertTrue("leaves must be persistent: " + d, d.contains("persistent=true"));
      });
      assertTrue(id + " unknown: " + bad, bad.isEmpty());
    }
  }

  @Test
  public void everyGraphNodeIsWalkableWithAFloorAndHeadroom() {
    for (String id : VbData.MAP_IDS) {
      VbMap m = map(id);
      VbPlan p = new VbPlan(m);
      VbCanvas c = canvas(id);
      for (Waypoints.Node n : m.waypoints(0, 0, 0).nodes()) {
        int col = p.col((int) Math.floor(n.x())), row = p.col((int) Math.floor(n.z()));
        assertEquals(id + " " + n.id() + " stands at the height of its cell", m.standY(p.at(col, row)), n.y(), 1e-9);
        int y = (int) Math.floor(n.y());
        for (double dx : new double[] {-0.3, 0.3})
          for (double dz : new double[] {-0.3, 0.3}) {
            int bx = (int) Math.floor(n.x() + dx), bz = (int) Math.floor(n.z() + dz);
            String floor = c.get(bx, y - 1, bz);
            assertTrue(id + " " + n.id() + " floor " + floor, floor != null && !VbCanvas.passable(floor) && !floor.equals("water"));
            for (int h = 0; h < 3; h++) assertFalse(id + " " + n.id() + " blocked at +" + h + ": " + c.get(bx, y + h, bz), c.solid(bx, y + h, bz));
          }
      }
    }
  }

  @Test
  public void straightLinksBetweenCellsOnTheSameLevelAreClear() {
    for (String id : VbData.MAP_IDS) {
      VbMap m = map(id);
      VbPlan p = new VbPlan(m);
      VbCanvas c = canvas(id);
      for (int r = 0; r < p.rows; r++)
        for (int col = 0; col < p.cols; col++)
          for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
            int nc = col + d[0], nr = r + d[1];
            if (!p.walk(col, r) || !p.walk(nc, nr) || m.standY(p.at(col, r)) != m.standY(p.at(nc, nr))) continue;
            int y = m.standY(p.at(col, r));
            double ax = (col + 0.5) * p.cell, az = (r + 0.5) * p.cell, bx = (nc + 0.5) * p.cell, bz = (nr + 0.5) * p.cell;
            for (int i = 0; i <= 20; i++) {
              double x = ax + (bx - ax) * i / 20, z = az + (bz - az) * i / 20;
              int blockX = (int) Math.floor(x), blockZ = (int) Math.floor(z);
              assertFalse(id + " floor on the link " + col + "," + r + " to " + nc + "," + nr, !c.solid(blockX, y - 1, blockZ));
              for (int h = 0; h < 3; h++)
                assertFalse(id + " link " + col + "," + r + " to " + nc + "," + nr + " blocked at " + blockX + "," + (y + h) + "," + blockZ + ": " + c.get(blockX, y + h, blockZ), c.solid(blockX, y + h, blockZ));
            }
          }
    }
  }

  @Test
  public void coveredPlacesAreLitSoNothingIsPitchDark() {
    for (String id : VbData.MAP_IDS) {
      VbMap m = map(id);
      VbCanvas c = canvas(id);
      List<int[]> lights = new ArrayList<>();
      c.emit((x, y, z, d) -> {
        if (VbThemes.LIGHTS.contains(VbCanvas.material(d))) lights.add(new int[] {x, y, z});
      });
      int covered = 0;
      for (Waypoints.Node n : m.waypoints(0, 0, 0).nodes()) {
        int x = (int) Math.floor(n.x()), z = (int) Math.floor(n.z()), y = (int) Math.floor(n.y());
        boolean under = false;
        for (int h = 3; h <= m.highestY() - y; h++) under |= c.solid(x, y + h, z) || (c.get(x, y + h, z) != null && c.get(x, y + h, z).contains("leaves"));
        if (!under) continue;
        covered++;
        boolean lit = false;
        for (int[] l : lights) lit |= Math.abs(l[0] - x) <= 7 && Math.abs(l[2] - z) <= 7 && l[1] >= y - 2 && l[1] <= y + 9;
        assertTrue(id + " " + n.id() + " is under a roof with no light near", lit);
      }
      if (id.equals("hourfall")) assertTrue("the halls and the vault are roofed", covered > 20);
    }
  }

  @Test
  public void wallsAreFourToSixHighWithASkylineAndNeverTouchTheBorder() {
    for (String id : VbData.MAP_IDS) {
      VbMap m = map(id);
      VbPlan p = new VbPlan(m);
      Set<Integer> heights = new TreeSet<>();
      for (int r = 0; r < p.rows; r++)
        for (int col = 0; col < p.cols; col++)
          if (p.wall(col, r)) {
            int top = p.wallTop(col, r);
            assertTrue(id + " wall " + top, top >= 4 && top <= 6);
            heights.add(top);
          }
      assertEquals(id, Set.of(4, 5, 6), heights);
    }
  }

  @Test
  public void hourfallHasOpenGatesArchesAndTwoLaddersUpTheHourglass() {
    VbMap m = map("hourfall");
    VbPlan p = new VbPlan(m);
    VbCanvas c = canvas("hourfall");
    for (int[] g : TimeTide.gates(p)) {
      for (int dx = 0; dx < p.cell; dx++)
        for (int dz = 0; dz < p.cell; dz++) {
          for (int y = 0; y < TimeTide.GATE_HEIGHT; y++) assertFalse("gate open", c.solid(g[0] * p.cell + dx, y, g[1] * p.cell + dz));
          assertTrue("arch over the gate", c.solid(g[0] * p.cell + dx, 4, g[1] * p.cell + dz));
        }
    }
    List<List<int[]>> towers = p.groups('h');
    assertFalse(towers.isEmpty());
    for (List<int[]> g : towers) {
      List<int[]> spots = p.ladderSpots(g, 2);
      assertEquals(2, spots.size());
      for (int[] s : spots) {
        int x = s[2] > 0 ? s[0] * p.cell + p.cell - 1 : s[0] * p.cell, z = s[3] > 0 ? s[1] * p.cell + p.cell - 1 : s[1] * p.cell;
        for (int y = 0; y < m.highY(); y++) assertNotNull("ladder at " + y, c.get(x, y, z));
        assertTrue(c.get(x, 0, z).startsWith("ladder"));
      }
    }
    int sand = 0, gold = 0, banners = 0, lanterns = 0, inlay = 0;
    for (String d : playBlocks(c, m)) {
      String mat = VbCanvas.material(d);
      if (mat.equals("SAND")) sand++;
      if (mat.equals("GOLD_BLOCK")) gold++;
      if (mat.endsWith("_WALL_BANNER")) banners++;
      if (mat.equals("LANTERN")) lanterns++;
      if (mat.equals("LIGHT_BLUE_TERRACOTTA") || mat.equals("CYAN_TERRACOTTA")) inlay++;
    }
    assertTrue(sand > 20 && banners > 5 && lanterns > 10 && inlay > 50);
    assertTrue("gold stays sparing: " + gold, gold > 0 && gold < 150);
  }

  @Test
  public void rootvaultHasCanopyAtPlusSixWithRailingsLaddersAndRootHoles() {
    VbMap m = map("rootvault");
    VbPlan p = new VbPlan(m);
    VbCanvas c = canvas("rootvault");
    assertEquals(6, m.highY());
    for (int[] h : p.cellsOf('h')) assertTrue("deck at " + h[0] + "," + h[1], c.solid(h[0] * p.cell + 1, 5, h[1] * p.cell + 1));
    int fences = 0, ladders = 0, leaves = 0, roots = 0;
    for (String d : blocks(c)) {
      String mat = VbCanvas.material(d);
      if (mat.equals("OAK_FENCE")) fences++;
      if (mat.equals("LADDER")) ladders++;
      if (mat.contains("AZALEA_LEAVES")) leaves++;
      if (mat.equals("MANGROVE_ROOTS") || mat.equals("HANGING_ROOTS")) roots++;
    }
    assertTrue(fences > 40 && ladders >= 4 * 6 && leaves > 500 && roots > 20);
    for (RootDrops.Root r : RootDrops.roots(p)) {
      int cx = (int) ((r.minX() + r.maxX()) / 2), cz = (int) ((r.minZ() + r.maxZ()) / 2);
      assertFalse("the hole is open above the cell", c.solid(cx, 5, cz) || c.solid(cx - 1, 5, cz - 1));
      for (int y = 0; y < 5; y++) assertFalse(c.solid(cx, y, cz));
    }
    for (int[] f : p.cellsOf('F'))
      for (int[] b : RootDrops.branch(p, f)) assertTrue("branch deck", c.solid(b[0] * p.cell, 5, b[1] * p.cell));
  }

  @Test
  public void stormbreakHasASeaToKillInIslandsPadsAndAChainOfThemedLandmarks() {
    VbMap m = map("stormbreak");
    VbPlan p = new VbPlan(m);
    VbCanvas c = canvas("stormbreak");
    for (int r = 0; r < p.rows; r++)
      for (int col = 0; col < p.cols; col++)
        if (p.sea(col, r)) for (int y = -4; y <= -2; y++) assertEquals("water", c.get(col * p.cell + 2, y, r * p.cell + 2));
    for (WindPads.Pad pad : WindPads.pads(p)) assertEquals("sea_lantern", c.get(pad.col() * p.cell + 2, -1, pad.row() * p.cell + 2));
    Map<String, Integer> count = new HashMap<>();
    for (String d : blocks(c)) count.merge(VbCanvas.material(d), 1, Integer::sum);
    for (String want : List.of("DEEPSLATE_BRICKS", "TUFF", "OXIDIZED_CUT_COPPER", "WAXED_OXIDIZED_COPPER", "TINTED_GLASS", "BLUE_STAINED_GLASS", "DARK_OAK_PLANKS", "CALCITE", "LIGHTNING_ROD", "WATER"))
      assertTrue(want, count.getOrDefault(want, 0) > 0);
    // the Lighthouse stands taller than any cliff, the Eyrie roost too
    assertTrue(c.solid(1 * p.cell + 2, 14, 4 * p.cell + 2));
    assertTrue(c.solid(14 * p.cell + 2, 9, 12 * p.cell + 2));
  }

  private static List<String> blocks(VbCanvas c) {
    List<String> out = new ArrayList<>();
    c.emit((x, y, z, d) -> out.add(d));
    return out;
  }

  /** The blocks inside the border (the scenery beyond it is counted apart). */
  private static List<String> playBlocks(VbCanvas c, VbMap m) {
    List<String> out = new ArrayList<>();
    c.emit((x, y, z, d) -> {
      if (x >= 0 && x < m.width() && z >= 0 && z < m.depth() && y >= -1) out.add(d);
    });
    return out;
  }

  @Test
  public void everySceneryStandsBeyondTheBorderWithItsLandmarksAndModels() {
    Map<String, Set<String>> models = Map.of(
        "hourfall", Set.of("hourfall_hourglass", "hourfall_ring", "hourfall_scarab"),
        "rootvault", Set.of("rootvault_heartseed", "rootvault_sporebloom"),
        "stormbreak", Set.of("stormbreak_beam", "stormbreak_mast", "stormbreak_stormcore"));
    for (String id : VbData.MAP_IDS) {
      VbMap m = map(id);
      VbCanvas c = canvas(id);
      int[] count = new int[3];
      c.emit((x, y, z, d) -> {
        boolean out = x < -1 || x > m.width() || z < -1 || z > m.depth();
        if (out) count[0]++;
        if (y >= 20) count[1]++;
        if (!out && y < -2) count[2]++;
      });
      assertTrue(id + ": scenery around the map: " + count[0], count[0] > 50_000);
      assertTrue(id + ": landmarks rise high: " + count[1], count[1] > 2_000);
      assertTrue(id + ": the island goes down under the map: " + count[2], count[2] > 5_000);
      assertTrue(id + ": the whole build stays a few hundred thousand blocks: " + c.size(), c.size() < 500_000);
      Set<String> ids = new TreeSet<>();
      for (VbCanvas.Model md : c.models()) {
        ids.add(md.id());
        // set pieces hang above the high ground or stand beyond the border: never in a line of fire
        boolean out = md.x() < -1 || md.x() > m.width() || md.z() < -1 || md.z() > m.depth();
        assertTrue(id + " " + md.id() + " above the play", out || md.y() >= m.highY() + 8);
      }
      assertEquals(id, models.get(id), ids);
    }
  }

}
