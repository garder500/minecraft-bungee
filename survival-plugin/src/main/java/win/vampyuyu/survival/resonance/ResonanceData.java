package win.vampyuyu.survival.resonance;

import java.util.HashMap;
import java.util.Map;

/** Profile section {@code "resonance"}: a player's Memory Shards and Resonance progress. Plain Gson class. */
public final class ResonanceData {
    /** Memory Shards held. */
    public long shards;
    /** Memory Shards earned since the beginning (never spent), for the progression display. */
    public long earned;
    /** The rank chosen for the next Bastion fights, 1 to 5. */
    public int selected = 1;
    /** Highest Resonance rank cleared, by boss id. */
    public Map<String, Integer> best = new HashMap<>();
    /** Bastion clears by boss id (every rank). */
    public Map<String, Integer> clears = new HashMap<>();
    /** Runs since the last component, by boss id (boss pity, GDD 15.2). */
    public Map<String, Integer> dry = new HashMap<>();
    /** Bosses whose component the player already owned once: only those can be forged with Shards (GDD 15.3). */
    public java.util.List<String> found = new java.util.ArrayList<>();
    /** Components forged with Shards, by boss id. */
    public Map<String, Integer> forged = new HashMap<>();
    /** UTC day of the last "first clear of the day" bonus, {@code yyyy-MM-dd}. */
    public String lastFirstClear = "";
    /** ISO week of the forging counter, {@code yyyy-Www}. */
    public String forgeWeek = "";
    /** Components forged during {@link #forgeWeek}. */
    public int forgedThisWeek;
}
