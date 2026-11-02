package win.vampyuyu.survival.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * A feature that adds rules to one or more Bastions (Hell Gao, GDD 19.4: Paragon rank, the window between two pulses,
 * Ascension ranks). Registered on the {@code BastionService}; every method has a neutral default.
 */
public interface BastionHook {
    /** How much a hook scales the Guardian of a fight, on top of the Resonance rank. */
    record Scaling(double hpMultiplier, double damageMultiplier) {
        public static final Scaling NONE = new Scaling(1.0, 1.0);
    }

    /** An extra reason to refuse the entry of this party, English, or null. Checked after the standard checks. */
    default String refuse(Player leader, List<UUID> members, String bastionId) { return null; }

    /** False hides the Bastion from {@code /bastion list} for this player (a secret Bastion). */
    default boolean visibleTo(Player viewer, String bastionId) { return true; }

    /** Scaling of the Guardian for this leader, or null for none. Read when the Guardian wakes. */
    default Scaling scaling(Player leader, String bastionId) { return null; }

    /** The Guardian woke: the leader's key is spent and these players fight. */
    default void opened(String bastionId, Player leader, Collection<UUID> participants, Scaling scaling) {}

    /** The fight was won by these players (those who dealt damage and are still in the run). */
    default void cleared(String bastionId, Collection<UUID> participants, long elapsedMillis, Scaling scaling) {}
}
