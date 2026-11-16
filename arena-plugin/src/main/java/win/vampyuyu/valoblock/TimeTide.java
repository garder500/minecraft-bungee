package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;

/**
 * Hourfall's Time Tide: the four sand gates ring the Hourglass tower and open and close in pairs every 30 s on the
 * Citadel clock, with a bell 3 s before each change. The clock counts ticks from the start of the combat phase, so
 * every round plays the same: at tick 0 the north pair is open and the south pair is closed. Pure: the world side
 * is {@link VbRealmService}.
 */
public final class TimeTide {
  private TimeTide() {}

  /** Ticks between two changes (30 s). */
  public static final long PERIOD = 600;
  /** Height of the sand wall of a closed gate: the arch above it (lintel, corbels, lantern) is never touched. */
  public static final int GATE_HEIGHT = 3;

  /** Ticks the bell rings before a change (3 s). */
  public static final long WARN = 60;

  /** The pair that is open at a tick: 0 = north, 1 = south. The other pair is closed. */
  public static int openPair(long tick) {
    return (int) (Math.max(0, tick) / PERIOD % 2);
  }

  public static int closedPair(long tick) {
    return 1 - openPair(tick);
  }

  /** Whether the bell rings in the ticks after {@code from} up to and including {@code to}. */
  public static boolean bellBetween(long from, long to) {
    return hit(from, to, PERIOD, PERIOD - WARN, 0);
  }

  /** Whether the pairs swap in the ticks after {@code from} up to and including {@code to} (not at tick 0). */
  public static boolean flipBetween(long from, long to) {
    return hit(from, to, PERIOD, 0, 1);
  }

  /** Whether a tick of the form k*period+offset (k >= kmin) falls in (from, to]. */
  static boolean hit(long from, long to, long period, long offset, long kmin) {
    if (to <= from) return false;
    long k = Math.max(kmin, Math.floorDiv(from - offset, period) + 1);
    return k * period + offset <= to;
  }

  /** Ticks until the next change. */
  public static long ticksToFlip(long tick) {
    return PERIOD - Math.max(0, tick) % PERIOD;
  }

  /** The gate cells (col, row) of a plan: every F cell, north pair first. Pair 0 is north of the middle row. */
  public static List<int[]> gates(VbPlan p) {
    List<int[]> out = new ArrayList<>(p.cellsOf('F'));
    out.sort(java.util.Comparator.<int[]>comparingInt(g -> pairOf(p, g[1])).thenComparingInt(g -> g[0]));
    return out;
  }

  public static int pairOf(VbPlan p, int row) {
    return row < p.rows / 2 ? 0 : 1;
  }

  /** Graph node ids of the gates of one pair (for the bots, which plan around closed gates). */
  public static java.util.Set<String> nodeIds(VbPlan p, int pair) {
    java.util.Set<String> ids = new java.util.LinkedHashSet<>();
    for (int[] g : gates(p)) if (pairOf(p, g[1]) == pair) ids.add("n_" + g[0] + "_" + g[1]);
    return ids;
  }
}
