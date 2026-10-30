package win.vampyuyu.survival.menu;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded memory of blocks players placed recently, so place-and-break loops do not count for "break blocks" quests.
 * Oldest entries are forgotten first. Main thread only.
 */
final class PlacedBlocks {
    private record Key(UUID world, int x, int y, int z) {}

    private final int capacity;
    private final Set<Key> keys = new LinkedHashSet<>();

    PlacedBlocks(int capacity) {
        this.capacity = Math.max(16, capacity);
    }

    void markPlaced(UUID world, int x, int y, int z) {
        Key key = new Key(world, x, y, z);
        keys.remove(key);
        keys.add(key);
        if (keys.size() > capacity) {
            var it = keys.iterator();
            it.next();
            it.remove();
        }
    }

    /** True (and forgets the block) when the block was placed by a player recently. */
    boolean consumePlaced(UUID world, int x, int y, int z) {
        return keys.remove(new Key(world, x, y, z));
    }

    int size() {
        return keys.size();
    }
}
