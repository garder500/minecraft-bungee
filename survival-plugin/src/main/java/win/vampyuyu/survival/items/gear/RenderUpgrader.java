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
 * Items created by {@code /sbgive}, other features' loot, an older renderer or an older stat schema (before the house
 * stats) carry the core's plain lore or stale stat bonuses. This
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
        // the catalog, and every other item with combat data (the ore ladder, the boss weapons): the full lore shows it
        boolean ours = gear.items().def(id.get()).map(def -> (def.itemModel() != null
            && def.itemModel().startsWith(ItemCatalog.MODEL)) || !def.stats().isEmpty() || def.element() != null
            || def.pdcDoubles().containsKey(Keys.DAMAGE)).orElse(false);
        if (!ours) {
            return false;
        }
        return gear.items().needsStatMigration(stack)
            || gear.items().getLong(stack, Keys.RENDERED).orElse(0L) != ItemRender.RENDER_VERSION;
    }

    /**
     * True for a plain custom item (a material, a machine: anything {@link #needsUpgrade} leaves alone) that still
     * carries the English-only name written before translations existed, while its name is translated now. Only that
     * old name (the plain English text) is replaced: a stack another feature renamed is never touched.
     */
    public boolean needsTranslation(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta() || needsUpgrade(stack)) {
            return false;
        }
        return gear.items().idOf(stack).flatMap(id -> gear.items().def(id)).map(def -> {
            net.kyori.adventure.text.Component now = stack.getItemMeta().hasItemName() ? stack.getItemMeta().itemName() : null;
            // the old name: the plain English text and nothing translatable in it (the colour is not compared: a stored
            // stack may spell it another way)
            return now != null && !hasTranslatable(now) && hasTranslatable(win.vampyuyu.survival.core.ItemRenderer.name(def))
                && def.name().equals(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(now));
        }).orElse(false);
    }

    private static boolean hasTranslatable(net.kyori.adventure.text.Component c) {
        if (c instanceof net.kyori.adventure.text.TranslatableComponent) {
            return true;
        }
        for (net.kyori.adventure.text.Component child : c.children()) {
            if (hasTranslatable(child)) {
                return true;
            }
        }
        return false;
    }

    /** The stack redrawn by whichever renderer owns it, or null when it is up to date. */
    private ItemStack redrawn(ItemStack stack) {
        if (needsUpgrade(stack)) {
            ItemStack copy = stack.clone();
            gear.rebuild(copy);
            return copy;
        }
        if (needsTranslation(stack)) {
            ItemStack copy = stack.clone();
            gear.items().refresh(copy);
            return copy;
        }
        return null;
    }

    /** Upgrades every stale stack of the player's inventory. Returns how many were redrawn. */
    public int upgrade(Player player) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack copy = redrawn(stack);
            if (copy != null) {
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
        ItemStack copy = redrawn(stack);
        if (copy != null) {
            event.getItem().setItemStack(copy);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItem(event.getNewSlot());
        ItemStack copy = redrawn(stack);
        if (copy != null) {
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
