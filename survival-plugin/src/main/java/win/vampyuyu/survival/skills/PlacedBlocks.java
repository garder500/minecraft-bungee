package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers which blocks were placed by a player (or moved by a piston, a falling block...) so that placing and
 * breaking them again does not farm experience or collections. One {@link BitSet} per chunk, indexed by
 * {@code ((y - minY) << 8) | (x & 15) << 4 | (z & 15)}: a chunk with a few hundred marks costs a few hundred bytes.
 *
 * <p>Persistence goes through a {@link Backend}: the plugin stores each chunk's bits in the chunk's own
 * persistent data container, so the server saves them with the chunk and this class never touches the disk.
 * Main thread only.
 */
public final class PlacedBlocks {
    /** Loads and saves the raw bits of a chunk. */
    public interface Backend {
        /** Bits previously saved for the chunk, or an empty array. */
        long[] load(UUID world, int chunkX, int chunkZ);

        /** Saves the bits (an empty array clears the stored value). */
        void save(UUID world, int chunkX, int chunkZ, long[] bits);
    }

    private record ChunkKey(UUID world, int x, int z) {}

    private static final class Entry {
        final BitSet bits;
        boolean dirty;

        Entry(BitSet bits) {
            this.bits = bits;
        }
    }

    private final Backend backend;
    private final Map<ChunkKey, Entry> chunks = new HashMap<>();

    public PlacedBlocks(Backend backend) {
        this.backend = backend;
    }

    static int index(int minY, int x, int y, int z) {
        return ((y - minY) << 8) | ((x & 15) << 4) | (z & 15);
    }

    private Entry entry(UUID world, int x, int z) {
        ChunkKey key = new ChunkKey(world, x >> 4, z >> 4);
        Entry entry = chunks.get(key);
        if (entry == null) {
            entry = new Entry(BitSet.valueOf(backend.load(world, key.x(), key.z())));
            chunks.put(key, entry);
        }
        return entry;
    }

    public boolean isPlaced(UUID world, int minY, int x, int y, int z) {
        Entry entry = entry(world, x, z);
        int i = index(minY, x, y, z);
        return i >= 0 && entry.bits.get(i);
    }

    public void mark(UUID world, int minY, int x, int y, int z) {
        int i = index(minY, x, y, z);
        if (i < 0) {
            return;
        }
        Entry entry = entry(world, x, z);
        if (!entry.bits.get(i)) {
            entry.bits.set(i);
            entry.dirty = true;
        }
    }

    /** Clears the mark and tells whether there was one. */
    public boolean unmark(UUID world, int minY, int x, int y, int z) {
        Entry entry = entry(world, x, z);
        int i = index(minY, x, y, z);
        if (i < 0 || !entry.bits.get(i)) {
            return false;
        }
        entry.bits.clear(i);
        entry.dirty = true;
        return true;
    }

    /** Writes a chunk (if modified) and forgets it. Call when the chunk unloads. */
    public void unload(UUID world, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(world, chunkX, chunkZ);
        Entry entry = chunks.remove(key);
        if (entry != null && entry.dirty) {
            backend.save(world, chunkX, chunkZ, entry.bits.toLongArray());
        }
    }

    /** Writes every modified chunk but keeps them cached. Returns how many were written. */
    public int flush() {
        int written = 0;
        for (Map.Entry<ChunkKey, Entry> e : new ArrayList<>(chunks.entrySet())) {
            if (e.getValue().dirty) {
                backend.save(e.getKey().world(), e.getKey().x(), e.getKey().z(), e.getValue().bits.toLongArray());
                e.getValue().dirty = false;
                written++;
            }
        }
        return written;
    }

    /** Number of chunks currently cached (diagnostics, tests). */
    public int cachedChunks() {
        return chunks.size();
    }

    /** Chunks with unsaved changes (tests). */
    public List<int[]> dirtyChunks() {
        List<int[]> out = new ArrayList<>();
        chunks.forEach((k, v) -> {
            if (v.dirty) {
                out.add(new int[] {k.x(), k.z()});
            }
        });
        return out;
    }
}
