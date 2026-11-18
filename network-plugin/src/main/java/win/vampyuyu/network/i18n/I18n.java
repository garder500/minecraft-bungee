package win.vampyuyu.network.i18n;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Translations of the network plugins. The language of a player is the one chosen with /lang (kept on the player,
 * under {@link #KEY}, so the other plugins of the server can read it without depending on this one), else the language
 * of their Minecraft client, else English.
 *
 * <p>Texts live in {@code lang/<tag>.yml} of the jar (en_US, fr_FR, de_DE), as MiniMessage with {@code <name>}
 * placeholders. A file with the same name in this plugin's data folder ({@code lang/}) overrides single keys.
 * A missing key falls back to English, then shows the key.
 */
public final class I18n {
    /** Player data key of the chosen language: a tag like "fr_FR". Absent = follow the client. */
    public static final NamespacedKey KEY = new NamespacedKey("vampyuyu", "lang");

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Translations translations = new Translations();

    public I18n(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Reads the language files again (jar, then data folder overrides). */
    public void reload() {
        for (Lang lang : Lang.values()) {
            Map<String, String> flat = new TreeMap<>();
            try (InputStream in = plugin.getResource("lang/" + lang.tag() + ".yml")) {
                if (in != null) flatten(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), flat);
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Cannot read lang/" + lang.tag() + ".yml: " + e.getMessage());
            }
            File override = new File(plugin.getDataFolder(), "lang/" + lang.tag() + ".yml");
            if (override.isFile()) flatten(YamlConfiguration.loadConfiguration(override), flat);
            translations.put(lang, flat);
            if (lang != Lang.DEFAULT) {
                int missing = translations.missing(lang).size();
                if (missing > 0) plugin.getLogger().info(lang.tag() + ": " + missing + " text(s) still fall back to English");
            }
        }
    }

    private static void flatten(ConfigurationSection section, Map<String, String> out) {
        for (String key : section.getKeys(true)) {
            if (section.isString(key)) out.put(key, section.getString(key));
        }
    }

    public Translations translations() { return translations; }

    /** The language a player reads. */
    public Lang of(Player player) {
        String stored = player.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        Locale client = player.locale();
        return LangChoice.resolve(stored, client);
    }

    /** Whether the player picked a language with /lang (false = they follow their client). */
    public boolean isChosen(Player player) {
        return Lang.parse(player.getPersistentDataContainer().get(KEY, PersistentDataType.STRING)) != null;
    }

    /** Saves the player's choice; null goes back to following the client. */
    public void choose(Player player, Lang lang) {
        if (lang == null) player.getPersistentDataContainer().remove(KEY);
        else player.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, lang.tag());
    }

    /** The language for any sender: players as above, the console reads English. */
    public Lang of(CommandSender sender) {
        return sender instanceof Player player ? of(player) : Lang.DEFAULT;
    }

    /**
     * A translated message. {@code pairs} are placeholder name/value pairs ({@code "player", name}), inserted as plain
     * text, never parsed, so a player name or any typed text cannot inject formatting.
     */
    public Component msg(CommandSender to, String key, String... pairs) {
        return msg(of(to), key, pairs);
    }

    public Component msg(Lang lang, String key, String... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("placeholders come as name, value pairs");
        TagResolver.Builder resolvers = TagResolver.builder();
        for (int i = 0; i < pairs.length; i += 2) resolvers.resolver(Placeholder.unparsed(pairs[i], pairs[i + 1]));
        return MINI.deserialize(translations.raw(lang, key), resolvers.build());
    }

    /** Sends a translated chat message. */
    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(msg(to, key, pairs));
    }
}
