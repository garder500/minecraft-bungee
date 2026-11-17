package win.vampyuyu.valoblock;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The agent outfit of a human player, pure bookkeeping (no Bukkit types, so it is unit-tested with plain objects).
 * Until signed skins exist the human wears the agent as four equippable layers (zero armour points, never
 * droppable). The array order is the one of {@code PlayerInventory#getArmorContents}: boots, leggings, chestplate,
 * helmet. Only EMPTY slots are filled: the worn shield (chestplate) and the scope (helmet) win while they are on,
 * and the outfit comes back as soon as the slot is empty again.
 */
final class Outfit {
  /** The four pieces, in armour-array order. */
  enum Piece {
    FEET("Boots"),
    LEGS("Leggings"),
    CHEST("Jacket"),
    HEAD("Hood");

    final String label;

    Piece(String label) {
      this.label = label;
    }
  }

  private Outfit() {}

  /** The equipment asset id (pack: equipment/valoblock_&lt;agent&gt;.json) the pieces name. */
  static String assetId(String agent) {
    return "valoblock_" + agent;
  }

  /** Puts a piece in every empty slot of the armour array; returns how many were added. */
  static <T> int fill(T[] armor, Function<Piece, T> maker, Predicate<T> empty) {
    int added = 0;
    Piece[] pieces = Piece.values();
    for (int i = 0; i < pieces.length && i < armor.length; i++) {
      if (!empty.test(armor[i])) continue;
      armor[i] = maker.apply(pieces[i]);
      added++;
    }
    return added;
  }

  /** Sets every outfit piece of the slots to {@code null}; returns how many were removed. */
  static <T> int strip(T[] slots, Predicate<T> isOutfit) {
    int removed = 0;
    for (int i = 0; i < slots.length; i++) {
      if (slots[i] != null && isOutfit.test(slots[i])) {
        slots[i] = null;
        removed++;
      }
    }
    return removed;
  }
}
