package win.vampyuyu.arena;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * How a player acts during a werewolf game, with no chest menu: look at a player (or a practice
 * bot's body) and right-click to pick them; the other actions (skip the vote, life and death
 * potions, bind, shoot, protect yourself, infect...) are hotbar items with a tooltip.
 */
final class ClickControls implements Listener {
  /** Hotbar item per ability key of {@link WerewolfGame#abilities}. */
  private record Look(Material material, String title, String lore) {}

  private static final Map<String, Look> LOOKS =
      Map.ofEntries(
          Map.entry("skip", new Look(Material.PAPER, "Skip the vote", "Right-click: abstain")),
          Map.entry("heal", new Look(Material.GLISTERING_MELON_SLICE, "Life potion", "Right-click: save tonight's victim")),
          Map.entry("poison", new Look(Material.FERMENTED_SPIDER_EYE, "Death potion", "Right-click: poison the player you picked")),
          Map.entry("sleep", new Look(Material.RED_BED, "Sleep", "Right-click: end your turn")),
          Map.entry("bind", new Look(Material.ARROW, "Bind the lovers", "Right-click: unite the two you picked")),
          Map.entry("shoot", new Look(Material.BOW, "Shoot", "Right-click: fire at the player you picked")),
          Map.entry("protect-self", new Look(Material.SHIELD, "Protect yourself", "Right-click: guard yourself tonight")),
          Map.entry("infect", new Look(Material.ROTTEN_FLESH, "Infect", "Right-click: tonight's victim becomes a wolf")));

  private static final double RANGE = 48;

  private final NexusArenaEngine plugin;
  private final NamespacedKey key;
  private final Map<UUID, String> shown = new ConcurrentHashMap<>();
  private final Map<UUID, Long> last = new ConcurrentHashMap<>();

  ClickControls(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.key = new NamespacedKey(plugin, "ww-action");
  }

  // ------------------------------------------------------------------ hotbar

  boolean control(ItemStack item) {
    return item != null
        && item.hasItemMeta()
        && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
  }

  private String ability(ItemStack item) {
    return control(item)
        ? item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING)
        : null;
  }

  /** Puts the abilities of the moment in the first hotbar slots; runs on the player's thread. */
  void sync(Player p, ArenaSession s) {
    if (!(s.game instanceof WerewolfGame game)) return;
    boolean live = plugin.gui.inGame(s) && s.presentation.isDone() && game.alive().contains(p.getUniqueId());
    Map<String, String> abilities = live ? game.abilities(p.getUniqueId()) : Map.of();
    String signature = String.join(",", abilities.keySet());
    if (signature.equals(shown.get(p.getUniqueId()))) return;
    shown.put(p.getUniqueId(), signature);
    var inventory = p.getInventory();
    for (int slot = 0; slot < 9; slot++) if (control(inventory.getItem(slot))) inventory.setItem(slot, null);
    int slot = 0;
    for (var entry : abilities.entrySet()) {
      Look look = LOOKS.get(entry.getKey());
      if (look == null || slot >= 8) continue;
      ItemStack item = CompassGUI.item(look.material(), look.title(), entry.getValue(), look.lore());
      var meta = item.getItemMeta();
      meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, entry.getKey());
      item.setItemMeta(meta);
      inventory.setItem(slot++, item);
    }
    if (slot > 0) inventory.setHeldItemSlot(0);
  }

  void clear(Player p) {
    shown.remove(p.getUniqueId());
    var inventory = p.getInventory();
    for (int slot = 0; slot < inventory.getSize(); slot++)
      if (control(inventory.getItem(slot))) inventory.setItem(slot, null);
  }

  // --------------------------------------------------------------- targeting

  /** The participant the player is looking at, or null. */
  UUID lookingAt(Player p, ArenaSession s) {
    Entity e = p.getTargetEntity((int) RANGE, false);
    if (e == null) return null;
    return participant(s, e);
  }

  UUID participant(ArenaSession s, Entity e) {
    if (e instanceof Player target) return s.players.containsKey(target.getUniqueId()) ? target.getUniqueId() : null;
    for (var body : s.bodies.entrySet()) if (body.getValue().getUniqueId().equals(e.getUniqueId())) return body.getKey();
    return null;
  }

  /** What picking a player means right now for this role, as a CompassGUI action string. */
  static String pick(WerewolfGame game, UUID me, UUID target) {
    String phase = game.phase();
    String role = game.role(me);
    boolean aims =
        phase.equals("CUPID") && role.equals("CUPID")
            || phase.equals("WITCH") && role.equals("WITCH")
            || phase.equals("HUNTER_SHOT");
    return (aims ? "aim:" : "select:") + target;
  }

  /** Right-click on a player: pick them (vote, inspect, protect, aim...). */
  boolean pickPlayer(Player p, ArenaSession s, UUID target) {
    if (!(s.game instanceof WerewolfGame game) || !plugin.gui.inGame(s)) return false;
    if (!s.presentation.isDone() || target.equals(p.getUniqueId())) return false;
    plugin.gui.act(p, s, game, pick(game, p.getUniqueId(), target));
    return true;
  }

  /** Right-click on a hotbar control. */
  boolean useItem(Player p, ArenaSession s, String ability) {
    if (!(s.game instanceof WerewolfGame game) || !plugin.gui.inGame(s) || !s.presentation.isDone()) return false;
    plugin.gui.act(p, s, game, "use:" + ability);
    return true;
  }

  private boolean debounce(Player p) {
    long now = System.currentTimeMillis();
    Long before = last.put(p.getUniqueId(), now);
    return before != null && now - before < 150;
  }

  // ------------------------------------------------------------------ events

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void interact(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) return;
    if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    Player p = e.getPlayer();
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !(s.game instanceof WerewolfGame)) return;
    String ability = ability(e.getItem());
    if (ability == null && !plugin.gui.inGame(s)) return;
    e.setCancelled(true);
    if (debounce(p)) return;
    if (ability != null) {
      useItem(p, s, ability);
      return;
    }
    UUID target = lookingAt(p, s);
    if (target != null) pickPlayer(p, s, target);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void interactEntity(PlayerInteractEntityEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) return;
    Player p = e.getPlayer();
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !plugin.gui.inGame(s)) return;
    e.setCancelled(true);
    if (debounce(p)) return;
    String ability = ability(p.getInventory().getItemInMainHand());
    if (ability != null) {
      useItem(p, s, ability);
      return;
    }
    UUID target = participant(s, e.getRightClicked());
    if (target != null) pickPlayer(p, s, target);
  }

  /** Hitting a player or a body does nothing; the game is not a fight. */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void hit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p && plugin.arenas.session(p.getUniqueId()) != null
        && plugin.arenas.session(p.getUniqueId()).game instanceof WerewolfGame) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void drop(PlayerDropItemEvent e) {
    if (control(e.getItemDrop().getItemStack())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void swap(PlayerSwapHandItemsEvent e) {
    if (control(e.getMainHandItem()) || control(e.getOffHandItem())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player)) return;
    if (control(e.getCurrentItem())
        || control(e.getCursor())
        || e.getClick() == ClickType.NUMBER_KEY
            && control(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()))) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void drag(InventoryDragEvent e) {
    if (control(e.getOldCursor())) e.setCancelled(true);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    shown.remove(e.getPlayer().getUniqueId());
    last.remove(e.getPlayer().getUniqueId());
  }

  /**
   * A marker, seen only by the voter, floating over the head of whoever their ballot is for: a
   * ring of coloured dust and a spark, drawn a few times a second while a vote is open.
   */
  void markVote(Player p, ArenaSession s) {
    if (!(s.game instanceof WerewolfGame game) || !plugin.gui.inGame(s)) return;
    UUID me = p.getUniqueId();
    boolean voting = game.phase().equals("DAY_VOTE") || game.phase().equals("NIGHT") && game.packMember(me);
    UUID ballot = voting && game.alive().contains(me) ? game.ballot(me) : null;
    if (ballot == null) return;
    org.bukkit.Location head = null;
    Player target = org.bukkit.Bukkit.getPlayer(ballot);
    if (target != null && target.isOnline()) head = target.getLocation().add(0, 2.35, 0);
    else if (s.bodies.get(ballot) != null) head = s.bodies.get(ballot).getLocation().add(0, 2.35, 0);
    if (head == null) return;
    int rgb = WerewolfPresentation.color(game.role(me));
    var dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(rgb), 1.3f);
    double turn = (System.currentTimeMillis() % 1600) / 1600.0 * Math.PI * 2;
    for (int i = 0; i < 6; i++) {
      double a = turn + i * Math.PI / 3;
      p.spawnParticle(org.bukkit.Particle.DUST, head.getX() + Math.cos(a) * 0.38, head.getY(), head.getZ() + Math.sin(a) * 0.38, 1, 0, 0, 0, 0, dust);
    }
    p.spawnParticle(org.bukkit.Particle.END_ROD, head.getX(), head.getY() + 0.35, head.getZ(), 1, 0, 0, 0, 0);
  }

  /** The line the action bar shows for the player's crosshair, or null. */
  Component target(Player p, ArenaSession s) {
    UUID target = lookingAt(p, s);
    if (target == null || !(s.game instanceof WerewolfGame game) || !game.alive().contains(target)) return null;
    String name = s.players.getOrDefault(target, "Player");
    return Component.text(" ▸ " + name, TextColor.color(WerewolfPresentation.color(game.role(p.getUniqueId()))));
  }
}
