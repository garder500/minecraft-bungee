package win.vampyuyu.survival.island;

/** Profile section {@code island}: per player extras (the island itself lives in the island registry). */
public final class PlayerIslandData {
    /** The starter chest was already filled once for this player. */
    public boolean starterClaimed;
    /** The Nether/End ruin chests were already filled once for this player. */
    public boolean lootClaimed;
    /** When the player last lost or left an island (create cooldown), epoch millis. */
    public long lastLostAt;
    public long islandsCreated;
    /** Last island id the player was part of (informational). */
    public String lastIsland = "";
    /** Show the visual border (player preference). */
    public boolean borderVisible = true;
}
