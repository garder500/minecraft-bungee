package win.vampyuyu.survival.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Thin wrapper over the Bukkit scheduler that hands back cancellable {@link Handle}s. Delays are in ticks. */
public final class Scheduler {
    /** A scheduled task. */
    public interface Handle {
        void cancel();

        boolean isCancelled();
    }

    private static final Handle DONE = new Handle() {
        public void cancel() {}

        public boolean isCancelled() { return false; }
    };

    private final Plugin plugin;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    private static Handle wrap(BukkitTask task) {
        return new Handle() {
            public void cancel() { task.cancel(); }

            public boolean isCancelled() { return task.isCancelled(); }
        };
    }

    /** Runs on the main thread: immediately when already there, otherwise on the next tick. */
    public Handle sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return DONE;
        }
        return wrap(Bukkit.getScheduler().runTask(plugin, task));
    }

    /** Runs on the main thread at the start of the next tick, even when already on it. */
    public Handle nextTick(Runnable task) {
        return wrap(Bukkit.getScheduler().runTask(plugin, task));
    }

    /** Runs on an async worker thread. */
    public Handle async(Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    /** Runs on the main thread after {@code ticks}. */
    public Handle later(long ticks, Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, ticks));
    }

    /** Runs on an async thread after {@code ticks}. */
    public Handle laterAsync(long ticks, Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, ticks));
    }

    /** Runs on the main thread every {@code period} ticks after {@code delay}. */
    public Handle repeating(long delay, long period, Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    /** Runs on an async thread every {@code period} ticks after {@code delay}. */
    public Handle repeatingAsync(long delay, long period, Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period));
    }

    /** Cancels every task of the plugin (called by Core on disable). */
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
