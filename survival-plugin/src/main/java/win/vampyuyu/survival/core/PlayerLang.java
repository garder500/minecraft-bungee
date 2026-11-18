package win.vampyuyu.survival.core;

import java.util.Locale;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * The language a player reads, as a two-letter code ("en", "fr", "de"): the one chosen with /lang of the network plugin
 * (kept on the player under {@code vampyuyu:lang}, so there is no dependency between the plugins), else the language of
 * their Minecraft client, else English. English is also what any text falls back to.
 */
public final class PlayerLang {
    /** The language every text exists in. */
    public static final String DEFAULT = "en";
    public static final Set<String> SUPPORTED = Set.of("en", "fr", "de");
    public static final NamespacedKey KEY = new NamespacedKey("vampyuyu", "lang");

    private PlayerLang() {}

    public static String of(Player player) {
        if (player == null) {
            return DEFAULT;
        }
        String stored = player.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return code(stored, player.locale());
    }

    /** The choice made with /lang wins, then the client's language, then English. */
    public static String code(String stored, Locale client) {
        String chosen = parse(stored);
        if (chosen != null) {
            return chosen;
        }
        String fromClient = client == null ? null : parse(client.getLanguage());
        return fromClient != null ? fromClient : DEFAULT;
    }

    /** "fr", "fr_FR", "FR-ca" -> "fr"; null when blank or not a language we have. */
    static String parse(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.strip().toLowerCase(Locale.ROOT);
        int cut = 0;
        while (cut < lower.length() && Character.isLetter(lower.charAt(cut))) {
            cut++;
        }
        String code = lower.substring(0, cut);
        return SUPPORTED.contains(code) ? code : null;
    }
}
