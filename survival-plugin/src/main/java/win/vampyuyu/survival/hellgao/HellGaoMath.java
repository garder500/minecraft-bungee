package win.vampyuyu.survival.hellgao;

import java.util.List;
import java.util.Set;

/** Pure rules of Hell Gao (GDD 19.4): the window between two pulses, Ascension ranks, lore order. */
public final class HellGaoMath {
    public static final int MAX_ASCENSION = 5;
    /** Guardian health multiplier of Ascension I and V (GDD 19.4: x3.5 to x5). */
    public static final double HP_FIRST = 3.5;
    public static final double HP_LAST = 5.0;
    /** Damage multiplier of Ascension I and V (GDD 19.4: x2 to x2.6). */
    public static final double DAMAGE_FIRST = 2.0;
    public static final double DAMAGE_LAST = 2.6;

    private HellGaoMath() {}

    /**
     * True when the clock is in the window: the last {@code windowSeconds} of every {@code periodSeconds} cycle, so the
     * window sits between the end of one pulse and the start of the next.
     */
    public static boolean windowOpen(long epochSeconds, int periodSeconds, int windowSeconds) {
        if (periodSeconds <= 0 || windowSeconds <= 0) {
            return false;
        }
        if (windowSeconds >= periodSeconds) {
            return true;
        }
        return Math.floorMod(epochSeconds, periodSeconds) >= periodSeconds - windowSeconds;
    }

    /** Seconds until the window opens (0 when open), -1 when it never opens. */
    public static long secondsToOpen(long epochSeconds, int periodSeconds, int windowSeconds) {
        if (periodSeconds <= 0 || windowSeconds <= 0) {
            return -1;
        }
        if (windowOpen(epochSeconds, periodSeconds, windowSeconds)) {
            return 0;
        }
        return periodSeconds - windowSeconds - Math.floorMod(epochSeconds, periodSeconds);
    }

    /** Seconds the open window still lasts (0 when closed). */
    public static long secondsToClose(long epochSeconds, int periodSeconds, int windowSeconds) {
        if (!windowOpen(epochSeconds, periodSeconds, windowSeconds)) {
            return 0;
        }
        if (windowSeconds >= periodSeconds) {
            return Long.MAX_VALUE;
        }
        return periodSeconds - Math.floorMod(epochSeconds, periodSeconds);
    }

    /**
     * The window of a Celestial Breath: no event is running and the next pulsation is due within {@code windowSeconds}.
     *
     * @param millisToNext milliseconds until the next pulsation, -1 when none is scheduled
     */
    public static boolean breathWindowOpen(boolean eventActive, long millisToNext, int windowSeconds) {
        return !eventActive && windowSeconds > 0 && millisToNext >= 0 && millisToNext <= windowSeconds * 1000L;
    }

    public static int clampRank(int rank) {
        return Math.max(0, Math.min(MAX_ASCENSION, rank));
    }

    /** Guardian health multiplier of an Ascension rank: 1 at rank 0 (the plain fight), then x3.5 rising to x5. */
    public static double hpMultiplier(int rank) {
        int r = clampRank(rank);
        return r == 0 ? 1.0 : HP_FIRST + (HP_LAST - HP_FIRST) * (r - 1) / (MAX_ASCENSION - 1);
    }

    /** Guardian damage multiplier of an Ascension rank: 1 at rank 0, then x2 rising to x2.6. */
    public static double damageMultiplier(int rank) {
        int r = clampRank(rank);
        return r == 0 ? 1.0 : DAMAGE_FIRST + (DAMAGE_LAST - DAMAGE_FIRST) * (r - 1) / (MAX_ASCENSION - 1);
    }

    /** The rank whose health multiplier is this one, 0 when none (reads a Scaling back). */
    public static int rankOfHp(double hpMultiplier) {
        for (int r = MAX_ASCENSION; r >= 1; r--) {
            if (Math.abs(hpMultiplier(r) - hpMultiplier) < 1e-9) {
                return r;
            }
        }
        return 0;
    }

    /**
     * Ascension needs Resonance V (the caller says whether the player has it) and goes one rank at a time: rank N opens
     * once rank N-1 was cleared. Rank 0 is always open.
     */
    public static boolean canSelect(int rank, int bestCleared, boolean resonanceVDone) {
        int r = clampRank(rank);
        return r == 0 || (resonanceVDone && r <= Math.max(0, bestCleared) + 1);
    }

    /** The highest rank a player may select right now. */
    public static int maxSelectable(int bestCleared, boolean resonanceVDone) {
        return resonanceVDone ? Math.min(MAX_ASCENSION, Math.max(0, bestCleared) + 1) : 0;
    }

    /** The first lore fragment (in the given order) the player does not know, or null when all are known. */
    public static String nextLore(List<String> orderedIds, Set<String> known) {
        for (String id : orderedIds) {
            if (!known.contains(id)) {
                return id;
            }
        }
        return null;
    }
}
