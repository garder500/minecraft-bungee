package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class MiniMapMathTest {
  private static VbMap map(String id) {
    return VbData.maps().get(id);
  }

  @Test
  public void theThirtyCellPlanTakesTwoPixelsACellInTheTopLeftCorner() {
    for (VbMap m : VbData.maps().values()) {
      assertEquals(2, MiniMapMath.cellPx(m));
      assertArrayEquals(new int[] {4, 4}, MiniMapMath.origin(m));
    }
  }

  @Test
  public void aPointProjectsToItsPixel() {
    VbMap h = map("hourfall"); // 4 blocks a cell = 2 blocks a pixel
    assertArrayEquals(new int[] {4, 4}, MiniMapMath.project(h, 0, 0));
    assertArrayEquals(new int[] {34, 63}, MiniMapMath.project(h, 60, 120 - 0.01));
    assertArrayEquals(new int[] {4 + 29, 4 + 14}, MiniMapMath.project(h, 59.9, 29.9));
    assertArrayEquals(new int[] {63, 63}, MiniMapMath.project(h, 119.9, 119.9));
    VbMap s = map("stormbreak"); // 5 blocks a cell: 2.5 blocks a pixel
    assertArrayEquals(new int[] {4 + 20, 4 + 40}, MiniMapMath.project(s, 50, 100));
    assertNull(MiniMapMath.project(h, -20, 0));
    assertNull(MiniMapMath.project(h, 0, 140));
  }

  @Test
  public void aYawFacesTheWayMinecraftDoes() {
    assertArrayEquals(new double[] {0, 1}, MiniMapMath.facing(0), 1e-9); // south: down the picture
    assertArrayEquals(new double[] {-1, 0}, MiniMapMath.facing(90), 1e-9); // west
    assertArrayEquals(new double[] {0, -1}, MiniMapMath.facing(180), 1e-9); // north
    assertArrayEquals(new double[] {1, 0}, MiniMapMath.facing(-90), 1e-9); // east
  }

  @Test
  public void theBaseLayerPaintsWallsFloorAndTheSiteDots() {
    VbMap h = map("hourfall");
    int[] base = MiniMapMath.base(h);
    assertEquals(MiniMapMath.SIZE * MiniMapMath.SIZE, base.length);
    assertEquals(MiniMapMath.CLEAR, base[0]); // outside the plan: transparent
    assertEquals(MiniMapMath.CLEAR, base[127 * 128 + 127]);
    assertEquals(MiniMapMath.EDGE, base[2 * 128 + 2]); // the frame round the plan
    assertEquals(MiniMapMath.WALL, base[4 * 128 + 4]); // cell (0,0) is wall
    int[] floor = MiniMapMath.project(h, 4 * 12 + 2, 4 * 1 + 2); // plain floor inside the Sealer spawn (12, 1)
    int at = base[floor[1] * 128 + floor[0]];
    assertTrue(at == MiniMapMath.FLOOR || at == MiniMapMath.FLOOR2);
    int[] spawn = MiniMapMath.project(h, 4 * 10 + 2, 4 * 1 + 2); // (10, 1) is the Sealer spawn's own colour
    assertEquals(MiniMapMath.DEFEND, base[spawn[1] * 128 + spawn[0]]);
    int[] siteB = MiniMapMath.project(h, 102, 34);
    boolean dots = false;
    for (int dx = -4; dx <= 4; dx++) dots |= base[siteB[1] * 128 + siteB[0] + dx] == MiniMapMath.DOT || base[(siteB[1] - 1) * 128 + siteB[0] + dx] == MiniMapMath.DOT;
    assertTrue(dots);
  }

  @Test
  public void markersAreDrawnOverTheBaseAndTheBaseIsLeftAlone() {
    VbMap h = map("hourfall");
    int[] base = MiniMapMath.base(h);
    int[] copy = base.clone();
    List<MiniMapMath.Marker> ms = List.of(
        new MiniMapMath.Marker(MiniMapMath.Kind.ALLY, 60, 60, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.DEAD, 40, 80, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.ENEMY, 80, 40, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.SEED, 20, 20, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.SELF, 100, 100, 0));
    int[] px = MiniMapMath.draw(h, base, ms);
    assertArrayEquals(copy, base);
    int[] a = MiniMapMath.project(h, 60, 60), d = MiniMapMath.project(h, 40, 80), e = MiniMapMath.project(h, 80, 40), s = MiniMapMath.project(h, 100, 100);
    assertEquals(MiniMapMath.ALLY, px[a[1] * 128 + a[0]]);
    assertEquals(MiniMapMath.ENEMY, px[e[1] * 128 + e[0]]);
    assertEquals(MiniMapMath.DEAD, px[(d[1] + 2) * 128 + d[0] + 2]); // the cross reaches its corners
    assertEquals(MiniMapMath.DEAD, px[(d[1] - 2) * 128 + d[0] + 2]);
    assertEquals(MiniMapMath.SELF, px[s[1] * 128 + s[0]]);
    assertEquals("the nose points south (yaw 0)", MiniMapMath.SELF, px[(s[1] + 3) * 128 + s[0]]);
    assertNotEquals(MiniMapMath.SELF, px[(s[1] - 3) * 128 + s[0]]);
  }

  @Test
  public void theFingerprintOnlyChangesWhenSomethingMovesEnoughToShow() {
    List<MiniMapMath.Marker> a = List.of(new MiniMapMath.Marker(MiniMapMath.Kind.SELF, 10.0, 10.0, 0));
    List<MiniMapMath.Marker> same = List.of(new MiniMapMath.Marker(MiniMapMath.Kind.SELF, 10.1, 10.1, 3));
    List<MiniMapMath.Marker> moved = List.of(new MiniMapMath.Marker(MiniMapMath.Kind.SELF, 12.0, 10.0, 0));
    List<MiniMapMath.Marker> turned = List.of(new MiniMapMath.Marker(MiniMapMath.Kind.SELF, 10.0, 10.0, 90));
    assertEquals(MiniMapMath.fingerprint(a), MiniMapMath.fingerprint(same));
    assertNotEquals(MiniMapMath.fingerprint(a), MiniMapMath.fingerprint(moved));
    assertNotEquals(MiniMapMath.fingerprint(a), MiniMapMath.fingerprint(turned));
  }
}
