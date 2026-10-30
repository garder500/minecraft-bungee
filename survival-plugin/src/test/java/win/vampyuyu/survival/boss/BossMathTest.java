package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import org.junit.Test;

public class BossMathTest {
    @Test
    public void healthScalesWithGroupAndIsCapped() {
        assertEquals(1.0, BossMath.hpMultiplier(1, 1, 0.3, 3), 1e-9);
        assertEquals(1.6, BossMath.hpMultiplier(3, 1, 0.3, 3), 1e-9);
        assertEquals(3.0, BossMath.hpMultiplier(50, 1, 0.3, 3), 1e-9);
        assertEquals(1.0, BossMath.hpMultiplier(2, 4, 0.3, 3), 1e-9);
        assertEquals(1.0, BossMath.hpMultiplier(0, 1, 0.3, 3), 1e-9);
    }

    @Test
    public void enrageStartsLateAndGrows() {
        assertEquals(1.0, BossMath.enrageMultiplier(100, 1200, 1.5, 0.25), 0);
        assertEquals(1.0, BossMath.enrageMultiplier(5000, 0, 1.5, 0.25), 0);
        assertEquals(1.5, BossMath.enrageMultiplier(1200, 1200, 1.5, 0.25), 1e-9);
        assertEquals(2.0, BossMath.enrageMultiplier(1200 + 2400, 1200, 1.5, 0.25), 1e-9);
    }

    @Test
    public void leashAndDefense() {
        assertFalse(BossMath.outsideLeash(10, 10, 20, 70, 0));
        assertTrue(BossMath.outsideLeash(20, 20, 20, 70, 0));
        assertTrue(BossMath.outsideLeash(0, 0, 20, -5, 0));
        assertEquals(0.5, BossMath.defenseMultiplier(100), 1e-9);
        assertEquals(1.0, BossMath.defenseMultiplier(-50), 1e-9);
        assertEquals(1.0, BossMath.fraction(5, 0) + 1, 1e-9);
        assertEquals(0.5, BossMath.fraction(50, 100), 1e-9);
        assertEquals(1.0, BossMath.fraction(500, 100), 1e-9);
    }
}
