package win.vampyuyu.valoblock;

/**
 * Which chest slot is which part of the agent select window (the art is resource-pack/art/valoblock_ui.py; the numbers are
 * resource-pack/vb_layout.py). Slot = row * 9 + column.
 *
 * <pre>
 * cols 0-2 rows 0-3   portrait panel (painted, no item)
 * cols 3-8 rows 0-3   roster: 3 tiles per row of 2 slots, the head in the left slot and an invisible twin on the right
 * row 4               Random (cols 0-1), role filters (cols 3-6)
 * row 5               team seats (cols 0-4), Lock in (cols 5-8)
 * </pre>
 */
public final class VbSelectLayout {
  public static final int ROSTER_COL = 3, TILE_COLS = 3, TILE_ROWS = 4, CAPACITY = TILE_COLS * TILE_ROWS;
  public static final int RANDOM = 4 * 9, RANDOM_TWIN = RANDOM + 1;
  public static final int[] FILTERS = {4 * 9 + 3, 4 * 9 + 4, 4 * 9 + 5, 4 * 9 + 6};
  public static final int SEAT_FIRST = 5 * 9, SEATS = 5;
  public static final int[] LOCK = {5 * 9 + 5, 5 * 9 + 6, 5 * 9 + 7, 5 * 9 + 8};

  private VbSelectLayout() {}

  /** The slot with the head of roster tile {@code i}. */
  public static int head(int i) {
    return (i / TILE_COLS) * 9 + ROSTER_COL + 2 * (i % TILE_COLS);
  }

  /** The invisible twin next to the head (same tooltip, same action). */
  public static int twin(int i) {
    return head(i) + 1;
  }

  /** The roster tile a slot belongs to (head or twin), or -1. */
  public static int tileAt(int slot) {
    int row = slot / 9, col = slot % 9;
    if (row >= TILE_ROWS || col < ROSTER_COL) return -1;
    return row * TILE_COLS + (col - ROSTER_COL) / 2;
  }

  /** The seat slot of the teammate at {@code i}. */
  public static int seat(int i) {
    return SEAT_FIRST + i;
  }

  /** True when the slot is covered by the portrait panel. */
  public static boolean inPortrait(int slot) {
    return slot / 9 < 4 && slot % 9 < ROSTER_COL;
  }
}
