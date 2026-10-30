package win.vampyuyu.survival.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profile section {@code "dungeon"}: statistics, best times and rewards waiting to be claimed. */
public final class DungeonData {
    /** Per floor statistics. */
    public static final class FloorStats {
        public int runs;
        public int clears;
        /** Best clear time in milliseconds, 0 = none. */
        public long bestMillis;
        public int bestScore;
        public String bestGrade = "";
        public int sPlusCount;
        public int secretsFound;
        public int mobsKilled;
        public int deaths;
    }

    /** One item waiting in the claim chest. {@code item} is a custom item id or {@code mc:MATERIAL}. */
    public static final class Pending {
        public String item = "";
        public int amount;

        public Pending() {}

        public Pending(String item, int amount) {
            this.item = item;
            this.amount = amount;
        }
    }

    public Map<String, FloorStats> floors = new LinkedHashMap<>();
    public List<Pending> pending = new ArrayList<>();
    public int totalRuns;
    public int totalClears;
    public int highestFloorCleared;
    public long totalSecrets;
    public long totalKills;
    public long totalDeaths;
    public long lastRunMillis;

    public FloorStats floor(int n) {
        return floors.computeIfAbsent(Integer.toString(n), k -> new FloorStats());
    }

    public boolean cleared(int n) {
        FloorStats s = floors.get(Integer.toString(n));
        return s != null && s.clears > 0;
    }

    /** Merges items into the pending list (same item stacks together). */
    public void addPending(String item, int amount) {
        if (amount <= 0 || item == null || item.isBlank()) {
            return;
        }
        for (Pending p : pending) {
            if (p.item.equals(item)) {
                p.amount += amount;
                return;
            }
        }
        pending.add(new Pending(item, amount));
    }

    public int pendingCount() {
        int n = 0;
        for (Pending p : pending) {
            n += p.amount;
        }
        return n;
    }

    /** Records a finished run. */
    public void record(int floor, boolean cleared, long millis, int score, Grade grade, int secrets, int kills, int deaths) {
        FloorStats s = floor(floor);
        s.runs++;
        totalRuns++;
        s.secretsFound += secrets;
        s.mobsKilled += kills;
        s.deaths += deaths;
        totalSecrets += secrets;
        totalKills += kills;
        totalDeaths += deaths;
        lastRunMillis = System.currentTimeMillis();
        if (!cleared) {
            return;
        }
        s.clears++;
        totalClears++;
        highestFloorCleared = Math.max(highestFloorCleared, floor);
        if (s.bestMillis == 0 || millis < s.bestMillis) {
            s.bestMillis = millis;
        }
        if (score > s.bestScore) {
            s.bestScore = score;
            s.bestGrade = grade.label();
        }
        if (grade == Grade.S_PLUS) {
            s.sPlusCount++;
        }
    }
}
