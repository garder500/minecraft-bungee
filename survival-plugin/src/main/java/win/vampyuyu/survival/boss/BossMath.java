package win.vampyuyu.survival.boss;

/** Pure fight formulas: health scaling with the group size, enrage, leash. */
public final class BossMath {
    private BossMath() {}

    /** Health multiplier for {@code participants} players: {@code 1 + perPlayer * (participants - minPlayers)}, capped. */
    public static double hpMultiplier(int participants, int minPlayers, double perPlayer, double cap) {
        int extra = Math.max(0, participants - Math.max(1, minPlayers));
        return Math.min(Math.max(1.0, cap), 1.0 + Math.max(0, perPlayer) * extra);
    }

    /**
     * Damage multiplier from the enrage timer.
     *
     * @param elapsedTicks ticks since the fight began
     * @param enrageAfterTicks ticks until the enrage, 0 = never
     * @param base multiplier at the start of the enrage
     * @param growthPerMinute extra multiplier for each further minute
     */
    public static double enrageMultiplier(long elapsedTicks, long enrageAfterTicks, double base, double growthPerMinute) {
        if (enrageAfterTicks <= 0 || elapsedTicks < enrageAfterTicks) {
            return 1.0;
        }
        double minutes = (elapsedTicks - enrageAfterTicks) / 1200.0;
        return Math.max(1.0, base) + Math.max(0, growthPerMinute) * minutes;
    }

    /** True when the boss strayed beyond the leash or sank below the floor. */
    public static boolean outsideLeash(double dx, double dz, double leash, double y, double minY) {
        return dx * dx + dz * dz > leash * leash || y < minY;
    }

    /** Damage kept after a defense value: {@code 100 / (100 + defense)}, so 100 defense halves the damage. */
    public static double defenseMultiplier(double defense) {
        return 100.0 / (100.0 + Math.max(0, defense));
    }

    /** Health fraction 0-1 of a virtual health pool. */
    public static double fraction(double hp, double max) {
        return max <= 0 ? 0 : Math.max(0, Math.min(1, hp / max));
    }
}
