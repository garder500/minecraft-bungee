package win.vampyuyu.survival.island;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Counts the blocks of an island square by material. Snapshots are taken on the main thread, a couple per tick, and
 * counted on worker threads; the result is complete when {@link #counts()} is read after the job finished.
 */
public final class ScanJob extends AreaJob implements IslandService.Counting {
    private final int minY;
    private final int maxY;
    private final int snapshotsPerTick;
    private final Map<Material, Long> totals = new EnumMap<>(Material.class);
    private final AtomicInteger outstanding = new AtomicInteger();
    private int takenThisTick;
    private volatile boolean failed;

    /** Counts the full world height, like the wipe. */
    public ScanJob(Plugin plugin, String label, World world, int centerX, int centerZ, int radius,
            int snapshotsPerTick, int timeoutTicks) {
        super(plugin, label, world, centerX, centerZ, radius, timeoutTicks);
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight() - 1;
        this.snapshotsPerTick = Math.max(1, snapshotsPerTick);
    }

    @Override
    protected boolean work(Chunk chunk, int cx, int cz, PlanRunner.Budget budget) {
        if (chunk == null) {
            return true;
        }
        if (takenThisTick >= snapshotsPerTick) {
            takenThisTick = 0;
            return false;
        }
        takenThisTick++;
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int worldMin = world.getMinHeight();
        int baseX = cx * 16;
        int baseZ = cz * 16;
        outstanding.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<Material, Long> local = new EnumMap<>(Material.class);
                for (int y = minY; y <= maxY; y++) {
                    if (((y - worldMin) & 15) == 0 && sectionEmpty(snapshot, (y - worldMin) >> 4)) {
                        y += 15;
                        continue;
                    }
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            if (inArea(baseX + lx, baseZ + lz)) {
                                Material m = snapshot.getBlockType(lx, y, lz);
                                if (!m.isAir()) {
                                    local.merge(m, 1L, Long::sum);
                                }
                            }
                        }
                    }
                }
                synchronized (totals) {
                    local.forEach((m, n) -> totals.merge(m, n, Long::sum));
                }
            } catch (RuntimeException e) {
                failed = true;
                plugin.getLogger().warning("Island scan failed: " + e);
            } finally {
                outstanding.decrementAndGet();
            }
        });
        return true;
    }

    @Override
    protected boolean settled() {
        if (failed) {
            throw new IllegalStateException("island scan worker failed");
        }
        return outstanding.get() == 0;
    }

    private static boolean sectionEmpty(ChunkSnapshot snapshot, int section) {
        try {
            return snapshot.isSectionEmpty(section);
        } catch (RuntimeException | AbstractMethodError e) {
            return false;
        }
    }

    /** First and last Y swept (always the whole world height). */
    int minY() {
        return minY;
    }

    int maxY() {
        return maxY;
    }

    /** Block counts (call after the job completed). */
    @Override
    public Map<Material, Long> counts() {
        synchronized (totals) {
            Map<Material, Long> copy = new EnumMap<>(Material.class);
            copy.putAll(totals);
            return copy;
        }
    }
}
