package win.vampyuyu.survival.promotion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Profile section {@code "promotion"}: what the player did (it picks the scenario) and the quest chain in progress. Plain Gson class. */
public final class PromotionData {
    /** Lifetime counters, keys in {@link PromotionRules}. */
    public Map<String, Integer> counters = new HashMap<>();
    /** Realm ids the player entered. */
    public List<String> realms = new ArrayList<>();
    /** The chain in progress, null when none. */
    public String chain;
    /** Index of the current step of {@link #chain}. */
    public int step;
    /** Progress on the current step. */
    public int progress;
    /** True once the Adept chain is done and the player has not yet chosen a specialization. */
    public boolean pendingSpec;
}
