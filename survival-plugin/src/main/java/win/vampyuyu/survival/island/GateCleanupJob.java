package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * One-time cleanup of the vanilla End gate that early islands carried: looks at the exact old footprint (see
 * {@link IslandTemplates#legacyGateFootprint()}) and replaces only End portal frames and End portal blocks by the
 * island's ground block. Any other block (a player's build, the island's own terrain) is never touched, so running
 * it twice is harmless.
 */
public class GateCleanupJob implements PlanRunner.Job {
    private final Plugin plugin;
    private final String label;
    private final World world;
    private final int ox;
    private final int oy;
    private final int oz;
    private final Material ground;
    private final Map<Long, CompletableFuture<Chunk>> chunks = new LinkedHashMap<>();
    private final List<int[]> cells = IslandTemplates.legacyGateFootprint();
    private boolean started;
    private int waited;
    private int removed;

    public GateCleanupJob(Plugin plugin, String label, World world, int ox, int oy, int oz, Material ground) {
        this.plugin = plugin;
        this.label = label;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.ground = ground;
    }

    @Override
    public String name() {
        return label;
    }

    /** Frame and portal blocks replaced (valid once the job finished). */
    public int removed() {
        return removed;
    }

    @Override
    public boolean step(PlanRunner.Budget budget) {
        if (!started) {
            started = true;
            for (int[] c : cells) {
                int cx = Math.floorDiv(ox + c[0], 16);
                int cz = Math.floorDiv(oz + c[1], 16);
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                if (!chunks.containsKey(key)) {
                    chunks.put(key, load(cx, cz));
                }
            }
        }
        for (CompletableFuture<Chunk> f : chunks.values()) {
            if (!f.isDone()) {
                if (++waited > 600) {
                    throw new IllegalStateException("chunks did not load in time for " + label);
                }
                return false;
            }
            if (f.isCompletedExceptionally()) {
                throw new IllegalStateException("chunk load failed for " + label);
            }
        }
        for (int[] c : cells) {
            Block b = world.getBlockAt(ox + c[0], oy, oz + c[1]);
            Material m = b.getType();
            if (m == Material.END_PORTAL_FRAME || m == Material.END_PORTAL) {
                b.setType(ground, false);
                removed++;
            }
        }
        release();
        return true;
    }

    /** Pins and asynchronously loads a chunk (overridden by tests: the mock server has no chunk tickets). */
    CompletableFuture<Chunk> load(int cx, int cz) {
        world.addPluginChunkTicket(cx, cz, plugin);
        return world.getChunkAtAsync(cx, cz, true);
    }

    void unpin(int cx, int cz) {
        world.removePluginChunkTicket(cx, cz, plugin);
    }

    private void release() {
        for (long key : new ArrayList<>(chunks.keySet())) {
            unpin((int) (key >> 32), (int) key);
        }
        chunks.clear();
    }

    @Override
    public void abort() {
        release();
    }
}
