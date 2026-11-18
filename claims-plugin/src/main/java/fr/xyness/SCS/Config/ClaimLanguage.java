package fr.xyness.SCS.Config;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import fr.xyness.SCS.SimpleClaimSystem;
import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Handles language settings and messages for the plugin.
 */
public class ClaimLanguage {

    private Map<String, String> lang = new ConcurrentHashMap<>();

    /** Player data key of the language chosen with /lang (written by the network plugin), e.g. "fr_FR". */
    private static final NamespacedKey LANG_KEY = new NamespacedKey("vampyuyu", "lang");

    /** Extra languages by two-letter code ("fr", "de"). A key missing there falls back to {@link #lang}. */
    private volatile Map<String, Map<String, String>> others = Map.of();

    /** The player whose command is running on this thread: {@link #getMessage(String)} then answers in their language. */
    private static final ThreadLocal<Player> VIEWER = new ThreadLocal<>();
    private SimpleClaimSystem instance;

    public ClaimLanguage(SimpleClaimSystem instance) {
        this.instance = instance;
    }

    /**
     * Sets the language map with the provided messages.
     *
     * @param messagesMap A map containing message keys and their corresponding messages.
     * @return true if the language map is successfully set.
     */
    public boolean setLanguage(Map<String, String> messagesMap) {
        lang = new ConcurrentHashMap<>(messagesMap);
        return true;
    }

    /** Sets the other languages (code "fr" or "de" -> messages); the server language set by config stays the default. */
    public void setOtherLanguages(Map<String, Map<String, String>> byCode) {
        others = Map.copyOf(byCode);
    }

    /**
     * Answers in the language of {@code sender} until {@link #clearViewer()}: the command executors call this around a
     * command so every message it builds goes out in the player's language. The console reads the server language.
     */
    public void setViewer(CommandSender sender) {
        if (sender instanceof Player player) VIEWER.set(player); else VIEWER.remove();
    }

    public void clearViewer() {
        VIEWER.remove();
    }

    /**
     * The two-letter language of a player: the one chosen with /lang, else their client's, null if they have none we
     * speak or none is loaded (then the server language applies).
     */
    private String codeOf(Player player) {
        if (player == null || others.isEmpty()) return null;
        String stored = player.getPersistentDataContainer().get(LANG_KEY, PersistentDataType.STRING);
        String code = codeOf(stored);
        if (code == null) {
            Locale client = player.locale();
            code = client == null ? null : codeOf(client.getLanguage());
        }
        return code;
    }

    private String codeOf(String tag) {
        if (tag == null || tag.length() < 2) return null;
        String code = tag.substring(0, 2).toLowerCase(Locale.ROOT);
        return others.containsKey(code) ? code : null;
    }

    private String lookup(String key, Player player) {
        String code = codeOf(player);
        if (code != null) {
            String text = others.get(code).get(key);
            if (text != null) return text;
        }
        return lang.getOrDefault(key, "");
    }

    /**
     * Gets a message corresponding to the provided key, in the language of the player running the current command
     * (see {@link #setViewer}), else the server language.
     *
     * @param key The key of the message to retrieve.
     * @return The message string, or an empty string if the key is not found.
     */
    public String getMessage(String key) {
        return lookup(key, VIEWER.get());
    }

    /**
     * Gets a message with PlaceholderAPI placeholders resolved for the targeted player.
     *
     * @param key    The key of the message to retrieve.
     * @param target The targeted player.
     * @return The message with placeholders replaced.
     */
    public String getMessage(String key, OfflinePlayer target) {
        String msg = lookup(key, target instanceof Player player ? player : VIEWER.get());
        if (msg.isEmpty() || !instance.getSettings().getBooleanSetting("placeholderapi")) {
            return msg;
        }
        return PlaceholderAPI.setPlaceholders(target, msg);
    }
}
