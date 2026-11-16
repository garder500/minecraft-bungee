package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.valoblock.AbilityEnv.Ctx;
import win.vampyuyu.valoblock.AgentSelectMenu.State;

/**
 * The agents on the server thread: the select menu (title countdown, picks, Lock in, Random), the agent
 * skins (saved and restored), the ability items on the hotbar (slots 4-7 are indexes 3-6) and their use.
 */
public final class VbAgentService implements AgentService, Listener {
  static final int ARMORY_A = 46, ARMORY_B = 47; // abilities 4 and 5 in the last row of the Armory
  private static final long COOLDOWN_MS = 350;

  private final NexusArenaEngine plugin;
  private final AbilityEnv env;
  private final Map<UUID, AgentKit> kits = new ConcurrentHashMap<>();
  private final Map<UUID, State> states = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastUse = new HashMap<>();
  private int ticks;

  public VbAgentService(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.env = new AbilityEnv(plugin);
  }

  /** Registers the listener and the per-tick task, and gives back any skin a crash left on a player. */
  public void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    for (Player p : Bukkit.getOnlinePlayers()) if (matchOf(p.getUniqueId()) == null) SkinSwap.restore(p);
  }

  /** Plugin disable: skins back, blocks back, entities gone. */
  public void shutdown() {
    env.fx.clear(null);
    env.reveals.clear();
    env.temp.restoreAll();
    for (Player p : Bukkit.getOnlinePlayers()) SkinSwap.restore(p);
  }

  AbilityEnv env() {
    return env;
  }

  @Override
  public int[] charges(UUID id) {
    AgentKit k = kits.get(id);
    if (k == null) return null;
    return new int[] {k.charges(4), k.charges(5), k.charges(6), k.charges(7)};
  }

  AgentKit kit(UUID id) {
    return kits.get(id);
  }

  State state(UUID id) {
    return states.computeIfAbsent(id, k -> new State());
  }

  private VbMatch matchOf(UUID id) {
    ArenaSession s = plugin.arenas.session(id);
    return s != null && s.game instanceof VbMatch m ? m : null;
  }

  private void sync(Runnable r) {
    if (Bukkit.isPrimaryThread()) r.run();
    else Bukkit.getScheduler().runTask(plugin, r);
  }

  // ------------------------------------------------------------------ AgentService

  @Override
  public List<String> agents() {
    return VbData.agents().ids();
  }

  @Override
  public void lockedIn(VbMatch match, UUID player, String agent) {
    sync(() -> {
      VbAgent a = VbData.agents().get(agent);
      Player p = Bukkit.getPlayer(player);
      if (a == null || p == null) return;
      kits.put(player, new AgentKit(a, VbData.agents().ultimate()));
      State st = state(player);
      st.locked = true;
      st.picked = agent;
      if (SkinSwap.apply(p, a)) p.sendMessage(Component.text("You are " + a.name() + ".", NamedTextColor.GRAY));
      else p.sendMessage(Component.text("You are " + a.name() + " (no skin set for this agent yet).", NamedTextColor.GRAY));
      // Never close a window inside its own click event: do it on the next tick.
      Bukkit.getScheduler().runTask(plugin, () -> {
        if (AgentSelectMenu.isMenu(p.getOpenInventory().getTopInventory())) p.closeInventory();
      });
      p.getInventory().remove(Material.NETHER_STAR);
      giveItems(p, kits.get(player)); // Lock in fills the hotbar
      refreshMenus(match);
    });
  }

  @Override
  public void restore(UUID player) {
    sync(() -> {
      kits.remove(player);
      states.remove(player);
      lastUse.remove(player);
      env.frozen.remove(player);
      env.marks.remove(player);
      Player p = Bukkit.getPlayer(player);
      if (p == null) return;
      SkinSwap.restore(p);
      p.setSilent(false);
      PlayerInventory inv = p.getInventory();
      for (int i = 0; i < inv.getSize(); i++) if (AgentItems.slotOf(inv.getItem(i)) > 0 || "reopen".equals(AgentItems.action(inv.getItem(i)))) inv.setItem(i, null);
    });
  }

  @Override
  public void matchEnded(VbMatch match) {
    sync(() -> cleanup(match));
  }

  private void cleanup(VbMatch match) {
    env.fx.clear(match);
    env.reveals.clear();
    env.temp.restoreAll();
    env.ghosts.clear();
    env.walls.clear();
    for (UUID id : match.participants()) {
      match.setInvulnerable(id, false);
      env.frozen.remove(id);
      Player p = Bukkit.getPlayer(id);
      if (p != null) p.setSilent(false);
    }
  }

  @Override
  public void phaseChanged(VbMatch match, VbPhase from, VbPhase to) {
    if (to == VbPhase.ROUND_END || to == VbPhase.BUY || to == VbPhase.END) sync(() -> cleanup(match));
  }

  @Override
  public void roundStarted(VbMatch match, UUID player, boolean keep) {
    if (match.hasLeft(player)) return;
    sync(() -> {
      AgentKit kit = kits.get(player);
      Player p = Bukkit.getPlayer(player);
      if (kit == null || p == null) return;
      kit.roundStart(keep);
      p.getInventory().remove(Material.NETHER_STAR);
      giveItems(p, kit);
    });
  }

  @Override
  public void killed(VbMatch match, UUID victim, UUID killer) {
    if (killer == null) return;
    sync(() -> {
      AgentKit kit = kits.get(killer);
      Player p = Bukkit.getPlayer(killer);
      if (kit == null) return;
      kit.onKill();
      if (p != null) giveItems(p, kit);
    });
  }

  @Override
  public void orb(VbMatch match, UUID player) {
    sync(() -> {
      AgentKit kit = kits.get(player);
      Player p = Bukkit.getPlayer(player);
      if (kit == null) return;
      kit.onOrb();
      if (p != null) giveItems(p, kit);
    });
  }

  @Override
  public void bulletHit(Block block) {
    TempBlocks.Wall w = env.temp.hit(block);
    if (w != null) block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), "block.glass.hit", SoundCategory.PLAYERS, 0.8f, 1.4f);
  }

  @Override
  public void bulletPassed(Player shooter, double[] o, double[] d, double dist) {
    if (!env.ghosts.isEmpty()) AbilitiesB.shotPassed(env, shooter, o, d, dist);
  }

  @Override
  public boolean frozen(UUID player) {
    return env.frozen.contains(player);
  }

  // ------------------------------------------------------------------ the per-tick task

  private void tick() {
    ticks++;
    env.now++;
    env.fx.tick();
    env.reveals.tick();
    if (ticks % 20 == 0) {
      selectTick();
      for (var e : kits.entrySet()) {
        Player p = Bukkit.getPlayer(e.getKey());
        if (p != null && matchOf(e.getKey()) != null) giveItems(p, e.getValue());
      }
    }
  }

  /** Once a second during agent select: open the menu for whoever has not locked in, refresh the title and the cells. */
  private void selectTick() {
    long now = System.currentTimeMillis();
    for (Player p : Bukkit.getOnlinePlayers()) {
      ArenaSession s = plugin.arenas.session(p.getUniqueId());
      if (s == null || !(s.game instanceof VbMatch m) || m.vbPhase() != VbPhase.AGENT_SELECT) continue;
      State st = state(p.getUniqueId());
      int left = (int) Math.max(0, (s.phaseEndsAt - now + 999) / 1000);
      Inventory top = p.getOpenInventory().getTopInventory();
      if (AgentSelectMenu.isMenu(top)) {
        setTitle(p, AgentText.title(left));
        AgentSelectMenu.fill(top, m, p.getUniqueId(), this::state);
      } else if (!st.locked) {
        if (!st.dismissed && top.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) p.openInventory(AgentSelectMenu.build(m, p, this::state, left));
        if (p.getInventory().getItem(4) == null) p.getInventory().setItem(4, AgentItems.selectItem());
      }
    }
  }

  private static void setTitle(Player p, String title) {
    try {
      p.getOpenInventory().setTitle(title);
    } catch (RuntimeException | LinkageError ignored) {
      // cosmetic: the countdown just does not move
    }
  }

  private void refreshMenus(VbMatch m) {
    for (UUID id : m.participants()) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      Inventory top = p.getOpenInventory().getTopInventory();
      if (AgentSelectMenu.isMenu(top)) AgentSelectMenu.fill(top, m, id, this::state);
    }
  }

  // ------------------------------------------------------------------ items

  /** Puts the four ability items in hotbar 4-7 (indexes 3-6), with their charges. */
  void giveItems(Player p, AgentKit kit) {
    PlayerInventory inv = p.getInventory();
    for (VbAbility ab : kit.agent().abilities()) inv.setItem(ab.hotbar(), AgentItems.ability(kit.agent(), ab, kit));
  }

  // ------------------------------------------------------------------ using an ability

  @EventHandler(priority = EventPriority.HIGH)
  public void interact(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) return;
    ItemStack it = e.getItem();
    if (it == null) return;
    int slot = AgentItems.slotOf(it);
    boolean reopen = "reopen".equals(AgentItems.action(it));
    if (slot < 0 && !reopen) return;
    Player p = e.getPlayer();
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null) return;
    e.setCancelled(true);
    e.setUseItemInHand(Event.Result.DENY);
    e.setUseInteractedBlock(Event.Result.DENY);
    boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
    if (!right) return;
    if (reopen) {
      if (m.vbPhase() == VbPhase.AGENT_SELECT) {
        state(p.getUniqueId()).dismissed = false;
        p.openInventory(AgentSelectMenu.build(m, p, this::state, m.seconds()));
      }
      return;
    }
    use(p, m, slot);
  }

  private void use(Player p, VbMatch m, int slot) {
    AgentKit kit = kits.get(p.getUniqueId());
    if (kit == null) return;
    long now = System.currentTimeMillis();
    if (now - lastUse.getOrDefault(p.getUniqueId(), 0L) < COOLDOWN_MS) return;
    lastUse.put(p.getUniqueId(), now);
    VbPhase phase = m.vbPhase();
    if ((phase != VbPhase.COMBAT && phase != VbPhase.PLANTED) || !m.alive().contains(p.getUniqueId())) {
      plugin.valoblock.hud.notice(p, Component.text("Abilities work during combat", NamedTextColor.GRAY));
      return;
    }
    if (env.frozen.contains(p.getUniqueId())) return;
    VbAbility ab = kit.agent().ability(slot);
    if (ab == null) return;
    Ctx c = new Ctx(p, m, kit.agent(), ab, env);
    try {
      if (AbilitiesB.reuse(c)) {
        giveItems(p, kit);
        return;
      }
      AgentKit.Result r = kit.canUse(slot);
      if (r != AgentKit.Result.OK) {
        plugin.valoblock.hud.notice(p, Component.text(r.text, NamedTextColor.RED));
        p.playSound(p.getLocation(), "block.note_block.bass", SoundCategory.PLAYERS, 0.8f, 0.7f);
        return;
      }
      kit.use(slot);
      boolean ok = AbilitiesA.cast(c) || AbilitiesB.cast(c);
      if (!ok) {
        kit.refund(slot);
        plugin.valoblock.hud.notice(p, Component.text("That cannot be used here", NamedTextColor.RED));
      }
    } catch (RuntimeException ex) {
      kit.refund(slot);
      plugin.getLogger().warning("ValoBlock ability " + ab.id() + " failed: " + ex);
    }
    giveItems(p, kit);
  }

  // ------------------------------------------------------------------ the select menu

  @EventHandler(priority = EventPriority.HIGH)
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !AgentSelectMenu.isMenu(e.getView().getTopInventory())) return;
    e.setCancelled(true);
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
    String action = AgentItems.action(e.getCurrentItem());
    if (action == null || action.equals("none")) return;
    State st = state(p.getUniqueId());
    if (st.locked || m.vbPhase() != VbPhase.AGENT_SELECT) return;
    if (action.startsWith("pick:")) {
      String id = action.substring(5);
      if (m.takenByTeammate(p.getUniqueId(), id)) {
        p.sendMessage(Component.text("A teammate already took that agent.", NamedTextColor.RED));
        return;
      }
      st.picked = id;
      p.playSound(p.getLocation(), "ui.button.click", SoundCategory.PLAYERS, 0.6f, 1.2f);
    } else if (action.startsWith("role:")) {
      String role = action.substring(5);
      st.filter = role.equals(st.filter) ? null : role;
    } else if (action.equals("random")) {
      String id = m.randomFree(p.getUniqueId());
      if (id == null) p.sendMessage(Component.text("No free agent is left.", NamedTextColor.RED));
      else st.picked = id;
    } else if (action.equals("lock")) {
      if (st.picked == null) {
        p.sendMessage(Component.text("Pick an agent first.", NamedTextColor.RED));
        return;
      }
      try {
        m.lockAgent(p.getUniqueId(), st.picked); // fires agentLocked: kit, skin, menu closed
      } catch (IllegalArgumentException | IllegalStateException ex) {
        p.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        st.picked = null;
      }
    }
    refreshMenus(m);
  }

  @EventHandler
  public void close(InventoryCloseEvent e) {
    if (e.getPlayer() instanceof Player p && AgentSelectMenu.isMenu(e.getInventory()) && !state(p.getUniqueId()).locked) state(p.getUniqueId()).dismissed = true;
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    Player p = e.getPlayer();
    SkinSwap.restore(p);
    p.setSilent(false);
    kits.remove(p.getUniqueId());
    states.remove(p.getUniqueId());
    env.frozen.remove(p.getUniqueId());
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    if (matchOf(p.getUniqueId()) == null) SkinSwap.restore(p); // a crash left an agent skin on them
  }

  // ------------------------------------------------------------------ the Armory: abilities 4 and 5

  /** Adds the two buyable abilities of the player's agent to the last row of the Armory. */
  @Override
  public void decorateArmory(Inventory inv, Player p, VbMatch m) {
    AgentKit kit = kits.get(p.getUniqueId());
    if (kit == null) return;
    int credits = m.credits(p.getUniqueId());
    int[] cells = {ARMORY_A, ARMORY_B};
    int i = 0;
    for (int slot = 4; slot <= 5; slot++) {
      VbAbility ab = kit.agent().ability(slot);
      boolean afford = credits >= ab.cost();
      boolean full = kit.charges(slot) >= ab.maxCharges();
      List<AgentText.Line> lines = new ArrayList<>();
      lines.add(new AgentText.Line("[" + slot + "] " + ab.name() + " · " + ab.cost(), afford ? kit.agent().trim() : "#777777"));
      lines.add(new AgentText.Line(ab.text(), AgentText.GRAY));
      lines.add(new AgentText.Line("Carrying " + kit.charges(slot) + " / " + ab.maxCharges(), AgentText.GOLD));
      if (full) lines.add(new AgentText.Line("You carry the most you can", AgentText.GRAY));
      else if (!afford) lines.add(new AgentText.Line("Missing " + String.format("%,d", ab.cost() - credits) + " Shards", AgentText.RED));
      else lines.add(new AgentText.Line("Left-click to buy · right-click to sell back", AgentText.YELLOW));
      Material mat = Material.matchMaterial(ab.item());
      ItemStack it = AgentItems.cell("agent_empty", lines, "ability:" + slot, kit.charges(slot) > 0);
      it.setType(mat == null ? Material.STICK : mat);
      var meta = it.getItemMeta();
      meta.setItemModel(null); // the vanilla icon of the ability
      it.setItemMeta(meta);
      inv.setItem(cells[i++], it);
    }
  }

  /** A click in the Armory on an ability cell; true when it was one (the Armory then redraws). */
  @Override
  public boolean armoryClick(Player p, VbMatch m, int slot, boolean sell) {
    if (slot != ARMORY_A && slot != ARMORY_B) return false;
    AgentKit kit = kits.get(p.getUniqueId());
    if (kit == null) return true;
    int abSlot = slot == ARMORY_A ? 4 : 5;
    AgentKit.Result r = sell ? kit.sell(m, p.getUniqueId(), abSlot) : kit.buy(m, p.getUniqueId(), abSlot);
    if (r == AgentKit.Result.OK) {
      p.playSound(p.getLocation(), sell ? "block.note_block.hat" : "entity.experience_orb.pickup", SoundCategory.PLAYERS, 0.8f, 1.2f);
      giveItems(p, kit);
    } else {
      p.sendMessage(Component.text(r.text, NamedTextColor.RED));
      p.playSound(p.getLocation(), "block.note_block.bass", SoundCategory.PLAYERS, 0.8f, 0.7f);
    }
    return true;
  }
}
