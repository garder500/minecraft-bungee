package win.vampyuyu.survival.items.menu;

import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;

/**
 * A menu whose slots can hold items the player puts in (enchant table, anvil, reforge, accessory bag). The core
 * {@code Gui} cannot do that, so these menus own their inventory and handle every click themselves: the global
 * {@link ItemMenuListener} cancels each click and drag and calls {@link #click}; the menu moves items explicitly.
 * Items held in input slots are returned to the player when the menu closes.
 */
public abstract class ItemMenu implements InventoryHolder {
    protected final Core core;
    protected final Player player;
    protected final Inventory inventory;
    private boolean closed;

    protected ItemMenu(Core core, Player player, int rows, Component title) {
        this.core = core;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public Player player() { return player; }

    public void open() {
        player.openInventory(inventory);
    }

    /** Handles a click. The event is already cancelled. {@code top} is true when the click is on this menu's inventory. */
    protected abstract void click(InventoryClickEvent event, boolean top);

    /** Called once when the menu closes (player closed it, quit, plugin disabled). */
    protected void closed(InventoryCloseEvent event) { }

    /** Called by the listener. */
    final void dispatchClose(InventoryCloseEvent event) {
        if (!closed) {
            closed = true;
            closed(event);
        }
    }

    final void dispatchClick(InventoryClickEvent event) {
        click(event, event.getClickedInventory() != null && event.getClickedInventory().equals(inventory));
    }

    void drag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    // ---------------------------------------------------------------- helpers

    /** Syncs the client after we moved items under a cancelled click. */
    protected final void refresh() {
        core.scheduler().nextTick(() -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        });
    }

    /**
     * Standard behaviour of an input slot: left / right click swaps the cursor with the slot (the cursor item must
     * satisfy {@code accept} and be a single item), shift click sends the slot's item to the player.
     *
     * @return true when something moved
     */
    protected final boolean inputSlot(InventoryClickEvent event, int slot, Predicate<ItemStack> accept) {
        ClickType click = event.getClick();
        ItemStack cursor = player.getItemOnCursor();
        ItemStack current = inventory.getItem(slot);
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            if (current == null || current.getType().isAir()) {
                return false;
            }
            inventory.setItem(slot, null);
            giveBack(current);
            refresh();
            return true;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT) {
            return false;
        }
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        if (cursorEmpty) {
            if (current == null || current.getType().isAir()) {
                return false;
            }
            inventory.setItem(slot, null);
            player.setItemOnCursor(current);
            refresh();
            return true;
        }
        if (cursor.getAmount() != 1 || !accept.test(cursor)) {
            return false;
        }
        inventory.setItem(slot, cursor.clone());
        player.setItemOnCursor(current == null || current.getType().isAir() ? null : current);
        refresh();
        return true;
    }

    /** Puts an item from the player's own inventory into the first free input slot that accepts it. */
    protected final boolean moveFromPlayer(InventoryClickEvent event, int[] slots, Predicate<ItemStack> accept) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir() || item.getAmount() != 1 || !accept.test(item)) {
            return false;
        }
        for (int slot : slots) {
            ItemStack there = inventory.getItem(slot);
            if (there == null || there.getType().isAir()) {
                inventory.setItem(slot, item.clone());
                event.setCurrentItem(null);
                refresh();
                return true;
            }
        }
        return false;
    }

    /** Gives an item to the player, dropping what does not fit at their feet. */
    protected final void giveBack(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** Returns the contents of the given slots to the player (menu closing). */
    protected final void returnSlots(int... slots) {
        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            inventory.setItem(slot, null);
            giveBack(item);
        }
    }
}
