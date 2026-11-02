package win.vampyuyu.survival.items.combat;

import java.util.Arrays;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/**
 * The expected damage per second of a player at each power tier, and the health that follows from it. Pure maths on
 * numbers read from the data: the sword damage of the ore ladder, the Might of the matching full armor, how fast a sword
 * can hit (its charge time, and the invulnerability gap of the target) and what a Surge adds on average.
 *
 * <p>A boss of tier N has {@code targetSeconds x dps(N, weakness)} health: one player with the tier N sword and armor,
 * hitting its weakness, takes about {@code targetSeconds} to kill it. The same player without the weakness takes 1.5x
 * longer, a tier lower roughly 2.8x, two tiers lower so long that the boss time limit ends the fight first.
 */
public final class PowerCurve {
    /** The fixed rules of the curve. */
    public record Rules(double weaponCooldownTicks, double iframeTicks, int surgeEvery, double surgeMultiplier,
            double weaknessMultiplier, double resistanceMultiplier) {
        public static final Rules DEFAULT = new Rules(12.5, 6, 5, 2.0, 1.5, 0.6);

        /** Fully charged hits per second: the sword charge, but never faster than the target's invulnerability gap. */
        public double hitsPerSecond() {
            return Math.min(20.0 / Math.max(1, weaponCooldownTicks), 20.0 / Math.max(1, iframeTicks));
        }

        /** Average damage multiplier of the Surge ({@code every - 1} normal hits and one Surge). */
        public double surgeAverage() {
            return (surgeEvery - 1 + surgeMultiplier) / surgeEvery;
        }
    }

    /** One tier of the ladder: the sword and the Might of the four armor pieces. */
    public record Tier(int tier, double damage, double might, Element element) {}

    public static final int TIERS = 5;

    private final Rules rules;
    private final Tier[] tiers; // index tier - 1, null = not defined by the data
    /** Ward and Vitality of the full armor of each tier (index tier - 1); how many boss hits that armor should take. */
    private final double[] ward = new double[TIERS];
    private final double[] vitality = new double[TIERS];
    private int hitsToDown = 8;
    /** GDD 8.6 weapon of each tier (index tier - 1), the gear {@link #referenceDps} is measured with. */
    private final double[] reference = new double[TIERS];

    public PowerCurve(Rules rules, Tier... defined) {
        this.rules = rules;
        this.tiers = new Tier[TIERS];
        for (int t = 1; t <= TIERS; t++) {
            reference[t - 1] = referenceWeapon(t);
        }
        for (Tier tier : defined) {
            if (tier != null && tier.tier() >= 1 && tier.tier() <= TIERS) {
                tiers[tier.tier() - 1] = tier;
            }
        }
    }

    /** Reads the ladder (the {@code item_def} rows of the quarry owner) and the rules from the content database. */
    public static PowerCurve load(Db db) {
        Settings items = Settings.load(db, "items");
        Settings boss = Settings.load(db, "boss");
        Rules rules = new Rules(items.getDouble("power.weapon-cooldown-ticks", Rules.DEFAULT.weaponCooldownTicks()),
            boss.getInt("settings.hit-iframes", 6), Math.max(2, items.getInt("combat.surge-every", 5)),
            items.getDouble("combat.surge-multiplier", 2.0), items.getDouble("combat.element-weakness", 1.5),
            items.getDouble("combat.element-resistance", 0.6));
        Tier[] found = new Tier[TIERS];
        double[] armorWard = new double[TIERS];
        double[] armorVitality = new double[TIERS];
        db.read(c -> {
            Db.query(c, "SELECT tier, damage, element FROM item_def WHERE owner = 'quarry' AND tier IS NOT NULL "
                + "AND damage IS NOT NULL AND type_label = 'SWORD' ORDER BY tier", rs -> { // a tier's weapon is its sword, not its axe or bow
                    int t = rs.getInt(1);
                    found[t - 1] = new Tier(t, rs.getDouble(2), 0, Element.parse(rs.getString(3)));
                    return null;
                });
            Db.query(c, "SELECT d.tier, s.stat, s.value FROM item_def d JOIN item_stat s ON s.item_id = d.id "
                + "WHERE d.owner = 'quarry' AND d.tier IS NOT NULL AND d.equip_slot IS NOT NULL", rs -> {
                    int t = rs.getInt(1);
                    for (Stat.Converted conv : Stat.resolve(rs.getString(2))) {
                        if (conv.stat() == Stat.WARD) armorWard[t - 1] += rs.getDouble(3) * conv.factor();
                        if (conv.stat() == Stat.VITALITY) armorVitality[t - 1] += rs.getDouble(3) * conv.factor();
                        if (conv.stat() == Stat.MIGHT && found[t - 1] != null) {
                            Tier old = found[t - 1];
                            found[t - 1] = new Tier(t, old.damage(), old.might() + rs.getDouble(3) * conv.factor(), old.element());
                        }
                    }
                    return null;
                });
            return null;
        });
        PowerCurve curve = new PowerCurve(rules, found);
        System.arraycopy(armorWard, 0, curve.ward, 0, TIERS);
        System.arraycopy(armorVitality, 0, curve.vitality, 0, TIERS);
        curve.hitsToDown = Math.max(2, boss.getInt("settings.hits-to-down", 8));
        for (int t = 1; t <= TIERS; t++) {
            curve.reference[t - 1] = Math.max(1, boss.getDouble("settings.reference-weapon." + t, referenceWeapon(t)));
        }
        return curve;
    }

    /**
     * Health a player of this tier has where the full stat engine is off: the vanilla 20 plus the armor's Vitality / 5
     * (100 Vitality is the 20 of a bare player).
     */
    public double playerHealth(int tier) {
        return 20 + vitality[tier - 1] / 5.0;
    }

    /**
     * The hit of a boss of this tier: a player wearing the full armor of the tier goes down after {@code hitsToDown}
     * hits ({@code boss.settings.hits-to-down}), counting the armor's Ward ({@code x 100 / (100 + Ward)} per hit).
     */
    public double bossHitDamage(int tier) {
        return playerHealth(tier) * (1 + ward[tier - 1] / 100.0) / hitsToDown;
    }

    public Rules rules() {
        return rules;
    }

    /** True when the data defines the sword of this tier. */
    public boolean has(int tier) {
        return tier >= 1 && tier <= TIERS && tiers[tier - 1] != null;
    }

    public Tier tier(int tier) {
        if (!has(tier)) {
            throw new IllegalArgumentException("No sword is defined for tier " + tier);
        }
        return tiers[tier - 1];
    }

    /** Damage per second of a player with {@code damage} base weapon damage and {@code might} Might, against {@code multiplier}. */
    public double dps(double damage, double might, double multiplier) {
        return damage * (1.0 + Math.max(-99, might) / 100.0) * multiplier * rules.surgeAverage() * rules.hitsPerSecond();
    }

    /** Damage per second of the tier's own sword and armor against an element multiplier (1 = neutral). */
    public double dps(int tier, double multiplier) {
        Tier t = tier(tier);
        return dps(t.damage(), t.might(), multiplier);
    }

    /** Damage per second of the tier's sword and armor when it hits the weakness of its target. */
    public double weakDps(int tier) {
        return dps(tier, rules.weaknessMultiplier());
    }

    /** GDD 8.6 weapon damage of tier {@code n}: {@code 25 + 10n + 5n^2} (40, 65, 100, 145, 200). */
    public static double referenceWeapon(int tier) {
        return 25 + 10.0 * tier + 5.0 * tier * tier;
    }

    /** The reference weapon of the tier (GDD 8.6, overridable by {@code boss.settings.reference-weapon.<tier>}). */
    public double referenceWeaponOf(int tier) {
        return reference[tier - 1];
    }

    /**
     * {@code DPS_ref(n)} of GDD 8.7: the reference weapon of the tier alone (no Might), charged hits, Surge average and
     * the weakness multiplier, so 576 at tier 5.
     */
    public double referenceDps(int tier) {
        return dps(reference[tier - 1], 0, rules.weaknessMultiplier());
    }

    /**
     * Health of a boss of the tier, GDD 8.7: {@code DPS_ref(n) x seconds x factor} ({@code seconds} = 180 and
     * {@code factor} = 0.5, the share of the time a player really lands hits; 51 840 at tier 5).
     */
    public double bossHealth(int tier, double seconds, double factor) {
        return Math.max(1, Math.round(seconds * factor * referenceDps(tier)));
    }

    /** Same with the factor 1: health of a boss that dies in {@code seconds} of nonstop hits on its weakness. */
    public double bossHealth(int tier, double seconds) {
        return bossHealth(tier, seconds, 1.0);
    }

    /** Health of a target of the tier with no weakness (slayers, dungeon monsters) that dies in {@code seconds}. */
    public double neutralHealth(int tier, double seconds) {
        return Math.max(1, Math.round(seconds * dps(tier, 1.0)));
    }

    /** Seconds one player needs to deal {@code health} damage with the given gear, or infinity when the dps is zero. */
    public double secondsToKill(double health, double damage, double might, double multiplier) {
        double dps = dps(damage, might, multiplier);
        return dps <= 0 ? Double.POSITIVE_INFINITY : health / dps;
    }

    @Override
    public String toString() {
        return "PowerCurve" + Arrays.toString(tiers);
    }
}
