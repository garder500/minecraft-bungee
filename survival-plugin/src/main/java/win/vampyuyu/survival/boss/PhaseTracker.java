package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.List;

/** Tracks which phase a boss is in from its health fraction. Phases only ever advance. */
public final class PhaseTracker {
    private final double[] thresholds;
    private int current;

    /** @param thresholds fraction at which each phase starts, strictly decreasing, first = 1.0 */
    public PhaseTracker(double[] thresholds) {
        if (thresholds.length == 0) {
            throw new IllegalArgumentException("at least one phase");
        }
        for (int i = 1; i < thresholds.length; i++) {
            if (!(thresholds[i] < thresholds[i - 1])) {
                throw new IllegalArgumentException("thresholds must decrease");
            }
        }
        this.thresholds = thresholds.clone();
    }

    public static PhaseTracker of(List<BossDefinition.Phase> phases) {
        double[] t = new double[phases.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = phases.get(i).at();
        }
        return new PhaseTracker(t);
    }

    public int current() {
        return current;
    }

    public int count() {
        return thresholds.length;
    }

    public boolean isLast() {
        return current == thresholds.length - 1;
    }

    /**
     * Feeds the current health fraction. Returns the indices of every phase entered by this update, in order
     * (a big hit can skip several), empty when nothing changed. Healing never goes back to an earlier phase.
     */
    public List<Integer> update(double fraction) {
        List<Integer> entered = new ArrayList<>();
        while (current + 1 < thresholds.length && fraction <= thresholds[current + 1]) {
            current++;
            entered.add(current);
        }
        return entered;
    }
}
