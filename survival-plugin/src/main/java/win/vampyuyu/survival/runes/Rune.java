package win.vampyuyu.survival.runes;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;

/**
 * One kind of rune (GDD 10, 3.2). A stat rune exists at ranks I to III and adds points of {@code stat}; a Realm rune has
 * no rank, is made from the Realm's local resource and brings a new effect instead of points.
 *
 * @param id lower case id, also the effect id of a Realm rune
 * @param stat the stat it raises, null for a Realm rune
 * @param target what it can be engraved on
 * @param component stat rune: custom item id spent to make rank II. Realm rune: id of the Realm resource it is made from
 */
public record Rune(String id, String name, @Nullable Stat stat, Target target, String component) {

    /** What a rune can be engraved on. */
    public enum Target {
        ARMOR, WEAPON, ANY;

        public static @Nullable Target parse(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** True for a Realm rune (an effect, no rank). */
    public boolean realm() {
        return stat == null;
    }

    public boolean fits(boolean weapon, boolean armor) {
        return switch (target) {
            case ARMOR -> armor;
            case WEAPON -> weapon;
            case ANY -> weapon || armor;
        };
    }

    /** Custom item id of this rune at a rank (rank is ignored for a Realm rune). */
    public String itemId(int rank) {
        return realm() ? "rune_" + id : "rune_" + id + "_" + rank;
    }

    public static String roman(int rank) {
        return switch (rank) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "";
        };
    }
}
