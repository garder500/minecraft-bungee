package win.vampyuyu.survival.boss;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference counts of plugin chunk tickets. A plugin holds at most ONE ticket per chunk, so two fights in the same
 * chunk must share it: the real ticket is added by the first holder and removed by the last one. Main thread only.
 */
final class TicketCounter {
    private final Map<String, Integer> counts = new HashMap<>();

    /** True when this is the first holder (the caller adds the real ticket). */
    boolean acquire(String key) {
        return counts.merge(key, 1, Integer::sum) == 1;
    }

    /** True when this was the last holder (the caller removes the real ticket). */
    boolean release(String key) {
        Integer left = counts.get(key);
        if (left == null) {
            return false;
        }
        if (left <= 1) {
            counts.remove(key);
            return true;
        }
        counts.put(key, left - 1);
        return false;
    }

    int holders(String key) {
        return counts.getOrDefault(key, 0);
    }
}
