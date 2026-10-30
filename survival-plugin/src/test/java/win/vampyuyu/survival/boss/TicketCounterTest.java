package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import org.junit.Test;

public class TicketCounterTest {
    @Test
    public void realTicketIsAddedByTheFirstAndRemovedByTheLastHolder() {
        TicketCounter c = new TicketCounter();
        assertTrue(c.acquire("w:1:2"));
        assertFalse("a second fight in the same chunk shares the ticket", c.acquire("w:1:2"));
        assertFalse("the first one ending must not unload the other fight", c.release("w:1:2"));
        assertEquals(1, c.holders("w:1:2"));
        assertTrue(c.release("w:1:2"));
        assertFalse("releasing something not held is a no-op", c.release("w:1:2"));
        assertTrue("and the chunk can be held again", c.acquire("w:1:2"));
    }
}
