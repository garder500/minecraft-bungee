package win.vampyuyu.survival.menu;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class DailyClockTest {
    private static List<String> pool(int n) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add("q" + i);
        }
        return ids;
    }

    @Test
    public void rotationIsDeterministicAndIgnoresPoolOrder() {
        LocalDate day = LocalDate.of(2026, 10, 29);
        List<String> a = DailyClock.rotation(pool(25), day, 3);
        List<String> reversed = pool(25);
        java.util.Collections.reverse(reversed);
        assertEquals(a, DailyClock.rotation(reversed, day, 3));
        assertEquals(a, DailyClock.rotation(pool(25), day, 3));
        assertEquals(3, new HashSet<>(a).size());
    }

    @Test
    public void rotationChangesFromDayToDayAndAvoidsYesterday() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 60; i++) {
            List<String> today = DailyClock.rotation(pool(25), day.plusDays(i), 3);
            List<String> yesterday = DailyClock.rotation(pool(25), day.plusDays(i - 1), 3);
            for (String id : today) {
                assertFalse("repeat on day " + i, yesterday.contains(id));
            }
        }
        assertNotEquals(DailyClock.rotation(pool(25), day, 3), DailyClock.rotation(pool(25), day.plusDays(1), 3));
    }

    @Test
    public void smallPoolStillFillsTheCount() {
        assertEquals(2, DailyClock.rotation(pool(2), LocalDate.of(2026, 3, 3), 5).size());
        assertEquals(3, DailyClock.rotation(pool(4), LocalDate.of(2026, 3, 3), 3).size());
        assertTrue(DailyClock.rotation(List.of(), LocalDate.of(2026, 3, 3), 3).isEmpty());
    }

    @Test
    public void questDayRollsOverAtTheResetTime() {
        DailyClock clock = new DailyClock(ZoneId.of("UTC"), LocalTime.of(4, 0));
        assertEquals(LocalDate.of(2026, 10, 28), clock.dayOf(Instant.parse("2026-10-29T03:59:59Z")));
        assertEquals(LocalDate.of(2026, 10, 29), clock.dayOf(Instant.parse("2026-10-29T04:00:00Z")));
        assertEquals(60_000, clock.millisUntilReset(Instant.parse("2026-10-29T03:59:00Z")));
        assertEquals(24 * 3600_000L, clock.millisUntilReset(Instant.parse("2026-10-29T04:00:00Z")));
    }

    @Test
    public void zoneIsHonouredAcrossDst() {
        DailyClock clock = new DailyClock(ZoneId.of("Europe/Paris"), LocalTime.MIDNIGHT);
        assertEquals(LocalDate.of(2026, 10, 25), clock.dayOf(Instant.parse("2026-10-24T22:30:00Z")));
        assertEquals(LocalDate.of(2026, 10, 24), clock.dayOf(Instant.parse("2026-10-24T21:59:00Z")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void badResetTimeIsRejected() {
        DailyClock.parseReset("25:99");
    }
}
