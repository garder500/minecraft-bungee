package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory list of block placements grouped by chunk, filled off the server thread-safely (single writer) and
 * applied later a few thousand blocks per tick. Later writes to the same block win because the order is kept.
 * Blocks are palette indexes of block data strings ({@code stone_bricks}, {@code wall_torch[facing=north]}).
 */
public final class BlockBuffer implements Canvas {
    /** Placements of one chunk: packed ints {@code lx | lz << 4 | (y + 64) << 8 | palette << 20}. */
    public static final class ChunkOps {
        public final int chunkX;
        public final int chunkZ;
        private int[] data = new int[64];
        private int size;

        ChunkOps(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        void add(int packed) {
            if (size == data.length) {
                data = java.util.Arrays.copyOf(data, size * 2);
            }
            data[size++] = packed;
        }

        public int size() { return size; }

        public int localX(int i) { return data[i] & 15; }

        public int localZ(int i) { return (data[i] >> 4) & 15; }

        public int y(int i) { return ((data[i] >> 8) & 0xFFF) - 64; }

        public int paletteIndex(int i) { return data[i] >>> 20; }
    }

    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private final Map<Long, ChunkOps> chunks = new LinkedHashMap<>();
    private long total;

    @Override
    public void set(int x, int y, int z, String block) {
        if (y < -64 || y > 4000) {
            return;
        }
        Integer idx = paletteIndex.get(block);
        if (idx == null) {
            idx = palette.size();
            if (idx >= 4000) {
                throw new IllegalStateException("too many distinct blocks");
            }
            palette.add(block);
            paletteIndex.put(block, idx);
        }
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        ChunkOps ops = chunks.get(key);
        if (ops == null) {
            ops = new ChunkOps(cx, cz);
            chunks.put(key, ops);
        }
        ops.add((x & 15) | ((z & 15) << 4) | ((y + 64) << 8) | (idx << 20));
        total++;
    }

    public List<String> palette() { return palette; }

    public String block(int paletteIndex) { return palette.get(paletteIndex); }

    /** Chunks in insertion order (stable). */
    public List<ChunkOps> chunks() { return new ArrayList<>(chunks.values()); }

    public long totalBlocks() { return total; }
}
