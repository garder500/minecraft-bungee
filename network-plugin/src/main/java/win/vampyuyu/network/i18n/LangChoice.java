package win.vampyuyu.network.i18n;

import java.util.Locale;

/**
 * Which language a player reads: the one chosen with /lang if any, else the one of their Minecraft client,
 * else English. Pure logic, no Bukkit, so it is unit tested.
 */
public final class LangChoice {
    private LangChoice() {}

    /**
     * @param stored the value saved by /lang (a tag or code), may be null or garbage
     * @param client the locale of the client, may be null
     */
    public static Lang resolve(String stored, Locale client) {
        Lang chosen = Lang.parse(stored);
        if (chosen != null) return chosen;
        Lang fromClient = Lang.of(client);
        return fromClient != null ? fromClient : Lang.DEFAULT;
    }
}
