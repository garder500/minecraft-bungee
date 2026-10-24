package win.vampyuyu.arena;

import java.util.*;
import java.util.concurrent.*;

public final class ArenaSession {
  public final ArenaConfig config;
  public final Map<UUID, String> players = new ConcurrentHashMap<>();
  public final Set<UUID> bots = ConcurrentHashMap.newKeySet();
  public volatile GamePreset game;

  /** The running werewolf game's play-by-play; silent for other games. */
  volatile WerewolfJournal journal = WerewolfJournal.SILENT;

  public volatile long startedAt, phaseEndsAt;
  public volatile String observedPhase = "WAITING";

  /** The Werewolf turn last seen by the clock (see {@link WerewolfGame#turnKey()}). */
  volatile String observedTurn = "";

  /** The practice bots of the current game, or null without bots. */
  volatile WerewolfBots botBrain;
  public volatile boolean stopping;
  /** Seat of every participant (humans and bots) while the places are in use. */
  final Map<UUID, ArenaConfig.Seat> seats = new ConcurrentHashMap<>();

  /** Where each participant currently is: "seat", "den" or "night" (see PlaceController). */
  final Map<UUID, String> placed = new ConcurrentHashMap<>();

  /** Bodies of the practice bots (mannequins) and the chairs of seated players. */
  final Map<UUID, org.bukkit.entity.Entity> bodies = new ConcurrentHashMap<>();

  final Map<UUID, org.bukkit.entity.Entity> chairs = new ConcurrentHashMap<>();

  /** Vote counters by the player they are over, and the voters' glowing frames (see VoteMarks). */
  final Map<UUID, VoteMarks.Tag> voteTags = new ConcurrentHashMap<>();

  final Map<UUID, VoteMarks.Glow> voteGlows = new ConcurrentHashMap<>();

  final Map<UUID, String> roster = new ConcurrentHashMap<>();
  final Set<UUID> abandoned = ConcurrentHashMap.newKeySet();
  volatile CompletableFuture<Void> presentation = CompletableFuture.completedFuture(null);
  volatile boolean resultQueued, finishing, practice;
  volatile String eventId;
  volatile long ambienceEpoch, nextHeartbeatAt;

  /** The role cards are dealt face down and turned over at this time. */
  volatile long cardsTurnAt;

  /** The day vote's verdict is on screen: no menu covers it. */
  volatile boolean verdictShowing;

  volatile boolean ambienceNight, ambienceFinale;
  /** When the last dawn broke, for the fading screen effect (WerewolfPresentation.fx). */
  volatile long dawnAt;
  volatile String ambiencePhase = "";
  final Set<UUID> revealed = ConcurrentHashMap.newKeySet();
  volatile CompletableFuture<Void> stopFuture = CompletableFuture.completedFuture(null);

  ArenaSession(ArenaConfig config) {
    this.config = config;
  }

  public String phase() {
    return stopping || finishing ? "RESTORING" : game == null ? "WAITING" : game.phase();
  }
}
