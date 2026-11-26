package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The minimap of the glyph HUD (top-left corner): what {@link VbHudService} draws over the map's framed plan for one player, as
 * {@link MiniMapMath.Mark}s: you and where you look, living teammates (crossed where they fell when dead), the Seed (planted, or dropped
 * for the Seedbearers) and enemies only while a reveal shows them to you. Called on the server thread by the HUD frame.
 *
 * <p>Before 2026-11-26 the minimap was a filled map held in the off hand; a stack of it left in an off hand (tagged {@link #TAG}) is
 * taken away the next time the HUD draws for that player.
 */
public final class MiniMapService implements Listener {
  static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "vb_minimap");

  private final NexusArenaEngine plugin;
  private final Map<UUID, double[]> lastSeen = new HashMap<>();

  MiniMapService(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  void shutdown() {
    lastSeen.clear();
  }

  static boolean isMinimap(ItemStack it) {
    return it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
  }

  /** The map this match is played on and its slot offset, or null (no map service, no stage yet). */
  private VbMapService.Stage stage(VbMatch m) {
    return plugin.valoblock.map instanceof VbMapService s ? s.stageOf(m) : null;
  }

  /** The panel sprite of the match's map, or null (no stage: no minimap). */
  String base(VbMatch m) {
    VbMapService.Stage st = stage(m);
    return st == null ? null : MiniMapMath.baseSprite(st.map());
  }

  /** The marker glyphs this player's minimap shows now (empty without a stage). */
  List<MiniMapMath.Mark> marks(Player p, VbMatch m) {
    if (isMinimap(p.getInventory().getItemInOffHand())) p.getInventory().setItemInOffHand(null); // the old held minimap
    VbMapService.Stage st = stage(m);
    if (st == null) return List.of();
    return MiniMapMath.marks(st.map(), markers(p, m, st));
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

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    lastSeen.remove(e.getPlayer().getUniqueId());
  }
}
