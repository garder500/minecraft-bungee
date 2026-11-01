package win.vampyuyu.survival.utility;

import java.util.LinkedHashMap;
import java.util.Map;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** Reads the utility feature's tuning from the content database ({@code utility.*} settings and {@code utility_portable}). */
public final class UtilityRepo {
    private UtilityRepo() {}

    /** The settings, with every clamp of {@link UtilityConfig#from}. */
    public static UtilityConfig load(Db db) {
        return UtilityConfig.from(Settings.load(db, "utility"));
    }

    /** Portable block name to "everybody has the permission"; a missing row falls back to the old default. */
    public static Map<String, Boolean> portables(Db db) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        out.put("ec", true);
        out.put("workbench", true);
        for (String name : new String[] {"anvil", "grindstone", "stonecutter", "loom"}) {
            out.put(name, false);
        }
        db.read(c -> Db.query(c, "SELECT name, public FROM utility_portable", rs -> {
            out.put(rs.getString(1), rs.getInt(2) == 1);
            return null;
        }));
        return out;
    }
}
