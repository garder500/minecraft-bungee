package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import win.vampyuyu.survival.SurvivalPlugin;

public class DurableFileTest {
    private SurvivalPlugin plugin;

    @Before
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    @Test
    public void profilesAreQueuedOnTheMainThreadAndAwaitedOnTheIoThread() {
        List<String> events = new ArrayList<>();
        DurableFile.Env env = new DurableFile.Env() {
            public void defer(long ticks, Runnable task) { task.run(); }

            public void io(Runnable task) {
                events.add("io:start");
                task.run();
            }

            public void main(Runnable task) { task.run(); }
        };
        DurableFile file = new DurableFile(plugin.core().store(), "economy/durable-test.json", () -> {
            events.add("snapshot");
            return java.util.Map.of("a", 1);
        }, env, () -> events.add("flush"), () -> events.add("barrier"), plugin.getLogger(), 0);
        file.request(ok -> events.add("callback:" + ok));
        // flush (serialising profiles) happens before the snapshot and before the hop to the IO thread;
        // only the wait for the profile writes happens on the IO thread, right before the market file is written
        assertEquals(List.of("flush", "snapshot", "io:start", "barrier", "callback:true"), events);
    }

    @Test
    public void closeFlushesThenWaitsThenWrites() {
        List<String> events = new ArrayList<>();
        DurableFile file = new DurableFile(plugin.core().store(), "economy/durable-test2.json", () -> {
            events.add("snapshot");
            return java.util.Map.of("a", 1);
        }, new DurableFile.Inline(), () -> events.add("flush"), () -> events.add("barrier"), plugin.getLogger(), 0);
        file.request(ok -> { });
        events.clear();
        file.close();
        assertEquals(List.of("flush", "snapshot", "barrier"), events);
    }
}
