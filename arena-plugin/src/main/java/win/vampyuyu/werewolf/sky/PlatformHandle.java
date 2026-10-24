package win.vampyuyu.werewolf.sky;

import java.util.List;
import org.bukkit.Location;

/** A game's platform in the sky world: its slot, the seat spots (ring order) and the waiting point. */
public record PlatformHandle(
    SkySlot slot, int players, List<Location> seats, Location spawn, double platformRadius) {
  public PlatformHandle {
    seats = List.copyOf(seats);
  }

  /** Whether a point is on or around this platform (a few blocks beyond its rim). */
  public boolean covers(Location l) {
    if (l.getWorld() == null || !l.getWorld().getName().equals(SkyWorld.NAME))
      return false;
    double reach = platformRadius + 8;
    return l.getY() >= SkySlot.PLATFORM_Y - 40
        && l.getY() <= SkySlot.PLATFORM_Y + 60
        && Math.abs(l.getX() - (slot.centreX() + 0.5)) <= reach
        && Math.abs(l.getZ() - (slot.centreZ() + 0.5)) <= reach;
  }
}
