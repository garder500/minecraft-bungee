package win.vampyuyu.werewolf.hud;

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
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.CompassGUI;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.WerewolfCompass;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.WerewolfSession;
import win.vampyuyu.werewolf.ambience.WerewolfAmbience;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;

/**
 * How a player acts during a werewolf game, with no chest menu: look at a player (or a practice
 * bot's body) and left-click (as if breaking a block) to pick them; the other actions (skip the vote, life and death
 * potions, bind, shoot, protect yourself, infect...) are hotbar items with a tooltip.
 */
public final class ClickControls implements Listener {
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
          Map.entry("infect", new Look(Material.ROTTEN_FLESH, "Infect", "Right-click: tonight's victim becomes a wolf")),
          Map.entry("pass", new Look(Material.RED_BED, "Go back to sleep", "Right-click: end your turn")),
          Map.entry("live", new Look(Material.LIME_DYE, "Thumbs up", "Right-click: let the accused live")),
          Map.entry("die", new Look(Material.RED_DYE, "Thumbs down", "Right-click: eliminate the accused")),
          Map.entry("let", new Look(Material.PAPER, "Let it pass", "Right-click: do nothing")),
          Map.entry("sacrifice", new Look(Material.TOTEM_OF_UNDYING, "Sacrifice yourself", "Right-click: take the condemned player's place")),
          Map.entry("stir", new Look(Material.FIREWORK_ROCKET, "Stir up trouble", "Right-click: two eliminations tomorrow")));

  private static final double RANGE = 48;

  private final NexusArenaEngine plugin;
  private final NamespacedKey key;
  private final Map<UUID, String> shown = new ConcurrentHashMap<>();
  private final Map<UUID, Long> last = new ConcurrentHashMap<>();

  public ClickControls(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.key = new NamespacedKey(plugin, "ww-action");
  }

  // ------------------------------------------------------------------ hotbar

  public boolean control(ItemStack item) {
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
  public void sync(Player p, ArenaSession s) {
    if (!(s.game instanceof WerewolfGame game)) return;
    boolean live = WerewolfCompass.inGame(s) && s.presentation.isDone() && game.alive().contains(p.getUniqueId());
    Map<String, String> abilities = live ? game.abilities(p.getUniqueId()) : Map.of();
    String signature = live + ":" + (live ? game.selfRoleName(p.getUniqueId()) + game.help(p.getUniqueId()) : "") + ":" + String.join(",", abilities.keySet()) + "|" + String.join(",", abilities.values());
    if (signature.equals(shown.get(p.getUniqueId()))) return;
    shown.put(p.getUniqueId(), signature);
    var inventory = p.getInventory();
    for (int slot = 0; slot < 9; slot++) if (control(inventory.getItem(slot))) inventory.setItem(slot, null);
    int slot = 0;
    for (var entry : abilities.entrySet()) {
      // A button a role module added without a look of its own is a paper with its label.
      Look look =
          LOOKS.getOrDefault(entry.getKey(), new Look(Material.PAPER, entry.getValue(), "Right-click to use"));
      if (slot >= 8) continue;
      ItemStack item = CompassGUI.item(look.material(), look.title(), entry.getValue(), look.lore());
      var meta = item.getItemMeta();
      meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, entry.getKey());
      item.setItemMeta(meta);
      inventory.setItem(slot++, item);
    }
    // The role card: a paper in the last slot whose tooltip says what the role does.
    if (live) {
      String role = game.selfRoleName(p.getUniqueId());
      ItemStack card =
          CompassGUI.item(
              Material.PAPER,
              WerewolfPresentation.roleName(role) + " · " + WerewolfPresentation.team(role),
              WerewolfAmbience.roleHelp(s, game, p.getUniqueId()));
      var meta = card.getItemMeta();
      meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "card");
      card.setItemMeta(meta);
      inventory.setItem(8, card);
    }
    if (slot > 0) inventory.setHeldItemSlot(0);
  }

  public void clear(Player p) {
    shown.remove(p.getUniqueId());
    var inventory = p.getInventory();
    for (int slot = 0; slot < inventory.getSize(); slot++)
      if (control(inventory.getItem(slot))) inventory.setItem(slot, null);
  }

  // --------------------------------------------------------------- targeting

  /**
   * The participant nearest the crosshair, or null. The exact-hitbox ray of getTargetEntity misses a
   * small body across the platform, so this takes whoever the line of sight passes close to, with a
   * tolerance that grows with the distance.
   */
  public UUID lookingAt(Player p, ArenaSession s) {
    var eye = p.getEyeLocation();
    var dir = eye.getDirection();
    UUID best = null;
    double bestScore = Double.MAX_VALUE;
    List<Entity> candidates = new ArrayList<>();
    for (UUID id : s.players.keySet()) {
      Player other = org.bukkit.Bukkit.getPlayer(id);
      if (other != null && !other.equals(p)) candidates.add(other);
    }
    candidates.addAll(WerewolfSession.of(s).bodies.values());
    for (Entity e : candidates) {
      if (!e.getWorld().equals(eye.getWorld())) continue;
      // The body is a column: the ray may pass near its feet, chest or head.
      double height = e.getHeight(), off = Double.MAX_VALUE, along = 0;
      for (double k : new double[] {0.1, 0.35, 0.6, 0.9}) {
        var v = e.getLocation().add(0, height * k, 0).toVector().subtract(eye.toVector());
        double a = v.dot(dir);
        if (a <= 0 || a > RANGE) continue;
        double o = v.clone().subtract(dir.clone().multiply(a)).length();
        if (o < off) {
          off = o;
          along = a;
        }
      }
      if (off == Double.MAX_VALUE) continue;
      double tolerance = 0.4 + along * 0.025;
      if (off > tolerance) continue;
      double score = off / tolerance;
      if (score >= bestScore) continue;
      UUID id = participant(s, e);
      if (id == null) continue;
      best = id;
      bestScore = score;
    }
    return best;
  }

  public UUID participant(ArenaSession s, Entity e) {
    if (e instanceof Player target) return s.players.containsKey(target.getUniqueId()) ? target.getUniqueId() : null;
    for (var body : WerewolfSession.of(s).bodies.entrySet()) if (body.getValue().getUniqueId().equals(e.getUniqueId())) return body.getKey();
    return null;
  }

  /** What picking a player means right now for this role, as a CompassGUI action string. */
  public static String pick(WerewolfGame game, UUID me, UUID target) {
    String phase = game.phase();
    String role = game.role(me);
    boolean aims =
        phase.equals("CUPID") && role.equals("CUPID")
            || phase.equals("WITCH") && role.equals("WITCH")
            || phase.equals("HUNTER_SHOT");
    return (aims ? "aim:" : "select:") + target;
  }

  /** Right-click on a player: pick them (vote, inspect, protect, aim...). */
  public boolean pickPlayer(Player p, ArenaSession s, UUID target) {
    if (!(s.game instanceof WerewolfGame game) || !WerewolfCompass.inGame(s)) return false;
    if (!s.presentation.isDone() || target.equals(p.getUniqueId())) return false;
    plugin.werewolf.compass.act(p, s, game, pick(game, p.getUniqueId(), target));
    return true;
  }

  /** Right-click on a hotbar control. */
  public boolean useItem(Player p, ArenaSession s, String ability) {
    if (!(s.game instanceof WerewolfGame game) || !WerewolfCompass.inGame(s) || !s.presentation.isDone()) return false;
    if (ability.equals("card")) return false;
    plugin.werewolf.compass.act(p, s, game, "use:" + ability);
    return true;
  }

  private boolean debounce(Player p) {
    long now = System.currentTimeMillis();
    Long before = last.put(p.getUniqueId(), now);
    return before != null && now - before < 150;
  }

  // ------------------------------------------------------------------ events

  /** Left-click (as if breaking a block) picks the player under the crosshair; right-click uses the held button. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void interact(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) return;
    Player p = e.getPlayer();
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !(s.game instanceof WerewolfGame)) return;
    Action action = e.getAction();
    boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    if (!left && !right) return;
    String ability = ability(e.getItem());
    if (!WerewolfCompass.inGame(s)) return;
    e.setCancelled(true);
    if (debounce(p)) return;
    if (left) {
      UUID target = lookingAt(p, s);
      if (target != null) pickPlayer(p, s, target);
    } else if (ability != null) {
      useItem(p, s, ability);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void interactEntity(PlayerInteractEntityEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) return;
    Player p = e.getPlayer();
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !WerewolfCompass.inGame(s)) return;
    e.setCancelled(true);
    if (debounce(p)) return;
    String ability = ability(p.getInventory().getItemInMainHand());
    if (ability != null) useItem(p, s, ability);
  }

  /** Hitting a player or a body picks them; the game is not a fight. */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void hit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p)) return;
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !(s.game instanceof WerewolfGame)) return;
    e.setCancelled(true);
    if (!WerewolfCompass.inGame(s) || debounce(p)) return;
    UUID target = participant(s, e.getEntity());
    if (target != null) pickPlayer(p, s, target);
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

  /** The line the action bar shows for the player's crosshair, or null. */
  public Component target(Player p, ArenaSession s) {
    UUID target = lookingAt(p, s);
    if (target == null || !(s.game instanceof WerewolfGame game) || !game.alive().contains(target)) return null;
    String name = s.players.getOrDefault(target, "Player");
    return Component.text(" ▸ " + name, TextColor.color(WerewolfPresentation.color(game.selfRoleName(p.getUniqueId()))));
  }
}
