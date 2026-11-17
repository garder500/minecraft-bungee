package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The minimap: a vanilla filled map in the off hand of every human in a match, drawn by a server-side {@link MapRenderer} from the plan
 * ({@link MiniMapMath}): walls, sites, you and where you look, living teammates (crossed when dead), the Seed (planted, or dropped for
 * the Seedbearers) and enemies only while a reveal shows them to you. The item is kept in the off hand (put back when something took
 * it, taken away when the player is not in a match) and cannot be moved, dropped or swapped: the swap key still reloads the gun, see
 * VbWeaponService.swap, which cancels the swap itself. One map view per player, reused from match to match (a map id is kept by the world).
 */
public final class MiniMapService implements Listener {
  static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "vb_minimap");
  private static final int OFF_HAND_SLOT = 40;

  /** What one player's map shows: the markers the task computed, drawn once when they changed. */
  private static final class Entry {
    final MapView view;
    VbMap map;
    int[] base;
    volatile List<MiniMapMath.Marker> markers = List.of();
    int drawn = Integer.MIN_VALUE;
    boolean fresh = true;

    Entry(MapView view) {
      this.view = view;
    }
  }

  private final NexusArenaEngine plugin;
  private final Map<UUID, Entry> entries = new HashMap<>();
  private final Map<UUID, MapView> views = new HashMap<>();
  private final Map<UUID, double[]> lastSeen = new HashMap<>();
  private final Map<String, int[]> bases = new HashMap<>();
  private final Map<Integer, Byte> bytes = new HashMap<>();
  private int ticks;

  MiniMapService(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 2);
  }

  /** Plugin disable: the items go. */
  void shutdown() {
    for (Player p : Bukkit.getOnlinePlayers()) take(p);
  }

  static boolean isMinimap(ItemStack it) {
    return it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
  }

  private VbMatch matchOf(UUID id) {
    ArenaSession s = plugin.arenas.session(id);
    return s != null && s.game instanceof VbMatch m ? m : null;
  }

  /** Whether this player gets a minimap: a human who is in the match and has not left it. */
  private boolean eligible(Player p, VbMatch m) {
    UUID id = p.getUniqueId();
    return m != null && p.isOnline() && m.participants().contains(id) && !m.isBot(id) && !m.hasLeft(id);
  }

  // ------------------------------------------------------------------ the task

  private void tick() {
    ticks += 2;
    VbMapService maps = plugin.valoblock.map instanceof VbMapService s ? s : null;
    for (Player p : Bukkit.getOnlinePlayers()) {
      VbMatch m = matchOf(p.getUniqueId());
      if (!eligible(p, m) || maps == null) {
        if (isMinimap(p.getInventory().getItemInOffHand())) take(p);
        entries.remove(p.getUniqueId());
        lastSeen.remove(p.getUniqueId());
        continue;
      }
      VbMapService.Stage stage = maps.stageOf(m);
      if (stage == null) continue;
      Entry e = entry(p, stage.map());
      if (ticks % 10 == 0 && !isMinimap(p.getInventory().getItemInOffHand())) give(p, e);
      e.markers = markers(p, m, stage);
    }
  }

  private Entry entry(Player p, VbMap map) {
    Entry e = entries.get(p.getUniqueId());
    if (e == null) {
      MapView view = views.computeIfAbsent(p.getUniqueId(), k -> Bukkit.createMap(p.getWorld())); // one map id per player for the whole run, not one per match
      for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
      view.setTrackingPosition(false);
      view.setUnlimitedTracking(false);
      e = new Entry(view);
      view.addRenderer(new Renderer(e));
      entries.put(p.getUniqueId(), e);
    }
    if (e.map != map) {
      e.map = map;
      e.base = bases.computeIfAbsent(map.id(), k -> MiniMapMath.base(map));
      e.fresh = true;
    }
    return e;
  }

  /** The markers this player's map shows now, the ones drawn last on top. */
  private List<MiniMapMath.Marker> markers(Player p, VbMatch m, VbMapService.Stage st) {
    UUID me = p.getUniqueId();
    double ox = st.ox();
    List<MiniMapMath.Marker> out = new ArrayList<>();
    // the Seed: planted (everyone), or lying dropped (the Seedbearers, who see where it fell)
    VbSite planted = m.plantedSite();
    if (planted != null) out.add(new MiniMapMath.Marker(MiniMapMath.Kind.SEED, (planted.min()[0] + planted.max()[0]) / 2 - ox, (planted.min()[2] + planted.max()[2]) / 2, 0));
    else if (m.side(me) == VbSide.SEEDBEARERS && plugin.valoblock.seeds != null) {
      Location drop = plugin.valoblock.seeds.droppedAt(m);
      if (drop != null) out.add(new MiniMapMath.Marker(MiniMapMath.Kind.SEED, drop.getX() - ox, drop.getZ(), 0));
    }
    // enemies: only the ones a reveal shows to this player
    if (plugin.valoblock.agents instanceof VbAgentService as)
      for (UUID id : as.env().reveals.visibleTo(me)) {
        double[] at = position(m, id, ox);
        if (at != null && m.alive().contains(id) && m.squadOf(id) != m.squadOf(me)) out.add(new MiniMapMath.Marker(MiniMapMath.Kind.ENEMY, at[0], at[1], 0));
      }
    // the squad: living ones where they are, dead ones crossed where they were last seen
    for (UUID id : m.squad(m.squadOf(me))) {
      if (id.equals(me)) continue;
      boolean alive = m.alive().contains(id);
      double[] at = alive ? position(m, id, ox) : lastSeen.get(id);
      if (at == null) continue;
      if (alive) lastSeen.put(id, at);
      out.add(new MiniMapMath.Marker(alive ? MiniMapMath.Kind.ALLY : MiniMapMath.Kind.DEAD, at[0], at[1], 0));
    }
    Location l = p.getLocation();
    if (m.alive().contains(me)) out.add(new MiniMapMath.Marker(MiniMapMath.Kind.SELF, l.getX() - ox, l.getZ(), l.getYaw()));
    return out;
  }

  /** {x, z} in blocks from the map corner of a living player or bot, or null. */
  private double[] position(VbMatch m, UUID id, double ox) {
    if (m.isBot(id)) {
      double[] b = plugin.valoblock.bots.position(id);
      return b == null ? null : new double[] {b[0] - ox, b[2]};
    }
    Player q = Bukkit.getPlayer(id);
    return q == null || !q.isOnline() ? null : new double[] {q.getLocation().getX() - ox, q.getLocation().getZ()};
  }

  // ------------------------------------------------------------------ the item

  private void give(Player p, Entry e) {
    ItemStack it = new ItemStack(Material.FILLED_MAP);
    MapMeta meta = (MapMeta) it.getItemMeta();
    meta.setMapView(e.view);
    meta.setScaling(false);
    meta.displayName(Component.text("Minimap", NamedTextColor.WHITE).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
    { List<Component> lore = new java.util.ArrayList<>(); TipWrap.add(lore, "You, your squad and the Rift Seed.", AgentText.GRAY); TipWrap.add(lore, "Enemies show while they are revealed.", AgentText.GRAY); meta.lore(lore); }
    meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    meta.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
    it.setItemMeta(meta);
    p.getInventory().setItemInOffHand(it);
    e.fresh = true;
  }

  private void take(Player p) {
    if (isMinimap(p.getInventory().getItemInOffHand())) p.getInventory().setItemInOffHand(null);
  }

  // ------------------------------------------------------------------ the renderer

  /** Draws the entry's markers when they changed (the server calls this every tick the map is held; most calls return at once). */
  private final class Renderer extends MapRenderer {
    private final Entry e;

    Renderer(Entry e) {
      super(false);
      this.e = e;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
      if (e.map == null || e.base == null) return;
      int print = MiniMapMath.fingerprint(e.markers);
      if (!e.fresh && print == e.drawn) return;
      e.fresh = false;
      e.drawn = print;
      int[] px = MiniMapMath.draw(e.map, e.base, e.markers);
      for (int y = 0; y < MiniMapMath.SIZE; y++) for (int x = 0; x < MiniMapMath.SIZE; x++) canvas.setPixel(x, y, colour(px[y * MiniMapMath.SIZE + x]));
    }
  }

  @SuppressWarnings("deprecation")
  private byte colour(int rgb) {
    return bytes.computeIfAbsent(rgb, k -> MapPalette.matchColor(new java.awt.Color(k)));
  }

  // ------------------------------------------------------------------ keeping it in the off hand

  @EventHandler(priority = EventPriority.HIGHEST)
  public void swap(PlayerSwapHandItemsEvent e) {
    if (isMinimap(e.getOffHandItem()) || isMinimap(e.getMainHandItem())) e.setCancelled(true); // the gun still reloads: VbWeaponService reads the event whatever its state
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drop(PlayerDropItemEvent e) {
    if (isMinimap(e.getItemDrop().getItemStack())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || matchOf(p.getUniqueId()) == null) return;
    boolean offHandSlot = e.getClickedInventory() == p.getInventory() && e.getSlot() == OFF_HAND_SLOT;
    boolean swapKey = e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND;
    if (isMinimap(e.getCurrentItem()) || isMinimap(e.getCursor()) || offHandSlot || swapKey) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drag(InventoryDragEvent e) {
    if (e.getWhoClicked() instanceof Player p && matchOf(p.getUniqueId()) != null && isMinimap(e.getOldCursor())) e.setCancelled(true);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    take(e.getPlayer());
    entries.remove(e.getPlayer().getUniqueId());
    lastSeen.remove(e.getPlayer().getUniqueId());
  }
}
