package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class LootMathTest {
    private static BossDefinition.LootEntry entry(double chance, int min, int max, double minShare, int maxRank) {
        return new BossDefinition.LootEntry("custom:x", chance, min, max, minShare, maxRank, true, 0, 0.5, 1);
    }

    @Test
    public void shareSplitWithFloor() {
        // one participant gets the whole pool whatever the floor
        assertEquals(1000, LootMath.share(1000, 1.0, 0.1, 1), 1e-9);
        // two participants: 10 percent split equally, 90 percent by share
        assertEquals(50 + 0.9 * 500, LootMath.share(1000, 0.5, 0.1, 2), 1e-9);
        assertEquals(700, LootMath.share(1000, 0.7, 0, 5), 1e-9);
        assertEquals(1000, LootMath.share(1000, 5, 0.0, 1), 1e-9); // share clamped
        assertEquals(0, LootMath.share(-5, 0.5, 0.2, 2), 1e-9);
        assertEquals(1000, LootMath.share(1000, 1.0, 1.0, 0), 1e-9); // bad count treated as one
    }

    @Test
    public void payoutNeverExceedsThePoolNoMatterHowManyAccountsJoin() {
        double pool = 1000, floor = 0.25;
        for (int n : new int[] {1, 2, 3, 10, 100}) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += LootMath.share(pool, 1.0 / n, floor, n);
            }
            assertEquals("n=" + n, pool, sum, 1e-6);
        }
    }

    @Test
    public void rankMultiplier() {
        List<Double> bonus = List.of(1.5, 1.25, 1.1);
        assertEquals(1.5, LootMath.rankMultiplier(bonus, 1), 0);
        assertEquals(1.1, LootMath.rankMultiplier(bonus, 3), 0);
        assertEquals(1.0, LootMath.rankMultiplier(bonus, 4), 0);
        assertEquals(1.0, LootMath.rankMultiplier(bonus, 0), 0);
    }

    @Test
    public void pityIsCapped() {
        assertEquals(0.03, LootMath.pityBonus(3, 0.01, 0.5), 1e-9);
        assertEquals(0.05, LootMath.pityBonus(100, 0.01, 0.05), 1e-9);
        assertEquals(0, LootMath.pityBonus(-4, 0.01, 0.05), 0);
    }

    @Test
    public void magicFindIsAPercentage() {
        assertEquals(0.05, LootMath.chance(0.05, 0, 1, 0, true), 1e-9);
        assertEquals(0.075, LootMath.chance(0.05, 0, 1, 50, true), 1e-9);
        assertEquals(0.05, LootMath.chance(0.05, 0, 1, 500, false), 1e-9);
        assertEquals(1.0, LootMath.chance(1.0, 0, 0.5, 0, true), 0);
        assertEquals(1.0, LootMath.chance(0.9, 0.5, 2, 100, true), 0);
        assertEquals(0.05, LootMath.chance(0.05, 0, 1, -100, true), 1e-9);
    }

    @Test
    public void eligibility() {
        var e = entry(0.1, 1, 1, 0.10, 3);
        assertFalse(LootMath.eligible(e, 0.05, 1));
        assertTrue(LootMath.eligible(e, 0.10, 3));
        assertFalse(LootMath.eligible(e, 0.5, 4));
        assertFalse(LootMath.eligible(e, 0.5, 0));
        assertTrue(LootMath.eligible(entry(0.1, 1, 1, 0, 0), 0.0, 9));
    }

    @Test
    public void amountStaysInRange() {
        Random r = new Random(1);
        var e = entry(1, 2, 5, 0, 0);
        for (int i = 0; i < 200; i++) {
            int n = LootMath.amount(e, r);
            assertTrue(n >= 2 && n <= 5);
        }
        assertEquals(3, LootMath.amount(entry(1, 3, 3, 0, 0), r));
    }

    @Test
    public void rollAlwaysHitsGuaranteed() {
        Random r = new Random(2);
        for (int i = 0; i < 50; i++) {
            assertTrue(LootMath.roll(1.0, r));
            assertFalse(LootMath.roll(0.0, r));
        }
    }
}
