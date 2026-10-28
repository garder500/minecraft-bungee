package win.vampyuyu.survival.menu;

import java.io.IOException;
import java.util.logging.Level;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.JsonStore;

/**
 * Writes one JSON file off the main thread such that the LAST requested state always wins, even though the Bukkit
 * async pool may run two writes at the same time: every request carries a version and an older snapshot is dropped.
 */
final class OrderedJsonWriter {
    private final Core core;
    private final String path;
    private final Object lock = new Object();
    private long requested;
    private long written;

    OrderedJsonWriter(Core core, String path) {
        this.core = core;
        this.path = path;
    }

    /** Takes the snapshot now (call from the thread that owns the data) and writes it later. */
    void write(Object snapshot) {
        long version;
        String json;
        synchronized (lock) {
            version = ++requested;
            json = JsonStore.GSON.toJson(snapshot);
        }
        Class<?> type = snapshot.getClass();
        core.scheduler().async(() -> flush(version, json, type));
    }

    /** Writes synchronously (disable). */
    void writeNow(Object snapshot) {
        long version;
        String json;
        synchronized (lock) {
            version = ++requested;
            json = JsonStore.GSON.toJson(snapshot);
        }
        flush(version, json, snapshot.getClass());
    }

    private void flush(long version, String json, Class<?> type) {
        synchronized (lock) {
            if (version < written) {
                return;
            }
            written = version;
            try {
                core.store().write(path, JsonStore.GSON.fromJson(json, type));
            } catch (IOException | RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not write " + path, e);
            }
        }
    }
}
