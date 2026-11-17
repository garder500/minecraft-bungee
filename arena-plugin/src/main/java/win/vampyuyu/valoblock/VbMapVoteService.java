package win.vampyuyu.valoblock;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The map vote of a ValoBlock lobby: one {@link MapVote} ballot per arena session (a session attachment), opened while humans wait,
 * voted from the {@link MapVoteMenu} window, resolved by {@link VbMapService#prepare} when the match starts. Bots never vote (only
 * humans get the window). The window is opened by {@link VbMenus} (the same rules as the other menus) or by /vb vote.
 */
final class VbMapVoteService implements Listener {
  private final NexusArenaEngine plugin;
  private final Random random = new Random();

  VbMapVoteService(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
  }

  MapVote ballot(ArenaSession s) {
    return s.attachment(MapVote.class, () -> new MapVote(VbData.MAP_IDS));
  }

  /** A ValoBlock arena whose lobby is waiting for the match (no game, not stopping). */
  static boolean waiting(ArenaSession s) {
    return s != null && s.config.preset().equals(VbPreset.ID) && s.game == null && !s.stopping && !s.finishing;
  }

  static boolean human(ArenaSession s, UUID id) {
    return !s.bots.contains(id);
  }

  /** Opens the ballot of a waiting lobby that has a human in it (a new ballot per lobby cycle). */
  void ensureOpen(ArenaSession s) {
    MapVote b = ballot(s);
    if (!b.isOpen()) b.open(System.currentTimeMillis());
  }

  /** The player's vote window now. Returns why not, or null when opened. */
  String open(Player p, ArenaSession s) {
    if (!waiting(s) || !ballot(s).isOpen()) return "No map vote is open (it opens while the lobby waits for the match)";
    p.openInventory(MapVoteMenu.build(p, s, ballot(s), System.currentTimeMillis()));
    return null;
  }

  /** Once a second: an empty lobby drops its ballot; the windows that show a stale clock are redrawn. */
  private void tick() {
    long now = System.currentTimeMillis();
    for (ArenaSession s : plugin.arenas.all()) {
      if (!s.config.preset().equals(VbPreset.ID)) continue;
      MapVote b = ballot(s);
      if (!b.isOpen()) continue;
      if (!waiting(s) || s.players.keySet().stream().noneMatch(id -> human(s, id))) {
        b.close();
        continue;
      }
      int left = b.secondsLeft(now);
      for (UUID id : s.players.keySet()) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !(p.getOpenInventory().getTopInventory().getHolder(false) instanceof MapVoteMenu menu) || menu.session != s) continue;
        if (menu.shown - left >= 5 || (left == 0 && menu.shown > 0)) p.openInventory(MapVoteMenu.build(p, s, b, now));
      }
    }
  }

  /** Redraws the vote window of everyone who has it open in this session (a vote changed the counts). */
  private void redrawAll(ArenaSession s) {
    MapVote b = ballot(s);
    long now = System.currentTimeMillis();
    for (UUID id : s.players.keySet()) {
      Player p = Bukkit.getPlayer(id);
      if (p != null && p.getOpenInventory().getTopInventory().getHolder(false) instanceof MapVoteMenu menu && menu.session == s)
        p.openInventory(MapVoteMenu.build(p, s, b, now));
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder(false) instanceof MapVoteMenu menu)) return;
    e.setCancelled(true);
    if (e.getClickedInventory() != e.getView().getTopInventory()) return;
    String map = MapVoteMenu.voteOf(e.getCurrentItem());
    if (map == null) return;
    ArenaSession s = menu.session;
    if (!waiting(s) || !human(s, p.getUniqueId()) || !ballot(s).cast(p.getUniqueId(), map)) {
      p.closeInventory(); // the match started (or the vote closed) under the window
      return;
    }
    p.playSound(p.getLocation(), "ui.button.click", SoundCategory.PLAYERS, 0.6f, 1.4f);
    Bukkit.getScheduler().runTask(plugin, () -> redrawAll(s));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void drag(InventoryDragEvent e) {
    if (MapVoteMenu.isMenu(e.getView().getTopInventory())) e.setCancelled(true);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    ArenaSession s = plugin.arenas.session(e.getPlayer().getUniqueId());
    if (s != null) ballot(s).retract(e.getPlayer().getUniqueId());
  }

  /** A player left the arena: their vote goes with them. */
  void left(ArenaSession s, Player p) {
    ballot(s).retract(p.getUniqueId());
    if (MapVoteMenu.isMenu(p.getOpenInventory().getTopInventory())) p.closeInventory();
  }

  /** The match starts: the votes decide (an admin's forced map wins), the ballot closes. */
  MapVote.Result resolve(ArenaSession s, String forced) {
    MapVote b = ballot(s);
    MapVote.Result r = b.resolve(random, forced);
    b.close();
    return r;
  }

  /** The match is over or the arena stopped: the next lobby cycle gets a fresh ballot. */
  void release(ArenaSession s) {
    ballot(s).close();
  }

  /** The chosen map, as a title for every human (server thread). */
  void announce(ArenaSession s, VbMap map, MapVote.Result r) {
    String how = switch (r.how()) {
      case FORCED -> "Chosen by an administrator";
      case VOTES -> "Won the vote with " + r.votes() + (r.votes() == 1 ? " vote" : " votes");
      case TIE -> "Drawn from a tie at " + r.votes() + (r.votes() == 1 ? " vote" : " votes");
      case RANDOM -> "Nobody voted: picked at random";
    };
    Title title = Title.title(
        Component.text(map.name(), TextColor.fromHexString(MapVoteMenu.REALM.getOrDefault(map.id(), AgentText.GOLD))),
        Component.text(map.realm() + " · " + how, TextColor.fromHexString(AgentText.GRAY)));
    for (UUID id : List.copyOf(s.players.keySet())) {
      Player p = Bukkit.getPlayer(id);
      if (p != null && human(s, id)) p.showTitle(title);
    }
  }
}
