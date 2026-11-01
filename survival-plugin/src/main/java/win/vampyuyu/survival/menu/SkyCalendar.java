package win.vampyuyu.survival.menu;

/**
 * The isles' own calendar, derived from the world's time: one Minecraft day is one day, 24 days make a moon, the eight
 * moons of the sky make a year (192 days). The time of day is told as a part of the day, not a clock. Pure.
 */
public final class SkyCalendar {
    private SkyCalendar() {}

    static final int DAYS_PER_MOON = 24;
    private static final String[] MOONS = {"Dawn", "Gale", "Bloom", "Blaze", "Ember", "Mist", "Frost", "Star"};

    /** "Gale Moon · Day 5" for a world's full time (ticks). */
    public static String date(long fullTime) {
        long day = Math.max(0, fullTime) / 24000;
        int moon = (int) (day / DAYS_PER_MOON % MOONS.length);
        int dayOfMoon = (int) (day % DAYS_PER_MOON) + 1;
        return MOONS[moon] + " Moon · Day " + dayOfMoon;
    }

    /** Year number, starting at 1. */
    public static long year(long fullTime) {
        return Math.max(0, fullTime) / 24000 / (DAYS_PER_MOON * MOONS.length) + 1;
    }

    /** The part of the day: Dawn, Morning, Noon, Afternoon, Dusk or Night. World tick 0 is sunrise. */
    public static String clock(long worldTime) {
        long tick = Math.floorMod(worldTime, 24000L);
        if (tick >= 23000 || tick < 1000) return "Dawn";
        if (tick < 5000) return "Morning";
        if (tick < 7000) return "Noon";
        if (tick < 11000) return "Afternoon";
        if (tick < 13000) return "Dusk";
        return "Night";
    }

    /** True from sunrise (tick 23000) to sunset (tick 12999). */
    public static boolean isDay(long worldTime) {
        long tick = Math.floorMod(worldTime, 24000L);
        return tick < 13000 || tick >= 23000;
    }
}
