package win.vampyuyu.survival.wings;

import com.destroystokyo.paper.event.inventory.PrepareGrindstoneEvent;
import java.util.Iterator;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the Wings what they are: not craftable, not editable (anvil, grindstone, smithing, enchanting), not
 * transferable (only the owner can pick one up, a stranger's pick up sends it home), kept through death and
 * indestructible as a dropped item.
 */
public final class WingsGuard implements Listener {
    private final WingsService service;

    public WingsGuard(WingsService service) {
        this.service = service;
    }

    private boolean wings(ItemStack stack) {
        return service.wingsItems().isWings(stack);
    }

    // ---------------------------------------------------------------- dropped items

    /** A dropped Wings never despawns, never burns, glows, and can only be taken by its owner. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        WingsItems.Tag tag = service.wingsItems().read(item.getItemStack());
        if (tag == null) {
            return;
        }
        // each property on its own: a server that lacks one (test doubles) must not lose the others
        safely(() -> item.setUnlimitedLifetime(true));
        safely(() -> item.setInvulnerable(true));
        safely(() -> item.setGlowing(true));
        if (tag.owner() != null) {
            safely(() -> item.setOwner(tag.owner()));
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // not supported by this server implementation (test doubles); the other properties still apply
        }
    }

    /** Picking up: the owner may, everybody else (and every mob) is refused and the item goes back to its owner. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        WingsItems.Tag tag = service.wingsItems().read(stack);
        if (tag == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (tag.unbound() || player.getUniqueId().equals(tag.owner())) {
            return;
        }
        event.setCancelled(true);
        event.getItem().remove();
        service.returnToOwner(tag.owner(), tag.uuid(), stack);
        service.core().msg().warn(player, "Those Wings are bound to someone else and flew back to their owner.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (wings(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (wings(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- no editing

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        var inv = event.getInventory();
        if (wings(inv.getFirstItem()) || wings(inv.getSecondItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        var inv = event.getInventory();
        if (wings(inv.getUpperItem()) || wings(inv.getLowerItem())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        var inv = event.getInventory();
        for (ItemStack s : inv.getContents()) {
            if (wings(s)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        var inv = event.getInventory();
        for (ItemStack s : inv.getMatrix()) {
            if (wings(s)) {
                inv.setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onEnchant(PrepareItemEnchantEvent event) {
        if (wings(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (wings(event.getPlayerItem()) || wings(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- death

    /** The Wings never drop on death: a fresh copy is handed back on respawn (the old id turns inert). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Player player = event.getEntity();
        WingsData data = service.data(player.getUniqueId());
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            WingsItems.Tag tag = service.wingsItems().read(stack);
            if (tag == null) {
                continue;
            }
            it.remove();
            if (player.getUniqueId().equals(tag.owner()) && !data.activeId.isEmpty()
                && data.activeId.equals(tag.uuid())) {
                data.pendingReturn = true;
                service.core().profiles().markDirty(player.getUniqueId());
            } else if (tag.owner() != null) {
                UUID owner = tag.owner();
                service.returnToOwner(owner, tag.uuid(), stack);
            }
        }
    }
}
