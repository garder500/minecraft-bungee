package win.vampyuyu.survival.api;

import java.util.UUID;

/** The machines standing on islands. Implemented by the {@code quarry} feature. */
@FunctionalInterface
public interface MachineApi {
    /** How many machines placed by the item {@code itemId} stand on the island of {@code owner}. */
    int placed(UUID owner, String itemId);
}
