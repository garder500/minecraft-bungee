package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Profile section {@code quests}. Plain Gson class, mutated on the main thread. */
public final class QuestData {
    /** Tutorial progress by quest id (only quests that were active). */
    public Map<String, Integer> progress = new HashMap<>();
    /** Completed (and paid) tutorial quest ids, in completion order. */
    public Set<String> completed = new LinkedHashSet<>();
    /** True once the player's older progress was carried over into the quest plan (see QuestService#migrate). */
    public boolean planMigrated;
    /** Quest the sidebar tracks: a tutorial id, {@code daily:<id>}, or empty for "first active". */
    public String tracked = "";

    /** Day key (ISO date) the daily fields belong to. */
    public String dailyDay = "";
    public List<String> dailyIds = new ArrayList<>();
    public Map<String, Integer> dailyProgress = new HashMap<>();
    public Set<String> dailyDone = new LinkedHashSet<>();
    /** True once the all-dailies bonus of {@link #dailyDay} was paid. */
    public boolean dailyBonusPaid;
    /** Consecutive days with every daily completed, as of {@link #streakDay}. */
    public int streak;
    /** Day key of the last day all dailies were completed. */
    public String streakDay = "";
    public int dailyTotal;

    /** Rewards earned while the economy / skills feature was unavailable, paid on the next join or menu open. */
    public double pendingCoins;
    public Map<String, Double> pendingXp = new HashMap<>();
    public Map<String, Integer> pendingItems = new HashMap<>();
}
