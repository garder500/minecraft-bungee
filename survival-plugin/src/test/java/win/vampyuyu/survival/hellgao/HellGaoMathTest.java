package win.vampyuyu.survival.hellgao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HellGaoMathTest {
    @Test
    void theWindowIsTheEndOfEachCycle() {
        // 1 hour cycle, 5 minute window
        assertFalse(HellGaoMath.windowOpen(0, 3600, 300));
        assertFalse(HellGaoMath.windowOpen(3299, 3600, 300));
        assertTrue(HellGaoMath.windowOpen(3300, 3600, 300));
        assertTrue(HellGaoMath.windowOpen(3599, 3600, 300));
        assertFalse(HellGaoMath.windowOpen(3600, 3600, 300));
        assertTrue(HellGaoMath.windowOpen(3600 * 7 + 3400, 3600, 300));
    }

    @Test
    void anOddWindowNeverOpensOrAlwaysDoes() {
        assertFalse(HellGaoMath.windowOpen(100, 0, 300));
        assertFalse(HellGaoMath.windowOpen(100, 3600, 0));
        assertTrue(HellGaoMath.windowOpen(100, 600, 600));
        assertEquals(-1, HellGaoMath.secondsToOpen(100, 0, 300));
    }

    @Test
    void waitingTimes() {
        assertEquals(3300, HellGaoMath.secondsToOpen(0, 3600, 300));
        assertEquals(1, HellGaoMath.secondsToOpen(3299, 3600, 300));
        assertEquals(0, HellGaoMath.secondsToOpen(3300, 3600, 300));
        assertEquals(300, HellGaoMath.secondsToClose(3300, 3600, 300));
        assertEquals(1, HellGaoMath.secondsToClose(3599, 3600, 300));
        assertEquals(0, HellGaoMath.secondsToClose(10, 3600, 300));
    }

    @Test
    void ascensionRunsFromThreeAndAHalfToFiveTimesTheHealth() {
        assertEquals(1.0, HellGaoMath.hpMultiplier(0), 1e-9);
        assertEquals(3.5, HellGaoMath.hpMultiplier(1), 1e-9);
        assertEquals(5.0, HellGaoMath.hpMultiplier(5), 1e-9);
        assertEquals(2.0, HellGaoMath.damageMultiplier(1), 1e-9);
        assertEquals(2.6, HellGaoMath.damageMultiplier(5), 1e-9);
        for (int r = 2; r <= 5; r++) {
            assertTrue(HellGaoMath.hpMultiplier(r) > HellGaoMath.hpMultiplier(r - 1));
            assertTrue(HellGaoMath.damageMultiplier(r) > HellGaoMath.damageMultiplier(r - 1));
        }
        assertEquals(5.0, HellGaoMath.hpMultiplier(99), 1e-9);
        assertEquals(1.0, HellGaoMath.hpMultiplier(-3), 1e-9);
    }

    @Test
    void aRankIsReadBackFromItsMultiplier() {
        for (int r = 1; r <= 5; r++) {
            assertEquals(r, HellGaoMath.rankOfHp(HellGaoMath.hpMultiplier(r)));
        }
        assertEquals(0, HellGaoMath.rankOfHp(1.0));
    }

    @Test
    void ascensionNeedsResonanceVAndGoesOneRankAtATime() {
        assertTrue(HellGaoMath.canSelect(0, 0, false));
        assertFalse(HellGaoMath.canSelect(1, 0, false));
        assertTrue(HellGaoMath.canSelect(1, 0, true));
        assertFalse(HellGaoMath.canSelect(2, 0, true));
        assertTrue(HellGaoMath.canSelect(2, 1, true));
        assertTrue(HellGaoMath.canSelect(5, 4, true));
        assertEquals(0, HellGaoMath.maxSelectable(3, false));
        assertEquals(4, HellGaoMath.maxSelectable(3, true));
        assertEquals(5, HellGaoMath.maxSelectable(5, true));
    }

    @Test
    void theBreathWindowIsTheQuietStretchBeforeThePulsation() {
        assertTrue(HellGaoMath.breathWindowOpen(false, 120_000, 300));
        assertTrue(HellGaoMath.breathWindowOpen(false, 0, 300));
        assertTrue(HellGaoMath.breathWindowOpen(false, 300_000, 300));
        assertFalse(HellGaoMath.breathWindowOpen(false, 301_000, 300));
        assertFalse(HellGaoMath.breathWindowOpen(true, 10_000, 300), "never during an event");
        assertFalse(HellGaoMath.breathWindowOpen(false, -1, 300), "no pulsation scheduled");
        assertFalse(HellGaoMath.breathWindowOpen(false, 1000, 0));
    }

    @Test
    void loreComesInOrder() {
        List<String> order = List.of("a", "b", "c");
        assertEquals("a", HellGaoMath.nextLore(order, Set.of()));
        assertEquals("b", HellGaoMath.nextLore(order, Set.of("a")));
        assertEquals("a", HellGaoMath.nextLore(order, Set.of("c", "b")));
        assertNull(HellGaoMath.nextLore(order, Set.of("a", "b", "c")));
    }
}
