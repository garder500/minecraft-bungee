package win.vampyuyu.survival.menu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The shared quest state of one island (file {@code islandquests/<island id>.json}). The main quest chain advances here,
 * once for the whole island; {@link #quests} holds its progress and completed ids (the engine edits it like a player's
 * {@link QuestData}). Everything else is the bookkeeping of who earned what. Plain Gson class, main thread only.
 */
public final class IslandQuests {
    /** Progress / completed / forged tally of the island-scope quests. */
    public QuestData quests = new QuestData();
    /** Quest id -> player uuid -> what the player added (actions, or the best level reported for absolute quests). */
    public Map<String, Map<String, Integer>> contributions = new HashMap<>();
    /** Quest id -> uuids that were paid or settled for it (a reward is never paid twice). */
    public Map<String, Set<String>> settled = new HashMap<>();
    /** Player uuid -> quest ids whose reward waits for that player to be online (a deferred reward). */
    public Map<String, Set<String>> owed = new LinkedHashMap<>();
    /** Player uuid -> seconds spent on this island while online (counted in steps of the presence tick). */
    public Map<String, Integer> seconds = new HashMap<>();
    /** Day key of {@link #coinsToday}. */
    public String coinDay = "";
    /** Coins the island's quests paid on {@link #coinDay}. */
    public double coinsToday;
    /** Members the record has seen (the ones present when it was created get no arrival kit, later ones do, once). */
    public Set<String> known = new HashSet<>();
}
