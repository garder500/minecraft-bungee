package win.vampyuyu.survival.island;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import win.vampyuyu.survival.core.JsonStore;

/**
 * Writes registry snapshots on one background thread, in order: the JSON is produced on the main thread (so it is a
 * consistent snapshot) and only the disk write is asynchronous.
 */
public final class IslandStorage {
    public static final String FILE = "island/islands.json";

    private final JsonStore store;
    private final Logger log;
    private final String file;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "island-store");
        thread.setDaemon(true);
        return thread;
    });
    private boolean closed;

    public IslandStorage(JsonStore store, Logger log) {
        this(store, log, FILE);
    }

    public IslandStorage(JsonStore store, Logger log, String file) {
        this.store = store;
        this.log = log;
        this.file = file;
    }

    /** Queues a write of an already serialised document. */
    public synchronized void saveAsync(String json) {
        if (closed) {
            write(json);
            return;
        }
        io.execute(() -> write(json));
    }

    private void write(String json) {
        try {
            store.writeText(file, json);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save " + file, e);
        }
    }

    /** Flushes the queue, then writes {@code finalJson} synchronously. */
    public synchronized void close(String finalJson) {
        if (closed) {
            return;
        }
        closed = true;
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                log.warning("Island store did not finish its queue in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        write(finalJson);
    }
}
