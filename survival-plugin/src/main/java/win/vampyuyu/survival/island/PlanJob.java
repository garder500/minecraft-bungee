package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Applies a {@link BlockPlan} to a world around an origin, a budgeted batch per tick: first the chunks are requested
 * asynchronously and pinned with plugin tickets, then blocks are placed grouped by chunk, then chests, signs and
 * biomes are filled in. Blocks are set without physics so nothing pops, falls or flows while the structure is half
 * built.
 */
public final class PlanJob implements PlanRunner.Job {
    private enum Phase { INIT, WAIT, PLACE, POST, DONE }

    private final Plugin plugin;
    private final Logger log;
    private final String label;
    private final World world;
    private final BlockPlan plan;
    private final int ox;
    private final int oy;
    private final int oz;
    private final int timeoutTicks;

    private Phase phase = Phase.INIT;
    private final Map<Long, CompletableFuture<Chunk>> chunks = new LinkedHashMap<>();
    private final List<long[]> pinned = new ArrayList<>();
    private int waited;
    private long[] keys;
    private BlockPlan.Blk[] blks;
    private int index;
    private final Map<BlockPlan.Blk, BlockData> dataCache = new HashMap<>();

    public PlanJob(Plugin plugin, String label, World world, BlockPlan plan, int ox, int oy, int oz, int timeoutTicks) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.label = label;
        this.world = world;
        this.plan = plan;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.timeoutTicks = timeoutTicks;
    }

    @Override
    public String name() {
        return label;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    @Override
    public boolean step(PlanRunner.Budget budget) {
        if (phase == Phase.INIT) {
            init();
            phase = Phase.WAIT;
        }
        if (phase == Phase.WAIT) {
            for (CompletableFuture<Chunk> future : chunks.values()) {
                if (!future.isDone()) {
                    if (++waited > timeoutTicks) {
                        throw new IllegalStateException("chunks did not load in time for " + label);
                    }
                    return false;
                }
                if (future.isCompletedExceptionally()) {
                    throw new IllegalStateException("chunk load failed for " + label);
                }
            }
            phase = Phase.PLACE;
        }
        if (phase == Phase.PLACE) {
            while (index < keys.length) {
                if (!budget.take(1)) {
                    return false;
                }
                place(index++);
            }
            phase = Phase.POST;
        }
        if (phase == Phase.POST) {
            post();
            release();
            phase = Phase.DONE;
        }
        return true;
    }

    private void init() {
        // order the blocks by chunk so each chunk is filled in one go; first-inserted chunks come first
        Map<Long, List<Long>> byChunk = new LinkedHashMap<>();
        Map<Long, BlockPlan.Blk> blocks = plan.blocks();
        for (Map.Entry<Long, BlockPlan.Blk> entry : blocks.entrySet()) {
            long key = entry.getKey();
            int wx = ox + BlockPlan.keyX(key);
            int wz = oz + BlockPlan.keyZ(key);
            byChunk.computeIfAbsent(chunkKey(Math.floorDiv(wx, 16), Math.floorDiv(wz, 16)), k -> new ArrayList<>()).add(key);
        }
        keys = new long[blocks.size()];
        blks = new BlockPlan.Blk[blocks.size()];
        int i = 0;
        for (List<Long> list : byChunk.values()) {
            for (long key : list) {
                keys[i] = key;
                blks[i++] = blocks.get(key);
            }
        }
        // chunks needed by blocks, chests, signs and biomes
        for (long chunk : byChunk.keySet()) {
            request((int) (chunk >> 32), (int) chunk);
        }
        for (BlockPlan.BiomeBox box : plan.biomes()) {
            for (int cx = Math.floorDiv(ox + box.minX(), 16); cx <= Math.floorDiv(ox + box.maxX(), 16); cx++) {
                for (int cz = Math.floorDiv(oz + box.minZ(), 16); cz <= Math.floorDiv(oz + box.maxZ(), 16); cz++) {
                    request(cx, cz);
                }
            }
        }
    }

    private void request(int cx, int cz) {
        long key = chunkKey(cx, cz);
        if (chunks.containsKey(key)) {
            return;
        }
        world.addPluginChunkTicket(cx, cz, plugin);
        pinned.add(new long[] {cx, cz});
        chunks.put(key, world.getChunkAtAsync(cx, cz, true));
    }

    private void place(int i) {
        long key = keys[i];
        BlockPlan.Blk blk = blks[i];
        Block block = world.getBlockAt(ox + BlockPlan.keyX(key), oy + BlockPlan.keyY(key), oz + BlockPlan.keyZ(key));
        block.setBlockData(data(blk), false);
    }

    private BlockData data(BlockPlan.Blk blk) {
        return dataCache.computeIfAbsent(blk, b -> {
            if (b.props() == null || b.props().isEmpty()) {
                return b.material().createBlockData();
            }
            try {
                return Bukkit.createBlockData(b.material().getKey() + "[" + b.props() + "]");
            } catch (IllegalArgumentException e) {
                log.warning("Bad block data " + b.material() + "[" + b.props() + "] in " + label + ": " + e.getMessage());
                return b.material().createBlockData();
            }
        });
    }

    private void post() {
        for (BlockPlan.ChestFill fill : plan.chests()) {
            try {
                Block block = world.getBlockAt(ox + fill.x(), oy + fill.y(), oz + fill.z());
                if (block.getState() instanceof Container container) {
                    container.getInventory().clear();
                    for (IslandConfig.ItemSpec spec : fill.items()) {
                        container.getInventory().addItem(new ItemStack(spec.material(), spec.amount()));
                    }
                }
            } catch (RuntimeException e) {
                log.warning("Could not fill the chest of " + label + ": " + e);
            }
        }
        for (BlockPlan.SignText text : plan.signs()) {
            try {
                Block block = world.getBlockAt(ox + text.x(), oy + text.y(), oz + text.z());
                if (block.getState() instanceof Sign sign) {
                    for (int line = 0; line < 4; line++) {
                        sign.getSide(Side.FRONT).line(line, Component.text(line < text.lines().size() ? text.lines().get(line) : ""));
                    }
                    sign.setWaxed(true);
                    sign.update(true, false);
                }
            } catch (RuntimeException e) {
                log.warning("Could not write a sign of " + label + ": " + e);
            }
        }
        for (BlockPlan.BiomeBox box : plan.biomes()) {
            Biome biome = biome(box.biome());
            if (biome == null) {
                log.warning("Unknown biome '" + box.biome() + "' in " + label);
                continue;
            }
            try {
                int x0 = Math.floorDiv(ox + box.minX(), 4) * 4;
                int z0 = Math.floorDiv(oz + box.minZ(), 4) * 4;
                int y0 = Math.floorDiv(oy + box.minY(), 4) * 4;
                for (int x = x0; x <= ox + box.maxX(); x += 4) {
                    for (int z = z0; z <= oz + box.maxZ(); z += 4) {
                        for (int y = y0; y <= oy + box.maxY(); y += 4) {
                            world.setBiome(x, y, z, biome);
                        }
                    }
                }
            } catch (RuntimeException e) {
                log.warning("Could not set the biome of " + label + ": " + e);
            }
        }
    }

    static Biome biome(String key) {
        String clean = key.toLowerCase(Locale.ROOT);
        NamespacedKey nk = clean.contains(":") ? NamespacedKey.fromString(clean) : NamespacedKey.minecraft(clean);
        return nk == null ? null : Registry.BIOME.get(nk);
    }

    private void release() {
        for (long[] chunk : pinned) {
            world.removePluginChunkTicket((int) chunk[0], (int) chunk[1], plugin);
        }
        pinned.clear();
    }

    @Override
    public void abort() {
        release();
    }

    /** Blocks that would be placed (tests). */
    public int total() {
        return plan.size();
    }

    /** Material the plan puts at a relative position (tests). */
    public Material expected(int x, int y, int z) {
        return plan.get(x, y, z);
    }
}
