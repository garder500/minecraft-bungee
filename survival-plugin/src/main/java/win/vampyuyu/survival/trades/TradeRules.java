package win.vampyuyu.survival.trades;

import win.vampyuyu.survival.api.Trade;

/** The pure rules of the trades: levels, how many a player may hold, and the choice. */
public final class TradeRules {
    /** Result of {@link #choose}. */
    public enum Choice { OK, ALREADY, FULL }

    private TradeRules() {}

    /** Level 1 to 5 for this experience (the trade is held). */
    public static int level(long xp, TradeConfig cfg) {
        int level = 1;
        for (long needed : cfg.levelXp()) {
            if (xp >= needed) {
                level++;
            }
        }
        return level;
    }

    /** Level of a trade in a profile, 0 when it is not practised. */
    public static int level(TradeData data, Trade trade, TradeConfig cfg) {
        return data.trades.contains(trade.id()) ? level(data.xp.getOrDefault(trade.id(), 0L), cfg) : 0;
    }

    /** One trade, and a second one once a trade reached the Master level (open question 4 of the GDD, proposal kept). */
    public static int slots(TradeData data, TradeConfig cfg) {
        if (cfg.secondTradeAtLevel() <= 0) {
            return 1;
        }
        for (Trade t : Trade.values()) {
            if (level(data, t, cfg) >= cfg.secondTradeAtLevel()) {
                return 2;
            }
        }
        return 1;
    }

    public static Choice choose(TradeData data, Trade trade, TradeConfig cfg) {
        if (data.trades.contains(trade.id())) {
            return Choice.ALREADY;
        }
        if (data.trades.size() >= slots(data, cfg)) {
            return Choice.FULL;
        }
        data.trades.add(trade.id());
        data.xp.putIfAbsent(trade.id(), 0L);
        return Choice.OK;
    }

    /** Experience still needed for the next level, 0 at the top. */
    public static long toNext(long xp, TradeConfig cfg) {
        int level = level(xp, cfg);
        return level >= Trade.MAX_LEVEL ? 0 : Math.max(0, cfg.levelXp()[level - 1] - xp);
    }
}
