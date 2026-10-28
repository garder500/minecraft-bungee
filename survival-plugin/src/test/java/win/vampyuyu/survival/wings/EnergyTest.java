package win.vampyuyu.survival.wings;

import static org.junit.Assert.*;

import org.junit.Test;

public class EnergyTest {
    @Test
    public void startsFullAndNeverExceedsCapacity() {
        Energy e = new Energy(100);
        assertTrue(e.full());
        e.regen(50, 15);
        assertEquals(100, e.value(), 1e-9);
        assertEquals(1.0, e.fraction(), 1e-9);
    }

    @Test
    public void drainingToZeroExhaustsOnceAndReportsIt() {
        Energy e = new Energy(100);
        assertFalse(e.drain(60));
        assertTrue(e.drain(60));
        assertEquals(0, e.value(), 1e-9);
        assertTrue(e.exhausted());
        assertFalse("already exhausted: no second signal", e.drain(10));
    }

    @Test
    public void exhaustedUntilTheResumeThresholdIsBack() {
        Energy e = new Energy(100);
        e.drain(500);
        e.regen(14, 15);
        assertTrue(e.exhausted());
        e.regen(1, 15);
        assertFalse(e.exhausted());
        assertEquals(15, e.value(), 1e-9);
    }

    @Test
    public void negativeAmountsAreIgnored() {
        Energy e = new Energy(100, 50);
        e.drain(-30);
        e.regen(-30, 15);
        assertEquals(50, e.value(), 1e-9);
    }

    @Test
    public void shrinkingTheCapacityClampsTheValueGrowingKeepsIt() {
        Energy e = new Energy(100, 80);
        e.setCapacity(60);
        assertEquals(60, e.value(), 1e-9);
        e.setCapacity(400);
        assertEquals(60, e.value(), 1e-9);
        assertEquals(400, e.capacity(), 1e-9);
    }

    @Test
    public void restoreClampsAndAnEmptyBarComesBackExhausted() {
        Energy e = new Energy(100);
        e.restore(250);
        assertEquals(100, e.value(), 1e-9);
        e.restore(-5);
        assertTrue(e.exhausted());
        e.fill();
        assertFalse(e.exhausted());
        assertTrue(e.full());
    }

    @Test
    public void capacityHasAFloorOfOne() {
        Energy e = new Energy(0);
        assertEquals(1, e.capacity(), 1e-9);
    }
}
