package win.vampyuyu.survival.trades;

import java.util.ArrayList;
import java.util.List;
import win.vampyuyu.survival.api.Trade;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * The {@code trades.*} setting rows. {@code levelXp} holds the experience needed for levels 2 to 5 (level 1 is the
 * moment the trade is taken). All numbers are guesses to measure in play (GDD 16.2) except the base effects of 11.3.
 */
public record TradeConfig(long[] levelXp, int secondTradeAtLevel, double forgeFactor, double minerEnergyFactor,
        double yieldFactor, double mendFraction, double mendRange, int mendLevel, long mendCooldownSeconds,
        int overclockLevel, long overclockCooldownSeconds, long overclockSeconds, double overclockSpeed,
        double overclockRange, List<String> problems) {

    public static TradeConfig load(Db db) {
        return parse(Settings.load(db, "trades"));
    }

    public static TradeConfig parse(Settings s) {
        List<String> problems = new ArrayList<>(s.problems());
        long[] xp = new long[Trade.MAX_LEVEL - 1];
        List<String> rows = s.getList("level-xp");
        long previous = 0;
        for (int i = 0; i < xp.length; i++) {
            long value;
            try {
                value = i < rows.size() ? Long.parseLong(rows.get(i).trim()) : Long.MAX_VALUE;
            } catch (NumberFormatException e) {
                problems.add("trades.level-xp: bad number '" + rows.get(i) + "'");
                value = Long.MAX_VALUE;
            }
            if (value <= previous) {
                if (i < rows.size()) {
                    problems.add("trades.level-xp: the thresholds must increase (row " + (i + 1) + ")");
                }
                value = Long.MAX_VALUE;
            }
            xp[i] = value;
            previous = value;
        }
        return new TradeConfig(xp, Math.max(0, Math.min(Trade.MAX_LEVEL, s.getInt("second-trade-at-level", 5))),
            clamp(s.getDouble("artisan.forge-duration", 0.85), 0.1, 1), clamp(s.getDouble("artisan.miner-energy", 0.90), 0.1, 1),
            clamp(s.getDouble("warden.yield", 1.20), 1, 3), clamp(s.getDouble("warden.mend-fraction", 0.15), 0.01, 1),
            clamp(s.getDouble("warden.mend-range", 8), 2, 32), Math.max(1, Math.min(5, s.getInt("warden.mend-level", 1))),
            Math.max(1, s.getLong("warden.mend-cooldown-seconds", 30)),
            Math.max(1, Math.min(5, s.getInt("artisan.overclock-level", 1))),
            Math.max(1, s.getLong("artisan.overclock-cooldown-seconds", 300)),
            Math.max(1, s.getLong("artisan.overclock-seconds", 30)), clamp(s.getDouble("artisan.overclock-speed", 2), 1, 4),
            clamp(s.getDouble("artisan.overclock-range", 8), 2, 32), List.copyOf(problems));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
