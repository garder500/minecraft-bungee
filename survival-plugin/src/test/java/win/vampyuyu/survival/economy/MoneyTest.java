package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import org.junit.Test;

public class MoneyTest {
    @Test
    public void parsesPlayerAmounts() {
        assertEquals(150000, Money.parse("1500").getAsLong());
        assertEquals(150000, Money.parse("1,500").getAsLong());
        assertEquals(150_000_00L, Money.parse("1.5k").getAsLong() * 100 / 100 * 100);
        assertEquals(1_500L * 100, Money.parse("1.5k").getAsLong());
        assertEquals(2_000_000L * 100, Money.parse("2M").getAsLong());
        assertEquals(1_000_000_000L * 100, Money.parse("1b").getAsLong());
        assertEquals(50, Money.parse("0.5").getAsLong());
        assertEquals(1, Money.parse("0.019").getAsLong());
    }

    @Test
    public void rejectsGarbageNegativesAndHugeValues() {
        for (String bad : new String[] {"", "abc", "-5", "0", "NaN", "Infinity", "1e9", "5x", "1..2", "9999999999999999",
                "99999999999t", null}) {
            assertTrue("should reject " + bad, Money.parse(bad).isEmpty());
        }
    }

    @Test
    public void toCentsRejectsNonFinite() {
        assertTrue(Money.toCents(Double.NaN).isEmpty());
        assertTrue(Money.toCents(Double.POSITIVE_INFINITY).isEmpty());
        assertTrue(Money.toCents(-1).isEmpty());
        assertTrue(Money.toCents(1e300).isEmpty());
        assertEquals(1234, Money.toCents(12.34).getAsLong());
        assertEquals(10, Money.toCents(0.1).getAsLong());
    }

    @Test
    public void addCappedNeverOverflows() {
        assertEquals(100, Money.addCapped(50, 50, 1000));
        assertEquals(1000, Money.addCapped(900, 500, 1000));
        assertEquals(1000, Money.addCapped(Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, 1000));
        assertEquals(0, Money.addCapped(10, -50, 1000));
    }

    @Test
    public void formatting() {
        assertEquals("1,234.5", Money.plain(123450));
        assertEquals("1 coin", Money.coins(100));
        assertEquals("2 coins", Money.coins(200));
        assertEquals("2.5k", Money.compact(250000));
    }

    @Test
    public void basisPointsFloors() {
        assertEquals(125, Money.basisPoints(10_000, 125));
        assertEquals(0, Money.basisPoints(10, 125));
        assertEquals(0, Money.basisPoints(-5, 125));
    }
}
