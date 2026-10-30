package win.vampyuyu.survival.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class FeatureManagerTest {
    private final List<String> log = new ArrayList<>();
    private Core core;

    private final class Recording implements Feature {
        private final String id;
        private final boolean fail;

        Recording(String id, boolean fail) {
            this.id = id;
            this.fail = fail;
        }

        public String id() { return id; }

        public void enable(Core core) {
            if (fail) throw new IllegalStateException("boom");
            log.add("enable:" + id);
        }

        public void disable() { log.add("disable:" + id); }
    }

    @Before
    public void setUp() {
        MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        core = new Core(plugin);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void throwingFeatureDoesNotStopTheOthers() {
        FeatureManager manager = new FeatureManager(Logger.getAnonymousLogger(), id -> true,
            List.of(new Recording("a", false), new Recording("bad", true), new Recording("c", false)));
        manager.enableAll(core);
        assertEquals(List.of("enable:a", "enable:c"), log);
        assertEquals(List.of("a", "c"), manager.enabledIds());
        assertEquals(List.of("bad"), manager.failedIds());
        manager.disableAll();
        assertEquals(List.of("enable:a", "enable:c", "disable:c", "disable:a"), log);
    }

    @Test
    public void failedFeatureRegistrationsAreRolledBack() {
        Feature half = new Feature() {
            public String id() { return "half"; }

            public void enable(Core c) {
                c.register(CharSequence.class, "half built");
                c.listen(new org.bukkit.event.Listener() {});
                c.commands().command("sbhalf").executor((s, cmd, l, a) -> true)
                    .permission("vampyuyu.sb.half.use", org.bukkit.permissions.PermissionDefault.TRUE).register();
                throw new IllegalStateException("late failure");
            }
        };
        FeatureManager manager = new FeatureManager(Logger.getAnonymousLogger(), id -> true,
            List.of(half, new Recording("after", false)));
        manager.enableAll(core);
        assertTrue(core.service(CharSequence.class).isEmpty());
        assertNull(org.bukkit.Bukkit.getCommandMap().getKnownCommands().get("sbhalf"));
        assertNull(org.bukkit.Bukkit.getPluginManager().getPermission("vampyuyu.sb.half.use"));
        assertEquals(0, core.commands().count());
        assertEquals(List.of("half"), manager.failedIds());
        assertEquals(List.of("after"), manager.enabledIds());
    }

    @Test
    public void featuresDisabledByConfigAreSkipped() {
        Set<String> off = Set.of("b");
        FeatureManager manager = new FeatureManager(Logger.getAnonymousLogger(), id -> !off.contains(id),
            List.of(new Recording("a", false), new Recording("b", false)));
        manager.enableAll(core);
        assertEquals(List.of("enable:a"), log);
        manager.disableAll();
        assertEquals(List.of("enable:a", "disable:a"), log);
    }

    @Test
    public void serviceRegistry() {
        assertTrue(core.service(CharSequence.class).isEmpty());
        core.register(CharSequence.class, "hello");
        assertEquals("hello", core.require(CharSequence.class));
        try {
            core.register(CharSequence.class, "again");
            fail();
        } catch (IllegalStateException expected) {
        }
        try {
            core.require(Runnable.class);
            fail();
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void featureConfigWithoutAResourceIsEmptyNotAnError() {
        assertTrue(core.featureConfig("nothing_here").getKeys(true).isEmpty());
    }
}
