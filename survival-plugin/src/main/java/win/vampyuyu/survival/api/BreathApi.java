package win.vampyuyu.survival.api;

import java.util.UUID;

/**
 * The Celestial Breath (GDD 4.7, 19.2-19.3): the world events of the end-game. Implemented by the {@code breath}
 * feature. The factors are 1 when no event is running, so a caller can always multiply by them.
 */
public interface BreathApi {
    /** True while an event is running. */
    boolean active();

    /** Number of pulsations since the beginning (every event is one pulsation). */
    int cycle();

    /** Multiplier of the amount a Realm resource gives (e.g. 1.3 for "+30 %"); {@code resourceId} is the resource id. */
    double yieldFactor(String resourceId);

    /** Multiplier of the Flux the machines burn. */
    double machineFactor();

    /** Multiplier of the damage hostile mobs deal in the Skyblock worlds. */
    double monsterFactor();

    /** Interpulsation Fragments the player holds (the key to Hell Gao, GDD 19.4). */
    int fragments(UUID player);

    /** Interpulsation Keys the player has assembled and not used yet. */
    int keys(UUID player);

    /** Uses one key (the Hell Gao arena calls it when it opens). False when the player has none. */
    boolean consumeKey(UUID player);
}
