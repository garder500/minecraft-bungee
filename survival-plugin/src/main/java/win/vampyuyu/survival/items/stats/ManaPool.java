package win.vampyuyu.survival.items.stats;

/**
 * Mana and overflow mana of one player (pure). Regular mana is capped at the intelligence stat and regenerates;
 * overflow mana sits on top of it (capped at a percentage of the max), does not regenerate and slowly decays.
 * Spending takes regular mana first, then overflow.
 */
public final class ManaPool {
    private double mana;
    private double overflow;
    private boolean full = true;

    public double mana() { return mana; }

    public double overflow() { return overflow; }

    /** Fills the pool (login, respawn). */
    public synchronized void fill(double max) {
        mana = Math.max(0, max);
        overflow = 0;
        full = false;
    }

    /** Sets the pool to saved values (login after a logout): not full, not pristine. */
    public synchronized void load(double manaValue, double overflowValue) {
        mana = Math.max(0, manaValue);
        overflow = Math.max(0, overflowValue);
        full = false;
    }

    /** True until the first {@link #fill} or tick set a value for this player. */
    public synchronized boolean pristine() { return full; }

    /**
     * Advances the pool by {@code seconds}: regenerates {@code regenPercent}% of {@code max} per second, clamps to
     * the (possibly shrunk) max, and decays overflow.
     */
    public synchronized void tick(double max, double seconds, double regenPercent, double overflowMaxPercent,
            double decayPercent) {
        if (full) {
            mana = max;
            full = false;
        }
        if (mana < max) {
            mana = Math.min(max, mana + max * regenPercent / 100.0 * seconds);
        } else if (mana > max) {
            mana = max;
        }
        double cap = max * overflowMaxPercent / 100.0;
        if (overflow > 0) {
            overflow = Math.max(0, overflow - max * decayPercent / 100.0 * seconds);
        }
        overflow = Math.min(overflow, cap);
    }

    /** Spends {@code cost}. Returns false (and spends nothing) when mana plus overflow is not enough. */
    public synchronized boolean spend(double cost) {
        if (cost <= 0) {
            return true;
        }
        if (mana + overflow + 1e-9 < cost) {
            return false;
        }
        double fromMana = Math.min(mana, cost);
        mana -= fromMana;
        overflow = Math.max(0, overflow - (cost - fromMana));
        return true;
    }

    /** True when {@code cost} could be paid. */
    public synchronized boolean canAfford(double cost) {
        return mana + overflow + 1e-9 >= cost;
    }

    /** Restores regular mana (never above max). Returns the amount actually restored. */
    public synchronized double restore(double amount, double max) {
        double before = mana;
        mana = Math.min(max, mana + Math.max(0, amount));
        return mana - before;
    }

    /** Adds overflow mana, limited to {@code max * overflowMaxPercent / 100}. Returns the amount added. */
    public synchronized double addOverflow(double amount, double max, double overflowMaxPercent) {
        double before = overflow;
        overflow = Math.min(max * overflowMaxPercent / 100.0, overflow + Math.max(0, amount));
        return overflow - before;
    }
}
