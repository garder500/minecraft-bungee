package win.vampyuyu.survival.items.menu;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Global listener behind every {@link ItemMenu}. */
public final class ItemMenuListener implements Listener {
    private final Logger log;

    public ItemMenuListener(Logger log) {
        this.log = log;
    }

    private static ItemMenu menuOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder;
        try {
            holder = inventory.getHolder(false);
        } catch (RuntimeException | AbstractMethodError e) {
            holder = inventory.getHolder();
        }
        return holder instanceof ItemMenu menu ? menu : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        ItemMenu menu = menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.equals(menu.player())) {
            return;
        }
        try {
            menu.dispatchClick(event);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Item menu click failed", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        ItemMenu menu = menuOf(event.getView().getTopInventory());
        if (menu != null) {
            menu.drag(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        ItemMenu menu = menuOf(event.getInventory());
        if (menu != null) {
            try {
                menu.dispatchClose(event);
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "Item menu close failed", e);
            }
        }
    }

    /** Closes every open item menu (plugin disable) so the menus hand their items back. */
    public static void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (menuOf(player.getOpenInventory().getTopInventory()) != null) {
                player.closeInventory();
            }
        }
    }
}
