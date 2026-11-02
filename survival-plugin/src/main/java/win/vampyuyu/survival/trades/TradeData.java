package win.vampyuyu.survival.trades;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Profile section {@code "trades"}: the trades a player practises and the experience earned in each. Plain Gson class. */
public final class TradeData {
    /** Trade ids ({@code artisan}, {@code warden}) in the order they were taken. */
    public Set<String> trades = new LinkedHashSet<>();
    /** Experience by trade id, earned by using the trade. */
    public Map<String, Long> xp = new HashMap<>();
}
