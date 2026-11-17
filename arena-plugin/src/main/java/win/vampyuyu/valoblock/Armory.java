package win.vampyuyu.valoblock;

import java.util.UUID;

/** Buying and selling back, on top of the match's Shards. Pure: the menu and the inventory are the service's job. */
public final class Armory {
  public enum Result {
    BOUGHT("Bought"),
    SOLD("Sold back"),
    NOT_BUY_PHASE("You can only buy during the buy phase"),
    TOO_POOR("Not enough Shards"),
    ALREADY_OWNED("You already have that"),
    NOT_OWNED("You do not own that"),
    NOT_SELLABLE("That item is free: nothing to sell back"),
    NOT_BETTER("You already wear an equal or better shield");

    public final String text;

    Result(String text) {
      this.text = text;
    }

    public boolean ok() {
      return this == BOUGHT || this == SOLD;
    }
  }

  private Armory() {}

  /**
   * Buys {@code w}. A gun in an occupied slot replaces the old one, which is sold back at full price
   * (only the difference is needed). A shield sets the match shield value.
   */
  public static Result buy(VbMatch m, Loadout loadout, UUID player, VbWeapon w) {
    if (m.vbPhase() != VbPhase.BUY) return Result.NOT_BUY_PHASE;
    if (w.isShield()) {
      if (m.shield(player) >= w.shield()) return Result.NOT_BETTER;
      if (!m.buyShield(player, w.shield(), w.price())) return Result.TOO_POOR;
      loadout.markBought(w);
      return Result.BOUGHT;
    }
    if (loadout.owns(w)) return Result.ALREADY_OWNED;
    VbWeapon old = loadout.get(w.slot());
    int refund = refundOf(loadout, old);
    if (m.credits(player) + refund < w.price()) return Result.TOO_POOR;
    // Spend first when we can (a refund never overflows the cap then); otherwise refund first.
    if (m.credits(player) >= w.price()) {
      if (!m.buy(player, w.price())) return Result.TOO_POOR;
      if (refund > 0) m.refund(player, refund);
    } else {
      m.refund(player, refund);
      if (!m.buy(player, w.price())) return Result.TOO_POOR;
    }
    loadout.set(w);
    loadout.markBought(w);
    return Result.BOUGHT;
  }

  /** Only a gun bought in this buy phase is paid back when replaced or sold: a pickup or a kept gun gives nothing. */
  private static int refundOf(Loadout loadout, VbWeapon old) {
    return old != null && loadout.boughtThisPhase(old) ? old.price() : 0;
  }

  /** Sells an owned item during the buy phase: full price when bought in this phase, 0 otherwise. Free items stay. */
  public static Result sell(VbMatch m, Loadout loadout, UUID player, VbWeapon w) {
    if (m.vbPhase() != VbPhase.BUY) return Result.NOT_BUY_PHASE;
    if (w.free()) return Result.NOT_SELLABLE;
    if (w.isShield()) {
      if (m.shield(player) != w.shield()) return Result.NOT_OWNED;
      if (!m.refund(player, loadout.boughtThisPhase(w) ? w.price() : 0)) return Result.NOT_BUY_PHASE;
      m.setShield(player, 0);
      loadout.remove(VbWeapon.Slot.SHIELD);
      return Result.SOLD;
    }
    if (!loadout.owns(w)) return Result.NOT_OWNED;
    if (!m.refund(player, loadout.boughtThisPhase(w) ? w.price() : 0)) return Result.NOT_BUY_PHASE;
    loadout.remove(w.slot());
    return Result.SOLD;
  }

  /** The state of an item for one player in the menu. */
  public enum Display {
    OWNED,
    AFFORDABLE,
    LOCKED
  }

  public static Display display(VbMatch m, Loadout loadout, UUID player, VbWeapon w) {
    boolean owned = w.isShield() ? m.shield(player) == w.shield() : loadout.owns(w);
    if (owned) return Display.OWNED;
    int refund = w.isShield() ? 0 : refundOf(loadout, loadout.get(w.slot()));
    return m.credits(player) + refund >= w.price() ? Display.AFFORDABLE : Display.LOCKED;
  }

  /** Shards still missing for an item (0 when affordable), counting the sell-back of the gun it replaces. */
  public static int missing(VbMatch m, Loadout loadout, UUID player, VbWeapon w) {
    int refund = w.isShield() ? 0 : refundOf(loadout, loadout.get(w.slot()));
    return Math.max(0, w.price() - m.credits(player) - refund);
  }
}
