package win.vampyuyu.valoblock;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

public class TempBlocksTest {
  private ServerMock server;
  private World world;
  private final TempBlocks temp = new TempBlocks();

  @Before
  public void setUp() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("vb");
  }

  @After
  public void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  public void aBlockComesBackWhenTheLastHolderLetsGo() {
    Block b = world.getBlockAt(1, 64, 1);
    b.setType(Material.STONE);
    temp.set(b, Material.MAGMA_BLOCK);
    temp.set(b, Material.OAK_LOG); // a second effect on the same block keeps the first original
    assertEquals(Material.OAK_LOG, b.getType());
    temp.release(b);
    assertEquals(Material.OAK_LOG, b.getType());
    temp.release(b);
    assertEquals(Material.STONE, b.getType());
    assertEquals(0, temp.size());
  }

  @Test
  public void restoreAllPutsEverythingBack() {
    Block a = world.getBlockAt(0, 64, 0), c = world.getBlockAt(1, 64, 0);
    a.setType(Material.DIRT);
    temp.set(a, Material.MAGMA_BLOCK);
    temp.set(c, Material.PACKED_ICE);
    temp.restoreAll();
    assertEquals(Material.DIRT, a.getType());
    assertEquals(Material.AIR, c.getType());
    assertEquals(0, temp.size());
  }

  @Test
  public void aWallOnlyReplacesFreeBlocksAndShotsWearItDown() {
    Block solid = world.getBlockAt(2, 65, 0);
    solid.setType(Material.STONE);
    List<Block> cells = List.of(world.getBlockAt(0, 65, 0), world.getBlockAt(1, 65, 0), solid);
    TempBlocks.Wall w = temp.wall(cells, Material.OAK_LOG, 3);
    assertNotNull(w);
    assertEquals(2, w.blocks.size());
    assertEquals("a solid block is never replaced", Material.STONE, solid.getType());
    assertEquals(Material.OAK_LOG, world.getBlockAt(0, 65, 0).getType());
    assertNull("a block that is no wall", temp.hit(solid));
    assertNotNull(temp.hit(world.getBlockAt(0, 65, 0)));
    assertNotNull(temp.hit(world.getBlockAt(1, 65, 0)));
    assertFalse(w.gone);
    assertNotNull(temp.hit(world.getBlockAt(1, 65, 0))); // third hit: it shatters
    assertTrue(w.gone);
    assertEquals(Material.AIR, world.getBlockAt(0, 65, 0).getType());
    assertEquals(Material.AIR, world.getBlockAt(1, 65, 0).getType());
  }

  @Test
  public void aWallWithNoRoomIsRefused() {
    Block solid = world.getBlockAt(5, 65, 5);
    solid.setType(Material.STONE);
    assertNull(temp.wall(List.of(solid), Material.OAK_LOG, 3));
  }
}
