package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stormbreak's wind pads (the F cells): crouch on one and the wind carries you to an island of the archipelago in
 * about 2 s, along an arc high enough to clear the cliffs. The sea is a kill volume. Pure geometry and timing; the
 * world side is {@link VbRealmService}.
 */
public final class WindPads {
  private WindPads() {}

  /** Ticks of a flight (2 s). */
  public static final int FLIGHT_TICKS = 40;
  /** Ticks before the same player can use a pad again. */
  public static final int COOLDOWN_TICKS = 80;
  /** Feet below this height (relative to the floor) are in the sea. */
  public static final double SEA_Y = -0.8;

  /** Whether a player at this height (relative to the floor) fell into the sea. */
  public static boolean drowned(double relY) {
    return relY < SEA_Y;
  }

  public record Pad(int col, int row, int toCol, int toRow) {}

  /** Where each pad lands: pad cell to landing cell. Every F cell of Stormbreak is listed (a test checks it). */
  private static final Map<String, int[]> TARGETS = Map.ofEntries(
      Map.entry("12,4", new int[] {4, 7}), Map.entry("17,4", new int[] {26, 7}),
      Map.entry("4,14", new int[] {3, 6}), Map.entry("4,15", new int[] {5, 6}),
      Map.entry("9,14", new int[] {12, 14}), Map.entry("9,15", new int[] {12, 15}),
      Map.entry("13,15", new int[] {3, 7}), Map.entry("14,15", new int[] {5, 7}),
      Map.entry("15,15", new int[] {25, 6}), Map.entry("16,15", new int[] {26, 7}),
      Map.entry("20,14", new int[] {17, 14}), Map.entry("20,15", new int[] {17, 15}),
      Map.entry("25,14", new int[] {25, 6}), Map.entry("25,15", new int[] {26, 6}),
      Map.entry("9,25", new int[] {12, 16}), Map.entry("20,25", new int[] {17, 16}));

  /** The pads of a plan, in reading order; an F cell without a landing is not a pad. */
  public static List<Pad> pads(VbPlan p) {
    List<Pad> out = new ArrayList<>();
    for (int[] f : p.cellsOf('F')) {
      int[] t = TARGETS.get(f[0] + "," + f[1]);
      if (t != null) out.add(new Pad(f[0], f[1], t[0], t[1]));
    }
    return out;
  }

  /** The pad under a position (blocks from the map corner, y relative to the floor), or null. Standing: feet within half a block of the floor. */
  public static Pad padAt(VbPlan p, List<Pad> pads, double x, double y, double z) {
    if (y < -0.5 || y > 0.6) return null;
    int col = p.col((int) Math.floor(x)), row = p.col((int) Math.floor(z));
    for (Pad pad : pads) if (pad.col() == col && pad.row() == row) return pad;
    return null;
  }

  /** One flight: an arc from a pad to a landing, the same duration whatever the distance. */
  public static final class Flight {
    private final double[] from, to;
    private final double lift;

    public Flight(double[] from, double[] to) {
      this.from = from.clone();
      this.to = to.clone();
      double d = Math.hypot(to[0] - from[0], to[2] - from[2]);
      this.lift = Math.max(10, d * 0.22); // clears the cliffs (up to 6 blocks) and the lighthouse ring
    }

    /** Where the player is meant to be after n ticks (0 to {@link #FLIGHT_TICKS}). */
    public double[] at(int tick) {
      double s = Math.min(1, Math.max(0, tick / (double) FLIGHT_TICKS));
      return new double[] {from[0] + (to[0] - from[0]) * s, from[1] + (to[1] - from[1]) * s + 4 * lift * s * (1 - s), from[2] + (to[2] - from[2]) * s};
    }

    public boolean done(int tick) {
      return tick >= FLIGHT_TICKS;
    }

    /**
     * The velocity (blocks per tick) that moves a player now at {@code cur} to the point of the arc one tick later:
     * set every tick, it follows the arc whatever drag and gravity do to the player in between.
     */
    public double[] velocity(int tick, double[] cur) {
      double[] n = at(tick + 1);
      double[] v = {n[0] - cur[0], n[1] - cur[1], n[2] - cur[2]};
      double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
      if (len > 3.5) for (int i = 0; i < 3; i++) v[i] *= 3.5 / len;
      return v;
    }

    /** Highest point of the arc above the floor. */
    public double apex() {
      return Math.max(from[1], to[1]) + lift;
    }
  }
}
