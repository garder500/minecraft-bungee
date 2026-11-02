package win.vampyuyu.survival.api;

/**
 * Hell Gao, the Anomaly (GDD 19.4). Implemented by the {@code hellgao} feature. Fragments and keys belong to the Celestial
 * Breath ({@link BreathApi}); the Rift opens in the stretch just before a pulsation, which this API can also force.
 */
public interface HellGaoApi {
    /** True while the window between two pulses is open. */
    boolean windowOpen();

    /** Forces the window open for this many seconds from now (an admin, an event). */
    void openWindow(int seconds);

    /** Closes a window opened with {@link #openWindow}. The Breath cycle (or the clock) still applies. */
    void closeWindow();
}
