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
    long campfires = blocks.stream().filter(b -> b.material() == Material.CAMPFIRE).count();
    assertEquals(1, campfires);
    var fire = blocks.stream().filter(b -> b.material() == Material.CAMPFIRE).findFirst().orElseThrow();
    assertEquals(0, fire.dx());
    assertEquals(1, fire.dy());
    assertEquals(0, fire.dz());
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.LANTERN));
    assertTrue(blocks.stream().anyMatch(b -> b.material() == Material.SPRUCE_FENCE));
    assertTrue(blocks.stream().anyMatch(b -> b.dy() < 0));
    // One bench log behind every seat.
    long logs = blocks.stream().filter(b -> b.material() == Material.SPRUCE_LOG || b.material() == Material.STRIPPED_SPRUCE_LOG).count();
    assertEquals(n, logs);
    // The underside tapers: it never reaches past the platform radius.
    for (var b : blocks.stream().filter(b -> b.dy() < 0).toList())
      assertTrue(Math.hypot(b.dx(), b.dz()) <= layout.platformRadius());
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
