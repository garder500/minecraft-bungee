package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Particle;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.TestDb;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class WingsSettingsTest {
    private WingsSettings shipped;

    @Before
    public void setUp() {
        MockBukkit.mock();
        shipped = custom(db -> { });
    }

    /** The seeded Wings tuning after {@code edits} were applied to the database. */
    static WingsSettings custom(java.util.function.Consumer<Db> edits) {
        try (Db db = TestDb.seeded()) {
            edits.accept(db);
            return WingsRepo.load(db);
        }
    }

    /** Replaces the level table by rows of capacity bonus only (the other columns copy the first seeded level). */
    static void capacityRows(Db db, double... values) {
        TestDb.exec(db, "DELETE FROM wings_level_stat");
        TestDb.exec(db, "DELETE FROM wings_level WHERE level > ?", values.length);
        for (int i = 0; i < values.length; i++) {
            TestDb.exec(db, "UPDATE wings_level SET capacity_bonus = ? WHERE level = ?", values[i], i + 1);
        }
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void shippedConfigMatchesTheCodeDefaults() {
        WingsSettings fallback = WingsSettings.defaults();
        assertEquals(fallback.maxLevel, shipped.maxLevel);
        assertArrayEquals(fallback.capacityBonus, shipped.capacityBonus, 1e-9);
        assertArrayEquals(fallback.flySpeed, shipped.flySpeed, 1e-6f);
        assertEquals(fallback.allowedWorlds, shipped.allowedWorlds);
        assertEquals(fallback.drainFly, shipped.drainFly, 1e-9);
        assertEquals(fallback.upgrades, shipped.upgrades);
        assertEquals(fallback.emergencyReasons, shipped.emergencyReasons);
    }

    @Test
    public void tablesHaveOneEntryPerLevelAndGrowWithTheLevel() {
        assertEquals(10, shipped.maxLevel);
        for (int level = 2; level <= 10; level++) {
            assertTrue(shipped.capacityBonus(level) > shipped.capacityBonus(level - 1));
            assertTrue(shipped.regenMultiplier(level) >= shipped.regenMultiplier(level - 1));
            assertTrue(shipped.drainMultiplier(level) <= shipped.drainMultiplier(level - 1));
            assertTrue(shipped.flySpeed(level) >= shipped.flySpeed(level - 1));
        }
        assertEquals(1.0, shipped.drainMultiplier(1), 1e-9);
    }

    @Test
    public void levelsAreClamped() {
        assertEquals(1, shipped.clampLevel(-5));
        assertEquals(10, shipped.clampLevel(99));
        assertEquals(shipped.capacityBonus(10), shipped.capacityBonus(500), 1e-9);
        assertEquals(shipped.capacityBonus(1), shipped.capacityBonus(0), 1e-9);
    }

    @Test
    public void tiersCoverTwoLevelsEach() {
        assertEquals(1, WingsSettings.tierOf(1));
        assertEquals(1, WingsSettings.tierOf(2));
        assertEquals(2, WingsSettings.tierOf(3));
        assertEquals(3, WingsSettings.tierOf(6));
        assertEquals(4, WingsSettings.tierOf(8));
        assertEquals(5, WingsSettings.tierOf(9));
        assertEquals(5, WingsSettings.tierOf(10));
        assertEquals(5, WingsSettings.tierOf(40));
    }

    @Test
    public void sprintingDrainsMoreAndHoveringLess() {
        double fly = shipped.drainPerSecond(1, false, false, false);
        assertTrue(shipped.drainPerSecond(1, true, false, false) > fly);
        assertTrue(shipped.drainPerSecond(1, false, true, false) < fly);
        assertTrue("island halves the drain", shipped.drainPerSecond(1, false, false, true) < fly);
        assertTrue("higher level drains less", shipped.drainPerSecond(10, false, false, false) < fly);
    }

    @Test
    public void regenIsFasterOnIslandsAndAtHigherLevels() {
        assertTrue(shipped.regenPerSecond(1, true) > shipped.regenPerSecond(1, false));
        assertTrue(shipped.regenPerSecond(10, false) > shipped.regenPerSecond(1, false));
    }

    @Test
    public void flightSecondsGrowPastAMinuteAtTheTop() {
        double l1 = shipped.flightSeconds(100 + shipped.capacityBonus(1), 1);
        double l10 = shipped.flightSeconds(100 + shipped.capacityBonus(10), 10);
        assertTrue(l1 > 20 && l1 < 60);
        assertTrue(l10 > 150);
    }

    @Test
    public void worldsAreAllowedByNameAndDungeonsNever() {
        for (String w : new String[] {"sb_hub", "sb_world", "sb_nether", "sb_end", "world"}) {
            assertTrue(w, shipped.worldAllowed(w));
        }
        assertFalse(shipped.worldAllowed("sb_dungeon_abc"));
        assertFalse(shipped.worldAllowed("lobby"));
        assertFalse(shipped.worldAllowed(null));
    }

    @Test
    public void deniedPrefixBeatsTheAllowList() {
        WingsSettings s = custom(db -> TestDb.setList(db, "wings.worlds.allowed", "sb_dungeon_x", "a"));
        assertFalse(s.worldAllowed("sb_dungeon_x"));
        assertTrue(s.worldAllowed("a"));
    }

    @Test
    public void regionsParseAndContain() {
        WingsSettings s = custom(db -> TestDb.exec(db, "INSERT INTO wings_region (id, world, min_x, min_y, min_z, max_x, max_y, max_z) "
            + "VALUES ('r1', 'sb_world', 10, 0, 10, 0, 100, 0)"));
        assertEquals(1, s.regions.size());
        assertTrue(s.inRegion("sb_world", 5, 50, 5));
        assertTrue("max corner is inclusive", s.inRegion("sb_world", 10.9, 100.5, 10.9));
        assertFalse(s.inRegion("sb_world", 11.2, 50, 5));
        assertFalse(s.inRegion("other", 5, 50, 5));
    }

    @Test
    public void trailLinesFallBackToTheCloudOnGarbage() {
        assertEquals(Particle.END_ROD, WingsSettings.parseTrail("end_rod", 3, 0.4).particle());
        assertEquals(3, WingsSettings.parseTrail("end_rod", 3, 0.4).count());
        assertEquals(0.4, WingsSettings.parseTrail("end_rod", 3, 0.4).spread(), 1e-9);
        assertEquals(0.15, WingsSettings.parseTrail("end_rod", 3, null).spread(), 1e-9);
        assertEquals(Particle.CLOUD, WingsSettings.parseTrail("not_a_particle", 2, null).particle());
        assertEquals(Particle.CLOUD, WingsSettings.parseTrail("DUST", 2, null).particle());  // needs data
        assertEquals(20, WingsSettings.parseTrail("CLOUD", 500, null).count());
    }

    @Test
    public void everyShippedTrailIsAValidDatalessParticle() {
        for (int level = 1; level <= 10; level++) {
            assertNull(shipped.trail(level).particle().getDataType() == Void.class ? null : "bad", null);
            assertEquals(Void.class, shipped.trail(level).particle().getDataType());
        }
        assertNotEquals(shipped.trail(1).particle(), shipped.trail(10).particle());
    }

    @Test
    public void upgradeTableCoversEveryStepAndTheTopHasNone() {
        for (int level = 1; level < 10; level++) {
            WingsSettings.Upgrade up = shipped.upgradeFrom(level);
            assertNotNull("level " + level, up);
            assertTrue(up.amount() >= 1);
            assertTrue(up.coreTier() >= 1 && up.coreTier() <= 5);
        }
        assertNull(shipped.upgradeFrom(10));
        assertTrue("cores get rarer", shipped.upgradeFrom(9).coreTier() >= shipped.upgradeFrom(1).coreTier());
    }

    @Test
    public void missingUpgradeRowsGetADefault() {
        WingsSettings s = custom(db -> {
            TestDb.set(db, "wings.max-level", "int", "4");
            TestDb.exec(db, "DELETE FROM wings_upgrade");
            TestDb.exec(db, "INSERT INTO wings_upgrade (level, core, amount, coins) VALUES (1, 2, 7, 0)");
            TestDb.exec(db, "INSERT INTO wings_upgrade (level, core, amount, coins) VALUES (9, 1, 1, 0)");  // beyond max-level: ignored
        });
        assertEquals(new WingsSettings.Upgrade(2, 7, 0), s.upgradeFrom(1));
        assertNotNull(s.upgradeFrom(3));
        assertNull(s.upgradeFrom(4));
    }

    @Test
    public void shortTablesRepeatTheirLastValue() {
        WingsSettings s = custom(db -> capacityRows(db, 10, 20));
        assertEquals(10, s.capacityBonus(1), 1e-9);
        assertEquals(20, s.capacityBonus(2), 1e-9);
        assertEquals(20, s.capacityBonus(10), 1e-9);
    }

    @Test
    public void absurdValuesAreClamped() {
        WingsSettings s = custom(db -> {
            TestDb.exec(db, "DELETE FROM wings_level_stat");
            TestDb.exec(db, "DELETE FROM wings_level WHERE level > 1");
            TestDb.exec(db, "UPDATE wings_level SET fly_speed = 5.0");
            TestDb.set(db, "wings.energy.island-drain-factor", "int", "9");
            TestDb.set(db, "wings.max-level", "int", "1000");
            TestDb.set(db, "wings.combat.seconds", "int", "-3");
        });
        assertEquals(1.0f, s.flySpeed(1), 1e-6f);
        assertEquals(1.0, s.islandDrainFactor, 1e-9);
        assertEquals(50, s.maxLevel);
        assertEquals(0, s.combatSeconds);
    }

    @Test
    public void shippedRowsLoadWithoutProblems() {
        try (Db db = TestDb.seeded()) {
            assertTrue(win.vampyuyu.survival.data.Settings.load(db, "wings").problems().isEmpty());
            assertEquals(10, TestDb.count(db, "SELECT COUNT(*) FROM wings_level"));
        }
    }

    @Test
    public void resumeThresholdHasAFloor() {
        assertEquals(5, shipped.resumeThreshold(10), 1e-9);
        assertEquals(30, shipped.resumeThreshold(200), 1e-9);
    }
}
