package win.vampyuyu.survival.breath;

/** The world's pulsation, saved in {@code breath.json} so that a restart does not lose a running event. Plain Gson class. */
public final class BreathState {
    /** Pulsations since the beginning. */
    public int cycle;
    /** Id of the running event, empty when quiet. */
    public String active = "";
    /** The event that ran last (never drawn twice in a row). */
    public String last = "";
    /** Epoch millis of the end of the running event. */
    public long endsAt;
    /** Epoch millis the next pulsation is due. 0 = not scheduled yet. */
    public long nextAt;
    /** Seed of the running Fracture dungeon. */
    public long fractureSeed;
}
