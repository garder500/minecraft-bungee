package win.vampyuyu.werewolf;

import org.bukkit.Location;

/** Where one participant sits (or stands) on the sky platform, looking at the campfire. */
public record SeatSpot(Location at, boolean sit) {}
