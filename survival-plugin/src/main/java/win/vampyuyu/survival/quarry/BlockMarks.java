package win.vampyuyu.survival.quarry;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Remembers blocks of one kind (generated cobble, Forge blocks) in the PDC of their chunk, so it survives restarts. */
final class BlockMarks {
    private final NamespacedKey key;

    BlockMarks(String kind) {
        this.key = new NamespacedKey("vampyuyu", "marks_" + kind);
    }

    private static long pack(Block block) {
        return ((long) (block.getY() + 2048) << 8) | ((long) (block.getX() & 15) << 4) | (block.getZ() & 15);
    }

    boolean has(Block block) {
        long[] marks = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        if (marks == null) {
            return false;
        }
        long packed = pack(block);
        for (long mark : marks) {
            if (mark == packed) {
                return true;
            }
        }
        return false;
    }

    void add(Block block) {
        if (has(block)) {
            return;
        }
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] marks = pdc.get(key, PersistentDataType.LONG_ARRAY);
        long[] next = marks == null ? new long[1] : java.util.Arrays.copyOf(marks, marks.length + 1);
        next[next.length - 1] = pack(block);
        pdc.set(key, PersistentDataType.LONG_ARRAY, next);
    }

    void remove(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] marks = pdc.get(key, PersistentDataType.LONG_ARRAY);
        if (marks == null) {
            return;
        }
        long packed = pack(block);
        long[] next = java.util.Arrays.stream(marks).filter(m -> m != packed).toArray();
        if (next.length == 0) {
            pdc.remove(key);
        } else if (next.length != marks.length) {
            pdc.set(key, PersistentDataType.LONG_ARRAY, next);
        }
    }
}
