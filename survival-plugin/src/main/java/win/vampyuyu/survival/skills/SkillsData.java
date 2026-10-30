package win.vampyuyu.survival.skills;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Profile section {@code skills}: experience per skill key, collection counters per collection id and the claimed
 * rewards. Plain Gson class: new fields need a default so old documents still load.
 */
public final class SkillsData {
    public int version = 1;
    /** Skill key ({@code "mining"}) to total experience. */
    public Map<String, Double> xp = new HashMap<>();
    /** Collection id ({@code "COBBLESTONE"}) to collected amount. */
    public Map<String, Long> collections = new HashMap<>();
    /** Claimed rewards: {@code "lvl:mining:12"} and {@code "col:COBBLESTONE:3"}. */
    public Set<String> claimed = new HashSet<>();
    /** Recipes already announced as unlocked (so a login does not repeat the message). */
    public Set<String> seenRecipes = new HashSet<>();

    public double xpOf(String skillKey) {
        Double value = xp.get(skillKey);
        return value == null ? 0 : value;
    }

    public long collected(String collectionId) {
        Long value = collections.get(collectionId);
        return value == null ? 0 : value;
    }

    public static String levelClaim(String skillKey, int level) {
        return "lvl:" + skillKey + ":" + level;
    }

    public static String tierClaim(String collectionId, int tier) {
        return "col:" + collectionId + ":" + tier;
    }
}
