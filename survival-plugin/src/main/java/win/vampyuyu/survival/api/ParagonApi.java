package win.vampyuyu.survival.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Paragon status (GDD 11.5, 19.1): the players who survived the Astral Interior, their Transcendence rank and the ultimate
 * capacity of their class. Implemented by the {@code paragon} feature. Nothing here gives a stat (GDD 9.2); the content
 * that needs a Paragon (Hell Gao, GDD 19.4) asks {@link #isParagon}.
 */
public interface ParagonApi {
    /** The player survived the Astral Interior (or an admin made them a Paragon). */
    boolean isParagon(UUID player);

    /** Transcendence rank, 0 for a player who is not a Paragon. */
    int rank(UUID player);

    /** Paragon points earned. */
    int points(UUID player);

    /**
     * The player survived the Astral Interior: they become a Paragon and earn the points of a first clear. Does nothing
     * the second time (the points of the next clears go through {@link #addPoints}).
     */
    void markAstralCleared(UUID player);

    /** Adds Paragon points (Hell Gao, Paragon challenges). Ignored for a player who is not a Paragon yet or a non-positive amount. */
    void addPoints(UUID player, int points, String reason);

    /** The title the player shows, empty when they have none. */
    String title(UUID player);

    /** Sum of the UTILITY rewards with this key over the ranks the player reached (for example {@code island_members}). */
    int perk(UUID player, String key);

    /**
     * The ultimate capacity of a Paragon who is a Master of their class. What {@code radius}, {@code power} and
     * {@code seconds} mean depends on the class, as for the Master capacity (see the {@code paragon_ultimate} table).
     */
    Optional<PromotionApi.Ability> ultimate(UUID player);
}
