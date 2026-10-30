package win.vampyuyu.survival.items.ability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.block.Block;

/**
 * Remembers blocks that players placed (bounded, least recently added first out) so breaking them again does not
 * give fortune drops: place + break would otherwise multiply items. In memory only: cleared by a restart.
 */
public final class PlacedBlocks {
    private final int capacity;
    private final Map<String, Boolean> placed;

    public PlacedBlocks(int capacity) {
        this.capacity = capacity;
        this.placed = new LinkedHashMap<>(256, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > PlacedBlocks.this.capacity;
            }
        };
    }

    static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public synchronized void add(Block block) {
        if (capacity > 0) {
            placed.put(key(block), Boolean.TRUE);
        }
    }

    /** True when the block was player placed; forgets it (the block is being broken). */
    public synchronized boolean consume(Block block) {
        return placed.remove(key(block)) != null;
    }

    public synchronized int size() {
        return placed.size();
    }
}
