package win.vampyuyu.survival.dungeon;

import static org.junit.Assert.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class DungeonConfigTest {
    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    @Test
    public void shippedDefaultsParseWithoutProblems() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/dungeon.yml");
                var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            y.load(reader);
        }
        DungeonConfig c = DungeonConfig.parse(y);
        assertEquals("no corrections on the shipped file: " + c.problems(), 0, c.problems().size());
        assertEquals(7, c.floorCount());
        assertEquals("The Entrance", c.floor(1).name());
        assertEquals(8, c.floor(1).rooms());
        assertEquals(34, c.floor(7).rooms());
        assertEquals(Grade.values().length, c.tiers.size());
        assertEquals(300, (int) c.thresholds.get(Grade.S_PLUS));
        assertEquals(0, (int) c.thresholds.get(Grade.D));
        assertEquals(11, c.rewardItems.size());
        assertEquals("the built-in reward table mirrors the file", DungeonConfig.DEFAULT_REWARDS, c.rewardItems);
        DungeonConfig defaults = DungeonConfig.parse(null);
        for (int f = 1; f <= 7; f++) {
            assertEquals("floor " + f + " built-in equals the file", defaults.floor(f), c.floor(f));
        }
        assertTrue(c.allowedCommands.contains("party"));
        assertEquals(3, c.maxConcurrentRuns);
        assertEquals(5, c.partyMaxSize);
        // the Dungeon Gate pad of the hub (x=33, z=0, hub y 100): the hint must fire where the signposts point
        assertEquals(33.5, c.entranceX, 0.0);
        assertEquals(101.0, c.entranceY, 0.0);
        assertEquals(0.5, c.entranceZ, 0.0);
        assertEquals("the scripted floor-scaled boss is the default", 0, c.bossExternalFromFloor);
    }

    @Test
    public void builtInEntranceEqualsTheShippedFile() {
        DungeonConfig d = DungeonConfig.parse(null);
        assertEquals(33.5, d.entranceX, 0.0);
        assertEquals(101.0, d.entranceY, 0.0);
        assertEquals(0.5, d.entranceZ, 0.0);
        DungeonConfig partial = DungeonConfig.parse(yaml("entrance:\n  enabled: true\n"));
        assertEquals(33.5, partial.entranceX, 0.0);
        assertEquals(101.0, partial.entranceY, 0.0);
        assertEquals(0.5, partial.entranceZ, 0.0);
    }

    @Test
    public void externalBossFloorIsClamped() {
        assertEquals(4, DungeonConfig.parse(yaml("boss-external-from-floor: 4")).bossExternalFromFloor);
        DungeonConfig c = DungeonConfig.parse(yaml("boss-external-from-floor: -3"));
        assertEquals(0, c.bossExternalFromFloor);
        assertFalse(c.problems().isEmpty());
    }

    @Test
    public void emptyConfigGivesSevenDefaultFloors() {
        DungeonConfig c = DungeonConfig.parse(null);
        assertEquals(7, c.floorCount());
        for (int f = 1; f <= 7; f++) {
            DungeonConfig.Floor fl = c.floor(f);
            assertTrue(fl.grid() >= 3 && fl.grid() <= 7);
            assertTrue(fl.rooms() >= fl.grid() + 2 && fl.rooms() <= fl.grid() * fl.grid());
            assertTrue(DungeonConfig.PALETTES.contains(fl.palette()));
        }
        assertTrue(c.floor(7).rooms() > c.floor(1).rooms());
        assertTrue(c.floor(7).hp() > c.floor(1).hp());
    }

    @Test
    public void badValuesAreClampedAndReported() {
        DungeonConfig c = DungeonConfig.parse(yaml("""
            max-concurrent-runs: 500
            close-delay-seconds: 1
            party: {max-size: 9}
            generation: {max-ms-per-tick: 0}
            mobs: {max-per-room: 30, max-per-run: 5}
            floors:
              1: {grid: 2, rooms: 99, palette: lava}
              3: {grid: 9}
            """));
        assertEquals(32, c.maxConcurrentRuns);
        assertEquals(5, c.closeDelaySeconds);
        assertEquals(5, c.partyMaxSize);
        assertEquals(1, c.maxMsPerTick);
        assertEquals(30, c.maxMobsPerRun);
        assertFalse(c.problems().isEmpty());
        assertEquals(3, c.floor(1).grid());
        assertEquals("unknown palette falls back", "stone_brick", c.floor(1).palette());
        assertEquals(9, c.floor(1).rooms()); // 3x3 grid has 9 cells
        // floor 3 became floor 2 (consecutive), grid clamped to 7
        assertEquals(2, c.floorCount());
        assertEquals(7, c.floor(2).grid());
        assertTrue(c.problems().stream().anyMatch(p -> p.contains("renumbered")));
    }

    @Test
    public void thresholdsAreForcedIncreasing() {
        DungeonConfig c = DungeonConfig.parse(yaml("""
            scoring:
              thresholds: {S+: 100, S: 200, A: 150, B: 160, C: 90, D: 5}
            """));
        int prev = Integer.MAX_VALUE;
        for (int i = Grade.values().length - 1; i >= 0; i--) {
            int t = c.thresholds.get(Grade.values()[i]);
            assertTrue(t < prev);
            prev = t;
        }
        assertEquals(0, (int) c.thresholds.get(Grade.D));
        assertFalse(c.problems().isEmpty());
    }

    @Test
    public void rewardEntriesAreValidated() {
        DungeonConfig c = DungeonConfig.parse(yaml("""
            rewards:
              items:
                - {item: a_item, weight: 5, min: 2, max: 4, min-floor: 2, min-grade: B}
                - {item: bad, weight: 0}
                - {item: bad2, weight: 1, min: 5, max: 2}
                - {weight: 1}
            """));
        assertEquals(1, c.rewardItems.size());
        assertEquals(Grade.B, c.rewardItems.get(0).minGrade());
        assertEquals(3, c.problems().size()); // the three bad entries
    }

    @Test
    public void tooManySpecialRoomsAreReported() {
        DungeonConfig c = DungeonConfig.parse(yaml("""
            floors:
              1: {grid: 3, rooms: 6, puzzles: 5, traps: 5}
            """));
        assertTrue(c.problems().stream().anyMatch(p -> p.contains("special rooms")));
    }
}
