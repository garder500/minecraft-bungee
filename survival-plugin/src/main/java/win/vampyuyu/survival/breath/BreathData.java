package win.vampyuyu.survival.breath;

/** Profile section {@code "breath"}: a player's Interpulsation Fragments and what they did during the pulsations. */
public final class BreathData {
    /** Fragments held, not yet assembled. */
    public int fragments;
    /** Keys assembled and not used. */
    public int keys;
    /** Fragments found since the beginning. */
    public int found;
    /** Cycle of the last Fragment found: one per pulsation at most. */
    public int lastFragmentCycle;
    /** Cycle of the last Fracture cleared for the reward: one reward per Fracture. */
    public int lastFractureCycle;
    /** Cycle the invader counters below belong to. */
    public int invadersCycle;
    /** Immune cells killed during {@link #invadersCycle}. */
    public int invaderKills;
    /** Memory Shards earned from immune cells during {@link #invadersCycle}. */
    public long invaderShards;
    /** Fractures cleared since the beginning. */
    public int fracturesCleared;
}
