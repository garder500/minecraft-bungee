package win.vampyuyu.survival.api;

import java.util.Optional;
import java.util.UUID;

/**
 * What a class promotion changes in the class abilities (GDD 11.5). Implemented by the {@code promotion} feature; the
 * class code asks it and gets neutral values when it is not loaded or the player was never promoted (rule 11.4).
 */
public interface PromotionApi {
    /**
     * The Adept specialization numbers. Vitality, Might, Dexterity and Attunement are added to the class bonus (the meta
     * caps still apply); the percents change the class active.
     */
    record Mods(double vitality, double might, double dexterity, double attunement, int cooldownPct, int costPct, int powerPct) {
        public static final Mods NONE = new Mods(0, 0, 0, 0, 0, 0, 0);

        public double cooldownFactor() { return factor(cooldownPct); }
        public double costFactor() { return factor(costPct); }
        public double powerFactor() { return factor(powerPct); }

        private static double factor(int pct) { return Math.max(0.1, 1 + pct / 100.0); }
    }

    /**
     * The third capacity of a Master. What {@code radius}, {@code power} and {@code seconds} mean depends on the class
     * (see the {@code promotion_ability} table).
     */
    record Ability(String name, String summary, double aetherCost, int cooldownSeconds, double radius, double power, double seconds) {}

    /** The numbers of the player's specialization, {@link Mods#NONE} for a Novice. */
    Mods modifiers(UUID player);

    /** The capacity of a Master of the player's class. */
    Optional<Ability> masterAbility(UUID player);

    /** The player used their class active (a quest step counts it). */
    void recordCast(UUID player);
}
