package win.vampyuyu.survival.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Fixed menu text (labels around the content: "Next:", "Rewards:", menu titles...) in the viewer's language. English
 * stays in the Java code as the fallback; {@code lang/menu_<lang>.properties} (UTF-8) of the jar holds the French and
 * German text by key. Placeholders are {@code {0}}, {@code {1}}..., replaced as plain text.
 */
public final class MenuLang {
    /** Languages with a file. */
    public static final Set<String> LANGUAGES = Set.of("fr", "de");
    private static final Map<String, Properties> TEXTS = load();

    private MenuLang() {}

    private static Map<String, Properties> load() {
        Map<String, Properties> out = new HashMap<>();
        for (String lang : LANGUAGES) {
            Properties props = new Properties();
            try (InputStream in = MenuLang.class.getResourceAsStream("/lang/menu_" + lang + ".properties")) {
                if (in != null) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                // a broken file reads English
            }
            out.put(lang, props);
        }
        return Map.copyOf(out);
    }

    /** The keys of one language file (tests). */
    public static Set<String> keys(String lang) {
        Properties props = TEXTS.get(lang);
        return props == null ? Set.of() : props.stringPropertyNames();
    }

    /** The text of {@code key} in {@code lang}, else {@code english}, with {@code {n}} replaced by {@code args[n]}. */
    public static String tr(String lang, String key, String english, Object... args) {
        Properties props = lang == null ? null : TEXTS.get(lang);
        String text = props == null ? english : props.getProperty(key, english);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }
}
