package win.vampyuyu.werewolf.sky;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** The seat circle for every supported table size. */
public class PlatformLayoutTest {
  @Test
  public void everySizeKeepsSpacingAndUniqueSeats() {
    for (int n = PlatformLayout.MIN_PLAYERS; n <= PlatformLayout.MAX_PLAYERS; n++) {
      var layout = PlatformLayout.of(n);
      assertEquals(n, layout.seats().size());
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < n; i++) {
        var a = layout.seats().get(i);
        var b = layout.seats().get((i + 1) % n);
        assertTrue("seat spacing for " + n, Math.hypot(a.x() - b.x(), a.z() - b.z()) >= PlatformLayout.MIN_SPACING - 1e-9);
        assertTrue("seat " + i + " inside the platform", Math.hypot(a.x(), a.z()) < layout.platformRadius() - 2);
        assertTrue(seen.add(a.x() + "/" + a.z()));
        assertTrue(a.sit());
      }
    }
  }

  @Test
  public void ringGrowsWithPlayers() {
    assertTrue(PlatformLayout.of(45).ringRadius() > PlatformLayout.of(12).ringRadius());
    assertTrue(PlatformLayout.of(45).platformRadius() > PlatformLayout.of(4).platformRadius());
    assertEquals(0, PlatformLayout.of(10).centreX(), 0);
    assertEquals(0, PlatformLayout.of(10).centreZ(), 0);
  }

  @Test
  public void seatsFaceTheCampfire() {
    for (int n : new int[] {4, 7, 16, 45}) {
      for (var s : PlatformLayout.of(n).seats()) {
        // Minecraft facing vector from a yaw: (-sin, cos). It must point from the seat to the centre.
        double yaw = Math.toRadians(s.yaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double len = Math.hypot(s.x(), s.z());
        assertEquals(-s.x() / len, fx, 1e-4);
        assertEquals(-s.z() / len, fz, 1e-4);
      }
    }
  }

  @Test
  public void outOfRangeIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> PlatformLayout.of(3));
    assertThrows(IllegalArgumentException.class, () -> PlatformLayout.of(46));
    assertThrows(IllegalArgumentException.class, () -> PlatformLayout.of(-1));
  }
}
