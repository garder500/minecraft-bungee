package win.vampyuyu.survival.api;

import java.util.UUID;

/** Memory Shards and Resonance ranks. Implemented by the {@code resonance} feature (published after {@code boss}). */
public interface ResonanceApi {
    /** The rank the player chose for their next Bastion fights, 1 to 5. */
    int selectedRank(UUID player);

    /**
     * The rank a fight against this boss really gets for this player: the chosen rank, held back to one above the
     * highest rank already cleared on that boss (rank I is always open).
     */
    int effectiveRank(UUID player, String bossId);

    /** Highest rank cleared on this boss, 0 when never cleared. */
    int bestRank(UUID player, String bossId);

    /** Memory Shards the player holds. */
    long shards(UUID player);

    /**
     * Pays the Shards of a lost fight (GDD 15.4): a fraction of a clear scaled by {@code progress}, to a player who
     * dealt at least the minimum {@code share} of the damage. Does nothing for a boss that is not a Bastion.
     */
    void payDefeat(UUID player, String bossId, int rank, double progress, double share);
}
