package win.vampyuyu.survival.resonance;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;

/** Pure rules of the Memory Shards and the boss guarantee. No Bukkit. */
public final class ResonanceMath {
    private ResonanceMath() {}

    /**
     * Shards of one cleared run: the boss base, +{@code rankBonus} of it per rank above I, doubled by the first
     * clear of the day (GDD 15.5). Rounded to the nearest shard, never below 1.
     */
    public static long killShards(long base, int rank, double rankBonus, boolean firstClearToday, double firstMultiplier) {
        double value = base * (1 + Math.max(0, rankBonus) * (win.vampyuyu.survival.api.Resonance.clamp(rank) - 1));
        if (firstClearToday) {
            value *= Math.max(1, firstMultiplier);
        }
        return Math.max(1, Math.round(value));
    }

    /** A late defeat still pays a small fraction of a clear (GDD 15.4), at least 1 once any progress was made. */
    public static long defeatShards(long base, int rank, double rankBonus, double fraction, double progress) {
        double p = Math.max(0, Math.min(1, progress));
        if (p <= 0 || fraction <= 0) {
            return 0;
        }
        double clear = killShards(base, rank, rankBonus, false, 1);
        return Math.max(1, Math.round(clear * Math.min(1, fraction) * p));
    }

    /** Highest rank a player may take on a boss: one above their best clear, rank I always open. */
    public static int unlockedRank(int bestCleared) {
        return win.vampyuyu.survival.api.Resonance.clamp(Math.max(0, bestCleared) + 1);
    }

    /** The rank a fight gets: the chosen rank, held back to what is unlocked on that boss. */
    public static int effectiveRank(int selected, int bestCleared) {
        return Math.min(win.vampyuyu.survival.api.Resonance.clamp(selected), unlockedRank(bestCleared));
    }

    /** Runs the component is guaranteed within: 4 normally, 3 from rank III up (GDD 15.2). */
    public static int pityLimit(int rank, int normal, int high, int highFrom) {
        return win.vampyuyu.survival.api.Resonance.clamp(rank) >= highFrom ? Math.min(normal, high) : normal;
    }

    /**
     * True when the run that just ended must hand out the component: {@code dryBefore} runs came without one, so this
     * run is number {@code dryBefore + 1} and the guarantee is {@code limit} runs at the latest.
     */
    public static boolean pityDue(int dryBefore, int limit) {
        return dryBefore + 1 >= Math.max(1, limit);
    }

    public static String day(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** ISO week key, {@code 2026-W45}. */
    public static String week(long epochMillis) {
        LocalDate d = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
