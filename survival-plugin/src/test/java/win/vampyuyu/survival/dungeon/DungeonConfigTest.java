package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;
import static win.vampyuyu.survival.dungeon.DungeonTestData.*;

import java.util.Map;
import org.junit.Test;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Db.DataException;

public class DungeonConfigTest {
    @Test
    public void everyFloorHasItsOwnBossWithAttacksThatExistAndARealEntityAndWeapon() {
        win.vampyuyu.survival.data.Db db = win.vampyuyu.survival.data.Db.memory();
        win.vampyuyu.survival.data.Seeds.sync(db, win.vampyuyu.survival.data.ContentDomains.ALL, null);
        DungeonConfig cfg = DungeonConfig.load(db);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int floor = 1; floor <= 7; floor++) {
            DungeonConfig.BossKit kit = cfg.bosses.get(floor);
            org.junit.Assert.assertNotNull("floor " + floor, kit);
            org.junit.Assert.assertTrue(kit.name(), names.add(kit.name()));
            org.junit.Assert.assertTrue(kit.name(), org.bukkit.entity.EntityType.valueOf(kit.entity()).isSpawnable());
            org.junit.Assert.assertTrue(kit.name(), org.bukkit.Material.valueOf(kit.weapon()).isItem());
            org.junit.Assert.assertFalse(kit.name(), kit.attacks().isEmpty());
            org.junit.Assert.assertFalse(kit.name(), kit.phaseGoal().isBlank());
            for (DungeonConfig.BossAttack attack : kit.attacks()) {
                org.junit.Assert.assertTrue("shown as a subtitle, it must fit on the screen: " + attack.warning(), attack.warning().length() <= 26);
            }
        }
    }

    private static void refused(Db db, String sql) {
        try {
            exec(db, sql);
            fail("the schema should refuse: " + sql);
        } catch (DataException expected) {
            // the CHECK constraint did its job
        }
    }

    @Test
    public void shippedDefaultsParseWithoutProblems() {
        DungeonConfig c;
        try (Db db = seeded()) {
            c = DungeonConfig.load(db);
        }
        assertEquals("no corrections on the shipped data: " + c.problems(), 0, c.problems().size());
        assertEquals(7, c.floorCount());
        assertEquals("The Entrance", c.floor(1).name());
        assertEquals(8, c.floor(1).rooms());
        assertEquals(34, c.floor(7).rooms());
        assertEquals(Grade.values().length, c.tiers.size());
        assertEquals(300, (int) c.thresholds.get(Grade.S_PLUS));
        assertEquals(0, (int) c.thresholds.get(Grade.D));
        assertEquals(11, c.rewardItems.size());
        assertEquals("the built-in reward table mirrors the seed", DungeonConfig.DEFAULT_REWARDS, c.rewardItems);
        DungeonConfig defaults = DungeonConfig.defaults();
        for (int f = 1; f <= 7; f++) {
            assertEquals("floor " + f + " built-in equals the seed", defaults.floor(f), c.floor(f));
        }
        for (Grade g : Grade.values()) {
            assertEquals("grade " + g + " built-in equals the seed", defaults.tiers.get(g), c.tiers.get(g));
            assertEquals("threshold " + g + " built-in equals the seed", defaults.thresholds.get(g), c.thresholds.get(g));
        }
        assertTrue(c.allowedCommands.contains("party"));
        assertEquals(defaults.allowedCommands, c.allowedCommands);
        assertEquals(3, c.maxConcurrentRuns);
        assertEquals(5, c.partyMaxSize);
        // the Dungeon Gate pad of the hub (x=33, z=0, hub y 100): the hint must fire where the signposts point
        assertEquals(33.5, c.entranceX, 0.0);
        assertEquals(101.0, c.entranceY, 0.0);
        assertEquals(0.5, c.entranceZ, 0.0);
        assertEquals("the scripted floor-scaled boss is the default", 0, c.bossExternalFromFloor);
    }

    @Test
    public void builtInEntranceEqualsTheShippedData() {
        DungeonConfig d = DungeonConfig.defaults();
        assertEquals(33.5, d.entranceX, 0.0);
        assertEquals(101.0, d.entranceY, 0.0);
        assertEquals(0.5, d.entranceZ, 0.0);
        try (Db db = empty()) {
            setting(db, "entrance.enabled", "bool", "true");
            DungeonConfig partial = DungeonConfig.load(db);
            assertEquals(33.5, partial.entranceX, 0.0);
            assertEquals(101.0, partial.entranceY, 0.0);
            assertEquals(0.5, partial.entranceZ, 0.0);
        }
    }

    @Test
    public void externalBossFloorIsClamped() {
        try (Db db = empty()) {
            setting(db, "boss-external-from-floor", "int", "4");
            assertEquals(4, DungeonConfig.load(db).bossExternalFromFloor);
        }
        try (Db db = empty()) {
            setting(db, "boss-external-from-floor", "int", "-3");
            DungeonConfig c = DungeonConfig.load(db);
            assertEquals(0, c.bossExternalFromFloor);
            assertFalse(c.problems().isEmpty());
        }
    }

    @Test
    public void emptyDataGivesSevenDefaultFloors() {
        for (DungeonConfig c : new DungeonConfig[] {DungeonConfig.defaults(), loadEmpty()}) {
            assertEquals(7, c.floorCount());
            for (int f = 1; f <= 7; f++) {
                DungeonConfig.Floor fl = c.floor(f);
                assertTrue(fl.grid() >= 3 && fl.grid() <= 7);
                assertTrue(fl.rooms() >= fl.grid() + 2 && fl.rooms() <= fl.grid() * fl.grid());
                assertTrue(DungeonConfig.PALETTES.contains(fl.palette()));
            }
            assertTrue(c.floor(7).rooms() > c.floor(1).rooms());
            assertTrue("deeper floors are higher tiers", c.floor(7).tier() > c.floor(1).tier());
            assertEquals(DungeonConfig.DEFAULT_REWARDS, c.rewardItems);
            assertEquals(DungeonConfig.DEFAULT_TRADES, c.forgeTrades);
        }
    }

    private static DungeonConfig loadEmpty() {
        try (Db db = empty()) {
            return DungeonConfig.load(db);
        }
    }

    @Test
    public void badValuesAreClampedAndReported() {
        try (Db db = empty()) {
            setting(db, "max-concurrent-runs", "int", "500");
            setting(db, "close-delay-seconds", "int", "1");
            setting(db, "party.max-size", "int", "9");
            setting(db, "generation.max-ms-per-tick", "int", "0");
            setting(db, "mobs.max-per-room", "int", "30");
            setting(db, "mobs.max-per-run", "int", "5");
            exec(db, floor(1, Map.of("rooms", "49")), floor(3, Map.of("grid", "7", "rooms", "20")));
            DungeonConfig c = DungeonConfig.load(db);
            assertEquals(32, c.maxConcurrentRuns);
            assertEquals(5, c.closeDelaySeconds);
            assertEquals(5, c.partyMaxSize);
            assertEquals(1, c.maxMsPerTick);
            assertEquals(30, c.maxMobsPerRun);
            assertFalse(c.problems().isEmpty());
            assertEquals(9, c.floor(1).rooms()); // 3x3 grid has 9 cells
            // floor 3 became floor 2 (consecutive)
            assertEquals(2, c.floorCount());
            assertEquals(7, c.floor(2).grid());
            assertTrue(c.problems().stream().anyMatch(p -> p.contains("renumbered")));
        }
    }

    @Test
    public void theSchemaRefusesFloorsOutsideTheRanges() {
        try (Db db = empty()) {
            refused(db, floor(1, Map.of("grid", "2")));
            refused(db, floor(1, Map.of("grid", "9")));
            refused(db, floor(1, Map.of("palette", "'lava'")));
            refused(db, floor(1, Map.of("par_seconds", "5")));
            refused(db, floor(21, Map.of()));
        }
    }

    @Test
    public void thresholdsAreForcedIncreasing() {
        try (Db db = empty()) {
            exec(db, grade("S+", 0, 100), grade("S", 1, 200), grade("A", 2, 150), grade("B", 3, 160), grade("C", 4, 90),
                grade("D", 5, 5));
            DungeonConfig c = DungeonConfig.load(db);
            int prev = Integer.MAX_VALUE;
            for (int i = Grade.values().length - 1; i >= 0; i--) {
                int t = c.thresholds.get(Grade.values()[i]);
                assertTrue(t < prev);
                prev = t;
            }
            assertEquals(0, (int) c.thresholds.get(Grade.D));
            assertFalse(c.problems().isEmpty());
        }
    }

    @Test
    public void rewardEntriesAreValidated() {
        try (Db db = empty()) {
            exec(db, "INSERT INTO dungeon_reward (id, position, item, weight, min_amount, max_amount, min_floor, min_grade) "
                + "VALUES ('a_item', 0, 'a_item', 5, 2, 4, 2, 'B')");
            refused(db, "INSERT INTO dungeon_reward (id, position, item, weight) VALUES ('bad', 1, 'bad', 0)");
            refused(db, "INSERT INTO dungeon_reward (id, position, item, weight, min_amount, max_amount) VALUES ('bad2', 2, 'bad2', 1, 5, 2)");
            refused(db, "INSERT INTO dungeon_reward (id, position, item, weight, min_grade) VALUES ('bad3', 3, 'bad3', 1, 'Z')");
            DungeonConfig c = DungeonConfig.load(db);
            assertEquals(1, c.rewardItems.size());
            assertEquals(Grade.B, c.rewardItems.get(0).minGrade());
            assertEquals(0, c.problems().size());
        }
    }

    @Test
    public void tooManySpecialRoomsAreReported() {
        try (Db db = empty()) {
            exec(db, floor(1, Map.of("rooms", "6", "puzzles", "5", "traps", "5")));
            DungeonConfig c = DungeonConfig.load(db);
            assertTrue(c.problems().stream().anyMatch(p -> p.contains("special rooms")));
        }
    }
}
