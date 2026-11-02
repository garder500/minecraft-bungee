package win.vampyuyu.survival.api;

import java.util.Optional;
import java.util.UUID;

/**
 * The class of a player and the combat modifiers it gives (GDD 11). The combat code asks this service for the
 * numbers and never needs a class: with no class chosen every modifier is neutral (rule 11.4, no class is required).
 */
public interface ClassApi {
    Optional<PlayerClass> classOf(UUID player);

    /** First choice: false when the player already has a class (a change goes through {@link #set}, the class-change quest). */
    boolean choose(UUID player, PlayerClass chosen);

    /** Sets or replaces the class (the class-change quest, an admin). Null removes it. */
    void set(UUID player, PlayerClass chosen);

    /** Might (percent damage) the class adds, already inside the meta cap. */
    double mightBonus(UUID player);

    /** Dexterity the class adds (Surge power), already inside the meta cap. */
    double dexterityBonus(UUID player);

    /** Attunement the class adds (elemental weakness). */
    double attunementBonus(UUID player);

    /** Hits between two Surges for this player ({@code normal} unless the class changes it). */
    int surgeEvery(UUID player, int normal);

    /** True once when a Rift Step is pending: the next hit is a Surge. */
    boolean consumeGuaranteedSurge(UUID player);
}
