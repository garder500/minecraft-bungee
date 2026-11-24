package win.vampyuyu.werewolf.hud;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * The words of the custom HUD in the player's language: {@code lang/<tag>.yml} of the jar (en_US is the source
 * and the fallback, fr_FR and de_DE have the same keys; docs/i18n.md). The files hold one {@code key: "TEXT"} per
 * line and nothing else, so this reader, resource-pack/art/hud_sheet.py and scripts/check_lang_parity.py agree.
 */
public final class HudLang {
  /** The language every missing text falls back to. */
  public static final String DEFAULT = "en_US";

  public static final List<String> TAGS = List.of(DEFAULT, "fr_FR", "de_DE");

  /** Where /lang of the network plugin stores a player's choice (a tag like fr_FR). */
  private static final NamespacedKey CHOICE = new NamespacedKey("vampyuyu", "lang");

  private static final Map<String, Map<String, String>> FILES = load();

  private HudLang() {}

  private static Map<String, Map<String, String>> load() {
    Map<String, Map<String, String>> out = new HashMap<>();
    for (String tag : TAGS) {
      try (var in = HudLang.class.getResourceAsStream("/lang/" + tag + ".yml")) {
        if (in == null) throw new IllegalStateException("lang/" + tag + ".yml missing");
        out.put(tag, parse(tag, new String(in.readAllBytes(), StandardCharsets.UTF_8)));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return out;
  }

  /** Reads {@code key: "TEXT"} lines; blank lines and # comments are skipped, anything else is refused. */
  static Map<String, String> parse(String name, String text) {
    Map<String, String> out = new LinkedHashMap<>();
    int n = 0;
    for (String line : text.split("\n")) {
      n++;
      if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
      int cut = line.indexOf(": ");
      String key = cut < 0 ? "" : line.substring(0, cut);
      String value = cut < 0 ? "" : line.substring(cut + 2).strip();
      if (key.isEmpty()
          || !key.equals(key.strip())
          || value.length() < 2
          || !value.startsWith("\"")
          || !value.endsWith("\"")
          || value.substring(1, value.length() - 1).contains("\"")
          || value.contains("\\"))
        throw new IllegalStateException(name + " line " + n + ": expected key: \"TEXT\"");
      out.put(key, value.substring(1, value.length() - 1));
    }
    return out;
  }

  /** The keys of a language file, in file order (for the tests). */
  public static Set<String> keys(String tag) {
    return FILES.getOrDefault(tag, Map.of()).keySet();
  }

  /** The text of a key in a language, else in English, else {@code fallback} (a word added by a data override). */
  public static String tr(String tag, String key, String fallback) {
    String text = FILES.getOrDefault(tag, Map.of()).get(key);
    if (text == null || text.isEmpty()) text = FILES.get(DEFAULT).get(key);
    return text == null || text.isEmpty() ? fallback : text;
  }

  /**
   * Which language file a player reads: the one chosen with /lang if any, else the one of their Minecraft client,
   * else English. Only the language part counts ("fr", "fr_FR", "fr-CA", "DE_at").
   */
  public static String tag(String stored, Locale client) {
    String chosen = match(stored);
    if (chosen != null) return chosen;
    String fromClient = client == null ? null : match(client.getLanguage());
    return fromClient != null ? fromClient : DEFAULT;
  }

  private static String match(String text) {
    if (text == null) return null;
    String lower = text.strip().toLowerCase(Locale.ROOT);
    int cut = 0;
    while (cut < lower.length() && Character.isLetter(lower.charAt(cut))) cut++;
    String code = lower.substring(0, cut);
    for (String tag : TAGS) if (tag.substring(0, 2).equals(code)) return tag;
    return null;
  }

  /** The language of a player (their /lang choice is stored on them, so no dependency on the network plugin). */
  public static String of(Player p) {
    return tag(p.getPersistentDataContainer().get(CHOICE, PersistentDataType.STRING), p.locale());
  }
}
