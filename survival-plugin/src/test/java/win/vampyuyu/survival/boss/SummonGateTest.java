package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class SummonGateTest {
    private static BossDefinition.Summon rules(int perPlayer, int global) {
        return new BossDefinition.Summon("void_sigil", "free", List.of(), null, 0, perPlayer, global, false, false, 1, true);
    }

    @Test
    public void perPlayerCooldown() {
        AtomicLong now = new AtomicLong(1_000_000);
        SummonGate gate = new SummonGate(now::get);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(gate.acquire(a, "voidwyrm", rules(60, 0)).isEmpty());
        assertTrue(gate.acquire(a, "voidwyrm", rules(60, 0)).isPresent());
        assertTrue(gate.acquire(b, "voidwyrm", rules(60, 0)).isEmpty());
        now.addAndGet(61_000);
        assertTrue(gate.acquire(a, "voidwyrm", rules(60, 0)).isEmpty());
    }

    @Test
    public void globalCooldownBlocksEveryone() {
        AtomicLong now = new AtomicLong(5_000);
        SummonGate gate = new SummonGate(now::get);
        assertTrue(gate.acquire(UUID.randomUUID(), "x", rules(0, 30)).isEmpty());
        var refused = gate.acquire(UUID.randomUUID(), "x", rules(0, 30));
        assertTrue(refused.isPresent());
        assertTrue(refused.get().contains("recovering"));
        now.addAndGet(31_000);
        assertTrue(gate.acquire(UUID.randomUUID(), "x", rules(0, 30)).isEmpty());
    }

    @Test
    public void releaseGivesTheCooldownBack() {
        AtomicLong now = new AtomicLong(1);
        SummonGate gate = new SummonGate(now::get);
        UUID a = UUID.randomUUID();
        gate.acquire(a, "x", rules(100, 100));
        gate.release(a, "x");
        assertTrue(gate.acquire(a, "x", rules(100, 100)).isEmpty());
    }

    @Test
    public void bossesHaveSeparateCooldowns() {
        AtomicLong now = new AtomicLong(1);
        SummonGate gate = new SummonGate(now::get);
        UUID a = UUID.randomUUID();
        assertTrue(gate.acquire(a, "one", rules(100, 0)).isEmpty());
        assertTrue(gate.acquire(a, "two", rules(100, 0)).isEmpty());
    }
}
