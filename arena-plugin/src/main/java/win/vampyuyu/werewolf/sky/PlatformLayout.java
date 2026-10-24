package win.vampyuyu.werewolf.sky;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry of one sky platform: where each player sits around the campfire. All offsets are
 * relative to the campfire centre (block centre, so 0,0 is the middle of the campfire block). The ring
 * grows with the player count so two neighbours are never closer than {@link #MIN_SPACING} blocks.
 */
public record PlatformLayout(
    int players, double centreX, double centreZ, double ringRadius, double platformRadius, List<SeatPos> seats) {
  public static final int MIN_PLAYERS = 4, MAX_PLAYERS = 45;

  /** Smallest distance between two neighbouring seats, in blocks. */
  public static final double MIN_SPACING = 1.6;

  /** Ring radius floor, so a table of four still leaves room around the fire. */
  static final double MIN_RING = 3.0;

  /** Floor kept between the seat ring and the platform edge (bench, walkway, railing). */
  static final double RIM = 4.5;

  /** One seat: offset from the centre, the yaw that faces the campfire, and whether the player sits. */
  public record SeatPos(double x, double z, float yaw, boolean sit) {}

  public PlatformLayout {
    seats = List.copyOf(seats);
  }

  public static PlatformLayout of(int players) {
    if (players < MIN_PLAYERS || players > MAX_PLAYERS)
      throw new IllegalArgumentException(
          "A sky platform seats " + MIN_PLAYERS + " to " + MAX_PLAYERS + " players, not " + players);
    // Chord between neighbours = 2 r sin(pi/n); solve for MIN_SPACING, then round up to half a block.
    double needed = MIN_SPACING / (2 * Math.sin(Math.PI / players));
    double ring = Math.ceil(Math.max(MIN_RING, needed) * 2) / 2;
    List<SeatPos> seats = new ArrayList<>(players);
    for (int i = 0; i < players; i++) {
      double angle = 2 * Math.PI * i / players;
      double x = ring * Math.sin(angle), z = ring * Math.cos(angle);
      // Minecraft yaw 0 looks towards +z and grows clockwise: facing (dx, dz) = (-x, -z) gives atan2(x, -z).
      seats.add(new SeatPos(x, z, (float) Math.toDegrees(Math.atan2(x, -z)), true));
    }
    return new PlatformLayout(players, 0, 0, ring, ring + RIM, seats);
  }
}
