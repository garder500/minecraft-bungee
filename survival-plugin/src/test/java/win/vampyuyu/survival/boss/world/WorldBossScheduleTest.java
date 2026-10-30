package win.vampyuyu.survival.boss.world;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class WorldBossScheduleTest {
    private static WorldBossConfig config() {
        return new WorldBossConfig(true, 120, 30, 2, List.of(300, 60, 10), 15, 5,
            List.of(new WorldBossConfig.Spot("sb_hub", 0, 80, 0, "A"), new WorldBossConfig.Spot("sb_hub", 5, 80, 5, "B")),
            List.of(new WorldBossConfig.Entry("a", 1), new WorldBossConfig.Entry("b", 3)), 5, 10);
    }

    @Test
    public void timesFollowTheConfig() {
        WorldBossSchedule s = new WorldBossSchedule(config());
        assertEquals(30 * 60_000L, s.initial(0));
        assertEquals(1000 + 120 * 60_000L, s.afterSpawn(1000));
        assertEquals(1000 + 5 * 60_000L, s.retry(1000));
        assertTrue(s.due(100, 100));
        assertFalse(s.due(99, 100));
        assertFalse(s.due(1_000_000, 0));
    }

    @Test
    public void eachAnnouncementFiresOnce() {
        WorldBossSchedule s = new WorldBossSchedule(config());
        long spawn = 1_000_000;
        assertEquals(List.of(), s.announcements(spawn - 400_000, spawn));
        assertEquals(List.of(300), s.announcements(spawn - 299_000, spawn));
        assertEquals(List.of(), s.announcements(spawn - 250_000, spawn));
        assertEquals(List.of(60), s.announcements(spawn - 59_000, spawn));
        assertEquals(List.of(10), s.announcements(spawn - 9_500, spawn));
        assertEquals(List.of(), s.announcements(spawn, spawn));
    }

    @Test
    public void aLongPauseAnnouncesOnlyTheClosestThreshold() {
        WorldBossSchedule s = new WorldBossSchedule(config());
        assertEquals(List.of(10), s.announcements(995_000, 1_000_000));
        assertEquals(List.of(), s.announcements(996_000, 1_000_000));
    }

    @Test
    public void weightedPickFavoursHeavyEntries() {
        WorldBossSchedule s = new WorldBossSchedule(config());
        Random r = new Random(7);
        int b = 0;
        for (int i = 0; i < 2000; i++) {
            if (s.pick(r).equals("b")) {
                b++;
            }
        }
        assertTrue("b should be about 75%: " + b, b > 1350 && b < 1650);
        assertNotNull(s.pickSpot(r));
    }
}
