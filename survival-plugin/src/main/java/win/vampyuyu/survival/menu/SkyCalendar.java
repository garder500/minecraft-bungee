package win.vampyuyu.survival.menu;

/**
 * The Skyblock date shown in the sidebar, derived from the world's time: one Minecraft day is one Skyblock day,
 * 31 days make a month, 12 months make a year (372 days). Pure.
 */
public final class SkyCalendar {
    private SkyCalendar() {}

    private static final String[] MONTHS = {
        "Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
        "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

    /** "Late Summer 15th" for a world's full time (ticks). */
    public static String date(long fullTime) {
        long day = Math.max(0, fullTime) / 24000;
        int month = (int) (day / 31 % 12);
        int dayOfMonth = (int) (day % 31) + 1;
        return MONTHS[month] + " " + dayOfMonth + ordinal(dayOfMonth);
    }

    /** Year number, starting at 1. */
    public static long year(long fullTime) {
        return Math.max(0, fullTime) / 24000 / 372 + 1;
    }

    /** "7:10pm" rounded down to 10 minutes. World tick 0 is 6:00am. */
    public static String clock(long worldTime) {
        long tick = Math.floorMod(worldTime, 24000L);
        int hour24 = (int) ((tick / 1000 + 6) % 24);
        int minute = (int) (tick % 1000 * 60 / 1000);
        minute -= minute % 10;
        int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
        return hour12 + ":" + (minute < 10 ? "0" : "") + minute + (hour24 < 12 ? "am" : "pm");
    }

    /** True from sunrise (tick 23000) to sunset (tick 12999). */
    public static boolean isDay(long worldTime) {
        long tick = Math.floorMod(worldTime, 24000L);
        return tick < 13000 || tick >= 23000;
    }

    static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) {
            return "th";
        }
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
