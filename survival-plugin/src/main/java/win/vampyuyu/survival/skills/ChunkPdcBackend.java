package win.vampyuyu.survival.skills;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Stores the placed-block bits in the chunk's own persistent data container: saved with the chunk, no extra IO. */
final class ChunkPdcBackend implements PlacedBlocks.Backend {
    static final NamespacedKey KEY = new NamespacedKey("vampyuyu", "skills_placed");

    @Override
    public long[] load(UUID worldId, int chunkX, int chunkZ) {
        Chunk chunk = loadedChunk(worldId, chunkX, chunkZ);
        if (chunk == null) {
            return new long[0];
        }
        long[] bits = chunk.getPersistentDataContainer().get(KEY, PersistentDataType.LONG_ARRAY);
        return bits == null ? new long[0] : bits;
    }

    @Override
    public void save(UUID worldId, int chunkX, int chunkZ, long[] bits) {
        Chunk chunk = loadedChunk(worldId, chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (bits.length == 0) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.LONG_ARRAY, bits);
        }
    }

    private static Chunk loadedChunk(UUID worldId, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldId);
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }
        return world.getChunkAt(chunkX, chunkZ, false);
    }
}
