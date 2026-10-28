package win.vampyuyu.survival.wings;

/** Flight energy of one player: a bar that drains in the air, refills on the ground, with a "run dry" lockout. Pure logic. */
public final class Energy {
    private double value;
    private double capacity;
    private boolean exhausted;

    public Energy(double capacity) {
        this(capacity, capacity);
    }

    public Energy(double capacity, double value) {
        this.capacity = Math.max(1, capacity);
        this.value = Math.max(0, Math.min(this.capacity, value));
    }

    public double value() {
        return value;
    }

    public double capacity() {
        return capacity;
    }

    public double fraction() {
        return value / capacity;
    }

    public boolean full() {
        return value >= capacity - 1e-9;
    }

    /** True after running dry until {@link #regen} refilled up to the resume threshold. */
    public boolean exhausted() {
        return exhausted;
    }

    /** Changes the capacity (the stat changed). Energy never exceeds it. A bigger bar keeps the current amount. */
    public void setCapacity(double capacity) {
        this.capacity = Math.max(1, capacity);
        this.value = Math.min(this.value, this.capacity);
    }

    /** Drains. Returns true when the energy just ran out (flight must stop now). */
    public boolean drain(double amount) {
        if (amount <= 0 || exhausted) {
            return false;
        }
        value = Math.max(0, value - amount);
        if (value <= 0) {
            exhausted = true;
            return true;
        }
        return false;
    }

    /** Refills; clears the exhausted lockout once {@code resumeThreshold} is reached. */
    public void regen(double amount, double resumeThreshold) {
        if (amount <= 0) {
            return;
        }
        value = Math.min(capacity, value + amount);
        if (exhausted && value >= Math.min(resumeThreshold, capacity)) {
            exhausted = false;
        }
    }

    /** Forces a full bar (admin tools, upgrades). */
    public void fill() {
        value = capacity;
        exhausted = false;
    }

    /** Restores a saved amount (clamped); an empty bar restores as exhausted. */
    public void restore(double saved) {
        value = Math.max(0, Math.min(capacity, saved));
        exhausted = value <= 0;
    }
}
