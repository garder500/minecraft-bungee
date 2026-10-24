package win.vampyuyu.werewolf.sky;

import java.util.BitSet;

/** Hands out the lowest free slot index. Pure bookkeeping, no Bukkit, so it is tested on its own. */
final class SlotAllocator {
  private final BitSet used = new BitSet();
  private final int capacity;

  SlotAllocator(int capacity) {
    this.capacity = capacity;
  }

  synchronized int allocate() {
    int i = used.nextClearBit(0);
    if (i >= capacity) throw new IllegalStateException("Every sky slot is in use (" + capacity + ")");
    used.set(i);
    return i;
  }

  /** Frees a slot; returns false when it was not allocated (a double release is harmless). */
  synchronized boolean release(int index) {
    if (index < 0 || index >= capacity || !used.get(index)) return false;
    used.clear(index);
    return true;
  }

  synchronized boolean inUse(int index) {
    return index >= 0 && index < capacity && used.get(index);
  }

  synchronized int count() {
    return used.cardinality();
  }
}
