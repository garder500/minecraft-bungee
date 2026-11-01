package win.vampyuyu.survival.items.combat;

/** The combat formulas as pure functions (no Bukkit), so they can be tested and tuned in one place. */
public final class DamageFormula {
    private DamageFormula() {}

    /**
     * Weapon damage before enchants and elements: {@code (weapon + flatBonus) * (1 + might / 100)}. Might is a plain
     * percentage: 40 Might is 40% more damage.
     */
    public static double base(double weapon, double flatBonus, double might) {
        return Math.max(0, weapon + flatBonus) * (1.0 + Math.max(-99, might) / 100.0);
    }

    /** Full outgoing hit before the element and the Surge: {@link #base} times the additive enchant bonus. */
    public static double hit(double weapon, double flatBonus, double might, double enchantAdditive) {
        return base(weapon, flatBonus, might) * (1.0 + Math.max(-0.99, enchantAdditive));
    }

    /**
     * Multiplier of the element: {@code weakness + attunement / 100} when the target is weak to the weapon's element,
     * {@code resistance} when it resists it, 1 otherwise (also for a weapon without element).
     */
    public static double elementMultiplier(win.vampyuyu.survival.api.Element weapon, win.vampyuyu.survival.api.Element weak,
            win.vampyuyu.survival.api.Element resists, double attunement, double weakness, double resistance) {
        if (weapon == null) {
            return 1.0;
        }
        if (weapon == weak) {
            return weakness + Math.max(0, attunement) / 100.0;
        }
        if (weapon == resists) {
            return resistance;
        }
        return 1.0;
    }

    /** Ward mitigation: {@code damage * 100 / (100 + ward)}. */
    public static double mitigate(double damage, double ward) {
        return damage * 100.0 / (100.0 + Math.max(0, ward));
    }

    /** Ability damage: {@code base * (1 + aether / divisor) * (1 + might / 100)}. */
    public static double ability(double base, double aether, double divisor, double might) {
        return base * (1.0 + Math.max(0, aether) / Math.max(1, divisor))
            * (1.0 + Math.max(0, might) / 100.0);
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
     * Colossus Bane: up to {@code perLevelPercent * level} percent more damage (as a fraction) scaled by how much more
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

    /** Scales a vanilla damage number by the incoming scale and the Ward of the victim. */
    public static double incoming(double vanilla, double scale, double ward) {
        return mitigate(vanilla * scale, ward);
    }
}
