package win.vampyuyu.survival.gadgets;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.quarry.OreRoller;
import win.vampyuyu.survival.quarry.QuarryService;

/** Events of the six utility items. */
final class GadgetListener implements Listener {
    private final Core core;
    private final Gadgets gadgets;
    private final SatchelService satchel;

    GadgetListener(Core core, Gadgets gadgets, SatchelService satchel) {
        this.core = core;
        this.gadgets = gadgets;
        this.satchel = satchel;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null) {
            return;
        }
        String id = core.items().idOf(held).orElse("");
        Player player = event.getPlayer();
        switch (id) {
            case GadgetItems.SKYHOOK -> {
                event.setCancelled(true);
                RayTraceResult hit = player.rayTraceBlocks(gadgets.settings().skyhookRange(), FluidCollisionMode.NEVER);
                if (hit == null || hit.getHitBlock() == null || !hit.getHitBlock().getType().isSolid()) {
                    core.msg().warn(player, "No block to hook within " + gadgets.settings().skyhookRange() + " blocks.");
                } else {
                    gadgets.skyhook(player, hit.getHitPosition().toLocation(player.getWorld()));
                }
            }
            case GadgetItems.CLOUD_ANCHOR -> {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    gadgets.setAnchor(player);
                } else {
                    gadgets.startReturn(player);
                }
            }
            case GadgetItems.ORE_COMPASS -> {
                event.setCancelled(true);
                gadgets.readCompass(player);
            }
            case GadgetItems.RAIN_TOTEM -> {
                event.setCancelled(true);
                if (gadgets.startTotem(player, player.getLocation())) {
                    held.setAmount(held.getAmount() - 1);
                }
            }
            case GadgetItems.SATCHEL -> {
                event.setCancelled(true);
                player.openInventory(satchel.bag(player.getUniqueId()));
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player p
                && gadgets.fallProtected(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gadgets.forget(event.getPlayer().getUniqueId());
        satchel.release(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- Satchel

    private boolean hasSatchel(Player player) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && core.items().is(stack, GadgetItems.SATCHEL)) {
                return true;
            }
        }
        return false;
    }

    /** Keys of every ore the generators give: {@code custom:<id>} or a Material name. */
    private Set<String> oreKeys() {
        Set<String> keys = new HashSet<>();
        core.service(QuarryService.class).ifPresent(q -> {
            for (OreRoller.Ore ore : q.config().roller.ores()) {
                keys.add(ore.item());
            }
            for (OreRoller.Ore ore : q.config().outputs.ores()) {
                keys.add(ore.item());
            }
        });
        return keys;
    }

    static boolean fits(PlayerInventory inv, ItemStack stack) {
        int room = 0;
        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                room += stack.getMaxStackSize();
            } else if (slot.isSimilar(stack)) {
                room += slot.getMaxStackSize() - slot.getAmount();
            }
            if (room >= stack.getAmount()) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        if (!hasSatchel(player)) {
            return;
        }
        Material type = stack.getType();
        boolean custom = core.items().isCustom(stack);
        String key = custom ? "custom:" + core.items().idOf(stack).orElse("") : type.name();
        boolean ore = oreKeys().contains(key);
        boolean overflow = !custom && gadgets.settings().satchelOverflow().contains(type) && !fits(player.getInventory(), stack);
        if (!ore && !overflow) {
            return;
        }
        int before = stack.getAmount();
        int left = satchel.add(player.getUniqueId(), stack);
        if (left == before) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.4f);
        if (left == 0) {
            event.setCancelled(true);
            item.remove();
        } else {
            stack.setAmount(left);
            item.setItemStack(stack);
        }
    }

    private static boolean isBag(org.bukkit.inventory.Inventory inv) {
        return inv != null && inv.getHolder() instanceof SatchelService.Holder;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!isBag(event.getView().getTopInventory())) {
            return;
        }
        boolean intoBag = isBag(event.getClickedInventory()) || event.isShiftClick();
        ItemStack moved = event.getClick().isKeyboardClick() && event.getHotbarButton() >= 0
            ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if (core.items().is(event.getCursor(), GadgetItems.SATCHEL) && isBag(event.getClickedInventory())
                || core.items().is(event.getCurrentItem(), GadgetItems.SATCHEL) && intoBag
                || core.items().is(moved, GadgetItems.SATCHEL) && isBag(event.getClickedInventory())) {
            event.setCancelled(true);
            return;
        }
        core.scheduler().nextTick(() -> satchel.save(((SatchelService.Holder) event.getView().getTopInventory().getHolder()).owner));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (isBag(event.getView().getTopInventory())) {
            if (core.items().is(event.getOldCursor(), GadgetItems.SATCHEL)) {
                event.setCancelled(true);
                return;
            }
            core.scheduler().nextTick(() -> satchel.save(((SatchelService.Holder) event.getView().getTopInventory().getHolder()).owner));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SatchelService.Holder holder) {
            satchel.save(holder.owner);
        }
    }
}
