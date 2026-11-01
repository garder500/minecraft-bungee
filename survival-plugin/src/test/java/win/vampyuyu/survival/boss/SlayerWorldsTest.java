package win.vampyuyu.survival.boss;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Design principle (docs/skyblock-design.md section 13): slayers never run in the hub. */
public class SlayerWorldsTest {
    @Before
    public void server() {
        MockBukkit.mock(); // entity types need a server
    }

    @After
    public void stop() {
        MockBukkit.unmock();
    }

    @Test
    public void shippedSlayersStayOutOfTheHub() {
        var slayers = BossTestData.shipped().slayers();
        assertTrue(!slayers.isEmpty());
        for (var slayer : slayers.values()) {
            assertFalse(slayer.id() + " must list at least one world", slayer.worlds().isEmpty());
            assertFalse(slayer.id() + " must not run in the hub", slayer.worlds().contains("sb_hub"));
        }
    }
}
