package win.vampyuyu.arena;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * What a game adds around the generic zone life cycle. The arena engine owns joining, bounds,
 * protection, snapshots, the clock and the panel; a game plugs in here instead of the engine
 * knowing about it. Every method has a neutral default, so a small game overrides only what it
 * needs. An {@link ArenaPreset} hands its hooks to the engine, which calls them with the session
 * (a game keeps its own state on it with {@link ArenaSession#attachment}).
 */
public interface GameHooks {
  /** Hooks that do nothing: a plain game that only needs the zone. */
  GameHooks NONE = new GameHooks() {};

  CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

  /** One endpoint the game adds to the panel API (after the bearer token has been checked). */
  interface Route {
    /** Answers the request and returns true, or returns false when the route is not its own. */
    boolean handle(HttpExchange request, String method, String path) throws IOException;

    /** Sends a JSON answer (a route's way of replying, like the engine's own endpoints). */
    static void reply(HttpExchange exchange, int status, Object value) throws IOException {
      byte[] bytes = ArenaManager.JSON.toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.getResponseHeaders().set("Cache-Control", "no-store");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
    }
  }

  // -------------------------------------------------------------- life cycle

  /** What still stops this session from starting, or null. */
  default String canStart(ArenaSession s) {
    return null;
  }

  /** The game is about to be created: reset whatever the previous game left on the session. */
  default void beforeStart(ArenaSession s) {}

  /** The game exists and the clock is about to start. */
  default void onStart(ArenaSession s) {}

  /** The opening presentation, before the first phase timer starts. */
  default CompletableFuture<Void> opening(ArenaSession s) {
    return DONE;
  }

  /** The phase's presentation is over (for the first phase too): its scene begins. */
  default CompletableFuture<Void> entered(ArenaSession s, boolean first) {
    return DONE;
  }

  /**
   * The game moved to a new phase. Called at once (take what the game must hand over now); the
   * returned step plays after the previous presentation and before the phase timer restarts.
   */
  default Supplier<CompletableFuture<Void>> onPhase(ArenaSession s, String previous) {
    return () -> DONE;
  }

  /** A new turn started inside the same phase (the clock restarts afterwards by itself). */
  default void onTurn(ArenaSession s) {}

  /**
   * One tick of the game clock. Returns true when the game moved on by itself (a bot ended the
   * turn): the engine then treats it like a phase change.
   */
  default boolean tickGame(ArenaSession s, long now, boolean due) {
    return false;
  }

  /** Anything still playing for the previous phase must stop: called wherever the phase changes. */
  default void epoch(ArenaSession s) {}

  /** The arena is stopping: wrap the game up (before the players are restored). */
  default CompletableFuture<Void> concluding(ArenaSession s, String reason) {
    return DONE;
  }

  /** The players are restored: give back what the game borrowed (before the arena is free). */
  default CompletableFuture<Void> released(ArenaSession s) {
    return DONE;
  }

  /** The stop is over, with the error that left restoration pending, or null. */
  default void onStop(ArenaSession s, Throwable error) {}

  /** The server is shutting down. */
  default void shutdown(ArenaSession s) {}

  /** Once a second for every session, game or not. */
  default void tick(ArenaSession s) {}

  /** Five times a second for every session. */
  default void fastTick(ArenaSession s) {}

  /** The game's own journal, if it keeps one. */
  default void log(ArenaSession s, String kind, String message) {}

  // ----------------------------------------------------------------- players

  /** A player has just been teleported into the arena (on their own thread). */
  default void onJoin(ArenaSession s, Player p) {}

  /** A player is about to be restored (on their own thread). */
  default void onLeave(ArenaSession s, Player p) {}

  default boolean leaveBlocked(ArenaSession s, UUID id) {
    return false;
  }

  /**
   * A place outside the zone where the game itself keeps this session's players (a stage it built).
   * Moving there is part of the game, not leaving the zone.
   */
  default boolean hosts(ArenaSession s, org.bukkit.Location where) {
    return false;
  }

  /** Whether the game can be played with practice bots. */
  default boolean practice() {
    return false;
  }

  /** Whether the engine announces phase changes and results in the chat. */
  default boolean announcesInChat() {
    return true;
  }

  /** An eliminated player who must still be able to act (a hunter's last shot). */
  default boolean keepsActing(ArenaSession s, UUID id) {
    return false;
  }

  /** False while something covers the screen and no menu may open over it. */
  default boolean menuAllowed(ArenaSession s) {
    return true;
  }

  /** Lets the game take a chat message first; true when it did. */
  default boolean onChat(ArenaSession s, Player p, String message) {
    return false;
  }

  /** Lets the game decide a damage event of an arena player; true when it did. */
  default boolean onDamage(ArenaSession s, Player victim, Player attacker, EntityDamageEvent e) {
    return false;
  }

  /** The player must not move on their own (asleep, pinned to a seat...). */
  default boolean frozen(ArenaSession s, Player p) {
    return false;
  }

  /** The player is held on something (a chair) until the game releases them. */
  default boolean holds(ArenaSession s, Player p) {
    return false;
  }

  /** Commands (lower case, with the slash) a locked player may use besides /arena. */
  default boolean allowsCommand(String command) {
    return false;
  }

  default boolean hunger(ArenaSession s) {
    return false;
  }

  /** Color of a role name in the generic menus (the engine's gold unless the game knows better). */
  default int roleColor(String role) {
    return net.kyori.adventure.text.format.NamedTextColor.nearestTo(
            net.kyori.adventure.text.format.TextColor.color(0xe9d99b))
        .value();
  }

  // ------------------------------------------------------------ compass menu

  /** A game in progress where the player acts on the world itself: the compass opens no menu. */
  default boolean inGame(ArenaSession s) {
    return false;
  }

  /** Opens the game's own menu for the player; false to let the generic compass menu open. */
  default boolean openMenu(CompassGUI gui, Player p, ArenaSession s, boolean skinned) {
    return false;
  }

  /** Redraws or replaces a game menu that is open; false when the window is not the game's own. */
  default boolean refreshMenu(CompassGUI gui, Player p, ArenaSession s, CompassMenuHolder holder) {
    return false;
  }

  /** A click on one of the game's own menu actions; true when it was handled. */
  default boolean menuClick(
      CompassGUI gui, Player p, ArenaSession s, CompassMenuHolder holder, String click) {
    return false;
  }

  /** A game menu was closed (not by a disconnection). */
  default void menuClosed(CompassGUI gui, Player p, ArenaSession s, InventoryCloseEvent e) {}

  /** A private notice of the game for one player. */
  default void notice(Player target, String message, ArenaSession s) {
    target.sendMessage(net.kyori.adventure.text.Component.text(message));
  }

  // ------------------------------------------------------------------- admin

  /** Extra entries of the admin setup menu. */
  default void setupMenu(
      ArenaAdmin admin, ArenaAdmin.Menu menu, Player p, ArenaConfig c) {}

  /** Numeric options shown (and edited, by key) in the admin setup menu after the generic ones. */
  default Map<String, Integer> numbers(ArenaConfig c) {
    return Map.of();
  }

  /** A hotbar tool of the admin kit the game adds (shown between the spawn and status tools). */
  record Tool(String id, Material material, String title, String... lore) {}

  default List<Tool> tools() {
    return List.of();
  }

  /** The game's tool was used; true when it was one of its own. */
  default boolean useTool(
      ArenaTools tools, Player p, String tool, boolean left, boolean sneak, Block block)
      throws Exception {
    return false;
  }

  /** Adds the game's lines to the readiness checklist; returns what stops a start, or null. */
  default String report(ArenaTools tools, Player p, ArenaConfig c, List<String> lines) {
    return null;
  }

  /** Runs after the checklist (asynchronous checks). */
  default void reportMore(ArenaTools tools, Player p, ArenaConfig c) {}

  /** Particles for the game's configured points while an admin holds a tool. */
  default void draw(ArenaTools tools, Player p, ArenaConfig c) {}

  // ------------------------------------------------------------------- panel

  /** Extra fields of this session's row in the panel state. */
  default Map<String, Object> stateForPanel(ArenaSession s) {
    return Map.of();
  }

  /** Endpoints the game adds to the panel API. */
  default List<Route> extraRoutes() {
    return List.of();
  }
}
