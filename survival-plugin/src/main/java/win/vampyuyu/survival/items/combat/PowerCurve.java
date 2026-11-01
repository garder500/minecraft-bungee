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

    public PowerCurve(Rules rules, Tier... defined) {
        this.rules = rules;
        this.tiers = new Tier[TIERS];
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
        db.read(c -> {
            Db.query(c, "SELECT tier, damage, element FROM item_def WHERE owner = 'quarry' AND tier IS NOT NULL "
                + "AND damage IS NOT NULL ORDER BY tier", rs -> {
                    int t = rs.getInt(1);
                    found[t - 1] = new Tier(t, rs.getDouble(2), 0, Element.parse(rs.getString(3)));
                    return null;
                });
            Db.query(c, "SELECT d.tier, s.stat, s.value FROM item_def d JOIN item_stat s ON s.item_id = d.id "
                + "WHERE d.owner = 'quarry' AND d.tier IS NOT NULL AND d.equip_slot IS NOT NULL", rs -> {
                    int t = rs.getInt(1);
                    for (Stat.Converted conv : Stat.resolve(rs.getString(2))) {
                        if (conv.stat() == Stat.MIGHT && found[t - 1] != null) {
                            Tier old = found[t - 1];
                            found[t - 1] = new Tier(t, old.damage(), old.might() + rs.getDouble(3) * conv.factor(), old.element());
                        }
                    }
                    return null;
                });
            return null;
        });
        return new PowerCurve(rules, found);
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

    /** Health of a boss of the tier that dies in {@code seconds} to one matching player hitting its weakness. */
    public double bossHealth(int tier, double seconds) {
        return Math.max(1, Math.round(seconds * weakDps(tier)));
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
