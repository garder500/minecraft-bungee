package win.vampyuyu.survival.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.JsonStore;

/**
 * The write-ahead helper behind the bazaar and the auction house: one JSON file that always holds the WHOLE market
 * state, written atomically (temp file + atomic move) off the main thread.
 *
 * <p>Protocol (main thread only): mutate the in-memory state, call {@link #request}. Requests made in the same short
 * window are coalesced into one snapshot, serialised on the main thread (so it is consistent), then written by one
 * IO thread, in order. Before each snapshot the main thread queues every pending player profile ({@code flush}) and
 * the IO thread waits for those writes ({@code barrier}) before it writes the market file. The callbacks run back on
 * the main thread once the write is on disk, with {@code true}, or {@code false} when it failed (the next request
 * retries with a fresh snapshot).
 *
 * <p>Ordering rules that make a crash lose value instead of duplicating it:
 * <ul>
 *   <li>Value moving INTO the market (coins/items escrowed): the player's debit is made durable (barrier) BEFORE the
 *       market file that records the escrow.</li>
 *   <li>Value moving OUT of the market (claims, and the proceeds of instant trades): it is removed from the market
 *       file FIRST; the player is credited / given the goods only in the callback, after that file is durable. A
 *       failed write puts the value back as a claim.</li>
 * </ul>
 */
final class DurableFile {
    /** Where tasks run. Production uses the scheduler and one IO thread; tests run everything inline. */
    interface Env {
        void defer(long ticks, Runnable task);

        void io(Runnable task);

        void main(Runnable task);
    }

    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private final JsonStore store;
    private final String path;
    private final Supplier<Object> snapshot;
    private final Env env;
    private final Runnable flush;
    private final Runnable barrier;
    private final Logger log;
    private volatile long delayTicks;
    private final List<Consumer<Boolean>> waiting = new ArrayList<>();
    private boolean scheduled;
    private boolean closed;

    /**
     * @param flush   runs on the MAIN thread right before each snapshot: queues every dirty player profile (they are
     *                serialised on the thread that asks, and other features edit their sections on the main thread)
     * @param barrier runs on the IO thread right before the file is written: waits until the queued profile writes
     *                are on disk
     */
    DurableFile(JsonStore store, String path, Supplier<Object> snapshot, Env env, Runnable flush, Runnable barrier,
            Logger log, long delayTicks) {
        this.store = store;
        this.path = path;
        this.snapshot = snapshot;
        this.env = env;
        this.flush = flush;
        this.barrier = barrier;
        this.log = log;
        this.delayTicks = delayTicks;
    }

    void delay(long ticks) {
        this.delayTicks = Math.max(0, ticks);
    }

    /** Asks for the current state to be written; {@code after} (may be null) runs on the main thread when done. */
    void request(Consumer<Boolean> after) {
        if (closed) {
            if (after != null) {
                after.accept(Boolean.FALSE);
            }
            return;
        }
        if (after != null) {
            waiting.add(after);
        }
        if (!scheduled) {
            scheduled = true;
            env.defer(delayTicks, this::kick);
        }
    }

    private void kick() {
        scheduled = false;
        List<Consumer<Boolean>> callbacks = new ArrayList<>(waiting);
        waiting.clear();
        String json;
        try {
            flush.run();
            json = COMPACT.toJson(snapshot.get());
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Could not serialise " + path, e);
            callbacks.forEach(c -> c.accept(Boolean.FALSE));
            return;
        }
        env.io(() -> {
            boolean ok = write(json);
            env.main(() -> {
                for (Consumer<Boolean> c : callbacks) {
                    try {
                        c.accept(ok);
                    } catch (RuntimeException e) {
                        log.log(Level.SEVERE, "Persist callback failed for " + path, e);
                    }
                }
                if (!ok) {
                    request(null); // retry with a fresh snapshot
                }
            });
        });
    }

    private boolean write(String json) {
        try {
            barrier.run();
            store.writeText(path, json);
            return true;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not write " + path + " (will retry)", e);
            return false;
        }
    }

    /**
     * Synchronous final write for plugin disable: runs the barrier, writes the latest state and completes every
     * waiting callback with {@code true}. Further requests are refused.
     */
    void close() {
        if (closed) {
            return;
        }
        List<Consumer<Boolean>> callbacks = new ArrayList<>(waiting);
        waiting.clear();
        boolean ok = false;
        try {
            flush.run();
            ok = write(COMPACT.toJson(snapshot.get()));
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Final write of " + path + " failed", e);
        }
        closed = true;
        for (Consumer<Boolean> c : callbacks) {
            try {
                c.accept(ok);
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Persist callback failed for " + path, e);
            }
        }
    }

    /** Production environment: Bukkit scheduler for defer/main, one daemon IO thread. */
    static final class Live implements Env {
        private final Core core;
        private final ExecutorService io;

        Live(Core core, String name) {
            this.core = core;
            this.io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "survival-economy-" + name);
                t.setDaemon(true);
                return t;
            });
        }

        @Override
        public void defer(long ticks, Runnable task) {
            if (ticks <= 0) {
                core.scheduler().nextTick(task);
            } else {
                core.scheduler().later(ticks, task);
            }
        }

        @Override
        public void io(Runnable task) {
            try {
                io.execute(task);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                task.run();
            }
        }

        @Override
        public void main(Runnable task) {
            core.scheduler().sync(task);
        }

        void shutdown() {
            io.shutdown();
            try {
                io.awaitTermination(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Inline environment for tests: everything happens immediately on the calling thread. */
    static final class Inline implements Env {
        @Override
        public void defer(long ticks, Runnable task) {
            task.run();
        }

        @Override
        public void io(Runnable task) {
            task.run();
        }

        @Override
        public void main(Runnable task) {
            task.run();
        }
    }
}
