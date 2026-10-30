package win.vampyuyu.survival.dungeon.run;

/** Life cycle of a dungeon run. */
public enum RunState {
    /** The world exists and the blocks are being placed. */
    GENERATING,
    /** Players are in, the countdown runs. */
    COUNTDOWN,
    RUNNING,
    /** The boss fight is on. */
    BOSS,
    /** Boss killed: score, rewards, waiting for the world to close. */
    SUCCESS,
    /** Everybody fell, time ran out or generation failed. */
    FAILED,
    /** Players are out, the world is being destroyed. */
    CLOSING,
    CLOSED;

    public boolean active() { return this == COUNTDOWN || this == RUNNING || this == BOSS; }

    public boolean over() { return this == SUCCESS || this == FAILED || this == CLOSING || this == CLOSED; }
}
