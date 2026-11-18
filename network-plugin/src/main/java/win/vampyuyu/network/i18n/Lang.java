package win.vampyuyu.network.i18n;

import java.util.Locale;

/** The languages the network speaks. English is the source of every text and the fallback of the others. */
public enum Lang {
    EN("en_US", "en", "English"),
    FR("fr_FR", "fr", "Français"),
    DE("de_DE", "de", "Deutsch");

    /** The language every missing text falls back to. */
    public static final Lang DEFAULT = EN;

    private final String tag;
    private final String code;
    private final String label;

    Lang(String tag, String code, String label) {
        this.tag = tag;
        this.code = code;
        this.label = label;
    }

    /** Full tag, also the language file name: {@code lang/<tag>.yml} (en_US, fr_FR, de_DE). */
    public String tag() { return tag; }

    /** Two-letter code, what a player types: {@code /lang fr}. */
    public String code() { return code; }

    /** Name of the language in itself, shown in the menu and by /lang. */
    public String label() { return label; }

    /**
     * Reads a language from a stored value, a command argument or a client locale tag ("fr", "fr_FR", "fr-CA", "DE_at").
     * Only the language part counts, so every French or German variant maps to ours. Null when blank or not supported.
     */
    public static Lang parse(String text) {
        if (text == null) return null;
        String lower = text.strip().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return null;
        int cut = 0;
        while (cut < lower.length() && Character.isLetter(lower.charAt(cut))) cut++;
        String code = lower.substring(0, cut);
        for (Lang lang : values()) {
            if (lang.code.equals(code)) return lang;
        }
        return null;
    }

    /** From a Java locale (the client's language in the options); null when it is not one of ours. */
    public static Lang of(Locale locale) {
        return locale == null ? null : parse(locale.getLanguage());
    }
}
