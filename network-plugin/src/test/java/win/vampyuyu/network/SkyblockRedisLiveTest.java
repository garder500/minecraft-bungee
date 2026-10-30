package win.vampyuyu.network;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import redis.clients.jedis.Jedis;

/**
 * Talks to a real Redis. Skipped unless SKYBLOCK_TEST_REDIS_HOST (and _PASSWORD) are set, e.g.
 * {@code docker run --rm -d redis:alpine redis-server --requirepass <32+ chars>} on a shared docker network.
 */
public class SkyblockRedisLiveTest {
    private JavaPlugin plugin;

    @Before
    public void boot() {
        assumeTrue(System.getenv("SKYBLOCK_TEST_REDIS_HOST") != null);
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @After
    public void stop() {
        if (MockBukkit.isMocked()) MockBukkit.unmock();
    }

    @Test
    public void theKeyLandsInRedisWithTheContractBodyAndATtl() throws Exception {
        String host = System.getenv("SKYBLOCK_TEST_REDIS_HOST");
        String password = System.getenv("SKYBLOCK_TEST_REDIS_PASSWORD");
        Path secret = Files.createTempFile("redis", ".secret");
        Files.writeString(secret, password + "\n");
        SkyblockHandoff handoff = SkyblockHandoff.load(plugin, Map.of(
                "ARENA_REDIS_PASSWORD_FILE", secret.toString(), "ARENA_REDIS_HOST", host));
        UUID id = UUID.randomUUID();
        try (Jedis jedis = new Jedis(host, 6379)) {
            jedis.auth(password);
            assertTrue(handoff.ping());
            assertTrue(handoff.write(id, 1234L));
            assertEquals("{\"dest\":\"skyblock\",\"ts\":1234}", jedis.get("sb:transfer:" + id));
            long ttl = jedis.ttl("sb:transfer:" + id);
            assertTrue("ttl " + ttl, ttl > 0 && ttl <= 30);
            handoff.discard(id);
            assertNull(jedis.get("sb:transfer:" + id));
        } finally {
            handoff.close();
            Files.delete(secret);
        }
    }

    @Test
    public void anUnreachableRedisFailsFastAndNeverThrows() throws Exception {
        Path secret = Files.createTempFile("redis", ".secret");
        Files.writeString(secret, "y".repeat(40));
        SkyblockHandoff handoff = SkyblockHandoff.load(plugin, Map.of(
                "ARENA_REDIS_PASSWORD_FILE", secret.toString(), "ARENA_REDIS_HOST", "203.0.113.1"));
        long start = System.nanoTime();
        assertFalse(handoff.write(UUID.randomUUID(), 1L));
        assertFalse(handoff.available());
        assertTrue("bounded by the 2 s connect timeout", (System.nanoTime() - start) / 1_000_000 < 6000);
        handoff.close();
        Files.delete(secret);
    }
}
