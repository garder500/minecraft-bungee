package win.vampyuyu.survival.items.combat;

import java.util.function.DoubleSupplier;

/** The combat formulas as pure functions (no Bukkit), so they can be tested and tuned in one place. */
public final class DamageFormula {
    private DamageFormula() {}

    /**
     * Damage before critical hits and enchants: {@code (weapon + flatBonus) * (1 + strength / 100)}.
     */
    public static double base(double weapon, double flatBonus, double strength) {
        return Math.max(0, weapon + flatBonus) * (1.0 + Math.max(-99, strength) / 100.0);
    }

    /** Multiplier of a critical hit: {@code 1 + critDamage / 100}. */
    public static double critMultiplier(double critDamage) {
        return 1.0 + Math.max(0, critDamage) / 100.0;
    }

    /** Rolls a critical hit: chance is a percentage (0-100). */
    public static boolean rollCrit(double chancePercent, DoubleSupplier random) {
        return chancePercent > 0 && random.getAsDouble() * 100.0 < chancePercent;
    }

    /** Full outgoing hit: base times enchant factor times the crit multiplier when it crits. */
    public static double hit(double weapon, double flatBonus, double strength, double enchantAdditive,
            boolean crit, double critDamage) {
        double damage = base(weapon, flatBonus, strength) * (1.0 + Math.max(-0.99, enchantAdditive));
        return crit ? damage * critMultiplier(critDamage) : damage;
    }

    /** Defense mitigation: {@code damage / (1 + defense / 100)}. */
    public static double mitigate(double damage, double defense) {
        return damage / (1.0 + Math.max(0, defense) / 100.0);
    }

    /** Ability damage: {@code base * (1 + intelligence / divisor) * (1 + abilityDamagePercent / 100)}. */
    public static double ability(double base, double intelligence, double divisor, double abilityDamagePercent) {
        return base * (1.0 + Math.max(0, intelligence) / Math.max(1, divisor))
            * (1.0 + Math.max(0, abilityDamagePercent) / 100.0);
    }

    /** Execute: {@code perLevelPercent * level} more damage per 1% of health the target is missing. */
    public static double execute(int level, double perLevelPercent, double targetHealth, double targetMax) {
        if (level <= 0 || targetMax <= 0) {
            return 0;
        }
        double missing = Math.max(0, Math.min(100, (1.0 - targetHealth / targetMax) * 100.0));
        return level * perLevelPercent * missing / 100.0;
    }

    /**
     * Giant Killer: up to {@code perLevelPercent * level} percent more damage (as a fraction) scaled by how much more
     * health the target has than the attacker: no bonus at equal health, the full amount at double health or more.
     */
    public static double giantKiller(int level, double perLevelPercent, double targetMax, double attackerMax) {
        if (level <= 0 || attackerMax <= 0 || targetMax <= attackerMax) {
            return 0;
        }
        double ratio = Math.min(1.0, (targetMax - attackerMax) / attackerMax);
        return level * perLevelPercent / 100.0 * ratio;
    }

    /** Snipe: {@code perLevelPercent * level} percent per block beyond 5, capped at 60%. Returns a fraction. */
    public static double snipe(int level, double perLevelPercent, double distance) {
        if (level <= 0 || distance <= 5) {
            return 0;
        }
        return Math.min(0.60, level * perLevelPercent * (distance - 5) / 100.0);
    }

    /** Fraction of the draw strength that counts for an arrow, clamped to 0.1 - 1. */
    public static double drawFactor(double force) {
        return Math.max(0.1, Math.min(1.0, force));
    }

    /** Scales a vanilla damage number by the incoming scale and the defense of the victim. */
    public static double incoming(double vanilla, double scale, double defense) {
        return mitigate(vanilla * scale, defense);
    }

    /** Number of extra ferocity strikes: the integer part always happens, the fraction is a chance. */
    public static int ferocityStrikes(double ferocity, DoubleSupplier random) {
        if (ferocity <= 0) {
            return 0;
        }
        int strikes = (int) Math.floor(ferocity / 100.0);
        double rest = ferocity - strikes * 100.0;
        if (rest > 0 && random.getAsDouble() * 100.0 < rest) {
            strikes++;
        }
        return Math.min(strikes, 5);
    }
}
