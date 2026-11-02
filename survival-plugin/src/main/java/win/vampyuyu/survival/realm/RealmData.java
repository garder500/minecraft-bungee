package win.vampyuyu.survival.realm;

import java.util.HashMap;
import java.util.Map;

/** The "realm" section of a player profile: pity counters, key {@code <resource>:<kind>}. */
public final class RealmData {
    public Map<String, Integer> misses = new HashMap<>();
}
