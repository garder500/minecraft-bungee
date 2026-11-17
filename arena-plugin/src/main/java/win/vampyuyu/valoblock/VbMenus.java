package win.vampyuyu.valoblock;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * Opens the agent select and the Armory by themselves (no right-click needed) and closes them when their phase is over. The
 * decisions are {@link MenuPlan}; this only looks at the players every tick and opens a tick later (after the teleport and the
 * loadout of a round, or after a close), at most once per player per tick, so the client does not drop the window.
 */
final class VbMenus implements Listener {
  private final NexusArenaEngine plugin;
  private final VbAgentService agents;
  final MenuPlan plan = new MenuPlan();
  final DoubleTap swapTap = new DoubleTap();
  private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

  VbMenus(NexusArenaEngine plugin, VbAgentService agents) {
    this.plugin = plugin;
    this.agents = agents;
  }

  void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::look, 10, 1);
  }

  private VbMatch matchOf(Player p) {
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    return s != null && s.game instanceof VbMatch m ? m : null;
  }

  private boolean packReady(Player p) {
    VbPack pack = plugin.valoblock.pack;
    return pack == null || pack.loaded(p.getUniqueId());
  }

  /** What this human is in right now, or null when the player gets no automatic menu (not in a match, a bot's seat, left). */
  MenuPlan.View view(Player p, VbMatch m) {
    UUID id = p.getUniqueId();
    if (!p.isOnline() || !m.participants().contains(id) || m.hasLeft(id) || m.isBot(id)) return null;
    Inventory top = p.getOpenInventory().getTopInventory();
    MenuPlan.Menu open = AgentSelectMenu.isMenu(top) ? MenuPlan.Menu.SELECT : ArmoryMenu.isMenu(top) ? MenuPlan.Menu.ARMORY : MenuPlan.Menu.NONE;
    boolean busy = open == MenuPlan.Menu.NONE && top.getType() != InventoryType.CRAFTING;
    return new MenuPlan.View(m.vbPhase(), m.round(), !m.agent(id).isEmpty(), m.alive().contains(id), packReady(p), open, busy);
  }

  private void look() {
    for (Player p : Bukkit.getOnlinePlayers()) {
      VbMatch m = matchOf(p);
      if (m == null) continue;
      MenuPlan.View v = view(p, m);
      if (v == null) continue;
      if (MenuPlan.mustClose(v)) {
        p.closeInventory();
        continue;
      }
      if (plan.decide(p.getUniqueId(), v) != MenuPlan.Menu.NONE) later(p);
    }
  }

  /** Opens on the next tick, looking again first: the phase, the pack or another window may have changed. */
  private void later(Player p) {
    UUID id = p.getUniqueId();
    if (!pending.add(id)) return;
    Bukkit.getScheduler().runTask(plugin, () -> {
      pending.remove(id);
      Player now = Bukkit.getPlayer(id);
      VbMatch m = now == null ? null : matchOf(now);
      MenuPlan.View v = m == null ? null : view(now, m);
      if (v == null) return;
      MenuPlan.Menu menu = plan.decide(id, v);
      if (menu == MenuPlan.Menu.NONE) return;
      plan.opened(id, v);
      if (menu == MenuPlan.Menu.SELECT) agents.openSelect(now, m);
      else plugin.valoblock.weapons.openArmory(now, m);
    });
  }

  /** The phase changed (clock thread or server thread): close what is out of date, on the server thread. */
  void phaseChanged(VbMatch m) {
    Bukkit.getScheduler().runTask(plugin, () -> {
      for (UUID id : m.participants()) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) continue;
        MenuPlan.View v = view(p, m);
        if (v != null && MenuPlan.mustClose(v)) p.closeInventory();
      }
    });
  }

  void forget(UUID id) {
    plan.forget(id);
    swapTap.forget(id);
    pending.remove(id);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    forget(e.getPlayer().getUniqueId());
  }
}
