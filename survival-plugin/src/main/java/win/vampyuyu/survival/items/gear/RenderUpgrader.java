package win.vampyuyu.survival.items.gear;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.items.catalog.ItemCatalog;

/**
 * Items created by {@code /sbgive}, other features' loot or an older renderer carry the core's plain lore. This
 * redraws them with the full lore (abilities, set bonuses, enchants) the first time a player joins, picks them up,
 * opens an inventory or selects them. It never runs per tick.
 */
public final class RenderUpgrader implements Listener {
    private final Core core;
    private final Gear gear;

    public RenderUpgrader(Core core, Gear gear) {
        this.core = core;
        this.gear = gear;
    }

    /** True when the stack is one of ours and was not drawn by the current renderer. */
    public boolean needsUpgrade(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        Optional<String> id = gear.items().idOf(stack);
        if (id.isEmpty()) {
            return false;
        }
        boolean ours = gear.items().def(id.get()).map(def -> def.itemModel() != null
            && def.itemModel().startsWith(ItemCatalog.MODEL)).orElse(false);
        if (!ours) {
            return false;
        }
        return gear.items().getLong(stack, Keys.RENDERED).orElse(0L) != ItemRender.RENDER_VERSION;
    }

    /** Upgrades every stale stack of the player's inventory. Returns how many were redrawn. */
    public int upgrade(Player player) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (needsUpgrade(stack)) {
                ItemStack copy = stack.clone();
                gear.rebuild(copy);
                inventory.setItem(slot, copy);
                count++;
            }
        }
        return count;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later(10, () -> {
            if (player.isOnline()) {
                upgrade(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (needsUpgrade(stack)) {
            ItemStack copy = stack.clone();
            gear.rebuild(copy);
            event.getItem().setItemStack(copy);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItem(event.getNewSlot());
        if (needsUpgrade(stack)) {
            ItemStack copy = stack.clone();
            gear.rebuild(copy);
            player.getInventory().setItem(event.getNewSlot(), copy);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            core.scheduler().nextTick(() -> {
                if (player.isOnline()) {
                    upgrade(player);
                }
            });
        }
    }
}
