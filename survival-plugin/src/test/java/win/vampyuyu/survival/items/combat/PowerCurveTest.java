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
import win.vampyuyu.survival.boss.BossMath;
import win.vampyuyu.survival.boss.BossSettings;
import win.vampyuyu.survival.boss.BossRepo;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.data.TestDb;

/**
 * The power scaling: a player with the sword and the full armor of a boss's tier kills it alone in 180-300 s, one tier
 * lower takes 2-3 times longer, two tiers lower cannot finish before the enrage, and the right element is
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

    /** Share of the time a player really lands hits (GDD 8.7 factor 0.5), read from the data. */
    private static double uptime() {
        return Settings.load(db, "boss").getDouble("settings.hp-factor", 0.5);
    }

    /** Seconds one player with the GDD reference weapon of {@code swordTier} needs (no Might, {@link #uptime()} of the time). */
    private static double seconds(BossDefinition boss, int swordTier) {
        PowerCurve.Tier tier = curve.tier(swordTier);
        return simulate(boss.health(), curve.referenceWeaponOf(swordTier), 0, multiplier(tier.element(), boss)) / uptime();
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
            assertEquals(boss.id(), curve.bossHealth(boss.tier(), 180, 0.5), boss.health(), 0);
        }
        // bosses of the same tier share the health, a higher tier has more
        assertEquals(config.bosses().get("frost_revenant").health(), config.bosses().get("sylvan_matriarch").health(), 0);
        assertTrue(config.bosses().get("voidwyrm").health() > config.bosses().get("abyssal_leviathan").health());
        assertTrue(config.bosses().get("abyssal_leviathan").health() > config.bosses().get("frost_revenant").health());
    }

    @Test
    public void theVoidwyrmHasAboutFiftyTwoThousandHealthAtTierFive() {
        BossDefinition wyrm = config.bosses().get("voidwyrm");
        assertEquals(52_000, wyrm.health(), 520); // GDD 8.7: DPS_ref(5) 576 x 180 s x 0.5
        assertEquals(576, curve.referenceDps(5), 1e-6);
        assertEquals(1.0, BossSettings.parse(Settings.load(db, "boss")).hpScale(), 0); // no second halving on top
    }

    @Test
    public void aPlayerOfTheTierKillsEachBossInAboutThreeMinutesAndOneTierBehindInAboutFive() {
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS) {
                continue;
            }
            double s = seconds(boss, boss.tier());
            assertTrue(boss.id() + " took " + s + " s", s >= 170 && s <= 200);
            if (boss.tier() >= 2) {
                double behind = seconds(boss, boss.tier() - 1);
                assertTrue(boss.id() + " one tier behind took " + behind + " s", behind >= 300 && behind <= 450);
                assertTrue(boss.id() + " is dead before the enrage", behind < boss.arena().enrageSeconds());
            }
        }
    }

    @Test
    public void theGroupScalingIsHalfAPlayerAndCapsAtTripleWithFive() {
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS) {
                continue;
            }
            var a = boss.arena();
            assertEquals(boss.id(), 1.0, BossMath.hpMultiplier(1, a.minPlayers(), a.scalePerPlayer(), a.scaleCap()), 1e-9);
            assertEquals(boss.id(), 1.5, BossMath.hpMultiplier(2, a.minPlayers(), a.scalePerPlayer(), a.scaleCap()), 1e-9);
            assertEquals(boss.id(), 3.0, BossMath.hpMultiplier(5, a.minPlayers(), a.scalePerPlayer(), a.scaleCap()), 1e-9);
            assertEquals(boss.id(), 3.0, BossMath.hpMultiplier(9, a.minPlayers(), a.scalePerPlayer(), a.scaleCap()), 1e-9);
            assertEquals(boss.id(), 540, a.enrageSeconds());
        }
    }

    @Test
    public void readabilityRulesHoldPhasesAtMostOnePointFourAndAbilitiesAtMostAThirdOfTheReferencePlayer() {
        double hitsToDown = Settings.load(db, "boss").getInt("settings.hits-to-down", 8);
        double damageScale = Settings.load(db, "boss").getDouble("settings.damage-scale", 1);
        for (BossDefinition boss : config.bosses().values()) {
            if (boss.kind() != BossDefinition.Kind.BOSS) {
                continue;
            }
            for (var phase : boss.phases()) {
                assertTrue(boss.id() + " phase " + phase.name() + " x" + phase.damageMult(), phase.damageMult() <= 1.4 + 1e-9);
                for (var ability : java.util.stream.Stream.concat(phase.enter().stream(), phase.abilities().stream()).toList()) {
                    // a boss hit takes 1/hitsToDown of the reference player's health, then the global and phase multipliers
                    double share = ability.damage() * phase.damageMult() * damageScale / hitsToDown;
                    assertTrue(boss.id() + " " + ability.type() + " takes " + share, share <= 0.35);
                }
            }
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
            int enrage = boss.arena().enrageSeconds();
            assertTrue(boss.id() + " has an enrage", enrage > 0);
            double s = seconds(boss, boss.tier() - 2);
            assertTrue(boss.id() + " would finish in " + s + " s, enrage " + enrage, s > enrage * 0.95);
            // and the matching player has plenty of room
            assertTrue(seconds(boss, boss.tier()) < enrage / 2.0);
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
        assertEquals(curve.bossHealth(1, 180, 0.5), BossRepo.load(db).bosses().get("crypt_lord").health(), 0);
    }

    @Test
    public void theTargetSecondsSettingScalesTheHealth() {
        TestDb.set(db, "boss.settings.target-seconds", "real", "360");
        try {
            assertEquals(curve.bossHealth(3, 360, 0.5), BossRepo.load(db).bosses().get("frost_revenant").health(), 0);
        } finally {
            TestDb.set(db, "boss.settings.target-seconds", "real", "180");
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
            double hp = curve.bossHealth(tier, 180, 0.5);
            assertTrue(hp > last * 1.4);
            last = hp;
        }
        Map<Integer, Double> seen = new java.util.HashMap<>();
        for (int tier = 1; tier <= PowerCurve.TIERS; tier++) {
            seen.put(tier, curve.neutralHealth(tier, 90));
        }
        assertEquals(curve.dps(3, 1.0) * 90, seen.get(3), 1);
    }
}
