package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The tactical map: an ability that is placed at a distance (see {@link TacTargeting}) opens this chest window instead of throwing. Its
 * background is the plan of the map (pack glyphs, {@link VbMenuSkin#tacMap}); over it a 5 x 5 grid of invisible items picks a zone, the
 * window then zooms into that zone (another glyph) with a finer 5 x 5 grid for the exact spot ({@link TacGrid}). Teammates show as
 * heads, the player's own smokes as rings, the spots already picked as crosses. The charge is spent only when the last spot is picked:
 * closing the window (Esc) keeps it. One session per player; a window that reopens for the next step is not a cancel.
 */
public final class TacMapService implements Listener {
  private static final double REACH_CELLS = 1.0;

  /** One player's targeting: the ability, the spots picked so far and where the window is zoomed. */
  static final class Session {
    final UUID id;
    final VbMatch match;
    final VbMapService.Stage stage;
    final int slot;
    final VbAbility ability;
    final int total;
    final List<Location> spots = new ArrayList<>();
    final List<double[]> picks = new ArrayList<>(); // the same spots as {x, z} from the map corner (for the markers)
    int zone = TacGrid.OVERVIEW;
    boolean reopening, done;

    Session(UUID id, VbMatch match, VbMapService.Stage stage, int slot, VbAbility ability) {
      this.id = id;
      this.match = match;
      this.stage = stage;
      this.slot = slot;
      this.ability = ability;
      this.total = Math.max(1, TacTargeting.spots(ability));
    }
  }

  /** The window's holder: how a click is known for ours. */
  static final class Menu implements InventoryHolder {
    final Session session;
    Inventory inventory;

    Menu(Session session) {
      this.session = session;
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }

  private final NexusArenaEngine plugin;
  private final VbAgentService agents;
  private final Map<UUID, Session> sessions = new HashMap<>();

  TacMapService(NexusArenaEngine plugin, VbAgentService agents) {
    this.plugin = plugin;
    this.agents = agents;
  }

  void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::watch, 10, 5);
  }

  static boolean isMenu(Inventory inv) {
    return inv != null && inv.getHolder(false) instanceof Menu;
  }

  boolean open(UUID id) {
    return sessions.containsKey(id);
  }

  /** Opens the window for an ability use. False when it cannot (no built map, the pack is not loaded): the ability then aims or throws as it always did. */
  boolean open(Player p, VbMatch m, int slot, VbAbility ab) {
    VbMapService maps = plugin.valoblock.map instanceof VbMapService s ? s : null;
    VbMapService.Stage stage = maps == null ? null : maps.stageOf(m);
    VbPack pack = plugin.valoblock.pack;
    if (stage == null || TacGrid.mapIndex(stage.map().id()) < 0 || (pack != null && !pack.loaded(p.getUniqueId()))) return false;
    Session s = new Session(p.getUniqueId(), m, stage, slot, ab);
    sessions.put(p.getUniqueId(), s);
    p.openInventory(build(p, s));
    p.playSound(p.getLocation(), "item.book.page_turn", SoundCategory.PLAYERS, 0.7f, 1.2f);
    return true;
  }

  // ------------------------------------------------------------------ the window

  private Inventory build(Player p, Session s) {
    Menu holder = new Menu(s);
    AgentKit kit = agents.kit(s.id);
    int charges = kit == null ? 0 : kit.charges(s.ability.slot());
    int map = TacGrid.mapIndex(s.stage.map().id());
    holder.inventory = Bukkit.createInventory(holder, 54, VbMenuSkin.tacMap(map, s.zone, TacTargeting.title(s.ability, s.spots.size(), charges)));
    fill(holder.inventory, p, s, kit);
    return holder.inventory;
  }

  private static ItemStack tag(ItemStack it, String action) {
    ItemMeta meta = it.getItemMeta();
    meta.getPersistentDataContainer().set(AgentItems.ACTION, PersistentDataType.STRING, action);
    it.setItemMeta(meta);
    return it;
  }

  private static ItemStack cell(String sprite, String action, AgentText.Line... lines) {
    return AgentItems.cell(sprite, List.of(lines), action, false);
  }

  private void fill(Inventory inv, Player p, Session s, AgentKit kit) {
    VbMap map = s.stage.map();
    double ox = s.stage.ox();
    double px = p.getLocation().getX() - ox, pz = p.getLocation().getZ();
    double range = TacTargeting.range(s.ability);
    boolean zoom = s.zone != TacGrid.OVERVIEW;
    for (int gy = 0; gy < TacGrid.GRID; gy++)
      for (int gx = 0; gx < TacGrid.GRID; gx++) {
        String action = "cell:" + gx + "," + gy;
        ItemStack it;
        if (!zoom) {
          int zone = TacGrid.zone(gx, gy);
          boolean ok = TacGrid.zoneReachable(map, zone, px, pz, range);
          List<AgentText.Line> lines = new ArrayList<>();
          lines.add(new AgentText.Line(ok ? "Zoom in here" : "Out of reach", ok ? AgentText.GOLD : AgentText.GRAY));
          for (String f : TacGrid.features(map, zone)) lines.add(new AgentText.Line(f, AgentText.GRAY));
          it = ok ? AgentItems.cell("blank", lines, action, false) : AgentItems.cell("tac_far", lines, "none", false);
        } else {
          double[] pick = TacGrid.pick(map, s.zone, gx, gy, REACH_CELLS);
          if (pick == null) it = cell("tac_far", "none", new AgentText.Line("Nothing to stand on", AgentText.GRAY));
          else if (!TacTargeting.inRange(range, px, pz, pick[0], pick[1])) it = cell("tac_far", "none", new AgentText.Line("Out of range", AgentText.RED), new AgentText.Line("At most " + (int) range + " blocks from you", AgentText.GRAY));
          else it = cell("blank", action, new AgentText.Line("Place here", AgentText.GOLD));
        }
        inv.setItem(TacGrid.slot(gx, gy), it);
      }
    markers(inv, p, s, zoom);
    // controls
    inv.setItem(TacGrid.BACK, zoom ? cell("tac_dim", "back", new AgentText.Line("Back to the whole map", AgentText.GOLD))
        : s.spots.isEmpty() ? null
        : cell("tac_dim", "back", new AgentText.Line("Undo the last " + TacTargeting.of(s.ability.id()).noun(), AgentText.GOLD)));
    if (kit != null) inv.setItem(TacGrid.INFO, AgentItems.ability(kit.agent(), s.ability, kit));
    inv.setItem(TacGrid.CANCEL, cell("tac_dim", "cancel", new AgentText.Line("Cancel", AgentText.RED), new AgentText.Line("Esc does the same: no charge is spent.", AgentText.GRAY)));
  }

  /** Teammates (heads, on the plan and in the left column), the player's own placed smokes and the spots picked so far. */
  private void markers(Inventory inv, Player p, Session s, boolean zoom) {
    VbMap map = s.stage.map();
    double ox = s.stage.ox();
    for (AbilityEnv.Placed placed : agents.env().placedBy(s.id)) {
      int[] g = TacGrid.cellOf(map, s.zone, placed.where().getX() - ox, placed.where().getZ());
      if (g != null) inv.setItem(TacGrid.slot(g[0], g[1]), cell("tac_mark", "cell:" + g[0] + "," + g[1], new AgentText.Line("Your " + abilityName(s, placed.ability()), AgentText.GOLD), new AgentText.Line("Still in play", AgentText.GRAY)));
    }
    for (int i = 0; i < s.picks.size(); i++) {
      double[] pk = s.picks.get(i);
      int[] g = TacGrid.cellOf(map, s.zone, pk[0], pk[1]);
      String what = TacTargeting.of(s.ability.id()).noun();
      if (g != null) inv.setItem(TacGrid.slot(g[0], g[1]), cell("tac_pick", "cell:" + g[0] + "," + g[1], new AgentText.Line(Character.toUpperCase(what.charAt(0)) + what.substring(1) + " " + (i + 1), AgentText.GOLD)));
    }
    int row = 0;
    for (UUID id : s.match.squad(s.match.squadOf(s.id))) {
      if (id.equals(s.id) || row >= 4) continue;
      boolean alive = s.match.alive().contains(id), bot = s.match.isBot(id);
      Player mate = bot ? null : Bukkit.getPlayer(id);
      String name = bot ? plugin.valoblock.bots.name(id) : mate == null ? "Teammate" : mate.getName();
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta) head.getItemMeta();
      if (mate != null) meta.setOwningPlayer(mate);
      meta.displayName(WeaponItems.text(name, alive ? "#eef0f4" : AgentText.RED));
      meta.lore(List.of(WeaponItems.text(alive ? "Alive" : "Down", alive ? AgentText.GREEN : AgentText.RED)));
      head.setItemMeta(meta);
      inv.setItem(TacGrid.teamSlot(row++), tag(head, "none"));
      if (!alive) continue;
      double[] at = null;
      if (bot) {
        double[] b = plugin.valoblock.bots.position(id);
        if (b != null) at = new double[] {b[0] - ox, b[2]};
      } else if (mate != null) at = new double[] {mate.getLocation().getX() - ox, mate.getLocation().getZ()};
      int[] g = at == null ? null : TacGrid.cellOf(map, s.zone, at[0], at[1]);
      if (g != null) {
        ItemStack onMap = head.clone();
        inv.setItem(TacGrid.slot(g[0], g[1]), tag(onMap, "cell:" + g[0] + "," + g[1]));
      }
    }
  }

  private static String abilityName(Session s, String abilityId) {
    return abilityId.equals(s.ability.id()) ? s.ability.name() : abilityId.replace('_', ' ');
  }

  // ------------------------------------------------------------------ clicks

  @EventHandler(priority = EventPriority.HIGH)
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
    e.setCancelled(true);
    Session s = menu.session;
    if (sessions.get(s.id) != s || e.getClickedInventory() != e.getView().getTopInventory()) return;
    String action = AgentItems.action(e.getCurrentItem());
    if (action == null || action.equals("none")) return;
    switch (action) {
      case "cancel" -> p.closeInventory();
      case "back" -> {
        if (s.zone != TacGrid.OVERVIEW) s.zone = TacGrid.OVERVIEW;
        else if (!s.spots.isEmpty()) {
          s.spots.remove(s.spots.size() - 1);
          s.picks.remove(s.picks.size() - 1);
        }
        reopen(p, s);
      }
      default -> {
        if (!action.startsWith("cell:")) return;
        String[] xy = action.substring(5).split(",");
        cellClicked(p, s, Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void drag(InventoryDragEvent e) {
    if (isMenu(e.getView().getTopInventory())) e.setCancelled(true);
  }

  private void cellClicked(Player p, Session s, int gx, int gy) {
    VbMap map = s.stage.map();
    double px = p.getLocation().getX() - s.stage.ox(), pz = p.getLocation().getZ();
    double range = TacTargeting.range(s.ability);
    if (s.zone == TacGrid.OVERVIEW) { // step one: the zone
      int zone = TacGrid.zone(gx, gy);
      if (!TacGrid.zoneReachable(map, zone, px, pz, range)) {
        deny(p, "Nothing to reach there");
        return;
      }
      s.zone = zone;
      p.playSound(p.getLocation(), "ui.button.click", SoundCategory.PLAYERS, 0.5f, 1.4f);
      reopen(p, s);
      return;
    }
    double[] pick = TacGrid.pick(map, s.zone, gx, gy, REACH_CELLS); // step two: the exact spot
    if (pick == null) {
        deny(p, "Nothing to stand on there");
        return;
      }
    if (!TacTargeting.inRange(range, px, pz, pick[0], pick[1])) {
        deny(p, "Out of range");
        return;
      }
    s.spots.add(spotLocation(s.stage, pick));
    s.picks.add(new double[] {pick[0], pick[1]});
    s.zone = TacGrid.OVERVIEW;
    p.playSound(p.getLocation(), "block.note_block.chime", SoundCategory.PLAYERS, 0.6f, 1.5f);
    if (s.spots.size() < s.total) {
      reopen(p, s);
      return;
    }
    s.done = true;
    Bukkit.getScheduler().runTask(plugin, () -> {
      sessions.remove(s.id);
      if (p.isOnline() && isMenu(p.getOpenInventory().getTopInventory())) p.closeInventory();
      agents.useAt(p, s.match, s.slot, List.copyOf(s.spots));
    });
  }

  private void deny(Player p, String text) {
    plugin.valoblock.hud.notice(p, Component.text(text, NamedTextColor.RED));
    p.playSound(p.getLocation(), "block.note_block.bass", SoundCategory.PLAYERS, 0.6f, 0.7f);
  }

  /** The place in the world of a picked spot: on the floor (or the high ground, the canopy, the island) of that part of the plan. */
  private Location spotLocation(VbMapService.Stage st, double[] pick) {
    World w = plugin.valoblock.map instanceof VbMapService ms ? ms.world() : Bukkit.getWorlds().get(0);
    double x = st.ox() + pick[0], z = pick[1], y = VbMapService.FEET_Y + pick[2];
    RayTraceResult r = w.rayTraceBlocks(new Location(w, x, y + 1.5, z), new Vector(0, -1, 0), 4, FluidCollisionMode.NEVER, true);
    double ground = r != null && Math.abs(r.getHitPosition().getY() - y) <= 1.3 ? r.getHitPosition().getY() : y; // what sits above the deck is not the floor
    return new Location(w, x, ground + 0.05, z);
  }

  private void reopen(Player p, Session s) {
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (sessions.get(s.id) != s || !p.isOnline()) return;
      s.reopening = true;
      try {
        p.openInventory(build(p, s));
      } finally {
        s.reopening = false;
      }
    });
  }

  // ------------------------------------------------------------------ closing

  @EventHandler
  public void close(InventoryCloseEvent e) {
    if (!(e.getInventory().getHolder(false) instanceof Menu menu) || menu.session.reopening || menu.session.done) return;
    sessions.remove(menu.session.id, menu.session); // Esc: nothing was spent
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    sessions.remove(e.getPlayer().getUniqueId());
  }

  /** Every quarter second: a window whose player died, left the match or whose fight is over is closed (the charge was never spent). */
  private void watch() {
    for (Session s : new ArrayList<>(sessions.values())) {
      Player p = Bukkit.getPlayer(s.id);
      ArenaSession as = p == null ? null : plugin.arenas.session(s.id);
      VbPhase phase = s.match.vbPhase();
      boolean fighting = (phase == VbPhase.COMBAT || phase == VbPhase.PLANTED) && s.match.alive().contains(s.id) && as != null && as.game == s.match;
      if (fighting && p != null) continue;
      sessions.remove(s.id);
      if (p != null && isMenu(p.getOpenInventory().getTopInventory())) p.closeInventory();
    }
  }
}
