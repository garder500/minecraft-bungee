package win.vampyuyu.survival.api;

import java.util.UUID;

/** Minions. Implemented by the {@code minions} feature. */
public interface MinionApi {
    /** Total minion slots the player has unlocked. */
    int slotsFor(UUID player);

    /** Number of minions the player currently has placed. */
    int placedCount(UUID player);
}
