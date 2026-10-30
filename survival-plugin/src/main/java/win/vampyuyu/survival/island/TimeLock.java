package win.vampyuyu.survival.island;

/** Fixed time of day for the players standing on an island (client-side per-player time). */
public enum TimeLock {
    NONE("Natural cycle", -1),
    DAY("Day", 1000),
    NOON("Noon", 6000),
    SUNSET("Sunset", 12500),
    NIGHT("Night", 14000),
    MIDNIGHT("Midnight", 18000),
    SUNRISE("Sunrise", 23000);

    private final String label;
    private final long ticks;

    TimeLock(String label, long ticks) {
        this.label = label;
        this.ticks = ticks;
    }

    public String label() {
        return label;
    }

    /** Time of day in ticks, -1 for the natural cycle. */
    public long ticks() {
        return ticks;
    }

    public TimeLock next() {
        TimeLock[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static TimeLock parse(String name) {
        if (name != null) {
            for (TimeLock lock : values()) {
                if (lock.name().equalsIgnoreCase(name)) {
                    return lock;
                }
            }
        }
        return NONE;
    }
}
