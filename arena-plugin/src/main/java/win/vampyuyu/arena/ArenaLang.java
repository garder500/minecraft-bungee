package win.vampyuyu.arena;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.yaml.snakeyaml.Yaml;

/**
 * The menu texts of this plugin in English, French and German: {@code lang/menus/<tag>.yml} of the
 * jar (next to {@code lang/<tag>.yml}, the HUD words), dotted keys, plain text with {@code {name}}
 * placeholders. English is the
 * source and the fallback; a key no file has shows as the key itself, so a gap is visible.
 *
 * <p>The language of a player follows docs/i18n.md: the one chosen with /lang (kept on the player
 * by the network plugin, read here without depending on it), else the client's, else English.
 * Loading needs no server (the files are read from the classpath once), so the menus stay testable.
 */
public final class ArenaLang {
  private ArenaLang() {}

  public enum Lang {
    EN("en_US", "en"),
    FR("fr_FR", "fr"),
    DE("de_DE", "de");

    /** File name: {@code lang/menus/<tag>.yml}. */
    public final String tag;

    private final String code;

    Lang(String tag, String code) {
      this.tag = tag;
      this.code = code;
    }

    /** From a stored tag, a code or a locale tag ("fr", "fr_FR", "de-AT"); null when not ours. */
    public static Lang parse(String text) {
      if (text == null) return null;
      String lower = text.strip().toLowerCase(Locale.ROOT);
      int cut = 0;
      while (cut < lower.length() && Character.isLetter(lower.charAt(cut))) cut++;
      for (Lang lang : values()) if (lang.code.equals(lower.substring(0, cut))) return lang;
      return null;
    }
  }

  /** Player data key of the language chosen with /lang (network plugin, I18n.KEY). */
  private static final String KEY_NAMESPACE = "vampyuyu", KEY_NAME = "lang";

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

  private static final Map<Lang, Map<String, String>> TEXTS = load();

  private static Map<Lang, Map<String, String>> load() {
    Map<Lang, Map<String, String>> out = new EnumMap<>(Lang.class);
    for (Lang lang : Lang.values()) {
      Map<String, String> flat = new TreeMap<>();
      try (InputStream in = ArenaLang.class.getResourceAsStream("/lang/menus/" + lang.tag + ".yml")) {
        if (in != null)
          flatten("", new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8)), flat);
      } catch (java.io.IOException | RuntimeException e) {
        // A broken translation must not take the menus down: the language falls back to English.
        flat.clear();
        java.util.logging.Logger.getLogger("NexusArenaEngine")
            .warning("Cannot read lang/menus/" + lang.tag + ".yml: " + e);
      }
      out.put(lang, flat);
    }
    return out;
  }

  private static void flatten(String prefix, Object node, Map<String, String> out) {
    if (node instanceof Map<?, ?> map)
      map.forEach((k, v) -> flatten(prefix.isEmpty() ? k.toString() : prefix + "." + k, v, out));
    else if (node != null) out.put(prefix, node.toString());
  }

  /** Every text of one language (for the parity test). */
  public static Map<String, String> texts(Lang lang) {
    return Map.copyOf(TEXTS.get(lang));
  }

  /** Which language a player reads: stored choice, else client locale, else English. */
  public static Lang resolve(String stored, Locale client) {
    Lang chosen = Lang.parse(stored);
    if (chosen != null) return chosen;
    Lang fromClient = client == null ? null : Lang.parse(client.getLanguage());
    return fromClient != null ? fromClient : Lang.EN;
  }

  public static Lang of(Player player) {
    String stored =
        player
            .getPersistentDataContainer()
            .get(new NamespacedKey(KEY_NAMESPACE, KEY_NAME), PersistentDataType.STRING);
    return resolve(stored, player.locale());
  }

  /**
   * The text of {@code key} in {@code lang} (else English, else the key), its {@code {name}}
   * placeholders filled from name/value pairs. Values are inserted as they are, in one pass: a
   * player name that looks like a placeholder is not expanded again.
   */
  public static String tr(Lang lang, String key, Object... pairs) {
    if (pairs.length % 2 != 0)
      throw new IllegalArgumentException("placeholders come as name, value pairs");
    String text = TEXTS.get(lang).get(key);
    if (text == null) text = TEXTS.get(Lang.EN).get(key);
    if (text == null) return key;
    if (pairs.length == 0) return text;
    Matcher m = PLACEHOLDER.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String value = null;
      for (int i = 0; i < pairs.length; i += 2)
        if (pairs[i].equals(m.group(1))) value = String.valueOf(pairs[i + 1]);
      m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
    }
    return m.appendTail(out).toString();
  }
}
