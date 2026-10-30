package win.vampyuyu.survival.island;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Runs long block jobs (island generation, wipes, scans) a little every tick so the server never stalls. A few jobs
 * run side by side (each gets what is left of the tick's budget); every job completes a future with {@code true}
 * when it finished and {@code false} when it failed or was aborted. Jobs submitted with the same non-null lane run
 * strictly one after the other in submission order (a wipe never overlaps the generation it follows, whatever the
 * queue looks like); jobs of different lanes, or without a lane, run side by side. Main thread only.
 */
public final class PlanRunner {
    /** Blocks and time left in the current tick. */
    public static final class Budget {
        private int blocks;
        private final long deadline;

        public Budget(int blocks, long maxNanos) {
            this.blocks = blocks;
            this.deadline = System.nanoTime() + maxNanos;
        }

        /** Spends {@code n} units; false when the tick's allowance is used up. */
        public boolean take(int n) {
            if (blocks <= 0 || System.nanoTime() > deadline) {
                return false;
            }
            blocks -= n;
            return true;
        }

        public boolean alive() {
            return blocks > 0 && System.nanoTime() <= deadline;
        }
    }

    /** A resumable piece of work. */
    public interface Job {
        String name();

        /** Does some work; true when finished. Exceptions fail the job. */
        boolean step(Budget budget) throws Exception;

        /** Releases anything held (chunk tickets...). Called on failure and on shutdown, not after success. */
        void abort();
    }

    private record Entry(Job job, Object lane, CompletableFuture<Boolean> done) {}

    private static final int PARALLEL = 3;

    private final Logger log;
    private final int blocksPerTick;
    private final long maxNanos;
    private final Deque<Entry> queue = new ArrayDeque<>();
    private Scheduler.Handle task;
    private boolean closed;

    public PlanRunner(Logger log, int blocksPerTick, int maxMillisPerTick) {
        this.log = log;
        this.blocksPerTick = blocksPerTick;
        this.maxNanos = maxMillisPerTick * 1_000_000L;
    }

    /** Starts the per tick loop. */
    public void start(Scheduler scheduler) {
        task = scheduler.repeating(1, 1, this::tick);
    }

    /** Queues a job without ordering constraints. The future completes on the main thread. */
    public CompletableFuture<Boolean> submit(Job job) {
        return submit(null, job);
    }

    /** Queues a job behind every earlier job of the same lane (a null lane is never serialised). */
    public CompletableFuture<Boolean> submit(Object lane, Job job) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        if (closed) {
            done.complete(false);
            return done;
        }
        queue.addLast(new Entry(job, lane, done));
        return done;
    }

    public int pending() {
        return queue.size();
    }

    /** Jobs queued or running in one lane. */
    public int pendingIn(Object lane) {
        int count = 0;
        for (Entry entry : queue) {
            if (lane.equals(entry.lane())) {
                count++;
            }
        }
        return count;
    }

    /** Runs one tick of work (called by the scheduler; tests may call it directly). */
    public void tick() {
        if (queue.isEmpty()) {
            return;
        }
        Budget budget = new Budget(blocksPerTick, maxNanos);
        // the first queued job of every lane may run; later jobs of a busy lane wait their turn
        List<Entry> active = new ArrayList<>();
        Set<Object> busy = new HashSet<>();
        for (Entry entry : queue) {
            if (active.size() >= PARALLEL) {
                break;
            }
            if (entry.lane() != null && !busy.add(entry.lane())) {
                continue;
            }
            active.add(entry);
        }
        for (Entry entry : active) {
            if (!budget.alive()) {
                break;
            }
            try {
                if (entry.job().step(budget)) {
                    queue.remove(entry);
                    entry.done().complete(true);
                }
            } catch (Exception | StackOverflowError e) {
                log.log(Level.SEVERE, "Island job '" + entry.job().name() + "' failed", e);
                queue.remove(entry);
                try {
                    entry.job().abort();
                } catch (RuntimeException ignored) {
                    // already failing
                }
                entry.done().complete(false);
            }
        }
    }

    /** Aborts everything that is queued (plugin disable). */
    public void shutdown() {
        closed = true;
        if (task != null) {
            task.cancel();
        }
        for (Entry entry : new ArrayList<>(queue)) {
            try {
                entry.job().abort();
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Could not abort island job " + entry.job().name(), e);
            }
            entry.done().complete(false);
        }
        queue.clear();
    }
}
