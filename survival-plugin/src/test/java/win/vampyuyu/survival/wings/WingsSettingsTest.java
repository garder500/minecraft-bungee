package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class WingsSettingsTest {
    private WingsSettings shipped;

    @Before
    public void setUp() {
        MockBukkit.mock();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/wings.yml"), StandardCharsets.UTF_8));
        shipped = WingsSettings.from(yaml);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void shippedConfigMatchesTheCodeDefaults() {
        WingsSettings fallback = WingsSettings.from(new YamlConfiguration());
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
        YamlConfiguration y = new YamlConfiguration();
        y.set("worlds.allowed", java.util.List.of("sb_dungeon_x", "a"));
        WingsSettings s = WingsSettings.from(y);
        assertFalse(s.worldAllowed("sb_dungeon_x"));
        assertTrue(s.worldAllowed("a"));
    }

    @Test
    public void regionsParseAndContain() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("no-fly.regions", java.util.List.of(
            java.util.Map.of("world", "sb_world", "min", java.util.List.of(10, 0, 10), "max", java.util.List.of(0, 100, 0)),
            java.util.Map.of("world", "broken")));
        WingsSettings s = WingsSettings.from(y);
        assertEquals(1, s.regions.size());
        assertTrue(s.inRegion("sb_world", 5, 50, 5));
        assertTrue("max corner is inclusive", s.inRegion("sb_world", 10.9, 100.5, 10.9));
        assertFalse(s.inRegion("sb_world", 11.2, 50, 5));
        assertFalse(s.inRegion("other", 5, 50, 5));
    }

    @Test
    public void trailLinesFallBackToTheCloudOnGarbage() {
        assertEquals(Particle.END_ROD, WingsSettings.parseTrailLine("end_rod:3:0.4").particle());
        assertEquals(3, WingsSettings.parseTrailLine("end_rod:3:0.4").count());
        assertEquals(0.4, WingsSettings.parseTrailLine("end_rod:3:0.4").spread(), 1e-9);
        assertEquals(Particle.CLOUD, WingsSettings.parseTrailLine("not_a_particle:2").particle());
        assertEquals(Particle.CLOUD, WingsSettings.parseTrailLine("DUST:2").particle());  // needs data
        assertEquals(1, WingsSettings.parseTrailLine("CLOUD:abc").count());
        assertEquals(20, WingsSettings.parseTrailLine("CLOUD:500").count());
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
        YamlConfiguration y = new YamlConfiguration();
        y.set("max-level", 4);
        y.set("upgrades.1.core", 2);
        y.set("upgrades.1.amount", 7);
        y.set("upgrades.9.core", 1);  // beyond max-level: ignored
        WingsSettings s = WingsSettings.from(y);
        assertEquals(new WingsSettings.Upgrade(2, 7, 0), s.upgradeFrom(1));
        assertNotNull(s.upgradeFrom(3));
        assertNull(s.upgradeFrom(4));
    }

    @Test
    public void shortTablesRepeatTheirLastValue() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("levels.capacity-bonus", java.util.List.of(10, 20));
        WingsSettings s = WingsSettings.from(y);
        assertEquals(10, s.capacityBonus(1), 1e-9);
        assertEquals(20, s.capacityBonus(2), 1e-9);
        assertEquals(20, s.capacityBonus(10), 1e-9);
    }

    @Test
    public void absurdValuesAreClamped() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("levels.fly-speed", java.util.List.of(5.0));
        y.set("energy.island-drain-factor", 9);
        y.set("max-level", 1000);
        y.set("combat.seconds", -3);
        WingsSettings s = WingsSettings.from(y);
        assertEquals(1.0f, s.flySpeed(1), 1e-6f);
        assertEquals(1.0, s.islandDrainFactor, 1e-9);
        assertEquals(50, s.maxLevel);
        assertEquals(0, s.combatSeconds);
    }

    @Test
    public void resumeThresholdHasAFloor() {
        assertEquals(5, shipped.resumeThreshold(10), 1e-9);
        assertEquals(30, shipped.resumeThreshold(200), 1e-9);
    }
}
