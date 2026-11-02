package win.vampyuyu.survival.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MiningCrackTest {
    @Test
    void tenStagesFromNothingToAlmostBroken() {
        assertEquals(0, MiningCrack.stage(0));
        assertEquals(1, MiningCrack.stage(0.01));
        assertEquals(5, MiningCrack.stage(0.45));
        assertEquals(10, MiningCrack.stage(0.95));
        assertEquals(10, MiningCrack.stage(5));
    }

    @Test
    void damageIsMonotonicAndZeroOnlyForNoCrack() {
        assertEquals(0f, MiningCrack.damage(0));
        float last = 0;
        for (int s = 1; s <= MiningCrack.STAGES; s++) {
            float d = MiningCrack.damage(s);
            assertTrue(d > last && d < 1f);
            assertEquals(s - 1, (int) (d * 10), "floored");
            assertEquals(s, (int) Math.ceil(d * 10), "same texture if the client rounds up");
            last = d;
        }
    }

    @Test
    void extrapolationFollowsTheRateWithinOneTickAndNeverFinishes() {
        assertEquals(0.3, MiningCrack.extrapolate(0.1, 0.4, 500), 1e-9);
        assertEquals(0.5, MiningCrack.extrapolate(0.5, 0, 800), 1e-9);
        assertEquals(0.999, MiningCrack.extrapolate(0.9, 4, 1000), 1e-9);
        assertEquals(0.5, MiningCrack.extrapolate(0.1, 0.4, 5000), 1e-9); // capped at one second
    }
}
