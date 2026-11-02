package win.vampyuyu.survival.paragon;

import java.util.ArrayList;
import java.util.List;

/** The small pure rules of the Paragon ranks. */
public final class ParagonRules {
    private ParagonRules() {}

    /** The highest rank whose points are reached (ranks sorted by number), 0 when none. */
    public static int rankFor(List<ParagonConfig.Rank> ranks, int points) {
        int best = 0;
        for (ParagonConfig.Rank r : ranks) {
            if (points >= r.points()) {
                best = Math.max(best, r.number());
            }
        }
        return best;
    }

    /** The ranks up to {@code rank} whose reward is of this kind, lowest first. */
    public static List<ParagonConfig.Rank> reached(List<ParagonConfig.Rank> ranks, int rank, ParagonConfig.Kind kind) {
        List<ParagonConfig.Rank> out = new ArrayList<>();
        for (ParagonConfig.Rank r : ranks) {
            if (r.number() <= rank && r.kind() == kind) {
                out.add(r);
            }
        }
        return out;
    }

    /** The sum of the UTILITY amounts with this key up to {@code rank}. */
    public static int perk(List<ParagonConfig.Rank> ranks, int rank, String key) {
        int sum = 0;
        for (ParagonConfig.Rank r : reached(ranks, rank, ParagonConfig.Kind.UTILITY)) {
            if (r.key().equalsIgnoreCase(key)) {
                sum += r.value();
            }
        }
        return sum;
    }

    /** The next rank above {@code rank}, null at the top. */
    public static ParagonConfig.Rank next(List<ParagonConfig.Rank> ranks, int rank) {
        ParagonConfig.Rank best = null;
        for (ParagonConfig.Rank r : ranks) {
            if (r.number() > rank && (best == null || r.number() < best.number())) {
                best = r;
            }
        }
        return best;
    }
}
