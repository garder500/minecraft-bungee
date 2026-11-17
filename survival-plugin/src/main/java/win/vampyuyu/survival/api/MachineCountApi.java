package win.vampyuyu.survival.api;

import java.util.UUID;

/** How many machines of a kind stand on an island. Implemented by the {@code quarry} feature. */
public interface MachineCountApi {
    /** Machines placed by {@code itemId} (fountain, spring_stone, miner_1...) on the island of {@code islandOwner}. */
    int machines(UUID islandOwner, String itemId);
}
