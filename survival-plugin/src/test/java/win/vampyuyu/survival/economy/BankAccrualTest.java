package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import org.junit.Test;

public class BankAccrualTest {
    private static final long PERIOD = 3_600_000L;

    @Test
    public void paysOncePerElapsedPeriodAndCompounds() {
        Bank.Accrual a = Bank.accrue(1_000_000, 0 + 1000, 1000 + 2 * PERIOD + 5, PERIOD, 10, 200, Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(2, a.periods());
        assertEquals(20_000 + 20_400, a.interest());
        assertEquals(1000 + 2 * PERIOD, a.clock());
    }

    @Test
    public void nothingBeforeAFullPeriod() {
        Bank.Accrual a = Bank.accrue(1_000_000, 1000, 1000 + PERIOD - 1, PERIOD, 10, 200, 1_000_000, 10_000_000);
        assertEquals(0, a.interest());
        assertEquals(1000, a.clock());
    }

    @Test
    public void perPeriodCapAndCapacityLimitTheInterest() {
        Bank.Accrual capped = Bank.accrue(10_000_000, 1000, 1000 + PERIOD, PERIOD, 10, 200, 50_000, 100_000_000);
        assertEquals(50_000, capped.interest());
        Bank.Accrual full = Bank.accrue(9_990_000, 1000, 1000 + PERIOD, PERIOD, 10, 200, 5_000_000, 10_000_000);
        assertEquals(10_000, full.interest());
        assertEquals(10_000_000, full.bank());
    }

    @Test
    public void offlineCatchUpIsLimitedAndTheRestForfeited() {
        long now = 1000 + 1000 * PERIOD;
        Bank.Accrual a = Bank.accrue(1_000_000, 1000, now, PERIOD, 3, 100, Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(3, a.periods());
        assertEquals(now, a.clock());
    }

    @Test
    public void emptyBankOrNoClockPaysNothing() {
        assertEquals(0, Bank.accrue(0, 5000, 99_999_999L, PERIOD, 10, 200, 100, 1000).interest());
        Bank.Accrual started = Bank.accrue(5000, 0, 77, PERIOD, 10, 200, 100, 100000);
        assertEquals(0, started.interest());
        assertEquals(77, started.clock());
        Bank.Accrual backwards = Bank.accrue(5000, 1000, 10, PERIOD, 10, 200, 100, 100000);
        assertEquals(0, backwards.interest());
    }
}
