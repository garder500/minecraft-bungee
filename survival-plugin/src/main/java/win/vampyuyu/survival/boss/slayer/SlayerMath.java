package win.vampyuyu.survival.boss.slayer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.api.Stat;

/** Pure slayer formulas: levels, progress, perk totals. */
public final class SlayerMath {
    private SlayerMath() {}

    /** Level (0 to levelXp.size()) for a total experience. */
    public static int level(long xp, List<Long> levelXp) {
        int level = 0;
        for (int i = 0; i < levelXp.size(); i++) {
            if (xp >= levelXp.get(i)) {
                level = i + 1;
            } else {
                break;
            }
        }
        return level;
    }

    /** Experience still needed for the next level, 0 at the maximum. */
    public static long toNext(long xp, List<Long> levelXp) {
        int level = level(xp, levelXp);
        return level >= levelXp.size() ? 0 : levelXp.get(level) - xp;
    }

    /** Fraction 0-1 of the way from the current level to the next (1 at the maximum). */
    public static double levelProgress(long xp, List<Long> levelXp) {
        int level = level(xp, levelXp);
        if (level >= levelXp.size()) {
            return 1;
        }
        long from = level == 0 ? 0 : levelXp.get(level - 1);
        long to = levelXp.get(level);
        return to <= from ? 1 : Math.max(0, Math.min(1, (double) (xp - from) / (to - from)));
    }

    /** Sum of every unlocked perk of one slayer, by stat. */
    public static Map<Stat, Double> perkTotals(List<SlayerDefinition.Perk> perks, int level) {
        Map<Stat, Double> totals = new EnumMap<>(Stat.class);
        for (SlayerDefinition.Perk perk : perks) {
            if (perk.level() <= level) {
                totals.merge(perk.stat(), perk.amount(), Double::sum);
            }
        }
        return totals;
    }

    /** Adds quest progress, capped at the requirement. Returns the new value. */
    public static double addProgress(double progress, double required, double amount) {
        if (!(amount > 0)) {
            return progress;
        }
        return Math.min(required, progress + amount);
    }

    /** Text bar like {@code ||||||....} for chat and menus. */
    public static String bar(double fraction, int width) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "|".repeat(filled) + ".".repeat(width - filled);
    }
}
