package win.vampyuyu.survival.core;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.data.Db;

/**
 * Content text in the player's language. English is the text of the content tables (item_def, item_lore, quest...);
 * the {@code i18n_text} table holds its translations by key ({@code item.<id>.name}, {@code item.<id>.lore.<n>},
 * (also the enchanted items), {@code quest.<id>.name}, {@code quest.<id>.desc}, {@code category.<id>.name|desc}).
 *
 * <p>Two ways to read it. Text built for one viewer (menus, sidebar, chat) is a plain lookup: {@link #tr}. Text written
 * on an item stack must not freeze a language (the stack ends in chests, trades and other players' hands), and Paper
 * does not translate item text per viewer, so {@link #name} and {@link #line} return translatable components whose
 * keys the resource pack translates ({@code assets/vy_i18n/lang}, written by scripts/i18n_seed.py), with the English
 * text as the fallback: a client without the pack, or in another language, reads English.
 *
 * <p>A translation is only used while its {@code source} is still the English text of the content row. For menus the
 * row is simply ignored; on a stack the key ends with a hash of the English text, so after an edit in the panel the
 * pack no longer has that key and the player reads the new English until the row is translated again. A key without
 * any translation (tests, a server whose table is empty) gives the plain English component, exactly as before.
 */
public final class Lang {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    /** A MiniMessage tag, as scripts/i18n_seed.py splits lines. */
    private static final Pattern TAG = Pattern.compile("</?[a-z_#0-9:]+>");

    /** language -> key -> text (MiniMessage for descriptions, plain for names). */
    private static volatile Map<String, Map<String, String>> translations = Map.of();

    private Lang() {}

    /** The English text of every translatable key, read from the content tables. */
    public static Map<String, String> english(Db db) {
        Map<String, String> out = new HashMap<>();
        db.read(c -> {
            for (String[] r : Db.query(c, "SELECT id, name FROM item_def", Lang::pair)) {
                out.put("item." + r[0] + ".name", r[1]);
            }
            for (String[] r : Db.query(c, "SELECT item_id || '.lore.' || position, line FROM item_lore", Lang::pair)) {
                out.put("item." + r[0], r[1]);
            }
            for (String[] r : Db.query(c, "SELECT id, name FROM enchanted_item", Lang::pair)) {
                out.put("item." + r[0] + ".name", r[1]);
            }
            for (String[] r : Db.query(c, "SELECT id, name, description FROM quest", Lang::triple)) {
                out.put("quest." + r[0] + ".name", r[1]);
                out.put("quest." + r[0] + ".desc", r[2]);
            }
            for (String[] r : Db.query(c, "SELECT id, name, description FROM quest_category", Lang::triple)) {
                out.put("category." + r[0] + ".name", r[1]);
                out.put("category." + r[0] + ".desc", r[2]);
            }
            return null;
        });
        return out;
    }

    private static String[] pair(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new String[] {rs.getString(1), rs.getString(2)};
    }

    private static String[] triple(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new String[] {rs.getString(1), rs.getString(2), rs.getString(3)};
    }

    /** Reads the translations that are still current and publishes them. Safe to call again after a data change. */
    public static void load(Db db) {
        Map<String, String> english = english(db);
        Map<String, Map<String, String>> next = new HashMap<>();
        db.read(c -> {
            for (String[] r : Db.query(c, "SELECT locale, key, source, text FROM i18n_text",
                    rs -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)})) {
                if (r[2].equals(english.get(r[1]))) {
                    next.computeIfAbsent(r[0], k -> new HashMap<>()).put(r[1], r[3]);
                }
            }
            return null;
        });
        publish(next);
    }

    /** Publishes translations given directly (tests): language -> key -> text. */
    public static void publish(Map<String, Map<String, String>> byLanguage) {
        Map<String, Map<String, String>> copy = new HashMap<>();
        byLanguage.forEach((language, rows) -> copy.put(language, Map.copyOf(rows)));
        translations = Map.copyOf(copy);
    }

    /** Forgets every translation (tests). */
    public static void clear() {
        translations = Map.of();
    }

    private static boolean translated(String key) {
        for (Map<String, String> language : translations.values()) {
            if (language.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** The first 8 hex digits of the SHA-1 of the English text: the same value as scripts/i18n_seed.py writes. */
    static String hash(String english) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1")
                .digest(english.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 4);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The resource pack key of a content key: {@code vy.item.void_sword.name.1a2b3c4d}. */
    public static String packKey(String key, String english) {
        return "vy." + key + "." + hash(english);
    }

    /** A name for an item stack: shown in each viewer's language. {@code english} is plain text. */
    public static Component name(String key, String english) {
        return translated(key) ? Component.translatable(packKey(key, english)).fallback(english) : Component.text(english);
    }

    /**
     * A description line for an item stack: shown in each viewer's language. {@code english} is MiniMessage. Every piece
     * of text between two tags is its own translatable ({@code <packKey>.<n>}), so the colours are those of the English
     * line whatever the client shows (the translation or the English fallback).
     */
    public static Component line(String key, String english) {
        if (!translated(key)) {
            return MINI.deserialize(english);
        }
        String base = packKey(key, english);
        Matcher tags = TAG.matcher(english);
        StringBuilder out = new StringBuilder();
        int from = 0;
        int piece = 0;
        while (true) {
            boolean found = tags.find();
            String text = english.substring(from, found ? tags.start() : english.length());
            if (!text.isEmpty()) {
                out.append("<lang_or:'").append(base).append('.').append(piece).append("':'")
                    .append(text.replace("\\", "\\\\").replace("'", "\\'")).append("'>");
            }
            piece++;
            if (!found) {
                break;
            }
            out.append(tags.group());
            from = tags.end();
        }
        return MINI.deserialize(out.toString());
    }

    /** The text of a key in a language ("fr", "de"...), or {@code english} when it has no current translation. */
    public static String tr(String language, String key, String english) {
        Map<String, String> rows = language == null ? null : translations.get(language);
        String text = rows == null ? null : rows.get(key);
        return text == null ? english : text;
    }

    /** The text of a key for one player. */
    public static String tr(Player player, String key, String english) {
        return tr(language(player), key, english);
    }

    /** The language code of a player's client ("fr" for fr_FR and fr_CA), or null when unknown. */
    public static String language(Player player) {
        try {
            return player == null ? null : player.locale().getLanguage();
        } catch (RuntimeException e) {
            return null; // a mock player without a locale
        }
    }
}
