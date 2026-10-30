package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class StorageTest {
    private static Storage chest(int slots) {
        return new Storage(new LinkedHashMap<>(), slots, key -> 64);
    }

    @Test
    public void capacityIsCountedInStacks() {
        Storage s = chest(3);
        assertEquals(192, s.add("a", 500));
        assertEquals(3, s.slotsUsed());
        assertEquals(0, s.add("b", 1));
        assertTrue(s.isFull());
        assertEquals(192, s.total());
    }

    @Test
    public void roomCountsPartialStackAndFreeSlots() {
        Storage s = chest(3);
        s.add("a", 10);
        assertEquals(54 + 2 * 64, s.room("a"));
        assertEquals(2 * 64, s.room("new"));
    }

    @Test
    public void addAllIsAllOrNothing() {
        Storage s = chest(1);
        s.add("a", 60);
        Map<String, Long> big = new LinkedHashMap<>();
        big.put("a", 4L);
        big.put("b", 1L);
        assertFalse(s.addAll(big));
        assertEquals(60, s.count("a"));
        assertEquals(0, s.count("b"));
        Map<String, Long> ok = Map.of("a", 4L);
        assertTrue(s.addAll(ok));
        assertEquals(64, s.count("a"));
    }

    @Test
    public void removeNeverGoesNegative() {
        Storage s = chest(2);
        s.add("a", 5);
        assertEquals(5, s.remove("a", 50));
        assertEquals(0, s.remove("a", 1));
        assertEquals(0, s.remove("a", -3));
        assertTrue(s.isEmpty());
    }

    @Test
    public void stacksSplitPerSlot() {
        Storage s = chest(5);
        s.add("a", 130);
        s.add("b", 3);
        var stacks = s.stacks();
        assertEquals(4, stacks.size());
        assertEquals(64, stacks.get(0).amount());
        assertEquals(2, stacks.get(2).amount());
        assertEquals("b", stacks.get(3).key());
    }

    @Test
    public void stackSizeOfSixteenStacksCostMoreSlots() {
        Storage s = new Storage(new LinkedHashMap<>(), 2, key -> key.equals("egg") ? 16 : 64);
        assertEquals(32, s.add("egg", 100));
        assertTrue(s.isFull());
    }

    @Test
    public void sanitizeDropsGarbage() {
        Map<String, Long> raw = new LinkedHashMap<>();
        raw.put("a", 0L);
        raw.put("b", -4L);
        raw.put("c", 3L);
        new Storage(raw, 1).sanitize();
        assertEquals(Map.of("c", 3L), raw);
    }
}
