package win.vampyuyu.werewolf.sky;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** The platform life cycle of a game: slots are taken on start and given back on stop. */
public class SkyStageTest {
  private ServerMock server;
  private final Map<String, Material> blocks = new HashMap<>();
  private SkyWorld sky;
  private SkyStage stage;

  @Before
  public void boot() {
    server = MockBukkit.mock();
    var world = server.addSimpleWorld(SkyWorld.NAME);
    var instance =
        new SkyInstance(
            world,
            (cx, cz, task) -> task.run(),
            (x, y, z, m) -> {
              if (m == Material.AIR) blocks.remove(x + "," + y + "," + z);
              else blocks.put(x + "," + y + "," + z, m);
            },
            DecorConfig.loadDefault());
    sky = new SkyWorld();
    stage = new SkyStage(sky, () -> instance);
  }

  @After
  public void stop() {
    MockBukkit.unmock();
  }

  @Test
  public void startTakesASlotAndStopFreesItAndTheBlocks() {
    AtomicReference<PlatformHandle> remembered = new AtomicReference<>();
    PlatformHandle handle = stage.open(8, remembered::set).join();
    assertSame(handle, remembered.get());
    assertTrue(sky.inUse(handle.slot()));
    assertFalse(blocks.isEmpty());
    assertEquals(8, handle.seats().size());
    stage.close(handle).join();
    assertFalse(sky.inUse(handle.slot()));
    assertTrue("the platform is gone", blocks.isEmpty());
    // A second stop is harmless.
    stage.close(handle).join();
    stage.close(null).join();
  }

  @Test
  public void concurrentGamesGetDifferentSlotsAndSeparatePlatforms() {
    List<PlatformHandle> games = new ArrayList<>();
    for (int n : new int[] {4, 12, 20, 45}) games.add(stage.open(n, h -> {}).join());
    long distinct = games.stream().map(h -> h.slot().index()).distinct().count();
    assertEquals(games.size(), distinct);
    // Their platforms do not overlap: every seat stays within its own slot's span.
    for (var g : games)
      for (var seat : g.seats())
        assertTrue(Math.abs(seat.getX() - g.slot().centreX()) < SkySlot.SPACING / 2.0);
    int before = blocks.size();
    stage.close(games.get(1)).join();
    assertTrue(blocks.size() < before);
    // The freed slot is the next one handed out.
    PlatformHandle again = stage.open(6, h -> {}).join();
    assertEquals(games.get(1).slot(), again.slot());
    for (var g : games) stage.close(g).join();
    stage.close(again).join();
    assertTrue(blocks.isEmpty());
  }

  @Test
  public void everySizeFrom4To45HasOneSeatPerPlayer() {
    for (int n = 4; n <= 45; n++) {
      PlatformHandle h = stage.open(n, x -> {}).join();
      assertEquals(n, h.seats().size());
      assertNull(stage.problem(n));
      stage.close(h).join();
    }
    assertTrue(blocks.isEmpty());
  }

  @Test
  public void aFailedBuildStillLetsTheGameCloseTheSlot() {
    var failing =
        new SkyInstance(
            server.getWorld(SkyWorld.NAME),
            (cx, cz, task) -> task.run(),
            (x, y, z, m) -> {
              if (m != Material.AIR) throw new IllegalStateException("boom");
            },
            DecorConfig.loadDefault());
    SkyStage bad = new SkyStage(sky, () -> failing);
    AtomicReference<PlatformHandle> remembered = new AtomicReference<>();
    CompletableFuture<PlatformHandle> opened = bad.open(6, remembered::set);
    assertTrue(opened.isCompletedExceptionally());
    assertNotNull("the game can still close it", remembered.get());
    assertTrue(sky.inUse(remembered.get().slot()));
    bad.close(remembered.get()).join();
    assertFalse(sky.inUse(remembered.get().slot()));
  }

  @Test
  public void aSlotThatCouldNotBeClearedStaysTaken() {
    boolean[] failClear = {false};
    var instance =
        new SkyInstance(
            server.getWorld(SkyWorld.NAME),
            (cx, cz, task) -> task.run(),
            (x, y, z, m) -> {
              if (m == Material.AIR && failClear[0]) throw new IllegalStateException("boom");
            },
            DecorConfig.loadDefault());
    SkyStage flaky = new SkyStage(sky, () -> instance);
    PlatformHandle h = flaky.open(6, x -> {}).join();
    failClear[0] = true;
    assertTrue(flaky.close(h).isCompletedExceptionally());
    assertTrue("reusing it would build over stale blocks", sky.inUse(h.slot()));
    assertNotEquals(h.slot(), flaky.open(6, x -> {}).join().slot());
  }

  @Test
  public void aPlatformCoversItsOwnAirOnly() {
    PlatformHandle h = stage.open(8, x -> {}).join();
    var at = h.seats().get(0);
    assertTrue(h.covers(at));
    assertFalse(h.covers(at.clone().add(0, 100, 0)));
    assertFalse(h.covers(at.clone().add(SkySlot.SPACING, 0, 0)));
  }

  @Test
  public void refusesSizesOutsideTheTableRangeAndAMissingWorld() {
    assertNotNull(stage.problem(3));
    assertNotNull(stage.problem(46));
    assertNull(stage.problem(4));
    MockBukkit.unmock();
    MockBukkit.mock();
    assertNotNull("no ww_sky world loaded", new SkyStage(new SkyWorld(), () -> null).problem(8));
  }
}
