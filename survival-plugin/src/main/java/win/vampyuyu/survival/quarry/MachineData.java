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

    MachineType kind() {
        try {
            return MachineType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
