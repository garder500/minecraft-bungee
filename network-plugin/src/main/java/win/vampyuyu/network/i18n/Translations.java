package win.vampyuyu.network.i18n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The loaded texts: for each language a flat map from a dotted key ({@code lang.current}) to a MiniMessage string.
 * A key missing in a language falls back to English, then to the key itself, so a gap is visible but never crashes.
 * Pure Java (the Bukkit side lives in {@link I18n}).
 */
public final class Translations {
    private final Map<Lang, Map<String, String>> texts = new EnumMap<>(Lang.class);

    public Translations() {
        for (Lang lang : Lang.values()) texts.put(lang, new TreeMap<>());
    }

    /** Replaces all the texts of one language. */
    public void put(Lang lang, Map<String, String> flat) {
        texts.put(lang, new TreeMap<>(flat));
    }

    /** The text of {@code key} for {@code lang}: its own, else English, else null. */
    public String find(Lang lang, String key) {
        String text = texts.get(lang).get(key);
        if (text == null && lang != Lang.DEFAULT) text = texts.get(Lang.DEFAULT).get(key);
        return text;
    }

    /** Like {@link #find} but returns the key itself when no language has it. */
    public String raw(Lang lang, String key) {
        String text = find(lang, key);
        return text != null ? text : key;
    }

    /**
     * Replaces the {@code <name>} markers of a text with values given as name/value pairs. The values are inserted as
     * they are: callers must escape anything player-written before (the Bukkit side passes them as plain text tags).
     */
    public static String fill(String text, String... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("placeholders come as name, value pairs");
        String out = text;
        for (int i = 0; i < pairs.length; i += 2) out = out.replace("<" + pairs[i] + ">", pairs[i + 1]);
        return out;
    }

    /** English keys that {@code lang} does not define (what the parity test and the check script report). */
    public List<String> missing(Lang lang) {
        List<String> out = new ArrayList<>();
        for (String key : texts.get(Lang.DEFAULT).keySet()) {
            if (!texts.get(lang).containsKey(key)) out.add(key);
        }
        return Collections.unmodifiableList(out);
    }

    /** Keys a language defines that English does not: a typo or an obsolete text. */
    public List<String> unknown(Lang lang) {
        TreeSet<String> extra = new TreeSet<>(texts.get(lang).keySet());
        extra.removeAll(texts.get(Lang.DEFAULT).keySet());
        return new ArrayList<>(extra);
    }

    public int size(Lang lang) { return texts.get(lang).size(); }
}
