package win.vampyuyu.survival.wings;

import com.destroystokyo.paper.event.inventory.PrepareGrindstoneEvent;
import java.util.Iterator;
import java.util.UUID;
import org.bukkit.Tag;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.Inventory;
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

    private boolean core(ItemStack stack) {
        return service.wingsItems().coreTier(stack) > 0;
    }

    /** Wings may only live in a player inventory or an ender chest: that is what the scan and the claim check. */
    static boolean storesWings(Inventory inv) {
        InventoryType t = inv.getType();
        return t == InventoryType.PLAYER || t == InventoryType.CRAFTING || t == InventoryType.ENDER_CHEST;
    }

    static boolean isBundle(ItemStack s) {
        return s != null && s.getType().name().endsWith("BUNDLE");
    }

    static boolean isShulker(ItemStack s) {
        return s != null && Tag.SHULKER_BOXES.isTagged(s.getType());
    }

    private void refuse(org.bukkit.entity.HumanEntity who) {
        if (who instanceof Player p) {
            service.core().msg().warn(p, "The Wings are bound to you: they cannot be stored in containers or bundles.");
        }
    }

    // ---------------------------------------------------------------- containers, shulker boxes, bundles

    /** Every way a click can put Wings into a foreign container, a shulker box or a bundle. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if ((wings(cursor) && isBundle(current)) || (isBundle(cursor) && wings(current))
            || (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player hp
            && ((wings(hp.getInventory().getItem(event.getHotbarButton())) && isBundle(current))
            || (isBundle(hp.getInventory().getItem(event.getHotbarButton())) && wings(current))))) {
            event.setCancelled(true);
            refuse(event.getWhoClicked());
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (storesWings(top)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        boolean into = false;
        if (clicked.equals(top)) {
            into = wings(cursor);
            if (!into && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && event.getWhoClicked() instanceof Player p) {
                into = wings(p.getInventory().getItem(event.getHotbarButton()));
            }
            if (!into && event.getClick() == ClickType.SWAP_OFFHAND && event.getWhoClicked() instanceof Player p) {
                into = wings(p.getInventory().getItemInOffHand());
            }
        } else if (event.isShiftClick()) {
            into = wings(current);  // shift click from the player's inventory goes to the top inventory
        }
        if (into) {
            event.setCancelled(true);
            refuse(event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!wings(event.getOldCursor()) && !wings(event.getCursor())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (storesWings(top)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                event.setCancelled(true);
                refuse(event.getWhoClicked());
                return;
            }
        }
    }

    /** Hoppers, droppers and minecarts never move Wings between containers. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (wings(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        var inv = event.getBlock().getState() instanceof org.bukkit.block.Crafter c ? c.getInventory() : null;
        if (inv == null) {
            return;
        }
        for (ItemStack s : inv.getContents()) {
            if (wings(s) || core(s)) {
                event.setCancelled(true);
                return;
            }
        }
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
            if (wings(s) || core(s)) {  // cores are amethyst shards: they are also a trim material
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        var inv = event.getInventory();
        for (ItemStack s : inv.getMatrix()) {
            if (wings(s) || core(s)) {  // cores are amethyst shards: no spyglass, tinted glass or block either
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
