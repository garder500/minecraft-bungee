package win.vampyuyu.survival.api;

import org.bukkit.entity.Player;

/** Dungeons. Implemented by the {@code dungeon} feature. */
public interface DungeonApi {
    /** True while the player is inside a dungeon run. */
    boolean inDungeon(Player player);

    /**
     * Starts a run for the player's party (a party of one is made if needed) on this floor, with this seed, whatever
     * floor they cleared before (used by the Fractures of the Celestial Breath). Returns a message when the run cannot
     * start, null when it is on its way.
     */
    default String startRun(Player leader, int floor, long seed) {
        return "Dungeons are not available.";
    }
}
