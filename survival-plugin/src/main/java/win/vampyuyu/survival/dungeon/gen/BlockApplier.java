package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Places a {@link BlockBuffer} into a world a few milliseconds per tick: chunks are loaded asynchronously (a window
 * of them ahead), each one is held by a plugin chunk ticket, and block placement stops when the time budget of the
 * tick is spent. Runs on the main thread only.
 */
public final class BlockApplier {
    private static final int WINDOW = 6;

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final Logger log;
    private final World world;
    private final List<BlockBuffer.ChunkOps> chunks;
    private final BlockBuffer buffer;
    private final long budgetNanos;
    private final int blocksPerCheck;
    private final IntConsumer progress;
    private final Runnable done;
    private final java.util.function.Consumer<Throwable> failed;
    private final BlockData[] cache;
    private final ArrayDeque<Integer> loading = new ArrayDeque<>();
    private final Chunk[] loaded;
    private final boolean[] requested;
    private int next;
    private int opIndex;
    private int current;
    private long applied;
    private final long total;
    private boolean finished;
    private Scheduler.Handle handle;
    private long ticks;

    public BlockApplier(Plugin plugin, Scheduler scheduler, World world, BlockBuffer buffer, int msPerTick,
            int blocksPerCheck, IntConsumer progress, Runnable done, java.util.function.Consumer<Throwable> failed) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.log = plugin.getLogger();
        this.world = world;
        this.buffer = buffer;
        this.chunks = buffer.chunks();
        this.budgetNanos = msPerTick * 1_000_000L;
        this.blocksPerCheck = blocksPerCheck;
        this.progress = progress;
        this.done = done;
        this.failed = failed;
        this.cache = new BlockData[buffer.palette().size()];
        this.loaded = new Chunk[chunks.size()];
        this.requested = new boolean[chunks.size()];
        this.total = Math.max(1, buffer.totalBlocks());
    }

    public void start() {
        for (BlockBuffer.ChunkOps ops : chunks) {
            world.addPluginChunkTicket(ops.chunkX, ops.chunkZ, plugin);
        }
        handle = scheduler.repeating(1, 1, this::tick);
    }

    public void cancel() {
        finished = true;
        if (handle != null) {
            handle.cancel();
        }
    }

    /** Releases the chunk tickets (the world is about to be unloaded). */
    public void releaseTickets() {
        for (BlockBuffer.ChunkOps ops : chunks) {
            try {
                world.removePluginChunkTicket(ops.chunkX, ops.chunkZ, plugin);
            } catch (RuntimeException ignored) {
                // world already unloading
            }
        }
    }

    public long applied() { return applied; }

    private void request(int index) {
        if (index >= chunks.size() || requested[index]) {
            return;
        }
        requested[index] = true;
        BlockBuffer.ChunkOps ops = chunks.get(index);
        try {
            world.getChunkAtAsync(ops.chunkX, ops.chunkZ, true).whenComplete((chunk, error) -> {
                // completion may arrive on another thread: hop to the main thread before touching the field
                scheduler.sync(() -> {
                    if (error != null || chunk == null) {
                        fail(error != null ? error : new IllegalStateException("chunk did not load"));
                    } else {
                        loaded[index] = chunk;
                    }
                });
            });
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void fail(Throwable t) {
        if (finished) {
            return;
        }
        cancel();
        failed.accept(t);
    }

    private BlockData data(int paletteIdx) {
        BlockData d = cache[paletteIdx];
        if (d == null) {
            String s = buffer.block(paletteIdx);
            try {
                d = Bukkit.createBlockData(s.contains(":") ? s : "minecraft:" + s);
            } catch (IllegalArgumentException e) {
                log.warning("Dungeon: invalid block data '" + s + "', using stone");
                d = Bukkit.createBlockData("minecraft:stone");
            }
            cache[paletteIdx] = d;
        }
        return d;
    }

    private void tick() {
        if (finished) {
            return;
        }
        ticks++;
        try {
            for (int i = 0; i < WINDOW; i++) {
                request(current + i);
            }
            long deadline = System.nanoTime() + budgetNanos;
            while (current < chunks.size() && System.nanoTime() < deadline) {
                Chunk chunk = loaded[current];
                if (chunk == null) {
                    break; // still loading; the next tick tries again
                }
                BlockBuffer.ChunkOps ops = chunks.get(current);
                int n = ops.size();
                int sinceCheck = 0;
                while (opIndex < n) {
                    chunk.getBlock(ops.localX(opIndex), ops.y(opIndex), ops.localZ(opIndex))
                        .setBlockData(data(ops.paletteIndex(opIndex)), false);
                    opIndex++;
                    applied++;
                    if (++sinceCheck >= blocksPerCheck) {
                        sinceCheck = 0;
                        if (System.nanoTime() >= deadline) {
                            break;
                        }
                    }
                }
                if (opIndex >= n) {
                    loaded[current] = null;
                    current++;
                    opIndex = 0;
                }
            }
            progress.accept((int) Math.min(100, applied * 100 / total));
            if (current >= chunks.size()) {
                finished = true;
                handle.cancel();
                done.run();
            }
        } catch (RuntimeException e) {
            fail(e);
        }
    }
}
