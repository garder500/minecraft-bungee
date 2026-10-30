package win.vampyuyu.survival.dungeon;

/**
 * Catacombs level: progression beyond "floor n cleared". XP comes from cleared runs; the level is a pure function of
 * the stored XP (nothing but the XP is persisted). Level n needs {@code n^2 * level-base} XP in total.
 */
public final class Catacombs {
    private Catacombs() {}

    /** Total XP needed to be at {@code level}. */
    public static long xpForLevel(DungeonConfig cfg, int level) {
        long n = Math.max(0, level);
        return n * n * cfg.catacombsLevelBase;
    }

    /** The level of an XP total, capped at the maximum level. */
    public static int level(DungeonConfig cfg, long xp) {
        if (xp <= 0 || cfg.catacombsLevelBase <= 0) {
            return 0;
        }
        int level = (int) Math.floor(Math.sqrt((double) xp / cfg.catacombsLevelBase));
        while (level > 0 && xpForLevel(cfg, level) > xp) {
            level--;
        }
        while (level < cfg.catacombsMaxLevel && xpForLevel(cfg, level + 1) <= xp) {
            level++;
        }
        return Math.min(level, cfg.catacombsMaxLevel);
    }

    /** Catacombs XP earned by a cleared run that paid {@code combatXp} Combat XP. */
    public static long gain(DungeonConfig cfg, double combatXp) {
        return Math.max(0, Math.round(combatXp * cfg.catacombsXpShare));
    }

    /** Percent added to the coins and Combat XP of a clear at this level. */
    public static double bonusPercent(DungeonConfig cfg, int level) {
        return Math.max(0, level) * cfg.catacombsRewardPercentPerLevel;
    }
}
