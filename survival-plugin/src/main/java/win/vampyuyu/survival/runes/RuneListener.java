package win.vampyuyu.survival.runes;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.runes.RuneService.Outcome;

/** The Runecraft table (a grindstone by default), engraving by clicking a rune onto an item, and the Verdant hook. */
final class RuneListener implements Listener {
    private final Core core;
    private final RuneService service;

    RuneListener(Core core, RuneService service) {
        this.core = core;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Material station = Material.matchMaterial(service.config().stationBlock());
        if (station == null || event.getClickedBlock().getType() != station
                || !SkyblockWorlds.isSkyblock(player.getWorld().getName())
                || player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        RunecraftMenu.open(core, service, player);
    }

    /** A rune on the cursor clicked onto a weapon or armor piece of the player's own inventory. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT
                || !(event.getClickedInventory() instanceof PlayerInventory)
                || event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.RESULT) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()
                || service.runeItem(cursor).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            core.msg().error(player, "Take the armor off before engraving it.");
            return;
        }
        ItemStack modified = target.clone();
        modified.setAmount(1);
        if (target.getAmount() != 1) {
            core.msg().error(player, "Engrave one item at a time.");
            return;
        }
        Outcome outcome = service.engrave(modified, cursor);
        if (!outcome.ok()) {
            core.msg().error(player, outcome.message());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        ItemStack left = cursor.clone();
        left.setAmount(cursor.getAmount() - 1);
        event.setCurrentItem(modified);
        event.setCursor(left.getAmount() <= 0 ? null : left);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.3f);
        core.msg().ok(player, outcome.message());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0
                && SkyblockWorlds.isSkyblock(player.getWorld().getName())) {
            service.onHurt(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }
}
