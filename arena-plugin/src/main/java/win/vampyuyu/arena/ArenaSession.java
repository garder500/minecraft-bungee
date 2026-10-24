package win.vampyuyu.arena;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * One arena's live state: who is inside, the running game, and the clock. What only a game needs
 * (a journal, seats, scenery...) hangs off it as an {@linkplain #attachment attachment} the game
 * owns.
 */
public final class ArenaSession {
  public final ArenaConfig config;
  /** What the arena's game adds around the life cycle ({@link GameHooks#NONE} for none). */
  public final GameHooks hooks;

  public final Map<UUID, String> players = new ConcurrentHashMap<>();
  public final Set<UUID> bots = ConcurrentHashMap.newKeySet();
  public volatile GamePreset game;

  public volatile long startedAt, phaseEndsAt;
  public volatile String observedPhase = "WAITING";
  public volatile boolean stopping;

  public final Map<UUID, String> roster = new ConcurrentHashMap<>();
  public final Set<UUID> abandoned = ConcurrentHashMap.newKeySet();
  public volatile CompletableFuture<Void> presentation = CompletableFuture.completedFuture(null);
  public volatile boolean resultQueued, finishing, practice;
  public volatile String eventId;
  public volatile CompletableFuture<Void> stopFuture = CompletableFuture.completedFuture(null);

  private final Map<Class<?>, Object> attachments = new ConcurrentHashMap<>();

  public ArenaSession(ArenaConfig config, GameHooks hooks) {
    this.config = config;
    this.hooks = hooks == null ? GameHooks.NONE : hooks;
  }

  public String phase() {
    return stopping || finishing ? "RESTORING" : game == null ? "WAITING" : game.phase();
  }

  /** The game's own state on this session, created on first use. */
  @SuppressWarnings("unchecked")
  public <T> T attachment(Class<T> type, Supplier<T> create) {
    return (T) attachments.computeIfAbsent(type, k -> create.get());
  }
}
