package win.vampyuyu.survival.dungeon;

import java.util.Locale;
import java.util.Optional;

/** Final rating of a run, from worst to best (order matters: {@code ordinal()} = tier). */
public enum Grade {
    D("D", "<gray>"),
    C("C", "<green>"),
    B("B", "<blue>"),
    A("A", "<dark_purple>"),
    S("S", "<gold>"),
    S_PLUS("S+", "<light_purple>");

    private final String label;
    private final String color;

    Grade(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }

    /** MiniMessage colour tag. */
    public String color() { return color; }

    public boolean atLeast(Grade other) { return ordinal() >= other.ordinal(); }

    /** Parses "S+", "s_plus", "a"... */
    public static Optional<Grade> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim().toUpperCase(Locale.ROOT).replace("PLUS", "+").replace("_", "");
        for (Grade g : values()) {
            if (g.label.equals(t)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }
}
