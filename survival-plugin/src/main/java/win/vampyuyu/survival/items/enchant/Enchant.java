package win.vampyuyu.survival.items.enchant;

import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.gear.ItemKind;

/**
 * One custom enchant. Effects are applied by the engine (stat enchants) or by the combat / break listeners
 * (everything else) through {@link EnchantRegistry#perLevel}.
 *
 * @param id lower case id stored in the item PDC
 * @param name display name
 * @param maxLevel highest level reachable (1-10)
 * @param ultimate true for ultimate enchants (only one per item, books only)
 * @param applies item kinds that can carry it
 * @param stat stat granted per level on the carrying piece, or null
 * @param perLevel numeric strength per level (meaning depends on the enchant, see {@code description})
 * @param description lore text, {@code {v}} is replaced by {@code perLevel * level} formatted
 * @param tableMax highest level the enchanting table offers (0 = books only)
 */
public record Enchant(String id, String name, int maxLevel, boolean ultimate, Set<ItemKind> applies,
        @Nullable Stat stat, double perLevel, String description, int tableMax) {

    public Enchant {
        applies = Set.copyOf(applies.isEmpty() ? EnumSet.noneOf(ItemKind.class) : EnumSet.copyOf(applies));
        if (maxLevel < 1 || maxLevel > 10) {
            throw new IllegalArgumentException("maxLevel must be 1-10 for " + id);
        }
    }

    public boolean appliesTo(ItemKind kind) {
        return applies.contains(kind);
    }

    /** Roman numeral of a level 1-10 (falls back to digits). */
    public static String roman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level < numerals.length ? numerals[level] : Integer.toString(level);
    }

    /** "Sharpness V" (or just the name for one level enchants). */
    public String label(int level) {
        return maxLevel == 1 ? name : name + " " + roman(level);
    }
}
