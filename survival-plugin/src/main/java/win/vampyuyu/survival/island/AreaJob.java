package win.vampyuyu.survival.island;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Base for jobs that sweep every chunk of a square area around an island centre, a few chunks at a time, with the
 * chunks loaded asynchronously and pinned while in use. Chunks that were never generated are skipped (nothing was
 * ever built there).
 */
abstract class AreaJob implements PlanRunner.Job {
    private static final int WINDOW = 4;

    protected final Plugin plugin;
    protected final World world;
    protected final int minX;
    protected final int maxX;
    protected final int minZ;
    protected final int maxZ;
    private final String label;
    private final int timeoutTicks;
    private final List<int[]> order = new ArrayList<>();
    private final Deque<Pending> window = new ArrayDeque<>();
    private int next;
    private int waited;
    private Pending current;
    private Chunk currentChunk;

    private record Pending(int cx, int cz, CompletableFuture<Chunk> future) {}

    AreaJob(Plugin plugin, String label, World world, int centerX, int centerZ, int radius, int timeoutTicks) {
        this.plugin = plugin;
        this.label = label;
        this.world = world;
        this.minX = centerX - radius;
        this.maxX = centerX + radius;
        this.minZ = centerZ - radius;
        this.maxZ = centerZ + radius;
        this.timeoutTicks = timeoutTicks;
        for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX, 16); cx++) {
            for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ, 16); cz++) {
                order.add(new int[] {cx, cz});
            }
        }
    }

    @Override
    public String name() {
        return label;
    }

    /**
     * Works on one chunk (possibly over several ticks). Returns true when the chunk is finished. The chunk is null
     * when it has never been generated.
     */
    protected abstract boolean work(Chunk chunk, int cx, int cz, PlanRunner.Budget budget);

    /** True once everything handed out asynchronously is finished (default: nothing is). */
    protected boolean settled() {
        return true;
    }

    @Override
    public final boolean step(PlanRunner.Budget budget) {
        while (true) {
            if (current != null) {
                if (!work(currentChunk, current.cx(), current.cz(), budget)) {
                    return false;
                }
                world.removePluginChunkTicket(current.cx(), current.cz(), plugin);
                current = null;
                currentChunk = null;
            }
            while (window.size() < WINDOW && next < order.size()) {
                int[] cell = order.get(next++);
                world.addPluginChunkTicket(cell[0], cell[1], plugin);
                window.add(new Pending(cell[0], cell[1], world.getChunkAtAsync(cell[0], cell[1], false)));
            }
            Pending head = window.peek();
            if (head == null) {
                return settled();
            }
            if (!head.future().isDone()) {
                if (++waited > timeoutTicks) {
                    throw new IllegalStateException("chunks did not load in time for " + label);
                }
                return false;
            }
            waited = 0;
            window.poll();
            current = head;
            currentChunk = head.future().isCompletedExceptionally() ? null : head.future().join();
            if (!budget.alive()) {
                return false;
            }
        }
    }

    @Override
    public void abort() {
        if (current != null) {
            world.removePluginChunkTicket(current.cx(), current.cz(), plugin);
            current = null;
        }
        for (Pending pending : window) {
            world.removePluginChunkTicket(pending.cx(), pending.cz(), plugin);
        }
        window.clear();
    }

    /** True when the block column is inside the swept square. */
    protected boolean inArea(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
