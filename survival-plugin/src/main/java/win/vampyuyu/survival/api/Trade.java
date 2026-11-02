package win.vampyuyu.survival.api;

import java.util.Locale;

/** The two trades (GDD 11.3). They stack with any class and are never required (GDD 11.4). */
public enum Trade {
    ARTISAN("Artisan"), WARDEN("Warden");

    public static final int MAX_LEVEL = 5;

    private final String title;

    Trade(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The trade with this id (case-insensitive), or null. */
    public static Trade parse(String id) {
        if (id == null) {
            return null;
        }
        for (Trade t : values()) {
            if (t.name().equalsIgnoreCase(id.trim())) {
                return t;
            }
        }
        return null;
    }
}
