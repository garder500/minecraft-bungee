package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Profile section {@code "boss"}: everything the boss feature remembers about a player. Plain Gson class. */
public final class BossData {
    /** Slayer experience by slayer id. */
    public Map<String, Long> slayerXp = new HashMap<>();
    /** Slayer bosses killed, key {@code <slayer>:<tier>}. */
    public Map<String, Integer> slayerKills = new HashMap<>();
    /** Regular boss kills by boss id. */
    public Map<String, Integer> kills = new HashMap<>();
    /** Dry streaks for pity, key {@code <bossId>|<item>}. */
    public Map<String, Integer> dry = new HashMap<>();
    /** Highest slayer level whose reward message was already shown, by slayer id. */
    public Map<String, Integer> seenLevel = new HashMap<>();
    /** The running slayer quest, null when none. */
    public Quest quest;
    /** Loot waiting for the player to log in (base64 of ItemStack.serializeAsBytes). */
    public List<String> pending = new ArrayList<>();
    /** Wings sources earned while offline (WingsApi.grantOnce on the next login). */
    public List<String> pendingWings = new ArrayList<>();
    /** Combat xp earned while offline, by source (SkillsApi.addXp on the next login). */
    public Map<String, Double> pendingXp = new HashMap<>();
    /** Kills made while offline, {@code <bossId>|<epochMillis>}. */
    public List<String> pendingKills = new ArrayList<>();
    /** Pity changes of offline kills, in order: {@code +<key>} adds a dry kill, {@code =<key>} resets the streak. */
    public List<String> pendingDry = new ArrayList<>();
    /** Recent kill times by boss id (epoch millis), used for diminishing coin and xp returns. */
    public Map<String, List<Long>> recentKills = new HashMap<>();

    /** A paid slayer quest. */
    public static final class Quest {
        public static final String COLLECTING = "collecting";
        public static final String READY = "ready";
        public static final String FIGHTING = "fighting";

        public String slayer = "";
        public int tier = 1;
        public double progress;
        public double required = 1;
        public String state = COLLECTING;
        public double paid;
        public long startedAt;

        public Quest() {}

        public Quest(String slayer, int tier, double required, double paid, long startedAt) {
            this.slayer = slayer;
            this.tier = tier;
            this.required = required;
            this.paid = paid;
            this.startedAt = startedAt;
        }
    }
}
