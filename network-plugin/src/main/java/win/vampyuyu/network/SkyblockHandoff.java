package win.vampyuyu.network;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Writes the Skyblock handoff the survival plugin reads on join: {@code SETEX sb:transfer:<uuid> 30
 * {"dest":"skyblock","ts":<epoch millis>}}. The {@code sb:} prefix is Skyblock's; the arena plugin owns
 * {@code nexus:transfer:*} and consumes anything under that exact key, so the two never mix.
 *
 * <p>Every call that touches Redis blocks (2 s connect, 3 s socket timeout): call it from an async task,
 * never from the main thread. Redis being down is a normal condition, not an error: {@link #available()}
 * turns false, the menu says so, and the player is connected to the survival server without a key.
 */
final class SkyblockHandoff implements AutoCloseable {
    static final String KEY_PREFIX = "sb:transfer:";
    static final int TTL_SECONDS = 30;
    /**
     * Incoming plugin channel the survival plugin can listen on for the same-server case. CLIENT-REACHABLE: Paper
     * delivers a custom payload a player sends to any registered listener. A listener must only act when the player's
     * metadata of this name holds the {@code nonce} of the payload (see {@link #notifyLocal}).
     */
    static final String CHANNEL = "vampyuyu:skyblock";
    private static final int PING_PERIOD_TICKS = 20 * 30;

    /** The three Redis commands used, so tests need no Redis. */
    interface Store extends AutoCloseable {
        void setex(String key, int ttlSeconds, String value);

        void del(String key);

        boolean ping();

        @Override
        void close();
    }

    /** How a same-server request was taken. */
    enum Local { GATEWAY, CHANNEL, NONE }

    private final JavaPlugin plugin;
    private final Store store;
    private final String reason;
    private volatile boolean available;
    private volatile boolean warned;
    private BukkitTask pinger;

    SkyblockHandoff(JavaPlugin plugin, Store store, String reason) {
        this.plugin = plugin;
        this.store = store;
        this.reason = reason;
        this.available = store != null;
    }

    static String key(UUID player) {
        return KEY_PREFIX + player;
    }

    /** The body of the key: exactly this shape is the contract with the survival plugin. */
    static String payload(long epochMillis) {
        return payload(epochMillis, "skyblock");
    }

    /** Same, with a landing: {@code skyblock} (the survival side decides), {@code island} or {@code hub}. */
    static String payload(long epochMillis, String dest) {
        return "{\"dest\":\"" + dest + "\",\"ts\":" + epochMillis + "}";
    }

    /**
     * The body of a same-server channel message: the key's body plus a {@code nonce}. The nonce is ALSO set as the
     * player's metadata {@link #CHANNEL} during the dispatch, which a client cannot do, so a listener can tell a real
     * request from a payload forged by a modified client.
     */
    static String channelPayload(long epochMillis, String dest, String nonce) {
        return "{\"dest\":\"" + dest + "\",\"ts\":" + epochMillis + ",\"nonce\":\"" + nonce + "\"}";
    }

    /** True when the player's Skyblock request can be routed (Redis configured and, as far as known, reachable). */
    boolean available() {
        return available;
    }

    /** Why Redis is not configured, or null. */
    String disabledReason() {
        return reason;
    }

    /** Blocking. Writes the key with its 30 s TTL; false (and {@link #available()} false) when Redis fails. */
    boolean write(UUID player, long epochMillis) {
        return write(player, epochMillis, "skyblock");
    }

    /** Blocking, as {@link #write(UUID, long)} with a landing. */
    boolean write(UUID player, long epochMillis, String dest) {
        if (store == null) return false;
        try {
            store.setex(key(player), TTL_SECONDS, payload(epochMillis, dest));
            markUp();
            return true;
        } catch (RuntimeException failure) {
            markDown(failure);
            return false;
        }
    }

    /** Blocking, best effort: removes a key nobody is going to consume. */
    void discard(UUID player) {
        if (store == null) return;
        try {
            store.del(key(player));
        } catch (RuntimeException failure) {
            markDown(failure);
        }
    }

    /** Blocking health check. */
    boolean ping() {
        if (store == null) return false;
        try {
            boolean ok = store.ping();
            if (ok) markUp(); else markDown(null);
            return ok;
        } catch (RuntimeException failure) {
            markDown(failure);
            return false;
        }
    }

    private void markUp() {
        if (!available) plugin.getLogger().info("Skyblock handoff: Redis is reachable again");
        available = true;
        warned = false;
    }

    private void markDown(Exception failure) {
        available = false;
        if (!warned) {
            warned = true;
            plugin.getLogger().log(Level.WARNING, "Skyblock handoff: Redis unavailable; mode selections wait for routing to recover; no"
                    + " fallback connection is attempted" + (failure == null ? "" : " (" + failure.getClass().getSimpleName() + ")"));
        }
    }

    /** Starts the background health check that keeps {@link #available()} (and so the card's tooltip) fresh. */
    void startPinging() {
        if (store == null || pinger != null) return;
        pinger = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::ping, 20L, PING_PERIOD_TICKS);
    }

    /**
     * Same-server request (the player is already on the survival server). Main thread.
     *
     * <p>Skyblock-wide requests ({@code dest} = {@code skyblock}) try the registered {@link SkyblockGateway} first (it
     * has no landing parameter). Then the incoming channel {@link #CHANNEL}: Paper hands a payload that a CLIENT sends
     * to the same listeners, so the message is only trustworthy together with the player metadata {@link #CHANNEL}
     * that is set for the duration of the (synchronous) dispatch and holds the {@code nonce} of the payload.
     */
    Local notifyLocal(Player player, String dest, long epochMillis) {
        if ("skyblock".equals(dest)) {
            try {
                RegisteredServiceProvider<SkyblockGateway> gateway = Bukkit.getServicesManager().getRegistration(SkyblockGateway.class);
                if (gateway != null && gateway.getProvider().sendToSkyblock(player)) return Local.GATEWAY;
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().log(Level.WARNING, "SkyblockGateway failed", failure);
            }
        }
        if (!Bukkit.getMessenger().getIncomingChannelRegistrations(CHANNEL).isEmpty()) {
            String nonce = UUID.randomUUID().toString();
            player.setMetadata(CHANNEL, new FixedMetadataValue(plugin, nonce));
            try {
                Bukkit.getMessenger().dispatchIncomingMessage(player, CHANNEL,
                        channelPayload(epochMillis, dest, nonce).getBytes(StandardCharsets.UTF_8));
            } finally {
                player.removeMetadata(CHANNEL, plugin);
            }
            return Local.CHANNEL;
        }
        return Local.NONE;
    }

    @Override
    public void close() {
        if (pinger != null) pinger.cancel();
        pinger = null;
        if (store != null) store.close();
    }

    // ---- configuration -------------------------------------------------------------------------------------

    /** Reads ARENA_REDIS_HOST / ARENA_REDIS_PORT / ARENA_REDIS_PASSWORD_FILE like the arena plugin. Never throws. */
    static SkyblockHandoff load(JavaPlugin plugin) {
        return load(plugin, System.getenv());
    }

    static SkyblockHandoff load(JavaPlugin plugin, Map<String, String> env) {
        String host = env.getOrDefault("ARENA_REDIS_HOST", "redis");
        int port = 6379;
        try {
            String raw = env.get("ARENA_REDIS_PORT");
            if (raw != null && !raw.isBlank()) port = Integer.parseInt(raw.trim());
            String secret = env.getOrDefault("ARENA_REDIS_PASSWORD_FILE", "/run/secrets/arena_redis_password");
            String password = Files.readString(Path.of(secret), StandardCharsets.UTF_8).trim();
            if (password.length() < 32) throw new IllegalArgumentException("Redis password must contain at least 32 characters");
            SkyblockHandoff handoff = new SkyblockHandoff(plugin, new JedisStore(host, port, password), null);
            handoff.startPinging();
            return handoff;
        } catch (IOException | RuntimeException | LinkageError failure) { // LinkageError: a broken shaded Jedis must not take the plugin down
            String why = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            plugin.getLogger().warning("Skyblock handoff disabled (" + why + "); mode selections stay put until routing recovers");
            return new SkyblockHandoff(plugin, null, why);
        }
    }

    /** Jedis behind {@link Store}: a small pool, bounded timeouts. */
    private static final class JedisStore implements Store {
        private final JedisPool pool;

        JedisStore(String host, int port, String password) {
            var config = DefaultJedisClientConfig.builder().password(password)
                    .connectionTimeoutMillis(2000).socketTimeoutMillis(3000).build();
            var pooling = new org.apache.commons.pool2.impl.GenericObjectPoolConfig<Jedis>();
            pooling.setMaxTotal(4);
            pooling.setMaxWait(java.time.Duration.ofSeconds(2));
            pool = new JedisPool(pooling, new HostAndPort(host, port), config);
        }

        @Override
        public void setex(String key, int ttlSeconds, String value) {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(key, ttlSeconds, value);
            }
        }

        @Override
        public void del(String key) {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(key);
            }
        }

        @Override
        public boolean ping() {
            try (Jedis jedis = pool.getResource()) {
                return "PONG".equalsIgnoreCase(jedis.ping());
            }
        }

        @Override
        public void close() {
            pool.close();
        }
    }
}
