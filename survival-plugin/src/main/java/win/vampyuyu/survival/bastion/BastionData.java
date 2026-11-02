package win.vampyuyu.survival.bastion;

import java.util.LinkedHashMap;
import java.util.Map;

/** Profile section {@code "bastion"}: runs, clears, defeats and best time per Bastion (Shards live in the resonance section). */
public final class BastionData {
    /** Per Bastion statistics. */
    public static final class Stats {
        public int runs;
        public int clears;
        public int defeats;
        /** Best clear time in milliseconds, 0 = none. */
        public long bestMillis;
    }

    public Map<String, Stats> bastions = new LinkedHashMap<>();

    public Stats of(String id) {
        return bastions.computeIfAbsent(id, k -> new Stats());
    }

    public boolean cleared(String id) {
        Stats s = bastions.get(id);
        return s != null && s.clears > 0;
    }

    public void recordClear(String id, long millis) {
        Stats s = of(id);
        s.runs++;
        s.clears++;
        if (s.bestMillis == 0 || millis < s.bestMillis) {
            s.bestMillis = millis;
        }
    }

    public void recordDefeat(String id) {
        Stats s = of(id);
        s.runs++;
        s.defeats++;
    }
}
