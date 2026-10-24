package win.vampyuyu.werewolf;

import static org.junit.Assert.*;

import java.util.*;
import org.bukkit.Location;
import org.junit.Test;
import win.vampyuyu.werewolf.sky.PlatformLayout;

public class SeatPlanTest {
  private static List<UUID> players(int n) {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) ids.add(UUID.randomUUID());
    return ids;
  }

  private static List<SeatSpot> spots(int n) {
    List<SeatSpot> out = new ArrayList<>();
    for (var seat : PlatformLayout.of(n).seats())
      out.add(new SeatSpot(new Location(null, seat.x(), 201, seat.z(), seat.yaw(), 0f), seat.sit()));
    return out;
  }

  @Test
  public void everyPlayerGetsOneDistinctSeatFrom4To45() {
    for (int n = PlatformLayout.MIN_PLAYERS; n <= PlatformLayout.MAX_PLAYERS; n++) {
      var ids = players(n);
      var layout = spots(n);
      assertEquals(n, layout.size());
      var plan = SeatPlan.assign(ids, layout, new Random(n));
      assertEquals("seats for " + n, n, plan.seats().size());
      assertEquals(new HashSet<>(ids), plan.seats().keySet());
      assertEquals("distinct seats for " + n, n, new HashSet<>(plan.seats().values()).size());
      assertEquals(n, plan.ring().size());
      assertEquals(new HashSet<>(ids), new HashSet<>(plan.ring()));
    }
  }

  @Test
  public void theRingFollowsTheSeatsAroundTheFire() {
    var ids = players(12);
    var layout = spots(12);
    var plan = SeatPlan.assign(ids, layout, new Random(7));
    int last = -1;
    for (UUID id : plan.ring()) {
      int index = layout.indexOf(plan.seats().get(id));
      assertTrue(index > last);
      last = index;
    }
  }

  @Test
  public void aTableWithTooFewSeatsIsRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SeatPlan.assign(players(6), spots(5), new Random(1)));
  }
}
