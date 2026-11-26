package win.vampyuyu.survival.boss;

/**
 * The afflictions a boss ability lays on a player, without any vanilla potion: no potion icon, no vanilla screen filter, no
 * frost or fire overlay. Each one is a transient attribute modifier and/or a damage tick that {@link BossStatus} owns and takes
 * back when it runs out (or on death, quit, world change, milk, the end of the fight, plugin stop). The look of each one (the
 * screen veil, the effect around the player) is drawn by the resource pack, never by vanilla particles.
 *
 * <p>{@code slowPerLevel}: movement speed lost per level (multiplicative, the strongest status wins, capped at 90 %).
 * {@code gravity}: added to the gravity attribute (vanilla 0.08): negative values make a player fall slowly, below -0.08 the
 * player rises. {@code dps}: damage per second (one tick a second), {@code lethal}: whether that damage may kill (a decay stops
 * at 1 health like poison did, a withering does not). {@code noJump}: jump strength set to zero (a root).
 */
public enum StatusKind {
    /** Dragged, mired, chilled: the old Slowness. */
    SLOW(0.15, 0, 0, false, false),
    /** Held in place by webs, chains or a cage: no walking, no jumping. */
    ROOT(1.0, 0, 0, false, true),
    /** Frozen by a blizzard or ice spikes: slowed and stung, the old Slowness plus freeze ticks. */
    FROST(0.2, 0, 0.5, false, false),
    /** The void swallows the light: the screen veil goes dark (drawn by the pack). */
    BLIND(0.0, 0, 0, false, false),
    /** A poison zone: damage over time that never kills. */
    DECAY(0.0, 0, 1.0, false, false),
    /** A withering or breath zone: damage over time that may kill. */
    WITHERING(0.0, 0, 1.0, true, false),
    /** Lava and fire: damage over time that may kill, the old fire ticks. */
    BURN(0.0, 0, 1.0, true, false),
    /** Gravity fails: a slow fall, the old Slow Falling. */
    FLOAT(0.0, -0.07, 0, false, false),
    /** Gravity turns over: the player rises, the old Levitation. */
    LIFT(0.0, -0.1, 0, false, false);

    private final double slowPerLevel;
    private final double gravity;
    private final double dps;
    private final boolean lethal;
    private final boolean noJump;

    StatusKind(double slowPerLevel, double gravity, double dps, boolean lethal, boolean noJump) {
        this.slowPerLevel = slowPerLevel;
        this.gravity = gravity;
        this.dps = dps;
        this.lethal = lethal;
        this.noJump = noJump;
    }

    public double slowPerLevel() {
        return slowPerLevel;
    }

    public double gravity() {
        return gravity;
    }

    public double dps() {
        return dps;
    }

    public boolean lethal() {
        return lethal;
    }

    public boolean noJump() {
        return noJump;
    }
}
