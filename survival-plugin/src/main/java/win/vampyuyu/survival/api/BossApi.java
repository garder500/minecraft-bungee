package win.vampyuyu.survival.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Bosses. Implemented by the {@code boss} feature. */
public interface BossApi {
    /**
     * Spawns a boss.
     *
     * @param bossId one of {@link #bossIds()}
     * @param summoner who triggered it, may be null (natural spawn, admin command)
     * @return false when the id is unknown or the spawn was refused
     */
    boolean spawn(String bossId, Location location, @Nullable Player summoner);

    /** Ids of every registered boss. */
    Collection<String> bossIds();

    /** How many times the player helped defeat this boss. */
    int kills(UUID player, String bossId);

    /** Registers a hook that adds loot to every boss kill. Hooks live until the plugin disables. */
    void registerLootHook(BossLootHook hook);

    /** How an instanced fight ended. */
    enum Outcome {
        /** The boss was killed. */
        CLEARED,
        /** The party lost: timeout, wipe, the boss left the arena after being hurt. */
        DEFEATED,
        /** The server's fault (shutdown, unloaded world, error): the key item given as {@code refundItem} is returned. */
        FAULT
    }

    /**
     * The end of an instanced fight. {@code damage} is the credited damage per player, {@code progress} the share of the
     * boss's health taken (0 to 1), {@code resonance} the Resonance rank the fight ran at (0 = none).
     */
    record FightResult(Outcome outcome, Map<UUID, Double> damage, double progress, long elapsedMillis, int resonance) {}

    /** A running instanced fight. */
    interface Fight {
        /** Ends the fight as a defeat (nobody is left to fight). Does nothing once it ended. */
        void abort();

        /** Index of the phase the boss is in (0 = the first). Fakes that have no phases keep the default. */
        default int phase() {
            return 0;
        }
    }

    /**
     * Spawns a boss for an instance (a Bastion): not subject to summon rules or cooldowns, at the Resonance rank of
     * {@code leader}. When the fight ends {@code onEnd} is called once; on a {@link Outcome#FAULT} the item
     * {@code refundItem} (may be null) is given back to {@code leader} by the boss feature itself.
     *
     * @return the fight, or empty when the spawn was refused
     */
    Optional<Fight> spawnFight(String bossId, Location location, Player leader, @Nullable String refundItem,
        Consumer<FightResult> onEnd);
}
