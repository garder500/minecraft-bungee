package win.vampyuyu.survival.api;

import java.util.UUID;

/**
 * Who may enter the island End. Published by the feature that owns the progression (the Quarry): the island feature
 * asks it before {@code /island end}. When nobody publishes it, the End is open.
 */
public interface EndAccessApi {
    /** True when the player has unlocked the End. */
    boolean canEnterEnd(UUID player);

    /** What to tell a player who has not (plain text). */
    String endLockedMessage();
}
