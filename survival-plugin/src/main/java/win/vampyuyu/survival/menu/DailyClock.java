package win.vampyuyu.survival.menu;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Day boundaries of the daily quests: a "quest day" starts at {@code resetTime} in {@code zone}, and the rotation
 * of daily quests is a pure function of the day (same quests for everybody, same result after a restart).
 */
public final class DailyClock {
    private final ZoneId zone;
    private final LocalTime reset;

    public DailyClock(ZoneId zone, LocalTime reset) {
        this.zone = zone;
        this.reset = reset;
    }

    /** Parses {@code "HH:mm"}; throws IllegalArgumentException on garbage. */
    public static LocalTime parseReset(String text) {
        try {
            return LocalTime.parse(text.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Bad reset time '" + text + "' (expected HH:mm)", e);
        }
    }

    public static ZoneId parseZone(String text) {
        try {
            return ZoneId.of(text.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Bad time zone '" + text + "'", e);
        }
    }

    /** The quest day an instant belongs to. */
    public LocalDate dayOf(Instant now) {
        ZonedDateTime zoned = now.atZone(zone);
        return zoned.minus(Duration.between(LocalTime.MIDNIGHT, reset)).toLocalDate();
    }

    /** Milliseconds until the next reset. */
    public long millisUntilReset(Instant now) {
        LocalDate day = dayOf(now);
        ZonedDateTime next = day.plusDays(1).atTime(reset).atZone(zone);
        return Math.max(0, Duration.between(now, next.toInstant()).toMillis());
    }

    /**
     * The daily quests of a day, deterministic and the same for everybody. The sorted pool is cut into cycles of
     * {@code pool / count} days; inside a cycle every quest is offered at most once (a seeded shuffle of the cycle
     * number), and the first day of a cycle never repeats the quests of the day before. A pool smaller than twice
     * {@code count} cannot avoid repeats and is simply shuffled per day.
     */
    public static List<String> rotation(List<String> pool, LocalDate day, int count) {
        List<String> sorted = new ArrayList<>(new java.util.TreeSet<>(pool));
        int wanted = Math.min(count, sorted.size());
        if (wanted <= 0) {
            return List.of();
        }
        long n = day.toEpochDay();
        if (sorted.size() < wanted * 2) {
            return List.copyOf(shuffled(sorted, n).subList(0, wanted));
        }
        int cycle = sorted.size() / wanted;
        long epoch = Math.floorDiv(n, (long) cycle);
        int index = (int) Math.floorMod(n, (long) cycle);
        List<String> order = shuffled(sorted, epoch);
        List<String> picked = new ArrayList<>(order.subList(index * wanted, (index + 1) * wanted));
        if (index == 0) {
            List<String> previous = shuffled(sorted, epoch - 1);
            List<String> previousLast = previous.subList((cycle - 1) * wanted, cycle * wanted);
            List<String> tomorrow = cycle > 1 ? order.subList(wanted, 2 * wanted) : List.of();
            for (int i = 0; i < picked.size(); i++) {
                if (previousLast.contains(picked.get(i))) {
                    // borrow from the far end of the cycle: never from tomorrow's slice, never from yesterday's
                    for (int c = order.size() - 1; c >= wanted; c--) {
                        String candidate = order.get(c);
                        if (!picked.contains(candidate) && !previousLast.contains(candidate)
                                && !tomorrow.contains(candidate)) {
                            picked.set(i, candidate);
                            break;
                        }
                    }
                }
            }
        }
        return List.copyOf(picked);
    }

    private static List<String> shuffled(List<String> sorted, long seedKey) {
        List<String> copy = new ArrayList<>(sorted);
        Collections.shuffle(copy, new Random(seedKey * 0x9E3779B97F4A7C15L + 0x51ED270B));
        return copy;
    }
}
