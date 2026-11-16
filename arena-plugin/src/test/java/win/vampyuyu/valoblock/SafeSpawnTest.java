package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

public class SafeSpawnTest {
  private ServerMock server;

  @Before
  public void setUp() {
    server = MockBukkit.mock();
  }

  @After
  public void tearDown() {
    MockBukkit.unmock();
  }

  /** Solid blocks are the ones whose y is in the set; everything else is air. */
  private static Predicate<int[]> solidAt(Set<Integer> ys) {
    return c -> !ys.contains(c[1]);
  }

  @Test
  public void aFreeSpotStaysWhereItIs() {
    // floor at y=63, air from 64 up
    assertEquals(64, SafeSpawn.safeY(solidAt(Set.of(63)), 0, 64, 0, 0, 256));
    // floating in the open: nothing to fix, even without a floor right under
    assertEquals(80, SafeSpawn.safeY(solidAt(Set.of(63)), 0, 80, 0, 0, 256));
  }

  @Test
  public void aBuriedSpawnMovesUpToTheFirstFloorWithTwoAirBlocks() {
    // solid from 60 to 70, air from 71: the spawn at y=65 is inside the rock
    Predicate<int[]> p = c -> c[1] > 70 || c[1] < 0;
    assertEquals(71, SafeSpawn.safeY(p, 0, 65, 0, 0, 256));
  }

  @Test
  public void aOneBlockGapIsNotEnough() {
    // solid at 65..70 and 72, a single air block at 71 under the slab: the player needs two
    Predicate<int[]> p = c -> c[1] == 71 || c[1] > 72;
    assertEquals(73, SafeSpawn.safeY(p, 0, 66, 0, 0, 256));
  }

  @Test
  public void headInsideABlockCountsAsBuried() {
    // feet in air at 64, a ceiling block at 65
    Predicate<int[]> p = c -> c[1] == 64 || c[1] > 66;
    assertEquals(67, SafeSpawn.safeY(p, 0, 64, 0, 0, 256));
  }

  @Test
  public void nothingFreeAboveGivesUp() {
    assertEquals(-1, SafeSpawn.safeY(c -> false, 0, 10, 0, 0, 256));
  }

  @Test
  public void liftedUsesTheRealWorld() {
    WorldMock w = server.addSimpleWorld("vb_arena");
    for (int y = w.getMinHeight(); y <= 70; y++) w.getBlockAt(3, y, 4).setType(Material.STONE);
    Location buried = new Location(w, 3.5, 65, 4.5, 90f, 10f);
    Location out = SafeSpawn.lifted(w, buried);
    assertNotNull(out);
    assertEquals(71, out.getBlockY());
    assertEquals(3.5, out.getX(), 0);
    assertEquals(90f, out.getYaw(), 0);
    Location free = new Location(w, 8.5, 64, 8.5);
    assertSame(free, SafeSpawn.lifted(w, free));
  }

  @Test
  public void startStopAndStatusAreTheOnlyAdminCommandsInsideAnArena() {
    for (String ok : new String[] {"start", "stop", "status", "help"}) assertTrue(ok, VbCommand.allowedInsideArena(ok));
    for (String no : new String[] {"reload", "debug", "map", "use", "level", ""}) assertFalse(no, VbCommand.allowedInsideArena(no));
  }
}
