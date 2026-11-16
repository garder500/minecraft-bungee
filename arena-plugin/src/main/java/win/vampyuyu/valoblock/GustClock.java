package win.vampyuyu.valoblock;

/**
 * Stormbreak's gust: every 45 s a gust shoves everyone standing on a bridge sideways, announced 3 s before. The clock
 * counts ticks from the start of the combat phase (the first gust is at 45 s, the next one blows the other way).
 * Pure: the world side is {@link VbRealmService}.
 */
public final class GustClock {
  private GustClock() {}

  /** Ticks between two gusts (45 s). */
  public static final long PERIOD = 900;
  /** Ticks the announcement comes before the gust (3 s). */
  public static final long WARN = 60;
  /** Ticks a gust keeps pushing. */
  public static final int DURATION = 14;
  /** Blocks per tick added to the velocity of a player on a bridge, each tick of the gust. */
  public static final double STRENGTH = 0.22;

  /** Whether a gust starts in the ticks after {@code from} up to and including {@code to}. */
  public static boolean gustBetween(long from, long to) {
    return TimeTide.hit(from, to, PERIOD, 0, 1); // never at tick 0
  }

  /** Whether the announcement is made in the ticks after {@code from} up to and including {@code to}. */
  public static boolean announceBetween(long from, long to) {
    return TimeTide.hit(from, to, PERIOD, PERIOD - WARN, 0);
  }

  /** Which gust a tick is in or before the next of: 1 for the first. */
  public static long number(long tick) {
    return Math.max(0, tick) / PERIOD;
  }

  /** The tick the n-th gust (1 for the first) starts. */
  public static long startOf(long n) {
    return n * PERIOD;
  }

  /** Direction of the n-th gust along the bridge's cross axis: +1 (east or south) for odd gusts, -1 for even ones. */
  public static int sign(long n) {
    return n % 2 == 1 ? 1 : -1;
  }

  /** Whether a tick is in the pushing part of a gust, and which one (its number), or 0. */
  public static long pushing(long tick) {
    long n = number(tick);
    return n >= 1 && tick - startOf(n) < DURATION ? n : 0;
  }

  /**
   * The axis a gust shoves a player along on a plan cell: 'x' on a bridge that runs north-south (sea to the west and east),
   * 'z' on one that runs east-west (sea north and south), 0 when the cell is not a one-cell bridge.
   */
  public static char pushAxis(VbPlan p, int col, int row) {
    if (p.at(col, row) != '.' && p.at(col, row) != 'F') return 0;
    if (p.sea(col - 1, row) && p.sea(col + 1, row)) return 'x';
    if (p.sea(col, row - 1) && p.sea(col, row + 1)) return 'z';
    return 0;
  }

  /** The push of one tick of a gust: {x, z} in blocks per tick. */
  public static double[] push(char axis, long gust) {
    double s = sign(gust) * STRENGTH;
    return axis == 'x' ? new double[] {s, 0} : axis == 'z' ? new double[] {0, s} : new double[] {0, 0};
  }
}
