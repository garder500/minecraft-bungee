package win.vampyuyu.survival.breath;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A found Fragment is a count, not an item: the message says where it is and how to see it. */
public class FragmentMessageTest {
    @Test
    public void namesTheCountAndTheCommand() {
        String text = BreathService.fragmentMessage(2, 5);
        assertTrue(text, text.contains("not in your inventory"));
        assertTrue(text, text.contains("2/5"));
        assertTrue(text, text.contains("/breath"));
    }
}
