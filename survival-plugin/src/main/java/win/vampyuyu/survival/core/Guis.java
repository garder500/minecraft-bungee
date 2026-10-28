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

    /** Creates a menu of 1-6 rows. */
    public Gui create(int rows, Component title) {
        return new Gui(this, rows, title);
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
            if (top != null && top.getHolder() instanceof Gui) {
                player.closeInventory();
            }
        }
    }

    private static Gui guiOf(Inventory top) {
        return top.getHolder() instanceof Gui gui ? gui : null;
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
