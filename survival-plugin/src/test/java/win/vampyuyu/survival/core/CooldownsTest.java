package win.vampyuyu.survival.core;

import static org.junit.Assert.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class CooldownsTest {
    @Test
    public void cooldownRunsOutWithTheClock() {
        AtomicLong now = new AtomicLong(1000);
        Cooldowns cooldowns = new Cooldowns(now::get);
        UUID id = UUID.randomUUID();
        assertTrue(cooldowns.tryUse(id, "dash", 500));
        assertFalse(cooldowns.tryUse(id, "dash", 500));
        assertEquals(500, cooldowns.remaining(id, "dash"));
        assertTrue("other key is independent", cooldowns.tryUse(id, "other", 500));
        now.set(1500);
        assertTrue(cooldowns.ready(id, "dash"));
        assertTrue(cooldowns.tryUse(id, "dash", 500));
        cooldowns.clear(id, "dash");
        assertTrue(cooldowns.ready(id, "dash"));
    }
}
