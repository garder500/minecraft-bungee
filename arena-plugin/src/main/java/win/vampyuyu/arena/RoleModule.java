package win.vampyuyu.arena;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of a few related roles, kept out of {@link WerewolfGame} so each batch of roles lives
 * in its own file. The game owns the phases, the night queue, the ballots, the deaths and the win
 * check; a module only fills the hooks it needs (every hook has a do-nothing default).
 *
 * <p>Every hook runs inside the game's lock, on the game's thread of the moment, and may call the
 * package-private "module API" at the end of {@link WerewolfGame} ({@code g.roleOf}, {@code
 * g.notify}, {@code g.kill}...). A module keeps its own per-game state in its own fields: the game
 * builds a fresh instance of each module (see {@link Modules#create()}), so nothing is shared
 * between games. A module must never block, never touch Bukkit, and never keep a hook's UUID
 * arguments past the game.
 *
 * <p>Hooks are asked of every module for every phase and every player, whatever the roles: a
 * module checks the phase and the role itself and answers the default for anything that is not its
 * business (for example {@link #ready} returns true for a phase it does not own).
 */
interface RoleModule {
  /** The roles this module implements (documentation, tests and the module registry). */
  Set<WerewolfGame.Role> roles();

  /** The night calls (or day interruptions) this module owns. */
  default Set<WerewolfGame.Phase> phases() {
    return Set.of();
  }

  // ------------------------------------------------------------------ the game's rhythm

  /** Roles are dealt (forced roles included) and nothing has happened yet. */
  default void deal(WerewolfGame g) {}

  /**
   * A night begins (also the very first one), before its calls are queued. {@code g.nightNumber()}
   * is the night's number, starting at 1. This is where a role changes sides or learns something.
   */
  default void nightfall(WerewolfGame g) {}

  /**
   * The night's calls are over and its deaths have been resolved; the day is about to start. Runs
   * after the wolves', the witch's and the delayed deaths, before win checks of the new day.
   */
  default void dawn(WerewolfGame g) {}

  /** The game enters this phase (after it was set). Use it to act when a phase opens. */
  default void entered(WerewolfGame g, WerewolfGame.Phase phase) {}

  /** The game leaves this phase (time ran out or every actor was done). */
  default void closed(WerewolfGame g, WerewolfGame.Phase phase) {}

  // ------------------------------------------------------------------ calls

  /**
   * Whether this actor may be called in this phase tonight. The game has already checked that the
   * actor is alive (a Ghost may be dead) and holds the call's role. Return false to skip the actor
   * (an ability used up, a drunk who has not woken yet...). Asked of every module.
   */
  default boolean ready(WerewolfGame g, WerewolfGame.Phase phase, UUID actor) {
    return true;
  }

  /**
   * The actor right-clicked a player during one of this module's phases. Return the reply shown to
   * the player, or throw IllegalArgumentException to refuse. Call {@code g.done(actor)} when the
   * actor's call is over (the phase ends once every called actor is done).
   */
  default String select(
      WerewolfGame g, WerewolfGame.Phase phase, UUID actor, UUID target) {
    throw new IllegalArgumentException("You cannot pick a player now");
  }

  /** Extra buttons for this player in the current phase: ability key -> label. */
  default Map<String, String> abilities(WerewolfGame g, UUID player) {
    return Map.of();
  }

  /** The player pressed one of {@link #abilities}. Reply text, or IllegalArgumentException. */
  default String use(WerewolfGame g, UUID player, String ability) {
    throw new IllegalArgumentException("This action is unavailable now");
  }

  /** A practice bot's turn in one of this module's phases; use the game's public actions. */
  default void bot(WerewolfGame g, WerewolfGame.Phase phase, UUID bot, Random random) {}

  // ------------------------------------------------------------------ deaths

  /**
   * A death is about to happen (see {@link WerewolfGame.Kill}). Return true to save the victim
   * (a protection, a spared prince, a curse that turns instead of kills). The module explains
   * itself with {@code g.notify}. Asked of every module until one saves.
   */
  default boolean saves(WerewolfGame g, WerewolfGame.Kill kill) {
    return false;
  }

  /**
   * Whether this kill would be refused anyway, with no side effect: asked of the modules before a
   * martyr is offered the condemned player's place (an unrevealed prince is spared whoever
   * stands in). Return true to skip the martyr's window.
   */
  default boolean spares(WerewolfGame g, WerewolfGame.Kill kill) {
    return false;
  }

  /**
   * A player just died (already removed from the living). {@code source} is the kill's source
   * (WOLVES, VOTE, POISON...), or OTHER. Called before the player's lover dies of grief.
   */
  default void died(WerewolfGame g, UUID player, String source, String cause) {}

  // ------------------------------------------------------------------ votes

  /** How many ballots this voter's day vote counts for: 1 normally, 2 for a mayor, 0 ignored. */
  default int weight(WerewolfGame g, UUID voter) {
    return 1;
  }

  /**
   * The day vote is closing and this voter has cast no ballot: return who they vote for anyway
   * (an idiot's forced vote), or null to leave them out.
   */
  default UUID autoBallot(WerewolfGame g, UUID voter, List<UUID> candidates) {
    return null;
  }

  // ------------------------------------------------------------------ winning

  /**
   * Whether this module's team has won right now, among the living: return the winner label
   * ("VAMPIRES", "CULT", "LONE_WOLF"...) or null. Asked after the lovers and before the wolves and
   * the village.
   */
  default String teamWin(WerewolfGame g) {
    return null;
  }

  /** Members of the team this label names, for the winners' list (dead members count). */
  default Set<UUID> members(WerewolfGame g, String winnerLabel) {
    return Set.of();
  }

  /**
   * Players who win on their own goal, whoever else wins (a tanner who died, a hoodlum whose
   * marks are dead). Asked once the game is over.
   */
  default Set<UUID> sideWinners(WerewolfGame g) {
    return Set.of();
  }

  // ------------------------------------------------------------------ perception and chat

  /**
   * What this player believes their own role is (a drunk thinks they are a villager), or the
   * actual role. Used for their HUD, help and abilities, never for the rules.
   */
  default WerewolfGame.Role perceived(WerewolfGame g, UUID player, WerewolfGame.Role actual) {
    return actual;
  }

  /**
   * A player typed this in the chat. Return true to keep it from the table (a ghost's single
   * letter is turned into a haunting instead of a message).
   */
  default boolean chat(WerewolfGame g, UUID speaker, String text) {
    return false;
  }

  /** True when this player may not chat now (a silenced player). */
  default boolean silenced(WerewolfGame g, UUID player) {
    return false;
  }
}
