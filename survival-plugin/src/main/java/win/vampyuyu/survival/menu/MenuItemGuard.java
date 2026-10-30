package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import win.vampyuyu.survival.core.Cooldowns;

/**
 * Keeps exactly one untouchable Skyblock Menu item in the configured hotbar slot of players standing in a Skyblock
 * world, and none anywhere else. The item cannot be dropped, moved, stored, crafted with, worn or lost on death.
 * It is a nether star underneath, so every path that could move or consume one is covered: inventory clicks (hotbar
 * swaps, drops, double click collect, creative), drags, drops, hand swaps, entity and armour stand interaction,
 * crafting / anvil / smithing previews, death drops and dropped item entities.
 */
final class MenuItemGuard implements Listener {
    private final MenuRuntime rt;
    private final SkyblockMenuItem item;
    private final Consumer<Player> opener;
    private final Cooldowns clicks = new Cooldowns();
    private final java.util.Set<UUID> warnedFull = java.util.concurrent.ConcurrentHashMap.newKeySet();

    MenuItemGuard(MenuRuntime rt, SkyblockMenuItem item, Consumer<Player> opener) {
        this.rt = rt;
        this.item = item;
        this.opener = opener;
    }

    // ---------------------------------------------------------------- placement

    private boolean enabled() {
        return rt.config().menuItemEnabled;
    }

    /** Applies the rule for the player's current world: one menu item in its slot, or none. */
    void sync(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (enabled() && MenuRuntime.inSkyblock(player.getWorld())) {
            ensure(player);
        } else {
            strip(player);
        }
    }

    /** Sweeps stray copies and puts exactly one in the slot. Never deletes a player's own item. */
    void ensure(Player player) {
        PlayerInventory inv = player.getInventory();
        int slot = rt.config().menuItemSlot;
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != slot && item.is(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (item.is(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        if (item.is(inv.getItem(slot))) {
            ItemStack current = inv.getItem(slot);
            if (current.getAmount() != 1) {
                current.setAmount(1);
            }
            warnedFull.remove(player.getUniqueId());
            return;
        }
        ItemStack occupant = inv.getItem(slot);
        if (occupant != null && !occupant.getType().isAir()) {
            int free = firstFreeOutside(inv, slot);
            if (free < 0) {
                if (warnedFull.add(player.getUniqueId())) {
                    rt.core.msg().warn(player, "Your hotbar slot " + (slot + 1)
                        + " is occupied and your inventory is full. Free a slot to get your Skyblock Menu.");
                }
                return;
            }
            inv.setItem(free, occupant);
        }
        inv.setItem(slot, item.create());
        warnedFull.remove(player.getUniqueId());
    }

    private static int firstFreeOutside(PlayerInventory inv, int skip) {
        for (int i = 0; i < 36; i++) {
            if (i != skip) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || stack.getType().isAir()) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Removes every menu item from the player (inventory, armour, off hand, cursor). */
    void strip(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (item.is(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (item.is(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        warnedFull.remove(player.getUniqueId());
    }

    void stripEveryone() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            strip(player);
        }
    }

    void forget(UUID id) {
        warnedFull.remove(id);
        clicks.clear(id, "open");
    }

    // ---------------------------------------------------------------- opening

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!item.is(event.getItem())) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            open(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (item.is(player.getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                open(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (item.is(event.getPlayerItem()) || item.is(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    private void open(Player player) {
        if (!clicks.tryUse(player.getUniqueId(), "open", rt.config().menuClickCooldownMs)) {
            return;
        }
        opener.accept(player);
    }

    // ---------------------------------------------------------------- the untouchable rules

    private boolean isMenu(ItemStack stack) {
        return item.is(stack);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (touchesMenu(event, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (touchesMenu(event, player)) {
            event.setCancelled(true);
        }
    }

    private boolean touchesMenu(InventoryClickEvent event, Player player) {
        if (isMenu(event.getCurrentItem()) || isMenu(event.getCursor())) {
            return true;
        }
        ClickType click = event.getClick();
        if (click == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            if (button >= 0 && button <= 8 && isMenu(player.getInventory().getItem(button))) {
                return true;
            }
        }
        if (click == ClickType.SWAP_OFFHAND && isMenu(player.getInventory().getItemInOffHand())) {
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (isMenu(event.getOldCursor()) || isMenu(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        for (ItemStack stack : event.getNewItems().values()) {
            if (isMenu(stack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isMenu(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isMenu(event.getMainHandItem()) || isMenu(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (isMenu(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenu);
        List<ItemStack> keep = new ArrayList<>(event.getItemsToKeep());
        if (keep.removeIf(this::isMenu)) {
            event.getItemsToKeep().clear();
            event.getItemsToKeep().addAll(keep);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftPreview(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (isMenu(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilPreview(PrepareAnvilEvent event) {
        Inventory inv = event.getInventory();
        if (isMenu(inv.getItem(0)) || isMenu(inv.getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithingPreview(PrepareSmithingEvent event) {
        Inventory inv = event.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isMenu(inv.getItem(i))) {
                event.setResult(null);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- keeping it in sync

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        rt.core.scheduler().later(2, () -> sync(player));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        sync(event.getPlayer());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            rt.core.scheduler().nextTick(() -> sync(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // The saved inventory must not carry the item into a non-Skyblock world on the next login.
        strip(event.getPlayer());
        forget(event.getPlayer().getUniqueId());
    }
}
