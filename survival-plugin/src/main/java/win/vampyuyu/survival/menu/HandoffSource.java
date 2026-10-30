package win.vampyuyu.survival.menu;

import java.util.UUID;

/**
 * Where the cross-server destination key comes from. The production implementation is Redis
 * ({@link RedisHandoffSource}); tests use a fake. {@link #take} must consume the value (GETDEL) so a
 * destination is honoured at most once.
 */
public interface HandoffSource extends AutoCloseable {
    /**
     * Atomically reads and deletes the handoff value of a player. Blocking: call it off the main thread.
     *
     * @return the raw value, or null when there is none
     * @throws Exception when the backend is unreachable (the caller falls back to a normal join)
     */
    String take(UUID player) throws Exception;

    @Override
    default void close() {}
}
