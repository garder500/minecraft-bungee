package win.vampyuyu.survival.minions;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import win.vampyuyu.survival.minions.Production.Clock;
import win.vampyuyu.survival.minions.Production.Outcome;

public class ProductionTest {
    private static Outcome run(Clock c, double elapsed, double cycle, double plain, double fuel, double burn, AtomicInteger made) {
        return Production.advance(c, elapsed, cycle, plain, fuel, burn, () -> {
            made.incrementAndGet();
            return true;
        });
    }

    @Test
    public void wholeCyclesOnly() {
        Clock c = new Clock(0, 0);
        AtomicInteger made = new AtomicInteger();
        Outcome o = run(c, 100_000, 28_000, 1, 1, 1, made);
        assertEquals(3, o.cycles());
        assertEquals(3, made.get());
        assertEquals(16_000, c.progress, 1e-6);
        assertFalse(o.stalled());
    }

    @Test
    public void fractionalProgressCarriesAcrossCalls() {
        Clock c = new Clock(0, 0);
        AtomicInteger made = new AtomicInteger();
        for (int i = 0; i < 280; i++) {
            run(c, 100, 28_000, 1, 1, 1, made);
        }
        assertEquals(1, made.get());
        assertEquals(0, c.progress, 1e-6);
    }

    @Test
    public void splittingTheWindowGivesTheSameTotalAsOneBigWindow() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        Clock one = new Clock(0, 3_600_000);
        Clock many = new Clock(0, 3_600_000);
        run(one, 10_000_000, 14_000, 1.0, 1.25, 1.0, a);
        for (int i = 0; i < 10_000; i++) {
            run(many, 1_000, 14_000, 1.0, 1.25, 1.0, b);
        }
        assertEquals(a.get(), b.get());
        assertEquals(one.progress, many.progress, 1e-3);
        assertEquals(0, one.fuelMs, 1e-3);
        assertEquals(0, many.fuelMs, 1e-3);
    }

    @Test
    public void fuelExpiringInTheMiddleOfAnOfflineWindowChangesSpeedExactlyThen() {
        // cycle 10s, fuel doubles the speed for 100s of real time, then normal. Window: 300s.
        Clock c = new Clock(0, 100_000);
        AtomicInteger made = new AtomicInteger();
        run(c, 300_000, 10_000, 1, 2, 1, made);
        // 100s * 2 = 200s of work = 20 cycles, then 200s at speed 1 = 20 cycles
        assertEquals(40, made.get());
        assertEquals(0, c.fuelMs, 1e-6);
        assertEquals(0, c.progress, 1e-6);
    }

    @Test
    public void fuelBurnRateStretchesTheFuel() {
        Clock c = new Clock(0, 100_000);
        AtomicInteger made = new AtomicInteger();
        // burn 0.5: 100s of fuel lasts 200s of real time
        run(c, 200_000, 10_000, 1, 2, 0.5, made);
        assertEquals(40, made.get());
        assertEquals(0, c.fuelMs, 1e-6);
    }

    @Test
    public void fuelIsNotBurnedWhileNothingIsProduced() {
        Clock c = new Clock(0, 50_000);
        Production.Outcome o = Production.advance(c, 1_000_000, 10_000, 1, 2, 1, () -> false);
        assertTrue(o.stalled());
        assertEquals(0, o.cycles());
        // a cycle needs 5s of real time at speed 2, so only 5s of fuel is gone
        assertEquals(45_000, c.fuelMs, 1e-6);
        assertEquals(10_000, c.progress, 1e-6);
    }

    @Test
    public void aStalledCycleCompletesAsSoonAsThereIsRoom() {
        Clock c = new Clock(10_000, 0);
        AtomicInteger made = new AtomicInteger();
        Outcome o = run(c, 0, 10_000, 1, 1, 1, made);
        assertEquals(1, o.cycles());
        assertEquals(0, c.progress, 1e-6);
    }

    @Test
    public void storageFillingStopsTheLoopAndTheRestOfTheTimeIsLost() {
        AtomicInteger room = new AtomicInteger(5);
        Clock c = new Clock(0, 0);
        Outcome o = Production.advance(c, 10_000_000, 10_000, 1, 1, 1, () -> room.getAndDecrement() > 0);
        assertEquals(5, o.cycles());
        assertTrue(o.stalled());
    }

    @Test
    public void negativeAndZeroElapsedDoNothing() {
        Clock c = new Clock(5, 0);
        AtomicInteger made = new AtomicInteger();
        assertEquals(0, run(c, -500, 10_000, 1, 1, 1, made).cycles());
        assertEquals(0, run(c, 0, 10_000, 1, 1, 1, made).cycles());
        assertEquals(5, c.progress, 1e-9);
    }

    @Test
    public void absurdConfigCannotLoopForever() {
        Clock c = new Clock(0, 0);
        AtomicInteger made = new AtomicInteger();
        Outcome o = run(c, 1e15, 1, 1000, 1000, 1, made);
        assertEquals(Production.MAX_CYCLES, o.cycles());
    }

    @Test
    public void cycleSeconds() {
        assertEquals(28, Production.cycleSeconds(28_000, 1), 1e-9);
        assertEquals(14, Production.cycleSeconds(28_000, 2), 1e-9);
    }
}
