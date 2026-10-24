package win.vampyuyu.werewolf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.werewolf.hud.VoteMarks;
import win.vampyuyu.werewolf.sky.PlatformHandle;

/** What a Werewolf game keeps on an arena session (see {@link ArenaSession#attachment}). */
public final class WerewolfSession {
  /** The running game's play-by-play; silent when none is kept. */
  public volatile WerewolfJournal journal = WerewolfJournal.SILENT;

  /** The turn last seen by the clock (see {@link WerewolfGame#turnKey()}). */
  public volatile String observedTurn = "";

  /** The practice bots of the current game, or null without bots. */
  public volatile WerewolfBots botBrain;

  /** Seat of every participant (humans and bots) while the platform is in use. */
  public final Map<UUID, SeatSpot> seats = new ConcurrentHashMap<>();

  /** The game's flying platform (see SkyStage), from the opening until the players are restored. */
  public volatile PlatformHandle platform;

  /** Where each participant currently is: "seat" once they have been seated (see PlaceController). */
  public final Map<UUID, String> placed = new ConcurrentHashMap<>();

  /** Bodies of the practice bots (mannequins) and the chairs of seated players. */
  public final Map<UUID, Entity> bodies = new ConcurrentHashMap<>();

  public final Map<UUID, Entity> chairs = new ConcurrentHashMap<>();

  /** Vote counters by the player they are over, and the voters' glowing frames (see VoteMarks). */
  public final Map<UUID, VoteMarks.Tag> voteTags = new ConcurrentHashMap<>();

  public final Map<UUID, VoteMarks.Glow> voteGlows = new ConcurrentHashMap<>();

  /** The frame everybody sees around a nominated player (nominated voting), and who it is on. */
  public volatile VoteMarks.Glow accusedGlow;

  /** The crown over the elected mayor, seen by every player. */
  public volatile VoteMarks.Tag mayorTag;

  /** Bumped whenever the phase changes, so scenes of the previous phase stop. */
  public volatile long ambienceEpoch, nextHeartbeatAt;

  /** The role cards are dealt face down and turned over at this time. */
  public volatile long cardsTurnAt;

  /** The day vote's verdict is on screen: no menu covers it. */
  public volatile boolean verdictShowing;

  public volatile boolean ambienceNight, ambienceFinale;

  /** When the last dawn broke, for the fading screen effect (WerewolfPresentation.fx). */
  public volatile long dawnAt;

  public volatile String ambiencePhase = "";
  public final Set<UUID> revealed = ConcurrentHashMap.newKeySet();

  public static WerewolfSession of(ArenaSession s) {
    return s.attachment(WerewolfSession.class, WerewolfSession::new);
  }
}
