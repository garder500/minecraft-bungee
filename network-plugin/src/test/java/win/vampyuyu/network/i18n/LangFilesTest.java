package win.vampyuyu.network.i18n;

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

/** The shipped language files: every language defines the same keys and the same placeholders as English. */
public class LangFilesTest {
    private static Map<String, String> load(Lang lang) throws Exception {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/lang/" + lang.tag() + ".yml")) {
            assertTrue("missing lang/" + lang.tag() + ".yml", in != null);
            ConfigurationSection yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> flat = new TreeMap<>();
            for (String key : yaml.getKeys(true)) if (yaml.isString(key)) flat.put(key, yaml.getString(key));
            return flat;
        }
    }

    @Test public void everyLanguageHasEveryKey() throws Exception {
        Translations t = new Translations();
        for (Lang lang : Lang.values()) t.put(lang, load(lang));
        for (Lang lang : Lang.values()) {
            assertTrue(lang + " lacks " + t.missing(lang), t.missing(lang).isEmpty());
            assertTrue(lang + " has unknown keys " + t.unknown(lang), t.unknown(lang).isEmpty());
        }
    }

    @Test public void placeholdersMatchEnglish() throws Exception {
        Map<String, String> en = load(Lang.EN);
        for (Lang lang : Lang.values()) {
            Map<String, String> other = load(lang);
            for (Map.Entry<String, String> entry : en.entrySet()) {
                assertTrue(lang + " " + entry.getKey(), tags(entry.getValue()).equals(tags(other.get(entry.getKey()))));
            }
        }
    }

    /** The {@code <word>} markers used as placeholders (lower-case words that are not MiniMessage colours). */
    private static java.util.Set<String> tags(String text) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        var m = java.util.regex.Pattern.compile("<([a-z_]+)>").matcher(text);
        while (m.find()) out.add(m.group(1));
        out.removeAll(java.util.Set.of("red", "green", "gray", "white", "yellow", "gold", "aqua", "bold", "italic", "reset"));
        return out;
    }
}
