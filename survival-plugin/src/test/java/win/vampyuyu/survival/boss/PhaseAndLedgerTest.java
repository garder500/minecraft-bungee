package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;
import org.junit.Test;
import win.vampyuyu.survival.api.event.BossKillEvent.Damager;

public class PhaseAndLedgerTest {
    @Test
    public void phasesAdvanceOnThresholds() {
        PhaseTracker t = new PhaseTracker(new double[] {1.0, 0.6, 0.25});
        assertEquals(List.of(), t.update(0.9));
        assertEquals(0, t.current());
        assertEquals(List.of(1), t.update(0.6));
        assertEquals(List.of(2), t.update(0.1));
        assertTrue(t.isLast());
        assertEquals(List.of(), t.update(0.0));
    }

    @Test
    public void oneBigHitSkipsSeveralPhasesInOrder() {
        PhaseTracker t = new PhaseTracker(new double[] {1.0, 0.7, 0.4, 0.1});
        assertEquals(List.of(1, 2), t.update(0.35));
        assertEquals(2, t.current());
    }

    @Test
    public void healingNeverGoesBack() {
        PhaseTracker t = new PhaseTracker(new double[] {1.0, 0.5});
        t.update(0.4);
        assertEquals(List.of(), t.update(0.9));
        assertEquals(1, t.current());
    }

    @Test(expected = IllegalArgumentException.class)
    public void thresholdsMustDecrease() {
        new PhaseTracker(new double[] {1.0, 0.5, 0.5});
    }

    @Test(expected = IllegalArgumentException.class)
    public void needsAPhase() {
        new PhaseTracker(new double[] {});
    }

    @Test
    public void ledgerRanksAndSharesAddUp() {
        DamageLedger l = new DamageLedger();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        l.record(a, 300, 1000);
        l.record(b, 500, 700);
        l.record(a, 100, 200);
        l.record(c, 100, 100);
        assertEquals(1000, l.total(), 1e-9);
        assertEquals(1, l.rankOf(b));
        assertEquals(2, l.rankOf(a));
        assertEquals(3, l.rankOf(c));
        assertEquals(0, l.rankOf(UUID.randomUUID()));
        double sum = l.participants().stream().mapToDouble(l::share).sum();
        assertEquals(1.0, sum, 1e-9);
        List<Damager> top = l.top(2);
        assertEquals(2, top.size());
        assertEquals(b, top.get(0).player());
        assertEquals(3, l.top(10).size());
        assertTrue(l.top(0).isEmpty());
    }

    @Test
    public void overkillAndInvalidHitsAreNotCredited() {
        DamageLedger l = new DamageLedger();
        UUID a = UUID.randomUUID();
        assertEquals(50, l.record(a, 9999, 50), 1e-9);
        assertEquals(0, l.record(a, 10, 0), 1e-9);
        assertEquals(0, l.record(a, -5, 100), 1e-9);
        assertEquals(0, l.record(a, Double.NaN, 100), 1e-9);
        assertEquals(50, l.total(), 1e-9);
        assertEquals(1.0, l.share(a), 1e-9);
    }

    @Test
    public void tieKeepsFirstHitterFirst() {
        DamageLedger l = new DamageLedger();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        l.record(a, 10, 100);
        l.record(b, 10, 90);
        assertEquals(a, l.ranking().get(0).player());
    }

    @Test
    public void emptyLedgerHasNoShare() {
        DamageLedger l = new DamageLedger();
        assertEquals(0, l.share(UUID.randomUUID()), 0);
        assertTrue(l.ranking().isEmpty());
        assertTrue(l.top(3).isEmpty());
    }
}
