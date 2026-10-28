package win.vampyuyu.network;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class SkyblockHandoffTest {
    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    /** Records the Redis commands; can be told to fail. */
    static final class FakeStore implements SkyblockHandoff.Store {
        final List<String> calls = new ArrayList<>();
        /** Optional shared timeline, to check the order against other events. */
        List<String> timeline;
        boolean failing;
        boolean closed;

        @Override
        public void setex(String key, int ttlSeconds, String value) {
            if (failing) throw new IllegalStateException("redis down");
            if (timeline != null) timeline.add("SETEX");
            calls.add("SETEX " + key + " " + ttlSeconds + " " + value);
        }

        @Override
        public void del(String key) {
            if (failing) throw new IllegalStateException("redis down");
            calls.add("DEL " + key);
        }

        @Override
        public boolean ping() {
            if (failing) throw new IllegalStateException("redis down");
            calls.add("PING");
            return true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private JavaPlugin plugin;

    @Before
    public void boot() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @After
    public void stop() {
        MockBukkit.unmock();
    }

    @Test
    public void theWireFormatIsExactlyTheContractOfTheSurvivalPlugin() {
        assertEquals("sb:transfer:" + ID, SkyblockHandoff.key(ID));
        assertEquals("{\"dest\":\"skyblock\",\"ts\":1700000000123}", SkyblockHandoff.payload(1700000000123L));
        assertEquals(30, SkyblockHandoff.TTL_SECONDS);
        assertNotEquals("the arena owns nexus:transfer:*", "nexus:transfer:", SkyblockHandoff.KEY_PREFIX);
    }

    @Test
    public void writeIsASetexWithTheThirtySecondTtl() {
        FakeStore store = new FakeStore();
        SkyblockHandoff handoff = new SkyblockHandoff(plugin, store, null);
        assertTrue(handoff.write(ID, 42L));
        assertEquals(List.of("SETEX sb:transfer:" + ID + " 30 {\"dest\":\"skyblock\",\"ts\":42}"), store.calls);
        assertTrue(handoff.available());
    }

    @Test
    public void aRedisFailureIsReportedNotThrownAndTheCardGoesOffline() {
        FakeStore store = new FakeStore();
        SkyblockHandoff handoff = new SkyblockHandoff(plugin, store, null);
        assertTrue(handoff.available());
        store.failing = true;
        assertFalse(handoff.write(ID, 1L));
        assertFalse(handoff.available());
        handoff.discard(ID); // must not throw either
        assertFalse(handoff.ping());
        store.failing = false;
        assertTrue(handoff.ping());
        assertTrue("a good ping brings it back", handoff.available());
    }

    @Test
    public void withoutRedisNothingIsWrittenAndTheMenuReportsItOffline() {
        SkyblockHandoff handoff = new SkyblockHandoff(plugin, null, "no secret");
        assertFalse(handoff.available());
        assertFalse(handoff.write(ID, 1L));
        assertFalse(handoff.ping());
        assertEquals("no secret", handoff.disabledReason());
        handoff.discard(ID);
        handoff.close();
    }

    @Test
    public void closeReleasesTheStore() {
        FakeStore store = new FakeStore();
        new SkyblockHandoff(plugin, store, null).close();
        assertTrue(store.closed);
    }

    @Test
    public void aMissingOrShortPasswordFileDisablesTheHandoffWithoutThrowing() throws Exception {
        SkyblockHandoff missing = SkyblockHandoff.load(plugin, Map.of("ARENA_REDIS_PASSWORD_FILE", "/nonexistent/secret"));
        assertFalse(missing.available());
        assertNotNull(missing.disabledReason());
        Path shortSecret = Files.createTempFile("redis", ".secret");
        Files.writeString(shortSecret, "too-short\n");
        SkyblockHandoff weak = SkyblockHandoff.load(plugin, Map.of("ARENA_REDIS_PASSWORD_FILE", shortSecret.toString()));
        assertFalse(weak.available());
        assertTrue(weak.disabledReason().contains("32"));
        Files.delete(shortSecret);
    }

    @Test
    public void aBadPortDisablesTheHandoffToo() throws Exception {
        Path secret = Files.createTempFile("redis", ".secret");
        Files.writeString(secret, "x".repeat(40));
        SkyblockHandoff handoff = SkyblockHandoff.load(plugin, Map.of(
                "ARENA_REDIS_PASSWORD_FILE", secret.toString(), "ARENA_REDIS_PORT", "not-a-port"));
        assertFalse(handoff.available());
        Files.delete(secret);
    }

    @Test
    public void aValidSecretBuildsAPoolWithoutConnectingYet() throws Exception {
        Path secret = Files.createTempFile("redis", ".secret");
        Files.writeString(secret, "x".repeat(40) + "\n");
        SkyblockHandoff handoff = SkyblockHandoff.load(plugin, Map.of(
                "ARENA_REDIS_PASSWORD_FILE", secret.toString(), "ARENA_REDIS_HOST", "127.0.0.1", "ARENA_REDIS_PORT", "1"));
        assertTrue("optimistic until the first ping says otherwise", handoff.available());
        handoff.close();
        Files.delete(secret);
    }
}
