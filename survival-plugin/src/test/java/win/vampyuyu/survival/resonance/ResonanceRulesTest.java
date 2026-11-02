package win.vampyuyu.survival.resonance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.Resonance;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;
import win.vampyuyu.survival.resonance.ResonanceConfig.Bastion;

/** Memory Shards, Resonance ranks and the boss guarantee: pure rules and the shipped seed. */
public class ResonanceRulesTest {
    private static final long DAY = 86_400_000L;
    private static final long T0 = 1_793_000_000_000L; // 2026-10-26, a Monday
    private final ResonanceConfig cfg = ResonanceConfig.parse(win.vampyuyu.survival.data.Settings.empty("resonance"));
    private final Bastion boss = new Bastion("magma_warden", Realm.ASHES, "warden_heart", 30);

    @Test
    public void ranksFollowTheGdd() {
        double[] hp = {1, 1.5, 2, 2.5, 3};
        double[] dmg = {1, 1.2, 1.4, 1.6, 1.8};
        for (int r = 1; r <= 5; r++) {
            assertEquals(hp[r - 1], Resonance.hpMultiplier(r), 1e-9);
            assertEquals(dmg[r - 1], Resonance.damageMultiplier(r), 1e-9);
        }
        assertEquals(1, Resonance.hpMultiplier(0), 1e-9);
        assertEquals(3, Resonance.hpMultiplier(99), 1e-9);
        assertEquals("III", Resonance.roman(3));
    }

    @Test
    public void aRankIsOpenOnceTheOneBelowIsCleared() {
        assertEquals(1, ResonanceMath.effectiveRank(5, 0));
        assertEquals(3, ResonanceMath.effectiveRank(5, 2));
        assertEquals(2, ResonanceMath.effectiveRank(2, 4));
        assertEquals(5, ResonanceMath.effectiveRank(5, 5));
    }

    @Test
    public void shardsGrowWithTheRankAndDoubleOnTheFirstClearOfTheDay() {
        assertEquals(30, ResonanceMath.killShards(30, 1, 0.25, false, 2));
        assertEquals(60, ResonanceMath.killShards(30, 5, 0.25, false, 2));
        assertEquals(60, ResonanceMath.killShards(30, 1, 0.25, true, 2));
        ResonanceData d = new ResonanceData();
        ResonanceRules.Clear first = ResonanceRules.clear(d, boss, 1, T0, cfg);
        assertTrue(first.firstOfDay());
        assertEquals(60, first.shards());
        ResonanceRules.Clear second = ResonanceRules.clear(d, boss, 1, T0 + 1000, cfg);
        assertFalse("only one bonus a day, no streak", second.firstOfDay());
        assertEquals(30, second.shards());
        assertTrue(ResonanceRules.clear(d, boss, 1, T0 + DAY, cfg).firstOfDay());
        assertEquals(150, d.shards);
        assertEquals(150, d.earned);
        assertEquals(3, (int) d.clears.get("magma_warden"));
    }

    @Test
    public void clearingARankOpensTheNextOne() {
        ResonanceData d = new ResonanceData();
        assertTrue(ResonanceRules.clear(d, boss, 1, T0, cfg).newBest());
        assertFalse(ResonanceRules.clear(d, boss, 1, T0, cfg).newBest());
        assertTrue(ResonanceRules.clear(d, boss, 2, T0, cfg).newBest());
        assertEquals(2, (int) d.best.get("magma_warden"));
        assertEquals(3, ResonanceMath.unlockedRank(2));
    }

    @Test
    public void aLateDefeatPaysASmallFraction() {
        ResonanceData d = new ResonanceData();
        assertEquals(0, ResonanceRules.defeat(d, boss, 1, 0, cfg));
        assertEquals(5, ResonanceRules.defeat(d, boss, 1, 0.85, cfg)); // 30 x 0.2 x 0.85
        assertEquals(1, ResonanceRules.defeat(d, boss, 1, 0.01, cfg));
        assertEquals(6, d.shards);
    }

    @Test
    public void theComponentIsGuaranteedByTheFourthRun() {
        ResonanceData d = new ResonanceData();
        for (int i = 0; i < 3; i++) {
            assertFalse("run " + (i + 1), ResonanceRules.pity(d, boss, 1, false, cfg));
        }
        assertTrue("the 4th run without a drop pays it", ResonanceRules.pity(d, boss, 1, false, cfg));
        assertEquals(0, d.dry.getOrDefault("magma_warden", 0).intValue());
        assertTrue(d.found.contains("magma_warden"));
    }

    @Test
    public void fromRankThreeTheGuaranteeIsTheThirdRun() {
        ResonanceData d = new ResonanceData();
        assertFalse(ResonanceRules.pity(d, boss, 3, false, cfg));
        assertFalse(ResonanceRules.pity(d, boss, 3, false, cfg));
        assertTrue(ResonanceRules.pity(d, boss, 3, false, cfg));
        assertFalse(ResonanceRules.pity(d, boss, 2, false, cfg));
        assertFalse(ResonanceRules.pity(d, boss, 2, false, cfg));
        assertFalse("rank II still waits for the 4th", ResonanceRules.pity(d, boss, 2, false, cfg));
        assertTrue(ResonanceRules.pity(d, boss, 2, false, cfg));
    }

    @Test
    public void aNaturalDropResetsTheCountAndMarksTheComponentFound() {
        ResonanceData d = new ResonanceData();
        ResonanceRules.pity(d, boss, 1, false, cfg);
        ResonanceRules.pity(d, boss, 1, false, cfg);
        assertFalse(ResonanceRules.pity(d, boss, 1, true, cfg));
        assertTrue(d.found.contains("magma_warden"));
        assertFalse(d.dry.containsKey("magma_warden"));
        assertFalse(ResonanceRules.pity(d, boss, 1, false, cfg));
    }

    @Test
    public void shardsForgeOnlyAFoundComponentWithAWeeklyLimit() {
        ResonanceData d = new ResonanceData();
        d.shards = 1000;
        assertEquals(ResonanceRules.Forge.NOT_FOUND, ResonanceRules.forge(d, boss, T0, cfg));
        d.found.add("magma_warden");
        for (int i = 0; i < 5; i++) {
            assertEquals(ResonanceRules.Forge.OK, ResonanceRules.forge(d, boss, T0, cfg));
        }
        assertEquals(500, d.shards);
        assertEquals(ResonanceRules.Forge.WEEKLY_LIMIT, ResonanceRules.forge(d, boss, T0, cfg));
        assertEquals("the next ISO week starts again", ResonanceRules.Forge.OK, ResonanceRules.forge(d, boss, T0 + 7 * DAY, cfg));
        d.shards = 99;
        assertEquals(ResonanceRules.Forge.NOT_ENOUGH, ResonanceRules.forge(d, boss, T0 + 14 * DAY, cfg));
    }

    @Test
    public void theShippedSeedDeclaresEveryBastionWithRealComponents() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            ResonanceConfig seeded = ResonanceConfig.load(db);
            assertEquals(java.util.List.of(), seeded.problems());
            assertEquals(6, seeded.bastions().size());
            assertEquals(Realm.ASHES, seeded.bastions().get("magma_warden").realm());
            assertNull("the Echo belongs to no Realm", seeded.bastions().get("voidwyrm").realm());
            assertNotNull(seeded.bastions().get("crypt_lord"));
            assertEquals(100, seeded.forgeCost());
            assertEquals(4, seeded.pityRuns());
            assertEquals(3, seeded.pityRunsHigh());
            assertEquals(0.25, seeded.rankBonus(), 1e-9);
        }
    }
}
