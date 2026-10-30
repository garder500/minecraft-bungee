package win.vampyuyu.survival.minions;

import java.util.function.BooleanSupplier;

/**
 * The time integration of a minion, with no Bukkit and no data classes so it can be tested to the millisecond.
 *
 * <p>Work is measured in "work milliseconds": one millisecond at normal speed is one work millisecond, a minion
 * 25 percent faster does 1.25 per real millisecond. A cycle needs {@code cycleMs} work milliseconds. Fuel is a
 * separate budget burned in real milliseconds (scaled by the burn rate) and only while the minion is working, so
 * a fuel expiring in the middle of an offline window changes the speed exactly at that moment.
 */
public final class Production {
    /** Safety valve against a broken config (a zero cycle time): at most this many cycles per call. */
    public static final int MAX_CYCLES = 500_000;

    private Production() {}

    /** Mutable clock of one minion. */
    public static final class Clock {
        /** Work done on the current cycle. */
        public double progress;
        /** Fuel left, in burn milliseconds (0 or less means none). */
        public double fuelMs;

        public Clock(double progress, double fuelMs) {
            this.progress = progress;
            this.fuelMs = fuelMs;
        }
    }

    /**
     * Result of {@link #advance}.
     *
     * @param cycles cycles that completed and were stored
     * @param stalled true when the last finished cycle could not be stored (storage full): the cycle stays ready
     */
    public record Outcome(int cycles, boolean stalled) {}

    /**
     * Lets {@code elapsedMs} of real time pass.
     *
     * @param speedPlain speed multiplier without fuel (1 = normal)
     * @param speedFuel speed multiplier while fuel burns
     * @param burn fuel burn rate (1 = one fuel millisecond per millisecond, 0.8 = lasts 25 percent longer)
     * @param produce called once per finished cycle; false when the product does not fit (the cycle is then
     *     kept ready and time stops being used)
     */
    public static Outcome advance(Clock clock, double elapsedMs, double cycleMs, double speedPlain, double speedFuel,
            double burn, BooleanSupplier produce) {
        double remaining = Math.max(0, elapsedMs);
        double cycle = Math.max(1, cycleMs);
        double rate = Math.max(1e-9, burn);
        int cycles = 0;
        while (remaining > 1e-9 || clock.progress >= cycle) {
            boolean fuelOn = clock.fuelMs > 1e-9;
            double speed = Math.max(1e-9, fuelOn ? speedFuel : speedPlain);
            double need = Math.max(0, cycle - clock.progress);
            double window = fuelOn ? Math.min(remaining, clock.fuelMs / rate) : remaining;
            double done = window * speed;
            if (done < need - 1e-9) {
                clock.progress += done;
                if (fuelOn) {
                    clock.fuelMs -= window * rate;
                    if (clock.fuelMs < 1e-6) {
                        clock.fuelMs = 0;
                    }
                }
                remaining -= window;
                if (!fuelOn || remaining <= 1e-9) {
                    break;
                }
                continue;
            }
            double used = need / speed;
            if (fuelOn) {
                clock.fuelMs -= used * rate;
                if (clock.fuelMs < 1e-6) {
                    clock.fuelMs = 0;
                }
            }
            remaining = Math.max(0, remaining - used);
            if (!produce.getAsBoolean()) {
                clock.progress = cycle;
                return new Outcome(cycles, true);
            }
            clock.progress = 0;
            if (++cycles >= MAX_CYCLES) {
                break;
            }
        }
        return new Outcome(cycles, false);
    }

    /** Real milliseconds a full cycle takes at the given speed (ignores fuel). */
    public static double cycleSeconds(double cycleMs, double speed) {
        return cycleMs / Math.max(1e-9, speed) / 1000d;
    }
}
