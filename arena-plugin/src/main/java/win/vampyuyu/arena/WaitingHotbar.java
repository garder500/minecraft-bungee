package win.vampyuyu.arena;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Clickable hotbar items for players waiting in a ValoBlock or Werewolf waiting hub, removing the
 * need to type commands (/vb vote, /vb start, /arena start, /arena leave).
 */
public final class WaitingHotbar implements Listener {
  private static final String VALOBLOCK_PRESET = "valoblock";
  private static final TextColor GOLD = TextColor.color(0xe9d99b);
  private static final TextColor CYAN = TextColor.color(0x52e0ff);
  private static final TextColor GREEN = TextColor.color(0x8ee06e);
  private static final TextColor ROSE = TextColor.color(0xd58b8e);

  private static final String ACT_VOTE = "vote";
  private static final String ACT_VILLAGE = "village";
  private static final String ACT_START = "start";
  private static final String ACT_LEAVE = "leave";

  private final NexusArenaEngine plugin;
  private final NamespacedKey key;
  private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

  public WaitingHotbar(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.key = new NamespacedKey(plugin, "waiting-action");
  }

  /** Gives waiting-room hotbar items for ValoBlock or Werewolf while no match is running. */
  public void give(Player p, ArenaSession s) {
    if (p == null || s == null || s.game != null) return;
    String preset = s.config.preset();
    if (!VALOBLOCK_PRESET.equals(preset) && !"werewolf".equals(preset)) return;
    ArenaLang.Lang lang = ArenaLang.of(p);
    if (VALOBLOCK_PRESET.equals(preset)) {
      p.getInventory()
          .setItem(
              0,
              tagged(
                  Material.FILLED_MAP,
                  ACT_VOTE,
                  tr(lang, "Map Vote", "Vote de carte", "Kartenwahl"),
                  CYAN,
                  tr(
                      lang,
                      "Right-click to vote for the next map",
                      "Clic droit pour voter la prochaine carte",
                      "Rechtsklick für die nächste Karte")));
      p.getInventory()
          .setItem(
              4,
              tagged(
                  Material.LIME_DYE,
                  ACT_START,
                  tr(lang, "Start Match", "Lancer la partie", "Match starten"),
                  GREEN,
                  tr(
                      lang,
                      "Right-click to start (bots fill empty seats)",
                      "Clic droit pour lancer (des bots complètent)",
                      "Rechtsklick zum Starten (Bots füllen auf)")));
    } else {
      p.getInventory()
          .setItem(
              0,
              tagged(
                  Material.BOOK,
                  ACT_VILLAGE,
                  tr(lang, "Village & Players", "Village & Joueurs", "Dorf & Spieler"),
                  GOLD,
                  tr(
                      lang,
                      "Right-click to view players and roles",
                      "Clic droit pour voir les joueurs et rôles",
                      "Rechtsklick für Spieler und Rollen")));
      p.getInventory()
          .setItem(
              4,
              tagged(
                  Material.EMERALD,
                  ACT_START,
                  tr(lang, "Start Game", "Lancer la partie", "Spiel starten"),
                  GREEN,
                  tr(
                      lang,
                      "Right-click to start (adds bots if under 4 players)",
                      "Clic droit pour lancer (ajoute des bots si < 4)",
                      "Rechtsklick zum Starten (Bots bei < 4 Spielern)")));
    }
    p.getInventory()
        .setItem(
            8,
            tagged(
                Material.RED_BED,
                ACT_LEAVE,
                tr(lang, "Leave Waiting Room", "Quitter la salle", "Warteraum verlassen"),
                ROSE,
                tr(
                    lang,
                    "Right-click to return where you came from",
                    "Clic droit pour retourner d'où tu viens",
                    "Rechtsklick zum Verlassen")));
  }

  /** Strips waiting-room items from the player's inventory (called when the match starts). */
  public void clear(Player p) {
    if (p == null) return;
    var inv = p.getInventory();
    for (int i = 0; i < inv.getSize(); i++) {
      if (actionOf(inv.getItem(i)) != null) inv.setItem(i, null);
    }
  }

  private ItemStack tagged(
      Material mat, String action, String title, TextColor color, String lore) {
    ItemStack item = new ItemStack(mat);
    var meta = item.getItemMeta();
    meta.displayName(
        Component.text(title, color)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
    meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
    item.setItemMeta(meta);
    return item;
  }

  private String actionOf(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return null;
    return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
  }

  private static String tr(ArenaLang.Lang lang, String en, String fr, String de) {
    return switch (lang) {
      case FR -> fr;
      case DE -> de;
      default -> en;
    };
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void interact(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) return;
    String action = actionOf(e.getItem());
    if (action == null) return;
    e.setCancelled(true);
    if (!e.getAction().isRightClick()) return;
    Player p = e.getPlayer();
    long now = System.currentTimeMillis();
    Long prev = cooldown.put(p.getUniqueId(), now);
    if (prev != null && now - prev < 500) return;

    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || s.game != null || s.stopping || s.finishing) return;

    switch (action) {
      case ACT_VOTE -> {
        String why = plugin.valoblock.openMapVote(p, s);
        if (why != null) plugin.message(p, why);
      }
      case ACT_VILLAGE -> plugin.gui.open(p);
      case ACT_START -> triggerStart(p, s);
      case ACT_LEAVE -> plugin.handle(p, plugin.arenas.leaveArena(p));
      default -> {}
    }
  }

  private void triggerStart(Player p, ArenaSession s) {
    try {
      int players = s.players.size();
      int min = s.config.minPlayers();
      if (players >= min) {
        plugin.arenas.start(s.config.id());
      } else if (s.hooks.practice()) {
        int needed = Math.max(1, min - players);
        plugin.handle(p, plugin.arenas.startPractice(s.config.id(), needed, null));
      } else {
        plugin.message(p, "Need at least " + min + " players to start (" + players + "/" + min + ").");
      }
    } catch (IllegalArgumentException ex) {
      plugin.message(p, ex.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void click(InventoryClickEvent e) {
    if (actionOf(e.getCurrentItem()) != null || actionOf(e.getCursor()) != null) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void drop(PlayerDropItemEvent e) {
    if (actionOf(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    cooldown.remove(e.getPlayer().getUniqueId());
  }
}
