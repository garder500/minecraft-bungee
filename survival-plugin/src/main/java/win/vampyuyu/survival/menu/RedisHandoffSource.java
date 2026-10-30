package win.vampyuyu.survival.menu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Redis implementation of {@link HandoffSource} (key {@code sb:transfer:<uuid>}, GETDEL). Nothing touches the
 * network or the secret file until the first {@link #take}, so the feature enables on a machine without Redis. After a
 * failure the source stays quiet for {@code backoffMillis} (it throws {@link Unavailable} immediately) so a dead Redis
 * never delays every join, and the first failure is logged once per outage.
 */
public final class RedisHandoffSource implements HandoffSource {
    /** Key prefix reserved for Skyblock (the arena plugin owns {@code nexus:}). */
    public static final String KEY_PREFIX = "sb:transfer:";

    /** Thrown while the backend is in its back-off window. */
    public static final class Unavailable extends Exception {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Logger log;
    private final String host;
    private final int port;
    private final String passwordFile;
    private final int connectTimeoutMs;
    private final int socketTimeoutMs;
    private final long backoffMillis;
    private JedisPool pool;
    private long retryAt;
    private Exception lastFailure;
    private boolean closed;

    public RedisHandoffSource(Logger log, String host, int port, String passwordFile, int connectTimeoutMs,
            int socketTimeoutMs, long backoffMillis) {
        this.log = log;
        this.host = host;
        this.port = port;
        this.passwordFile = passwordFile;
        this.connectTimeoutMs = connectTimeoutMs;
        this.socketTimeoutMs = socketTimeoutMs;
        this.backoffMillis = backoffMillis;
    }

    /** Builds the source from {@code ARENA_REDIS_HOST} / {@code ARENA_REDIS_PASSWORD_FILE} unless overridden. */
    public static RedisHandoffSource fromEnvironment(Logger log, String hostOverride, int port, String fileOverride,
            int connectTimeoutMs, int socketTimeoutMs, long backoffMillis) {
        String host = hostOverride != null && !hostOverride.isBlank() ? hostOverride
            : System.getenv().getOrDefault("ARENA_REDIS_HOST", "redis");
        String file = fileOverride != null && !fileOverride.isBlank() ? fileOverride
            : System.getenv().getOrDefault("ARENA_REDIS_PASSWORD_FILE", "/run/secrets/arena_redis_password");
        return new RedisHandoffSource(log, host, port, file, connectTimeoutMs, socketTimeoutMs, backoffMillis);
    }

    private synchronized JedisPool pool() throws Exception {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        if (System.currentTimeMillis() < retryAt) {
            throw new Unavailable("Redis handoff in back-off", lastFailure);
        }
        if (pool == null) {
            try {
                String password = null;
                Path path = Path.of(passwordFile);
                if (Files.isRegularFile(path)) {
                    password = Files.readString(path).trim();
                    if (password.isEmpty()) {
                        password = null;
                    }
                }
                DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(connectTimeoutMs).socketTimeoutMillis(socketTimeoutMs);
                if (password != null) {
                    cfg.password(password);
                }
                var poolConfig = new org.apache.commons.pool2.impl.GenericObjectPoolConfig<Jedis>();
                poolConfig.setMaxTotal(4);
                poolConfig.setMaxWait(java.time.Duration.ofMillis(Math.max(500, connectTimeoutMs)));
                pool = new JedisPool(poolConfig, new HostAndPort(host, port), cfg.build());
            } catch (Exception | LinkageError e) {
                fail(e instanceof Exception ex ? ex : new Exception(e));
                throw lastFailure;
            }
        }
        return pool;
    }

    private synchronized void fail(Exception e) {
        boolean first = lastFailure == null;
        lastFailure = e;
        retryAt = System.currentTimeMillis() + backoffMillis;
        if (first) {
            log.warning("Skyblock handoff: Redis unavailable (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                + "); joins fall back to a normal join until it answers again");
        }
    }

    private synchronized void succeeded() {
        if (lastFailure != null) {
            lastFailure = null;
            log.info("Skyblock handoff: Redis is reachable again");
        }
    }

    @Override
    public String take(UUID player) throws Exception {
        JedisPool current = pool();
        try (Jedis jedis = current.getResource()) {
            String value = jedis.getDel(KEY_PREFIX + player);
            succeeded();
            return value;
        } catch (Exception e) {
            synchronized (this) {
                fail(e);
            }
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }
}
