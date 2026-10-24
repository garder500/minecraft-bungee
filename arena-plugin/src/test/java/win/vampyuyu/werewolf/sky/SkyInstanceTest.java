package win.vampyuyu.werewolf.sky;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Slot allocation, and build/clear symmetry against a recording block sink. */
public class SkyInstanceTest {
  private ServerMock server;
  private final Map<String, Material> blocks = new HashMap<>();
  private final List<String> chunkTasks = new ArrayList<>();
  private SkyInstance sky;

  @Before
  public void boot() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld(SkyWorld.NAME);
    // Runs tasks inline but records which chunk each one belongs to.
    sky =
        new SkyInstance(
            world,
            (cx, cz, task) -> {
              chunkTasks.add(cx + "," + cz);
              task.run();
            },
            (x, y, z, m) -> {
              // A block may only be written by the task of its own chunk.
              assertEquals((x >> 4) + "," + (z >> 4), chunkTasks.get(chunkTasks.size() - 1));
              if (m == Material.AIR) blocks.remove(x + "," + y + "," + z);
              else blocks.put(x + "," + y + "," + z, m);
            },
            DecorConfig.loadDefault());
  }

  @After
  public void stop() {
    MockBukkit.unmock();
  }

  @Test
  public void slotsAreHandedOutAndReused() {
    SkyWorld w = new SkyWorld();
    SkySlot a = w.allocate(), b = w.allocate(), c = w.allocate();
    assertEquals(List.of(0, 1, 2), List.of(a.index(), b.index(), c.index()));
    w.release(b);
    assertFalse(w.inUse(b));
    assertEquals(1, w.allocate().index());
    w.release(a);
    w.release(a); // double release is harmless
    assertEquals(0, w.allocate().index());
    assertEquals(256, b.centreX());
    assertEquals(0, b.centreZ());
  }

  @Test
  public void allocatorRunsOutAtCapacity() {
    var small = new SlotAllocator(2);
    small.allocate();
    small.allocate();
    assertThrows(IllegalStateException.class, small::allocate);
    assertTrue(small.release(0));
    assertFalse(small.release(0));
    assertEquals(0, small.allocate());
    assertThrows(IllegalArgumentException.class, () -> new SkySlot(-1));
    assertThrows(IllegalArgumentException.class, () -> new SkySlot(SkySlot.MAX_SLOTS));
  }

  @Test
  public void buildThenClearLeavesNothing() {
    SkySlot slot = new SkySlot(3);
    assertTrue(sky.build(slot, 10).isDone());
    assertTrue(sky.isBuilt(slot));
    assertEquals(sky.placedBlocks(slot), blocks.size());
    assertEquals(Material.CAMPFIRE, blocks.get((3 * 256) + "," + (SkySlot.PLATFORM_Y + 1) + ",0"));
    Set<String> chunks = new HashSet<>(chunkTasks);
    assertEquals("exactly one task per chunk", chunkTasks.size(), chunks.size());
    assertTrue(chunks.size() > 1);
    for (String key : blocks.keySet()) assertTrue(key.split(",")[0].matches("-?\\d+"));

    assertTrue(sky.clear(slot).isDone());
    assertTrue(blocks.isEmpty());
    assertFalse(sky.isBuilt(slot));
    assertEquals(0, sky.placedBlocks(slot));
  }

  @Test
  public void clearTouchesOnlyItsOwnSlot() {
    SkySlot one = new SkySlot(0), two = new SkySlot(1);
    sky.build(one, 8);
    int afterOne = blocks.size();
    sky.build(two, 20);
    assertTrue(blocks.size() > afterOne);
    sky.clear(two);
    assertEquals(afterOne, blocks.size());
    sky.clear(one);
    assertTrue(blocks.isEmpty());
  }

  @Test
  public void doubleBuildAndBadCountsAreRefused() {
    SkySlot slot = new SkySlot(0);
    sky.build(slot, 6);
    int before = blocks.size();
    assertThrows(IllegalStateException.class, () -> sky.build(slot, 6));
    assertEquals(before, blocks.size());
    assertThrows(IllegalArgumentException.class, () -> sky.build(new SkySlot(1), 3));
    assertFalse(sky.isBuilt(new SkySlot(1)));
    assertTrue(sky.clear(new SkySlot(5)).isDone()); // nothing built: harmless
  }

  @Test
  public void seatsAreAboveTheFloorAndFaceTheFire() {
    SkySlot slot = new SkySlot(2);
    sky.build(slot, 9);
    List<Location> seats = sky.seatLocations(slot, 9);
    assertEquals(9, seats.size());
    for (Location l : seats) {
      assertEquals(SkySlot.PLATFORM_Y + 1, l.getY(), 0);
      assertEquals(Material.SPRUCE_PLANKS, blocks.get(l.getBlockX() + "," + (SkySlot.PLATFORM_Y) + "," + l.getBlockZ()));
      assertNull("seat is free of blocks", blocks.get(l.getBlockX() + "," + (SkySlot.PLATFORM_Y + 1) + "," + l.getBlockZ()));
    }
    Location spawn = sky.spawnPoint(slot, 9);
    assertEquals(SkySlot.PLATFORM_Y + 1, spawn.getY(), 0);
    assertNull(blocks.get(spawn.getBlockX() + "," + (SkySlot.PLATFORM_Y + 1) + "," + spawn.getBlockZ()));
  }
}
