package win.vampyuyu.survival.island;

import java.util.UUID;

/**
 * Island upgrade values for features that want them (the minions feature adds {@link #minionSlotBonus} to its own
 * slot count). Players without an island get 0 / the base values.
 */
public interface IslandUpgradesApi {
    /** Extra minion slots from the island's "Minion slots" upgrade. */
    int minionSlotBonus(UUID player);

    /** Current border radius of the player's island, 0 without an island. */
    int radius(UUID player);

    /** Current member capacity (owner included), 0 without an island. */
    int memberSlots(UUID player);
}
