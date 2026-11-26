package win.vampyuyu.werewolf.sky;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Decor parsing, validation and generation. A mocked server is booted so Material is fully usable. */
public class DecorConfigTest {
  private static final String MINIMAL =
      "{\"floor\":{\"inner\":\"oak_planks\",\"outer\":\"stone\"},\"campfire\":{\"block\":\"minecraft:campfire\"}}";

  @Before
  public void boot() {
    MockBukkit.mock();
  }

  @After
  public void stop() {
    MockBukkit.unmock();
  }

  @Test
  public void shippedDecorFitsEverySize() {
    DecorConfig decor = DecorConfig.loadDefault();
    for (int n = PlatformLayout.MIN_PLAYERS; n <= PlatformLayout.MAX_PLAYERS; n++) {
      List<DecorConfig.Placement> blocks = decor.generate(n);
      assertTrue(blocks.size() <= decor.maxBlocks());
      Set<String> cells = new HashSet<>();
      for (var b : blocks) assertTrue("duplicate cell for " + n, cells.add(b.dx() + "," + b.dy() + "," + b.dz()));
    }
  }

  @Test
  public void decorHasTheExpectedParts() {
    int n = 12;
    var layout = PlatformLayout.of(n);
    var blocks = DecorConfig.loadDefault().generate(n);
    // One campfire on the floor, in the middle; the others smoke on top of the chimneys.
    var floorFires = blocks.stream().filter(b -> b.material() == Material.CAMPFIRE && b.dy() <= 2).toList();
    assertEquals(1, floorFires.size());
    var fire = floorFires.get(0);
    assertEquals(0, fire.dx());
    assertEquals(1, fire.dy());
    assertEquals(0, fire.dz());
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.LANTERN));
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.SPRUCE_FENCE));
    assertTrue(blocks.stream().anyMatch(b -> b.dy() < 0));
    // One bench log behind every seat.
    long logs = blocks.stream().filter(b -> b.material() == Material.SPRUCE_LOG || b.material() == Material.STRIPPED_SPRUCE_LOG).count();
    assertEquals(n, logs);
    // The keel tapers under the whole island: it never reaches past the village rim (16 wide).
    for (var b : blocks.stream().filter(b -> b.dy() < 0).toList())
      assertTrue(Math.hypot(b.dx(), b.dz()) <= layout.platformRadius() + 16);
    // The hamlet: half-timbered houses (plaster, slate stair roofs, lit windows) and trees around the platform.
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.DEEPSLATE_TILE_STAIRS && b.state().startsWith("facing=")));
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.CALCITE));
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.REDSTONE_LAMP && "lit=true".equals(b.state())));
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.OAK_LEAVES && "persistent=true".equals(b.state())));
    // No seat stands inside a block: the cell above the floor at each seat is free.
    Set<String> solid = new HashSet<>();
    for (var b : blocks) if (b.dy() >= 1) solid.add(b.dx() + "," + b.dz());
    for (var s : layout.seats()) assertFalse(solid.contains(Math.round(s.x()) + "," + Math.round(s.z())));
  }

  @Test
  public void seatsAreNeverBuiltOver() {
    DecorConfig decor = DecorConfig.loadDefault();
    for (int n = PlatformLayout.MIN_PLAYERS; n <= PlatformLayout.MAX_PLAYERS; n++) {
      Set<String> solid = new HashSet<>();
      for (var b : decor.generate(n)) if (b.dy() >= 1) solid.add(b.dx() + "," + b.dz());
      for (var s : PlatformLayout.of(n).seats())
        assertFalse("seat blocked for " + n, solid.contains(Math.round(s.x()) + "," + Math.round(s.z())));
      long logs =
          decor.generate(n).stream()
              .filter(b -> b.material() == Material.SPRUCE_LOG || b.material() == Material.STRIPPED_SPRUCE_LOG)
              .count();
      assertEquals("one bench per seat for " + n, n, logs);
    }
  }

  @Test
  public void nothingStandsBetweenTheSeats() {
    // Inside the seat ring only the floor and the campfire: a vote across the fire is never hidden.
    DecorConfig decor = DecorConfig.loadDefault();
    for (int n : new int[] {4, 12, 45}) {
      double ring = PlatformLayout.of(n).ringRadius();
      for (var b : decor.generate(n))
        if (b.dy() >= 1 && Math.hypot(b.dx(), b.dz()) < ring + 0.5)
          assertTrue(n + " players: " + b, b.dx() == 0 && b.dz() == 0 && b.material() == Material.CAMPFIRE);
    }
  }

  @Test
  public void blockStatesAreWellFormed() {
    // MockBukkit does not know every property (lantern hanging...), so only the shape is checked here;
    // the real server places a block whose state it refuses without the state, and logs it once.
    for (int n : new int[] {4, 12, 45})
      for (var b : DecorConfig.loadDefault().generate(n))
        if (b.state() != null) assertTrue(b.toString(), b.state().matches("[a-z_]+=[a-z0-9_]+(,[a-z_]+=[a-z0-9_]+)*"));
  }

  @Test
  public void landmarksAndModels() {
    DecorConfig decor = DecorConfig.loadDefault();
    var items = decor.models(12).stream().map(DecorConfig.Model::item).toList();
    assertEquals(List.of("moon", "belfry_crown_bell", "belfry_crown_vane", "mill_sails"), items);
    // The belfry's spire is the highest block; the island hangs deep under it.
    var blocks = decor.generate(12);
    assertTrue(blocks.stream().mapToInt(DecorConfig.Placement::dy).max().orElse(0) >= DecorConfig.BELFRY_TIP);
    assertTrue(blocks.stream().mapToInt(DecorConfig.Placement::dy).min().orElse(0) <= -15);
    // Models can be turned off, and an old override without the new keys still parses (no models, solid underside).
    assertTrue(DecorConfig.parse(MINIMAL).models(12).isEmpty());
  }

  @Test
  public void theKeelHasFangsRootsAndChains() {
    for (int n : new int[] {4, 12, 45}) {
      var blocks = DecorConfig.loadDefault().generate(n);
      assertTrue(n + " fangs", blocks.stream().filter(b -> b.material() == Material.POINTED_DRIPSTONE).count() >= 4);
      assertTrue(n + " roots", blocks.stream().filter(b -> b.material() == Material.HANGING_ROOTS).count() >= 10);
      assertTrue(n + " chains", blocks.stream().anyMatch(b -> b.material() == Material.IRON_CHAIN));
      // Every fang tip hangs under a block (it is attached to the keel).
      Set<String> cells = new HashSet<>();
      for (var b : blocks) cells.add(b.dx() + "," + b.dy() + "," + b.dz());
      for (var b : blocks)
        if (b.material() == Material.POINTED_DRIPSTONE) assertTrue(cells.contains(b.dx() + "," + (b.dy() + 1) + "," + b.dz()));
    }
  }

  @Test
  public void theFirstShippedDecorStillParses() {
    // A production override may still be the first default.json: it parses, fits the cap, and gets the landmarks.
    String old =
        "{\"version\":1,\"maxBlocks\":18000,\"floor\":{\"inner\":\"spruce_planks\",\"outer\":\"stone_bricks\",\"innerMargin\":2.5},"
            + "\"campfire\":{\"block\":\"campfire\"},\"bench\":{\"blocks\":[\"stripped_spruce_log\",\"spruce_log\"]},"
            + "\"railing\":{\"block\":\"spruce_fence\"},\"lanterns\":{\"block\":\"lantern\",\"everySeats\":2},"
            + "\"underside\":{\"blocks\":[\"stone\",\"andesite\",\"cobblestone\",\"mossy_cobblestone\",\"tuff\"],"
            + "\"depthFactor\":0.6,\"minDepth\":4,\"maxDepth\":14},\"village\":{\"width\":16}}";
    DecorConfig decor = DecorConfig.parse(old);
    for (int n = PlatformLayout.MIN_PLAYERS; n <= PlatformLayout.MAX_PLAYERS; n++)
      assertTrue(decor.generate(n).size() <= decor.maxBlocks());
    assertEquals(4, decor.models(12).size());
  }

  @Test
  public void blockBudget() {
    DecorConfig decor = DecorConfig.loadDefault();
    StringBuilder sizes = new StringBuilder();
    for (int n : new int[] {4, 8, 12, 20, 30, 45}) sizes.append(n).append(':').append(decor.generate(n).size()).append(' ');
    System.out.println("decor blocks per table size: " + sizes);
  }

  @Test
  public void biggerTablesNeedMoreBlocks() {
    DecorConfig decor = DecorConfig.loadDefault();
    assertTrue(decor.generate(45).size() > decor.generate(4).size());
  }

  @Test
  public void minimalConfigParses() {
    var blocks = DecorConfig.parse(MINIMAL).generate(6);
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.CAMPFIRE));
    assertTrue(blocks.stream().noneMatch(b -> b.dy() < 0));
  }

  @Test
  public void unknownBlockIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("oak_planks", "not_a_block")));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("oak_planks", "diamond_sword")));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("oak_planks", "air")));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("oak_planks", "tnt")));
  }

  @Test
  public void unknownKeysAndMissingSectionsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("{\"floor\"", "{\"flor\":1,\"floor\"")));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse("{\"campfire\":{\"block\":\"campfire\"}}"));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse("[]"));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse("not json"));
    // Lanterns need a railing to hang from.
    String noRail =
        "{\"floor\":{\"inner\":\"oak_planks\",\"outer\":\"stone\"},\"campfire\":{\"block\":\"campfire\"},"
            + "\"lanterns\":{\"block\":\"lantern\"}}";
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(noRail));
  }

  @Test
  public void numbersMustBeExact() {
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("{\"floor\"", "{\"maxBlocks\":8000.9,\"floor\"")));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("{\"floor\"", "{\"version\":null,\"floor\"")));
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(MINIMAL.replace("{\"floor\"", "{\"version\":2,\"floor\"")));
  }

  @Test
  public void blockCapIsEnforced() {
    String tiny = MINIMAL.replace("{\"floor\"", "{\"maxBlocks\":50,\"floor\"");
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(tiny));
    String huge = MINIMAL.replace("{\"floor\"", "{\"maxBlocks\":" + (DecorConfig.HARD_CAP + 1) + ",\"floor\"");
    assertThrows(IllegalArgumentException.class, () -> DecorConfig.parse(huge));
  }
}
