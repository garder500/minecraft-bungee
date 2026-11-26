package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class MiniMapMathTest {
  private static VbMap map(String id) {
    return VbData.maps().get(id);
  }

  @Test
  public void aPointOnThePlanGoesFromZeroAtTheNorthWestCornerTo255AtTheSouthEast() {
    VbMap h = map("hourfall"); // 30 x 30 cells of 4 blocks
    assertArrayEquals(new int[] {0, 0}, MiniMapMath.project(h, 0, 0));
    assertArrayEquals(new int[] {128, 128}, MiniMapMath.project(h, 60, 60));
    assertArrayEquals(new int[] {255, 255}, MiniMapMath.project(h, 119.99, 119.99));
    VbMap s = map("stormbreak"); // 30 x 30 cells of 5 blocks: the same point of the plan is further in blocks
    assertArrayEquals(new int[] {128, 64}, MiniMapMath.project(s, 75, 37.5));
    assertNull(MiniMapMath.project(h, -0.5, 10));
    assertNull(MiniMapMath.project(h, 10, 120));
  }

  @Test
  public void facingAndHeadingAgreeWithMinecraftYaw() {
    assertArrayEquals(new double[] {0, 1}, MiniMapMath.facing(0), 1e-9); // south: down the picture
    assertArrayEquals(new double[] {-1, 0}, MiniMapMath.facing(90), 1e-9); // west
    assertEquals(0, MiniMapMath.heading(180)); // north: the arrow points up
    assertEquals(4, MiniMapMath.heading(270)); // east: right
    assertEquals(4, MiniMapMath.heading(-90));
    assertEquals(8, MiniMapMath.heading(0)); // south: down
    assertEquals(12, MiniMapMath.heading(90)); // west: left
    assertEquals(0, MiniMapMath.heading(180 + 11)); // rounds to the nearest of the 16 headings
    assertEquals(1, MiniMapMath.heading(180 + 12));
    assertEquals(0, MiniMapMath.heading(180 - 11 + 360 * 3));
  }

  @Test
  public void eachKindHasItsSpriteAndOnlyYouAreBig() {
    VbMap h = map("hourfall");
    List<MiniMapMath.Mark> marks = MiniMapMath.marks(h, List.of(
        new MiniMapMath.Marker(MiniMapMath.Kind.SEED, 20, 20, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.ENEMY, 80, 40, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.DEAD, 40, 80, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.ALLY, 60, 60, 0),
        new MiniMapMath.Marker(MiniMapMath.Kind.ALLY, 500, 60, 0), // off the plan: left out
        new MiniMapMath.Marker(MiniMapMath.Kind.SELF, 100, 100, 270)));
    assertEquals(List.of("mm_seed", "mm_enemy", "mm_dead", "mm_ally", "mm_self_4"), marks.stream().map(MiniMapMath.Mark::sprite).toList());
    assertEquals(List.of(false, false, false, false, true), marks.stream().map(MiniMapMath.Mark::big).toList());
    assertEquals(new MiniMapMath.Mark("mm_self_4", true, 213, 213), marks.get(4));
  }

  @Test
  public void everyShippedMapAndMarkerHasItsSprite() {
    VbGlyphs g = VbGlyphs.get();
    assertTrue(g.hasElement("minimap"));
    for (VbMap m : VbData.maps().values()) assertTrue(m.id(), g.hasSprite(MiniMapMath.baseSprite(m)));
    for (String s : List.of("mm_ally", "mm_dead", "mm_enemy", "mm_seed")) assertTrue(s, g.hasSprite(s));
    for (int k = 0; k < MiniMapMath.HEADINGS; k++) assertTrue(g.hasSprite("mm_self_" + k));
  }
}
