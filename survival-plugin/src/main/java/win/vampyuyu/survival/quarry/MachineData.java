package win.vampyuyu.survival.quarry;

/** One placed machine. Plain Gson class: the state that has to survive a restart. */
public final class MachineData {
    public String type;
    /** Owner of the island it stands on (the player whose generator tier and ores it uses). */
    public String owner;
    /** Island id, to count the machines of one island. */
    public String island;
    /** Batteries: stored Flux. */
    public long charge;
    /** Generators: seconds of burning coal left. */
    public int fuel;
    /** Miners: cycles accumulated (a cycle runs when it reaches 1). */
    public double progress;
    /** Miners: epoch millis until which an Artisan's Overclock runs this miner faster (0 when not overclocked). */
    public long overclockUntil;
    /** Miners: the speed multiplier of that Overclock. */
    public double overclockSpeed = 1;
    /** The generator this Fountain last was (a {@link GeneratorStructure.Kind} name), empty when it is only a Fountain. */
    public String built = "";
    /** Miners: the Realm module slotted in ({@link MachineModule} name), empty when none. */
    public String module = "";
    /** Yaw of the 3D model (its front faces the player who placed it). */
    public float yaw = 180f;

    MachineType kind() {
        try {
            return MachineType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
