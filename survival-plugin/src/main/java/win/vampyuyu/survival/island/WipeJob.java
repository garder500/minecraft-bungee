package win.vampyuyu.survival.island;

import java.util.Arrays;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Clears every block (and removes every non-player entity) in an island's square. It scans a chunk snapshot,
 * skipping empty 16-block sections, and then sets the non-air blocks to air within the tick budget, so a wipe of a
 * 300x300 area costs time only where something was built.
 */
public final class WipeJob extends AreaJob {
    private final int minY;
    private final int maxY;
    private int[] pending;
    private int pendingIndex;
    private int cleared;
    private Chunk scanned;

    public WipeJob(Plugin plugin, String label, World world, int centerX, int centerZ, int radius, int minY, int maxY,
            int timeoutTicks) {
        super(plugin, label, world, centerX, centerZ, radius, timeoutTicks);
        this.minY = Math.max(minY, world.getMinHeight());
        this.maxY = Math.min(maxY, world.getMaxHeight() - 1);
    }

    @Override
    protected boolean work(Chunk chunk, int cx, int cz, PlanRunner.Budget budget) {
        if (chunk == null) {
            return true;
        }
        if (scanned != chunk) {
            if (!budget.take(300)) {
                return false;
            }
            scan(chunk);
            scanned = chunk;
        }
        while (pendingIndex < pending.length) {
            if (!budget.take(1)) {
                return false;
            }
            int packed = pending[pendingIndex++];
            int lx = (packed >> 4) & 15;
            int lz = packed & 15;
            int y = (packed >> 8) + world.getMinHeight();
            chunk.getBlock(lx, y, lz).setType(Material.AIR, false);
            cleared++;
        }
        scanned = null;
        pending = null;
        return true;
    }

    private void scan(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player || entity.hasMetadata("NPC")) {
                continue;
            }
            if (inArea(entity.getLocation().getBlockX(), entity.getLocation().getBlockZ())) {
                entity.remove();
            }
        }
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int[] found = new int[256];
        int count = 0;
        int worldMin = world.getMinHeight();
        int baseX = chunk.getX() * 16;
        int baseZ = chunk.getZ() * 16;
        for (int y = minY; y <= maxY; y++) {
            if (((y - worldMin) & 15) == 0 || y == minY) {
                if (sectionEmpty(snapshot, (y - worldMin) >> 4)) {
                    y = ((((y - worldMin) >> 4) + 1) << 4) + worldMin - 1;
                    continue;
                }
            }
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    if (!inArea(baseX + lx, baseZ + lz)) {
                        continue;
                    }
                    if (!snapshot.getBlockType(lx, y, lz).isAir()) {
                        if (count == found.length) {
                            found = Arrays.copyOf(found, count * 2);
                        }
                        found[count++] = ((y - worldMin) << 8) | (lx << 4) | lz;
                    }
                }
            }
        }
        pending = Arrays.copyOf(found, count);
        pendingIndex = 0;
    }

    private static boolean sectionEmpty(ChunkSnapshot snapshot, int section) {
        try {
            return snapshot.isSectionEmpty(section);
        } catch (RuntimeException | AbstractMethodError e) {
            return false;
        }
    }

    /** Number of blocks cleared so far. */
    public int cleared() {
        return cleared;
    }
}
