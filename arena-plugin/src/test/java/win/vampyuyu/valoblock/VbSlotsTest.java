package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.Test;

/** The slot volume covers every map's build, slots never overlap, and the dirty-slot file keeps what a forced stop leaves. */
public class VbSlotsTest {
  private static VbSlots.Box box(int slot) {
    int[] f = VbSlots.footprint(VbData.maps().values());
    VbMap any = VbData.maps().values().iterator().next();
    return VbSlots.box(slot, VbMapService.SPACING, VbMapService.FEET_Y, f[0], f[1], any.lowestY(), any.highestY());
  }

  @Test
  public void theSlotVolumeCoversEveryBlockOfEveryTheme() {
    for (int slot : new int[] {0, 1, 7, VbMapService.PREVIEW_SLOT}) {
      VbSlots.Box b = box(slot);
      for (String id : VbData.MAP_IDS) {
        int ox = slot * VbMapService.SPACING;
        int[] n = {0};
        VbThemes.canvas(VbData.maps().get(id)).emit((x, y, z, d) -> {
          if (!b.contains(ox + x, VbMapService.FEET_Y + y, z)) n[0]++;
        });
        assertEquals(id + " slot " + slot + ": blocks outside the wipe volume", 0, n[0]);
        VbMap m = VbData.maps().get(id);
        m.barrierBlocks((x, y, z, k) -> {
          if (!b.contains(ox + x, VbMapService.FEET_Y + y, z)) n[0]++;
        });
        assertEquals(id + " barriers", 0, n[0]);
      }
    }
  }

  @Test
  public void slotsNeverOverlapAndStayInTheWorldHeight() {
    assertTrue(VbSlots.disjoint(box(0), box(1)));
    assertTrue(VbSlots.disjoint(box(VbMapService.MAX_SLOTS - 1), box(VbMapService.PREVIEW_SLOT)));
    assertTrue(box(0).minY() >= -64 && box(0).maxY() < 320);
  }

  @Test
  public void chunkListsMatchTheBox() {
    VbSlots.Box b = new VbSlots.Box(-8, 0, -8, 40, 10, 20);
    assertEquals(4 * 3, b.chunks().size());
    assertEquals(3, b.sampleChunks().size());
    assertArrayEquals(new int[] {-1, -1}, b.chunks().get(0));
  }

  @Test
  public void dirtySlotsSurviveARestartAndClearWhenDone() throws Exception {
    Path dir = Files.createTempDirectory("dirty");
    Path f = dir.resolve("sub/dirty.txt");
    VbDirtySlots d = new VbDirtySlots(f);
    assertTrue(d.snapshot().isEmpty());
    assertTrue(d.mark(3));
    assertFalse(d.mark(3));
    assertTrue(d.mark(0));
    VbDirtySlots again = new VbDirtySlots(f); // the next start
    assertEquals(List.of(0, 3), again.snapshot());
    assertTrue(again.isDirty(3));
    assertTrue(again.clear(3));
    assertFalse(again.clear(3));
    assertEquals(List.of(0), new VbDirtySlots(f).snapshot());
    again.clear(0);
    assertFalse(Files.exists(f)); // nothing dirty: no file
  }

  @Test
  public void aDamagedDirtyFileIsReadAsFarAsPossible() throws Exception {
    Path f = Files.createTempFile("dirty", ".txt");
    Files.writeString(f, "2\nnonsense\n\n5\n");
    assertEquals(List.of(2, 5), new VbDirtySlots(f).snapshot());
  }
}
