package win.vampyuyu.survival.core;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gives the custom gear made before the durability rework its new durability: when it would lose a point, and for
 * everything a player carries when they join.
 */
final class DurabilityUpgrade implements Listener {
    private final Items items;

    DurabilityUpgrade(Items items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        items.upgradeDurability(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        for (ItemStack stack : event.getPlayer().getInventory().getContents()) {
            if (stack != null) {
                items.upgradeDurability(stack);
            }
        }
    }
}
