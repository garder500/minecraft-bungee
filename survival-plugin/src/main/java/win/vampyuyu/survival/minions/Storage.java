package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The chest of a minion over its saved {@code key -> amount} map. Capacity is counted in slots of one stack each, so
 * a minion with 3 slots holds 192 cobblestone or 3 different single items, like a chest. No partial item is ever
 * lost: {@link #add} says exactly how much it took.
 */
public final class Storage {
    /** One displayed stack. */
    public record Stack(String key, int amount) {}

    private final Map<String, Long> counts;
    private final int capacity;
    private final ToIntFunction<String> stackSize;

    public Storage(Map<String, Long> counts, int capacitySlots, ToIntFunction<String> stackSize) {
        this.counts = counts;
        this.capacity = capacitySlots;
        this.stackSize = stackSize;
    }

    public Storage(Map<String, Long> counts, int capacitySlots) {
        this(counts, capacitySlots, ItemKey::stackSize);
    }

    /** A storage with the same capacity and stack sizes over another map (for trial runs). */
    public Storage over(Map<String, Long> other) {
        return new Storage(other, capacity, stackSize);
    }

    public int capacity() {
        return capacity;
    }

    public long count(String key) {
        return counts.getOrDefault(key, 0L);
    }

    public boolean isEmpty() {
        return counts.values().stream().noneMatch(n -> n > 0);
    }

    public long total() {
        long sum = 0;
        for (long n : counts.values()) {
            sum += n;
        }
        return sum;
    }

    private int slots(String key, long amount) {
        if (amount <= 0) {
            return 0;
        }
        long size = Math.max(1, stackSize.applyAsInt(key));
        return (int) ((amount + size - 1) / size);
    }

    public int slotsUsed() {
        int used = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            used += slots(e.getKey(), e.getValue());
        }
        return used;
    }

    public int freeSlots() {
        return Math.max(0, capacity - slotsUsed());
    }

    /** True when not even one more item of an unseen kind fits. */
    public boolean isFull() {
        return freeSlots() == 0;
    }

    /** True when every slot holds a complete stack: not one more item of ANY kind (stored or new) fits. */
    public boolean isCompletelyFull() {
        if (freeSlots() > 0) {
            return false;
        }
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            long size = Math.max(1, stackSize.applyAsInt(e.getKey()));
            if (e.getValue() > 0 && e.getValue() % size != 0) {
                return false;
            }
        }
        return true;
    }

    /** How many more items of this kind fit (the partial slot plus every free slot). */
    public long room(String key) {
        long size = Math.max(1, stackSize.applyAsInt(key));
        long current = count(key);
        long partial = current % size == 0 ? 0 : size - current % size;
        return partial + (long) freeSlots() * size;
    }

    /** Adds up to {@code amount} items; returns how many were taken. */
    public long add(String key, long amount) {
        if (amount <= 0) {
            return 0;
        }
        long taken = Math.min(amount, room(key));
        if (taken > 0) {
            counts.merge(key, taken, Long::sum);
        }
        return taken;
    }

    /** Adds every entry or nothing at all. */
    public boolean addAll(Map<String, Long> items) {
        Map<String, Long> trial = new LinkedHashMap<>(counts);
        Storage probe = over(trial);
        for (Map.Entry<String, Long> e : items.entrySet()) {
            if (e.getValue() > 0 && probe.add(e.getKey(), e.getValue()) < e.getValue()) {
                return false;
            }
        }
        counts.clear();
        counts.putAll(trial);
        return true;
    }

    /** Removes up to {@code amount}; returns how many were removed. */
    public long remove(String key, long amount) {
        long have = count(key);
        long taken = Math.min(have, Math.max(0, amount));
        if (taken <= 0) {
            return 0;
        }
        if (taken == have) {
            counts.remove(key);
        } else {
            counts.put(key, have - taken);
        }
        return taken;
    }

    /** The content as stacks, one entry per slot, in insertion order. */
    public List<Stack> stacks() {
        List<Stack> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            long left = e.getValue();
            long size = Math.max(1, stackSize.applyAsInt(e.getKey()));
            while (left > 0) {
                int part = (int) Math.min(size, left);
                out.add(new Stack(e.getKey(), part));
                left -= part;
            }
        }
        return out;
    }

    /** Removes zero and negative entries left by hand edits. */
    public void sanitize() {
        counts.values().removeIf(n -> n == null || n <= 0);
    }
}
