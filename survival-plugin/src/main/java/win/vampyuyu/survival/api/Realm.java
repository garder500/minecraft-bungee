package win.vampyuyu.survival.api;

import java.util.Locale;

/**
 * The eight Celestial Realms, in the order of the GDD (section 3). The ordinal is the Sky Gate number minus one.
 * The id is the stable key stored on disk and typed in commands; never rename one.
 */
public enum Realm {
    ASHES("ashes", "Cracked Ashes", "Lava Colossus"),
    GROVE("grove", "Whispering Grove", "Mycelial Queen"),
    HEIGHTS("heights", "Frozen Heights", "Boreal Watcher"),
    CITADEL("citadel", "Citadel of Golden Sands", "Temporal Pharaoh"),
    MARSHES("marshes", "Putrid Marshes", "Mire Abomination"),
    ARCHIPELAGO("archipelago", "Tempestuous Archipelago", "Primordial Thunder Dragon"),
    DEPTHS("depths", "Abyssal Depths", "Chained Leviathan"),
    NECROPOLIS("necropolis", "Ethereal Necropolis", "Judge of Lost Souls");

    private final String id;
    private final String title;
    private final String guardian;

    Realm(String id, String title, String guardian) {
        this.id = id;
        this.title = title;
        this.guardian = guardian;
    }

    public String id() {
        return id;
    }

    /** English display name, without the word "Realm". */
    public String title() {
        return title;
    }

    public String guardian() {
        return guardian;
    }

    /** Gate number, 1 to 8. */
    public int number() {
        return ordinal() + 1;
    }

    /** The realm with this id (case insensitive), or null. */
    public static Realm byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.toLowerCase(Locale.ROOT);
        for (Realm realm : values()) {
            if (realm.id.equals(wanted)) {
                return realm;
            }
        }
        return null;
    }
}
