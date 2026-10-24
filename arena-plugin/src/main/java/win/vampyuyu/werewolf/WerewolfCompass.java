package win.vampyuyu.werewolf;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import win.vampyuyu.arena.*;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.CompassGUI;
import win.vampyuyu.arena.CompassMenuHolder;
import win.vampyuyu.arena.MenuSkin;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.ambience.WerewolfAmbience;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;

/**
 * The Werewolf menus on the arena compass: a role menu when it is the player's turn, the village
 * overview otherwise; and the private notices (Cupid's arrow).
 */
public final class WerewolfCompass {
  private final NexusArenaEngine plugin;

  public WerewolfCompass(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  /** A werewolf game in progress: no chest-style menu, the player acts on the world itself. */
  public static boolean inGame(ArenaSession s) {
    return s != null
        && s.game instanceof WerewolfGame game
        && !game.phase().equals("FINISHED")
        && !s.stopping
        && !s.finishing;
  }

  /** Opens the role menu of the player's turn, or the village; false for a game that is not ours. */
  public boolean open(CompassGUI gui, Player p, ArenaSession s, boolean skinned) {
    WerewolfMenus.Kind kind =
        s.game instanceof WerewolfGame wolf ? WerewolfMenus.kind(wolf, p.getUniqueId()) : null;
    if (kind != null) {
      // The painted background carries the menu's name in its title tab.
      WerewolfMenuHolder holder =
          new WerewolfMenuHolder(
              p.getUniqueId(),
              s.config.id(),
              skinned,
              skinned
                  ? MenuSkin.title(kind.tile, MenuSkin.ROWS_54, Component.empty())
                  : Component.text(kind.title, TextColor.color(kind.color)));
      holder.kind = kind;
      p.openInventory(holder.getInventory());
      render(gui, holder, p, s);
      return true;
    }
    if (s.game == null || s.game instanceof WerewolfGame) {
      WerewolfMenuHolder holder =
          new WerewolfMenuHolder(
              p.getUniqueId(),
              s.config.id(),
              skinned,
              skinned
                  ? MenuSkin.title(WerewolfMenus.VILLAGE, MenuSkin.ROWS_54, Component.empty())
                  : Component.text("The village", TextColor.color(WerewolfPresentation.VILLAGER)));
      holder.village = true;
      p.openInventory(holder.getInventory());
      render(gui, holder, p, s);
      return true;
    }
    return false;
  }

  /** Redraws an open Werewolf window, or swaps it for the menu that is now the player's. */
  public boolean refresh(CompassGUI gui, Player p, ArenaSession s, CompassMenuHolder window) {
    if (!(window instanceof WerewolfMenuHolder holder)) return false;
    WerewolfMenus.Kind now =
        s.game instanceof WerewolfGame wolf ? WerewolfMenus.kind(wolf, p.getUniqueId()) : null;
    if (holder.kind != null && now != holder.kind) {
      // The window title is the painted background: another menu needs a new window.
      if (now != null) gui.open(p);
      else p.closeInventory();
    } else if (holder.village && now != null) gui.open(p); // Your turn now.
    else render(gui, holder, p, s);
    return true;
  }

  public void closed(CompassGUI gui, Player p, ArenaSession s, InventoryCloseEvent e) {
    if (s.game instanceof WerewolfGame) gui.clearBottom(p);
  }

  /** A click on a menu action (select:/aim:/use:); true when the game took it. */
  public boolean click(CompassGUI gui, Player p, ArenaSession s, String click) {
    if (!(s.game instanceof WerewolfGame wolf)) return false;
    if (!s.presentation.isDone() || s.finishing) {
      WerewolfSession.of(s)
          .journal
          .log(
              "IGNORED",
              p.getName()
                  + " clicked "
                  + click
                  + (s.finishing ? " while the arena is stopping" : " during a presentation"));
      return false;
    }
    act(p, s, wolf, click);
    return true;
  }

  private void render(CompassGUI gui, WerewolfMenuHolder holder, Player p, ArenaSession s) {
    if (holder.kind != null && s.game instanceof WerewolfGame wolf) renderRole(gui, holder, p, s, wolf);
    else renderVillage(gui, holder, p, s);
  }

  /** A role menu of the design guide: heads on the ring, buttons in the bottom boxes, hotbar. */
  private void renderRole(
      CompassGUI gui, WerewolfMenuHolder holder, Player p, ArenaSession s, WerewolfGame game) {
    Inventory inv = holder.getInventory();
    holder.targets.clear();
    holder.abilities.clear();
    holder.clicks.clear();
    holder.leaveSlot = -1;
    holder.phase = game.phase();
    inv.clear();
    gui.clearBottom(p);
    long seconds = Math.max(0, (s.phaseEndsAt - System.currentTimeMillis() + 999) / 1000);
    for (var entry :
        WerewolfMenus.menu(
            game,
            p.getUniqueId(),
            s.players,
            seconds,
            plugin.arenas.leaveBlocked(p.getUniqueId()))) {
      CompassGUI.place(inv, p, entry.raw(), item(gui, holder, entry));
      if (entry.click() != null) holder.clicks.put(entry.raw(), entry.click());
    }
  }

  /** The village overview: who is alive, the game, the village, and the usual hotbar. */
  private void renderVillage(
      CompassGUI gui, WerewolfMenuHolder holder, Player p, ArenaSession s) {
    Inventory inv = holder.getInventory();
    holder.targets.clear();
    holder.abilities.clear();
    holder.clicks.clear();
    holder.leaveSlot = -1;
    holder.phase = s.game == null ? "" : s.game.phase();
    inv.clear();
    gui.clearBottom(p);
    long seconds =
        s.game == null ? 0 : Math.max(0, (s.phaseEndsAt - System.currentTimeMillis() + 999) / 1000);
    for (var entry :
        WerewolfMenus.village(
            s.game instanceof WerewolfGame wolf ? wolf : null,
            p.getUniqueId(),
            s.players,
            s.config.name(),
            seconds,
            plugin.arenas.leaveBlocked(p.getUniqueId()))) {
      // Before the deal the compass still sits in hotbar slot 9: the lock moves next to it.
      int raw =
          entry.raw() == WerewolfMenus.LOCK && gui.compass(p.getInventory().getItem(8))
              ? WerewolfMenus.LOCK - 1
              : entry.raw();
      CompassGUI.place(inv, p, raw, item(gui, holder, entry));
      if (entry.click() != null) holder.clicks.put(raw, entry.click());
    }
  }

  public static Component line(WerewolfMenus.Line line) {
    return line.text().isEmpty()
        ? Component.empty()
        : Component.text(line.text(), TextColor.color(line.color()))
            .decoration(TextDecoration.ITALIC, false);
  }

  /** "Private · …": what only this player sees, in the chat journal. */
  public static Component whisper(String text, int color) {
    return Component.text("Private · ", TextColor.color(WerewolfPresentation.INK_DIM))
        .append(Component.text(text, TextColor.color(color)))
        .decoration(TextDecoration.ITALIC, false);
  }

  /** Plain item standing in for each pack icon when the player has no resource pack. */
  public static Material fallback(String icon) {
    if (icon == null) return Material.PAPER;
    if (icon.startsWith("card-")) return Material.PAPER;
    if (icon.startsWith("potion-life")) return Material.HONEY_BOTTLE;
    if (icon.startsWith("potion-death")) return Material.FERMENTED_SPIDER_EYE;
    return switch (icon) {
      case "moon" -> Material.GRAY_BED;
      case "target" -> Material.TARGET;
      case "heart" -> Material.RED_DYE;
      case "shield" -> Material.SHIELD;
      case "lock" -> Material.BARRIER;
      case "timer" -> Material.CLOCK;
      case "mark-wolf" -> Material.BONE;
      default -> Material.GRAY_DYE; // skip, mark-last
    };
  }

  private ItemStack item(CompassGUI gui, WerewolfMenuHolder holder, WerewolfMenus.Entry entry) {
    ItemStack item =
        new ItemStack(entry.head() != null ? Material.PLAYER_HEAD : fallback(entry.icon()));
    ItemMeta meta = item.getItemMeta();
    meta.displayName(line(entry.lines().getFirst()));
    meta.lore(entry.lines().stream().skip(1).map(WerewolfCompass::line).toList());
    if (entry.glint()) meta.setEnchantmentGlintOverride(true);
    if (entry.amount() > 1) meta.setMaxStackSize(99);
    if (entry.head() != null && meta instanceof SkullMeta skull) {
      var profile = gui.profile(entry.head());
      if (profile != null) skull.setOwnerProfile(profile.clone());
    }
    if (holder.skinned && entry.icon() != null)
      meta.setItemModel(new NamespacedKey("vampyuyu", "icon/" + entry.icon()));
    meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    item.setItemMeta(meta);
    item.setAmount(Math.max(1, Math.min(99, entry.amount())));
    return item;
  }

  /**
   * One player action in a werewolf game: "select:<uuid>", "aim:<uuid>" or "use:<ability>". Shared
   * by the menus and by clicking players and hotbar items (ClickControls).
   */
  public void act(Player p, ArenaSession s, WerewolfGame wolf, String click) {
    int color = WerewolfPresentation.color(wolf.selfRoleName(p.getUniqueId()));
    try {
      String response =
          click.startsWith("select:")
              ? wolf.select(p.getUniqueId(), UUID.fromString(click.substring(7)))
              : click.startsWith("aim:")
                  ? wolf.aim(p.getUniqueId(), UUID.fromString(click.substring(4)))
                  : wolf.use(p.getUniqueId(), click.substring(4));
      p.sendMessage(whisper(CompassGUI.friendly(response, s), color));
      String cue = WerewolfPresentation.actionSound(wolf.selfRoleName(p.getUniqueId()), click);
      if (cue != null && WerewolfOptions.of(s.config).rules().ambienceEnabled())
        WerewolfAmbience.cue(plugin, p, cue, Sound.UI_BUTTON_CLICK, .35f, 1.3f);
      else p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, .35f, 1.3f);
      plugin.gui.deliverNotices(s);
      plugin.arenas.phaseChanged(s);
      plugin.gui.refresh(p);
    } catch (IllegalArgumentException ex) {
      p.sendMessage(whisper(CompassGUI.friendly(ex.getMessage(), s), WerewolfPresentation.NO));
      p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, .35f, .7f);
    }
  }

  /** A private notice reaches its recipient as a whisper; the lovers' bond gets a title and sound. */
  public void notice(Player target, String message) {
    target.sendMessage(whisper(message, WerewolfPresentation.CUPID));
    if (!message.startsWith(WerewolfGame.LOVE_NOTICE)) return;
    target.showTitle(
        net.kyori.adventure.title.Title.title(
            Component.text("♥ In love", TextColor.color(WerewolfPresentation.CUPID)),
            Component.text(
                "Your lover's name is on the right of your screen",
                TextColor.color(WerewolfPresentation.INK))));
    target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .5f, 1.6f);
  }
}
