package win.vampyuyu.survival.core;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * GUI service and the single global listener behind every {@link Gui}. A click or drag is cancelled whenever
 * the player's TOP inventory is one of our menus, wherever the click lands (shift-click from the player's own
 * inventory, number keys, double click collect...): nothing can be moved in or out of a menu.
 */
public final class Guis implements Listener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    final Scheduler scheduler;
    private final Logger log;

    public Guis(Scheduler scheduler, Logger log) {
        this.scheduler = scheduler;
        this.log = log;
    }

    /**
     * Creates a menu of 1-6 rows with a painted background: 3 rows or fewer get the small window, bigger menus the
     * 6-row panel (the slot numbers of a 4 or 5 row layout stay valid, the bottom rows are just empty).
     */
    public Gui create(int rows, Component title) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1-6, got " + rows);
        }
        return create(MenuArt.forRows(rows), title);
    }

    /** Creates a menu on a given painted background (its chest size follows the picture). */
    public Gui create(MenuArt.Kind kind, Component title) {
        return new Gui(this, kind.chestRows(), MenuArt.title(kind, title));
    }

    /** Same with MiniMessage markup for the title text. */
    public Gui create(MenuArt.Kind kind, String miniTitle) {
        return create(kind, MINI.deserialize(miniTitle).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    /** Creates a menu whose title is MiniMessage markup. */
    public Gui create(int rows, String miniTitle) {
        return create(rows, MINI.deserialize(miniTitle).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
    }

    /**
     * An icon with a MiniMessage name and lore lines, italics off, attribute/other tooltips hidden.
     * Plain text and tooltips only: menu text must stay English.
     */
    public static ItemStack icon(Material material, String miniName, String... miniLore) {
        List<Component> lore = new ArrayList<>();
        for (String line : miniLore) {
            lore.add(MINI.deserialize(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        return icon(material, MINI.deserialize(miniName).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE), lore);
    }

    /** Accent colour of the action line of a tooltip. */
    public static final String ACCENT = "<#F6D58E>";

    /**
     * A menu button: name (coloured by meaning, MiniMessage), ONE short grey description line, then
     * {@code ▸ action} in the accent colour (skipped when {@code action} is null). Extra lines go through
     * {@link #icon} directly.
     */
    public static ItemStack button(Material material, String miniName, String description, String action) {
        List<String> lore = new ArrayList<>();
        if (description != null && !description.isEmpty()) {
            lore.add("<gray>" + description);
        }
        if (action != null) {
            lore.add(ACCENT + "▸ " + action);
        }
        return icon(material, miniName, lore.toArray(String[]::new));
    }

    /** Compact progress text, {@code 12/30 · 40%}. */
    public static String progress(long done, long total) {
        long pct = total <= 0 ? 0 : Math.min(100, done * 100 / total);
        return done + "/" + total + " · " + pct + "%";
    }

    /** The pack's item model {@code vampyuyu:<path>} on an item (a vanilla look without the pack). */
    public static ItemStack model(ItemStack item, String path) {
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new org.bukkit.NamespacedKey("vampyuyu", path));
        item.setItemMeta(meta);
        return item;
    }

    /** A button with a menu icon model {@code vampyuyu:menu/<name>} (textures in the pack's item/menu folder). */
    public static ItemStack menuButton(Material fallback, String menuModel, String miniName, String description,
            String action, String... extra) {
        List<String> lore = new ArrayList<>();
        if (description != null && !description.isEmpty()) {
            lore.add("<gray>" + description);
        }
        lore.addAll(List.of(extra));
        if (action != null) {
            lore.add(ACCENT + "▸ " + action);
        }
        return model(icon(fallback, miniName, lore.toArray(String[]::new)), "menu/" + menuModel);
    }

    /** The Back button: a small arrow (item model {@code vampyuyu:icon/back}). */
    public static ItemStack backIcon(String label) {
        return model(icon(Material.ARROW, "<white>Back", label == null || label.isEmpty() ? new String[0]
            : new String[] {"<gray>" + label}), "icon/back");
    }

    /** A small Previous/Next arrow ({@code icon/back} or {@code icon/next}). */
    public static ItemStack arrow(String name, String page, String model) {
        return model(icon(Material.ARROW, "<white>" + name, "<gray>" + page), "icon/" + model);
    }

    /** Same with components. */
    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** Closes every open menu (called on disable so nobody keeps a dead inventory). */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (guiOf(top) != null) {
                player.closeInventory();
            }
        }
    }

    /** Set once if the server (MockBukkit) does not implement {@code getHolder(boolean)}. */
    private static volatile boolean snapshotHolderOnly;

    /**
     * The holder without building a block state snapshot of real chests (Paper's {@code getHolder(false)}), which
     * matters because this runs for EVERY inventory click, drag and close on the server.
     */
    static org.bukkit.inventory.InventoryHolder holderOf(Inventory inventory) {
        if (!snapshotHolderOnly) {
            try {
                return inventory.getHolder(false);
            } catch (RuntimeException | AbstractMethodError e) {
                snapshotHolderOnly = true;
            }
        }
        return inventory.getHolder();
    }

    private static Gui guiOf(Inventory top) {
        return top != null && holderOf(top) instanceof Gui gui ? gui : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Gui gui = guiOf(top);
        if (gui == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Gui.ClickHandler handler = gui.handler(event.getSlot());
        if (handler == null) {
            return;
        }
        try {
            handler.onClick(new Gui.Click(player, gui, event.getSlot(), event.getClick()));
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Menu click handler failed", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (guiOf(top) == null) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Gui gui = guiOf(event.getInventory());
        if (gui != null && event.getPlayer() instanceof Player player) {
            try {
                gui.closed(player);
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Menu close handler failed", e);
            }
        }
    }
}
