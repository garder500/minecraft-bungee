package win.vampyuyu.survival.api;

/**
 * The Time Tide of the Citadel of Golden Sands (GDD 3.1: "le temps local est instable"). A repeating cycle that depends
 * only on the clock, so every server and every machine agrees without sharing any state: a calm stretch, a surge when
 * time runs fast, a stall when it crawls. The Citadel itself and the Chrono Module of the Miners follow it.
 */
public final class TimeTide {
    /** The three moods of the tide, in the order of one cycle. */
    public enum Phase {
        CALM("Calm"), SURGE("Surge"), STALL("Stall");

        private final String title;

        Phase(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** One position in the cycle. {@code remainingSeconds} is how long the phase still lasts. */
    public record Reading(Phase phase, long remainingSeconds) {}

    public static final int DEFAULT_CALM = 720;
    public static final int DEFAULT_SURGE = 240;
    public static final int DEFAULT_STALL = 240;

    private final int calm;
    private final int surge;
    private final int stall;

    public TimeTide(int calmSeconds, int surgeSeconds, int stallSeconds) {
        this.calm = Math.max(1, calmSeconds);
        this.surge = Math.max(1, surgeSeconds);
        this.stall = Math.max(1, stallSeconds);
    }

    public static TimeTide defaults() {
        return new TimeTide(DEFAULT_CALM, DEFAULT_SURGE, DEFAULT_STALL);
    }

    /** Length of the whole cycle in seconds. */
    public int cycleSeconds() {
        return calm + surge + stall;
    }

    /** The phase at an instant (epoch milliseconds), and how long it lasts from then. */
    public Reading at(long epochMillis) {
        long second = Math.floorMod(Math.floorDiv(epochMillis, 1000L), (long) cycleSeconds());
        if (second < calm) {
            return new Reading(Phase.CALM, calm - second);
        }
        if (second < calm + surge) {
            return new Reading(Phase.SURGE, calm + surge - second);
        }
        return new Reading(Phase.STALL, cycleSeconds() - second);
    }
}
