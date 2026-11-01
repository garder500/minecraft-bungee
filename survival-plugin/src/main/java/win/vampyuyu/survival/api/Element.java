package win.vampyuyu.survival.api;

import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * The five elements of the world, one per ore: Earth (Bronzite), Frost (Frostium), Ember (Sunstone), Void (Voidglass)
 * and Astral (Starcore). A weapon carries one; a boss, slayer or monster can be weak to one and resist another.
 */
public enum Element {
    EARTH("Earth", NamedTextColor.GOLD, "⛰"),
    FROST("Frost", NamedTextColor.AQUA, "❄"),
    EMBER("Ember", NamedTextColor.RED, "♨"),
    VOID("Void", NamedTextColor.DARK_PURPLE, "◉"),
    ASTRAL("Astral", NamedTextColor.YELLOW, "✶");

    private final String displayName;
    private final TextColor color;
    private final String symbol;

    Element(String displayName, TextColor color, String symbol) {
        this.displayName = displayName;
        this.color = color;
        this.symbol = symbol;
    }

    public String displayName() {
        return displayName;
    }

    public TextColor color() {
        return color;
    }

    public String symbol() {
        return symbol;
    }

    /** Lower case key used in data files and PDC, e.g. "frost". */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The element named {@code name} (any case), or null for null, empty and unknown names. */
    public static Element parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        for (Element element : values()) {
            if (element.name().equals(key)) {
                return element;
            }
        }
        return null;
    }
}
