package win.vampyuyu.survival.menu;

import java.util.LinkedHashSet;
import java.util.Set;

/** Profile section {@code fairy_souls}. */
public final class FairyData {
    /** Ids of the souls this player collected (island souls share one id across islands). */
    public Set<String> found = new LinkedHashSet<>();
    /** Milestones already paid (count / every). Raised BEFORE paying so a reward is never paid twice. */
    public int paidMilestones;
    /** Coins of milestones that could not be paid because the economy feature was unavailable. */
    public double pendingCoins;
}
