package win.vampyuyu.survival.items.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.boss.BossConfig;
import win.vampyuyu.survival.boss.BossDefinition;
import win.vampyuyu.survival.boss.BossRepo;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.TestDb;

/**
 * The power scaling: a player with the sword and the full armor of a boss's tier kills it alone in 180-300 s, one tier
 * lower takes 2-3 times longer, two tiers lower cannot finish before the boss's time limit, and the right element is
 * clearly faster than the wrong one. Every number comes from the shipped seed, so a change of the data that breaks the
 * curve fails here.
 */
public class PowerCurveTest {
    private static Db db;
    private static PowerCurve curve;
    private static BossConfig config;

    @BeforeClass
    public static void load() {
        MockBukkit.mock(); // the boss tables name vanilla entity types
        db = TestDb.seeded();
        curve = PowerCurve.load(db);
        config = BossRepo.load(db);
    }

    @AfterClass
    public static void unload() {
        MockBukkit.unmock();
    }

    /**
     * Seconds to deal {@code health} with fully charged hits: one hit per sword charge (never faster than the boss's
     * invulnerability gap), every Nth one a Surge, counted hit by hit (no averages).
     */
    private static double simulate(double health, double damage, double might, double multiplier) {
        PowerCurve.Rules rules = curve.rules();
        double interval = Math.max(rules.weaponCooldownTicks(), rules.iframeTicks()) / 20.0;
        double perHit = damage * (1 + might / 100.0) * multiplier;
        double dealt = 0;
        int hits = 0;
        while (dealt < health) {
            hits++;
            dealt += perHit * (hits % rules.surgeEvery() == 0 ? rules.surgeMultiplier() : 1.0);
            if (hits > 10_000_000) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return hits * interval;
    }

    private static double multiplier(Element weapon, BossDefinition boss) {
        if (weapon == Element.parse(boss.weakness())) {
            return curve.rules().weaknessMultiplier();
        }
        if (weapon == Element.parse(boss.resistance())) {
            return curve.rules().resistanceMultiplier();
        }
        return 1.0;
    }

    private static double seconds(BossDefinition boss, int swordTier) {
        PowerCurve.Tier tier = curve.tier(swordTier);
        return simulate(boss.health(), tier.damage(), tier.might(), multiplier(tier.element(), boss));
    }

    @Test
    public void everyTierOfTheLadderIsDefined() {
        double previous = 0;
        for (int tier = 1; tier <= PowerCurve.TIERS; tier++) {
            assertTrue("tier " + tier, curve.has(tier));
            assertNotNull(curve.tier(tier).element());
            assertTrue(curve.tier(tier).damage() > previous);
            assertTrue(curve.tier(tier).might() > 0);
            previous = curve.tier(tier).damage();
        }
        assertEquals(40, curve.tier(1).damage(), 0);
        assertEquals(70, curve.tier(2).damage(), 0);
        assertEquals(120, curve.tier(3).damage(), 0);
        assertEquals(200, curve.tier(4).damage(), 0);
        assertEquals(320, curve.tier(5).damage(), 0);
        assertEquals(Element.EARTH, curve.tier(1).element());
        assertEquals(Element.FROST, curve.tier(2).element());
        assertEquals(Element.EMBER, curve.tier(3).element());
        assertEquals(Element.VOID, curve.tier(4).element());
        assertEquals(Element.ASTRAL, curve.tier(5).element());
    }

    @Test
    public void theRulesFollowTheSwordChargeAndTheInvulnerabilityGap() {
        PowerCurve.Rules rules = curve.rules();
        assertEquals(1.6, rules.hitsPerSecond(), 1e-9); // 12.5 ticks of charge, slower than the 6 tick gap
        assertEquals(1.2, rules.surgeAverage(), 1e-9);  // four hits and one that counts double
        PowerCurve.Rules fast = new PowerCurve.Rules(3, 6, 5, 2.0, 1.5, 0.6);
        assertEquals(20.0 / 6, fast.hitsPerSecond(), 1e-9); // a fast weapon is held back by the gap
    }

    @Test
    public void everyBossIsAutoScaledFromItsTier() {
        assertEquals(6, config.bosses().values().stream().filter(b -> b.kind() == BossDefinition.Kind.BOSS).count());
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS) {
                continue;
            }
            assertTrue(boss.id(), boss.tier() >= 1);
            assertNotNull(boss.id() + " has a weakness", boss.weakness());
            assertNotNull(boss.id() + " has a resistance", boss.resistance());
            assertEquals(boss.id(), curve.bossHealth(boss.tier(), 240), boss.health(), 0);
        }
        // bosses of the same tier share the health, a higher tier has more
        assertEquals(config.bosses().get("frost_revenant").health(), config.bosses().get("sylvan_matriarch").health(), 0);
        assertTrue(config.bosses().get("voidwyrm").health() > config.bosses().get("abyssal_leviathan").health());
        assertTrue(config.bosses().get("abyssal_leviathan").health() > config.bosses().get("frost_revenant").health());
    }

    @Test
    public void aMatchingSwordAndArmorKillEachBossAloneInThreeToFiveMinutes() {
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS) {
                continue;
            }
            double s = seconds(boss, boss.tier());
            assertTrue(boss.id() + " took " + s + " s", s >= 180 && s <= 300);
        }
    }

    @Test
    public void oneTierLowerTakesTwoToThreeTimesLonger() {
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS || boss.tier() < 2) {
                continue;
            }
            double ratio = seconds(boss, boss.tier() - 1) / seconds(boss, boss.tier());
            assertTrue(boss.id() + " ratio " + ratio, ratio >= 2.0 && ratio <= 3.0);
        }
    }

    @Test
    public void twoTiersLowerCannotFinishBeforeTheTimeLimit() {
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS || boss.tier() < 3) {
                continue;
            }
            int limit = boss.arena().timeLimitSeconds();
            assertTrue(boss.id() + " has a time limit", limit > 0);
            double s = seconds(boss, boss.tier() - 2);
            assertTrue(boss.id() + " would finish in " + s + " s, limit " + limit, s > limit);
            // and the matching player has plenty of room
            assertTrue(seconds(boss, boss.tier()) < limit / 2.0);
        }
    }

    @Test
    public void theRightElementIsClearlyFasterThanTheWrongOne() {
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS) {
                continue;
            }
            PowerCurve.Tier tier = curve.tier(boss.tier());
            double right = simulate(boss.health(), tier.damage(), tier.might(), curve.rules().weaknessMultiplier());
            double neutral = simulate(boss.health(), tier.damage(), tier.might(), 1.0);
            double resisted = simulate(boss.health(), tier.damage(), tier.might(), curve.rules().resistanceMultiplier());
            assertTrue(boss.id() + " neutral " + neutral + " vs " + right, neutral >= right * 1.4);
            assertTrue(boss.id() + " resisted " + resisted + " vs " + right, resisted >= right * 2.2);
            // the data really makes the tier's sword the right one, and never a resisted one
            assertEquals(boss.id(), tier.element(), Element.parse(boss.weakness()));
            assertFalse(boss.id(), Element.parse(boss.resistance()) == tier.element());
            // no boss resists the element of the tier just below it, otherwise "one tier lower" would not be 2-3x
            if (boss.tier() >= 2) {
                assertFalse(boss.id(), Element.parse(boss.resistance()) == curve.tier(boss.tier() - 1).element());
            }
        }
    }

    @Test
    public void aBossKeepsAWrittenHealthWhenHpAutoIsOff() {
        TestDb.exec(db, "UPDATE boss SET hp_auto = 0, health = 12345 WHERE id = 'crypt_lord'");
        try {
            assertEquals(12345, BossRepo.load(db).bosses().get("crypt_lord").health(), 0);
        } finally {
            TestDb.exec(db, "UPDATE boss SET hp_auto = 1 WHERE id = 'crypt_lord'");
        }
        assertEquals(curve.bossHealth(1, 240), BossRepo.load(db).bosses().get("crypt_lord").health(), 0);
    }

    @Test
    public void theTargetSecondsSettingScalesTheHealth() {
        TestDb.set(db, "boss.settings.target-seconds", "real", "480");
        try {
            assertEquals(curve.bossHealth(3, 480), BossRepo.load(db).bosses().get("frost_revenant").health(), 0);
        } finally {
            TestDb.set(db, "boss.settings.target-seconds", "real", "240");
        }
    }

    @Test
    public void slayerTiersFollowTheCurveToo() {
        for (var slayer : config.slayers().values()) {
            for (var tier : slayer.tiers()) {
                BossDefinition boss = tier.boss();
                assertEquals(slayer.id() + " " + tier.tier(), curve.neutralHealth(tier.tier(), 90), boss.health(), 0);
                assertEquals(tier.tier(), boss.tier());
                // a slayer is beaten inside its own time limit by the matching tier of gear, element ignored
                PowerCurve.Tier gear = curve.tier(tier.tier());
                double s = simulate(boss.health(), gear.damage(), gear.might(), 1.0);
                assertTrue(slayer.id() + " tier " + tier.tier() + " took " + s, s > 60 && s < tier.timeLimitSeconds() / 2.0);
            }
        }
    }

    @Test
    public void healthIsMonotonicAlongTheLadder() {
        double last = 0;
        for (int tier = 1; tier <= PowerCurve.TIERS; tier++) {
            double hp = curve.bossHealth(tier, 240);
            assertTrue(hp > last * 1.5);
            last = hp;
        }
        Map<Integer, Double> seen = new java.util.HashMap<>();
        for (int tier = 1; tier <= PowerCurve.TIERS; tier++) {
            seen.put(tier, curve.neutralHealth(tier, 90));
        }
        assertEquals(curve.dps(3, 1.0) * 90, seen.get(3), 1);
    }
}
