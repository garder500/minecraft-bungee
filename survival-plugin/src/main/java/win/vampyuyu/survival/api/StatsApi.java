package win.vampyuyu.survival.api;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Player stats. Implemented by the {@code items} feature (stats engine); consumers fetch it with
 * {@code core.service(StatsApi.class)} at use time and must cope with it being absent.
 *
 * <p>A stat value is {@code Stat.base() + sum(modifiers)}. Modifiers belong to a namespaced
 * <em>source</em> string such as {@code "wings"}, {@code "skill:mining"}, {@code "pet"},
 * {@code "armor"}: a feature only ever replaces or removes its own sources.
 */
public interface StatsApi {
    /**
     * Current total of a stat for an online player (base + every modifier, after the
     * {@code StatsRecalculateEvent}). Cheap: served from a cache, recomputed by {@link #recompute}.
     */
    double stat(Player player, Stat stat);

    /**
     * Sets the modifier a source applies to a stat. A second call with the same source and stat
     * replaces the first, it does not add up. Persisting the modifier is the caller's job: modifiers
     * live in memory only. Does not recompute by itself.
     */
    void addModifier(UUID player, String source, Stat stat, double amount);

    /** Removes every modifier registered by {@code source} for the player. Does not recompute. */
    void removeModifiers(UUID player, String source);

    /**
     * Recomputes the cached totals of an online player and fires a {@code StatsRecalculateEvent}.
     * Call it after changing modifiers. Must be called on the main thread.
     */
    void recompute(Player player);
}
