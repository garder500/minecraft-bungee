package win.vampyuyu.werewolf;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import win.vampyuyu.arena.*;
import win.vampyuyu.arena.ArenaAdmin;
import win.vampyuyu.arena.ArenaConfig;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.ArenaTools;
import win.vampyuyu.arena.CompassGUI;
import win.vampyuyu.arena.CompassMenuHolder;
import win.vampyuyu.arena.GameHooks;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;
import win.vampyuyu.werewolf.sky.PlatformHandle;

/** What Werewolf adds around the arena life cycle: scenery, the sky platform, bots, menus and the journal. */
public final class WerewolfHooks implements GameHooks {
  private final NexusArenaEngine plugin;
  private final WerewolfModule module;

  WerewolfHooks(NexusArenaEngine plugin, WerewolfModule module) {
    this.plugin = plugin;
    this.module = module;
  }

  // ------------------------------------------------------------- life cycle

  @Override
  public String canStart(ArenaSession s) {
    return module.stage.problem(s.players.size());
  }

  @Override
  public void beforeStart(ArenaSession s) {
    WerewolfSession w = WerewolfSession.of(s);
    w.ambienceEpoch++;
    w.ambienceNight = false;
    w.ambiencePhase = "";
    w.revealed.clear();
    w.ambienceFinale = false;
    w.seats.clear();
    w.placed.clear();
    // A platform whose stop never finished must not keep its slot for ever.
    PlatformHandle leftover = w.platform;
    w.platform = null;
    if (leftover != null) module.stage.close(leftover);
  }

  @Override
  public void onStart(ArenaSession s) {
    WerewolfSession w = WerewolfSession.of(s);
    w.botBrain = s.bots.isEmpty() ? null : new WerewolfBots(new Random());
    w.observedTurn = s.game instanceof WerewolfGame game ? game.turnKey() : "";
    w.journal.close();
    w.journal = WerewolfJournal.SILENT;
    if (s.game instanceof WerewolfGame wolf && module.logs != null) {
      w.journal = module.logs.open(s.config.id(), s.eventId, Map.copyOf(s.players));
      w.journal.log(
          "START",
          s.players.size()
              + " players ("
              + s.bots.size()
              + " bots) · time limit "
              + s.config.durationSeconds()
              + "s"
              + (s.bots.isEmpty() ? "" : " · practice: every phase capped at 15s"));
      wolf.journal(w.journal);
    }
  }

  @Override
  public CompletableFuture<Void> opening(ArenaSession s) {
    // The platform is built first; nobody is moved before it stands.
    long began = System.currentTimeMillis();
    return module
        .stage
        .open(
            s.players.size(),
            platform -> WerewolfSession.of(s).platform = platform,
            fraction -> module.ambience.building(s, fraction, began))
        .thenCompose(platform -> module.places.prepare(s, platform))
        .thenCompose(v -> module.places.present(s, "WAITING"));
  }

  @Override
  public CompletableFuture<Void> entered(ArenaSession s, boolean first) {
    return module.ambience.enter(s, first);
  }

  @Override
  public Supplier<CompletableFuture<Void>> onPhase(ArenaSession s, String previous) {
    WerewolfSession w = WerewolfSession.of(s);
    if (s.game instanceof WerewolfGame game) w.observedTurn = game.turnKey();
    // Taken now: by the time the presentation reaches it, the game may have moved on.
    WerewolfGame.Verdict verdict = s.game instanceof WerewolfGame game ? game.takeVerdict() : null;
    if (verdict != null) w.verdictShowing = true;
    return () ->
        module
            .ambience
            .verdict(s, verdict)
            .whenComplete((v, e) -> w.verdictShowing = false)
            .thenCompose(v -> module.places.present(s, previous));
  }

  @Override
  public void onTurn(ArenaSession s) {
    WerewolfSession w = WerewolfSession.of(s);
    if (!(s.game instanceof WerewolfGame game) || game.turnKey().equals(w.observedTurn)) return;
    w.observedTurn = game.turnKey();
    s.phaseEndsAt = System.currentTimeMillis() + plugin.arenas.phaseMillis(s);
    // The troublemaker's second vote opens in the same phase: the first verdict plays first.
    WerewolfGame.Verdict again = game.takeVerdict();
    if (again != null) {
      w.verdictShowing = true;
      s.presentation =
          s.presentation
              .thenCompose(v -> module.ambience.verdict(s, again))
              .whenComplete(
                  (v, e) -> {
                    w.verdictShowing = false;
                    if (s.game != null)
                      s.phaseEndsAt = System.currentTimeMillis() + plugin.arenas.phaseMillis(s);
                  });
    }
  }

  /**
   * Lets the practice bots act. Their clicks go through the game like anyone's, so the game's
   * journal already records each of them with the game's answer or refusal.
   */
  @Override
  public boolean tickGame(ArenaSession s, long now, boolean due) {
    WerewolfSession w = WerewolfSession.of(s);
    if (!(s.game instanceof WerewolfGame werewolf) || w.botBrain == null) return false;
    String before = werewolf.turnKey();
    w.botBrain.think(werewolf, Set.copyOf(s.bots), now, s.phaseEndsAt, due);
    if (plugin.gui != null) plugin.gui.deliverNotices(s); // a nomination by a bot, a thumb...
    // A bot ended the turn (the seer's vision, the pack agreeing, the witch going to sleep...).
    return !werewolf.turnKey().equals(before);
  }

  @Override
  public void epoch(ArenaSession s) {
    WerewolfSession.of(s).ambienceEpoch++;
  }

  @Override
  public CompletableFuture<Void> concluding(ArenaSession s, String reason) {
    return module.ambience.conclude(s, reason);
  }

  @Override
  public CompletableFuture<Void> released(ArenaSession s) {
    WerewolfSession w = WerewolfSession.of(s);
    // The players are back: only now the platform under them can go and its slot be reused.
    return module
        .places
        .restore(s)
        .handle((v, e) -> e)
        .thenCompose(
            error -> {
              PlatformHandle platform = w.platform;
              w.platform = null;
              return module
                  .stage
                  .close(platform)
                  .handle(
                      (v, e) -> {
                        // A platform that cannot be cleared keeps its slot; it never fails the stop.
                        if (e != null)
                          w.journal.log(
                              "ERROR", "Platform not cleared: " + NexusArenaEngine.reason(e));
                        if (error != null) throw new java.util.concurrent.CompletionException(error);
                        return (Void) null;
                      });
            });
  }

  @Override
  public boolean hosts(ArenaSession s, org.bukkit.Location where) {
    PlatformHandle platform = WerewolfSession.of(s).platform;
    return platform != null && platform.covers(where);
  }

  @Override
  public void onStop(ArenaSession s, Throwable error) {
    WerewolfJournal journal = WerewolfSession.of(s).journal;
    journal.log(
        "STOP",
        error == null
            ? "Players restored, the arena is free"
            : "Restoration pending: " + NexusArenaEngine.reason(error));
    journal.close();
  }

  @Override
  public void shutdown(ArenaSession s) {
    WerewolfSession w = WerewolfSession.of(s);
    w.ambienceEpoch++;
    w.journal.log("STOP", "The server is shutting down");
    w.journal.close();
  }

  @Override
  public void tick(ArenaSession s) {
    module.ambience.tick(s);
  }

  @Override
  public void fastTick(ArenaSession s) {
    if (s.game instanceof WerewolfGame)
      for (UUID id : s.players.keySet()) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) continue;
        plugin.onPlayer(
            p,
            () -> {
              module.controls.sync(p, s);
              module.ambience.aim(s, p);
              return null;
            });
      }
    module.votes.tick(s);
  }

  @Override
  public void log(ArenaSession s, String kind, String message) {
    WerewolfSession.of(s).journal.log(kind, message);
  }

  // ----------------------------------------------------------------- players

  @Override
  public void onLeave(ArenaSession s, Player p) {
    module.ambience.clearNow(p, s);
  }

  @Override
  public boolean leaveBlocked(ArenaSession s, UUID id) {
    return s.game instanceof WerewolfGame game
        && game.winner().isEmpty()
        && game.alive().contains(id);
  }

  @Override
  public boolean practice() {
    return true;
  }

  @Override
  public boolean announcesInChat() {
    return false;
  }

  /** A pending hunter must still be able to click the shot menu. */
  @Override
  public boolean keepsActing(ArenaSession s, UUID id) {
    return s.game instanceof WerewolfGame wolf && wolf.actors().contains(id);
  }

  @Override
  public boolean menuAllowed(ArenaSession s) {
    return !WerewolfSession.of(s).verdictShowing;
  }

  @Override
  public boolean onChat(ArenaSession s, Player p, String message) {
    if (!(s.game instanceof WerewolfGame wolf)) return false;
    UUID from = p.getUniqueId();
    boolean alive = wolf.alive().contains(from);
    if (wolf.chat(from, message)) return true; // A role took the message (a ghost's letter).
    if (alive && wolf.silenced(from)) {
      plugin.message(p, "You cannot speak today.");
      return true;
    }
    if (wolf.night() && alive) {
      plugin.message(p, "The village is asleep. Your menu opens when your role wakes up.");
      return true;
    }
    return false;
  }

  /** Nobody dies of damage in a village game: the votes decide. */
  @Override
  public boolean onDamage(ArenaSession s, Player victim, Player attacker, EntityDamageEvent e) {
    e.setCancelled(true);
    return true;
  }

  @Override
  public boolean frozen(ArenaSession s, Player p) {
    return module.places.sleeping(p) || module.places.pinned(p);
  }

  @Override
  public boolean holds(ArenaSession s, Player p) {
    return module.places.seated(p);
  }

  @Override
  public boolean allowsCommand(String command) {
    return command.startsWith("/ww vote ")
        || command.startsWith("/ww hud")
        || command.equals("/ww abstain");
  }

  @Override
  public int roleColor(String role) {
    return (switch (role) {
          case "WEREWOLF", "ALPHA_WOLF", "INFECTOR", "WOLF_CUB" -> NamedTextColor.RED;
          case "SEER" -> NamedTextColor.LIGHT_PURPLE;
          case "GUARD" -> NamedTextColor.AQUA;
          case "WITCH" -> NamedTextColor.DARK_PURPLE;
          case "CUPID" -> NamedTextColor.LIGHT_PURPLE;
          case "HUNTER" -> NamedTextColor.DARK_GREEN;
          case "VILLAGER", "Not assigned yet" -> NamedTextColor.WHITE;
          default -> NamedTextColor.nearestTo(TextColor.color(WerewolfPresentation.color(role)));
        })
        .value();
  }

  // ------------------------------------------------------------ compass menu

  @Override
  public boolean inGame(ArenaSession s) {
    return WerewolfCompass.inGame(s);
  }

  @Override
  public boolean openMenu(CompassGUI gui, Player p, ArenaSession s, boolean skinned) {
    return module.compass.open(gui, p, s, skinned);
  }

  @Override
  public boolean refreshMenu(
      CompassGUI gui, Player p, ArenaSession s, CompassMenuHolder holder) {
    return module.compass.refresh(gui, p, s, holder);
  }

  @Override
  public boolean menuClick(
      CompassGUI gui, Player p, ArenaSession s, CompassMenuHolder holder, String click) {
    return module.compass.click(gui, p, s, click);
  }

  @Override
  public void menuClosed(CompassGUI gui, Player p, ArenaSession s, InventoryCloseEvent e) {
    module.compass.closed(gui, p, s, e);
  }

  @Override
  public void notice(Player target, String message, ArenaSession s) {
    module.compass.notice(target, message);
  }

  // ------------------------------------------------------------------- admin

  @Override
  public void setupMenu(ArenaAdmin admin, ArenaAdmin.Menu menu, Player p, ArenaConfig c) {
    module.tools.setupMenu(admin, menu, p, c);
  }

  @Override
  public Map<String, Integer> numbers(ArenaConfig c) {
    return module.tools.numbers(c);
  }

  @Override
  public List<Tool> tools() {
    return module.tools.tools();
  }

  @Override
  public boolean useTool(
      ArenaTools tools, Player p, String tool, boolean left, boolean sneak, Block block)
      throws Exception {
    return module.tools.use(tools, p, tool, left, sneak, block);
  }

  @Override
  public String report(ArenaTools tools, Player p, ArenaConfig c, List<String> lines) {
    return module.tools.report(tools, c, lines);
  }

  @Override
  public void reportMore(ArenaTools tools, Player p, ArenaConfig c) {
    module.tools.reportMore(p, c);
  }

  @Override
  public void draw(ArenaTools tools, Player p, ArenaConfig c) {
    module.tools.draw(tools, p, c);
  }

  // ------------------------------------------------------------------- panel

  /** The play-by-play logs of the games, listed and downloaded by the panel. */
  @Override
  public List<Route> extraRoutes() {
    return List.of(this::logs);
  }

  private boolean logs(HttpExchange request, String method, String path) throws IOException {
    if (!method.equals("GET")) return false;
    if (path.equals("/arenas/logs")) {
      Route.reply(request, 200, Map.of("logs", module.logs.list()));
      return true;
    }
    if (!path.startsWith("/arenas/logs/")) return false;
    java.nio.file.Path file;
    try {
      file = module.logs.file(path.substring("/arenas/logs/".length()));
    } catch (NoSuchElementException missing) {
      Route.reply(request, 404, Map.of("error", missing.getMessage()));
      return true;
    }
    // A running game's journal is sent as far as it is written.
    byte[] text = java.nio.file.Files.readAllBytes(file);
    request.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    request.getResponseHeaders().set("Cache-Control", "no-store");
    request
        .getResponseHeaders()
        .set("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"");
    request.sendResponseHeaders(200, text.length);
    request.getResponseBody().write(text);
    return true;
  }
}
