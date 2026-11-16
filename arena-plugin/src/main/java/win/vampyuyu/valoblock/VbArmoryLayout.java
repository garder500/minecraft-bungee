package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;

/**
 * The Armory window: class tabs down column 0, weapon cards of 2 x 2 slots (the art sits in the first slot, the other three
 * are invisible twins with the same tooltip and action), the two Wards on rows 4-5 and the agent's two buyable abilities.
 * Slot = row * 9 + column. Numbers mirror resource-pack/vb_layout.py.
 */
public final class VbArmoryLayout {
  public static final String[] TABS = {"Sidearms", "SMGs", "Shotguns", "Rifles", "Snipers", "Heavy"};
  public static final int CARD_COL = 1, CARD_COLS = 4, CARD_ROWS = 2, CARD_CAPACITY = CARD_COLS * CARD_ROWS;
  public static final int SHIELD_ROW = 4, SHIELD_COLS = 2;
  public static final int[] ABILITY = {42, 44};

  private VbArmoryLayout() {}

  /** The slot of tab {@code i}. */
  public static int tab(int i) {
    return i * 9;
  }

  /** The tab a slot is the button of, or -1. */
  public static int tabAt(int slot) {
    return slot % 9 == 0 && slot / 9 < TABS.length ? slot / 9 : -1;
  }

  /** The first (top-left) slot of card {@code i} in a tab. */
  public static int card(int i) {
    return (i / CARD_COLS) * 18 + CARD_COL + 2 * (i % CARD_COLS);
  }

  /** The four slots of a card: the art slot first. */
  public static int[] cardSlots(int i) {
    int a = card(i);
    return new int[] {a, a + 1, a + 9, a + 10};
  }

  /** The card a slot belongs to, or -1. */
  public static int cardAt(int slot) {
    int row = slot / 9, col = slot % 9;
    if (row >= CARD_ROWS * 2 || col < CARD_COL) return -1;
    return (row / 2) * CARD_COLS + (col - CARD_COL) / 2;
  }

  /** The first slot of shield card {@code i} (0 or 1). */
  public static int shield(int i) {
    return SHIELD_ROW * 9 + CARD_COL + 2 * i;
  }

  public static int[] shieldSlots(int i) {
    int a = shield(i);
    return new int[] {a, a + 1, a + 9, a + 10};
  }

  /** The shield card a slot belongs to, or -1. */
  public static int shieldAt(int slot) {
    int row = slot / 9, col = slot % 9;
    if (row < SHIELD_ROW || row >= SHIELD_ROW + 2 || col < CARD_COL || col >= CARD_COL + 2 * SHIELD_COLS) return -1;
    return (col - CARD_COL) / 2;
  }

  /** The tab a weapon is shown in, or -1 for what the Armory does not sell by class (knife, shields). */
  public static int tabOf(VbWeapon w) {
    if (w.isShield() || w.slot() == VbWeapon.Slot.MELEE) return -1;
    String k = w.kind().toLowerCase(java.util.Locale.ROOT);
    if (w.slot() == VbWeapon.Slot.SIDEARM) return 0;
    if (k.contains("smg")) return 1;
    if (k.contains("shotgun")) return 2;
    if (k.contains("lmg")) return 5;
    if (k.contains("sniper") || k.contains("marksman")) return 4;
    return 3;
  }

  /** The weapons of a tab, cheapest first (at most {@link #CARD_CAPACITY}). */
  public static List<VbWeapon> weaponsOf(int tab, Iterable<VbWeapon> all) {
    List<VbWeapon> out = new ArrayList<>();
    for (VbWeapon w : all) if (tabOf(w) == tab) out.add(w);
    out.sort(java.util.Comparator.comparingInt(VbWeapon::price).thenComparing(VbWeapon::id));
    return out.size() > CARD_CAPACITY ? out.subList(0, CARD_CAPACITY) : out;
  }

  /** The shields, cheapest first. */
  public static List<VbWeapon> shields(Iterable<VbWeapon> all) {
    List<VbWeapon> out = new ArrayList<>();
    for (VbWeapon w : all) if (w.isShield()) out.add(w);
    out.sort(java.util.Comparator.comparingInt(VbWeapon::price));
    return out.size() > SHIELD_COLS ? out.subList(0, SHIELD_COLS) : out;
  }
}
