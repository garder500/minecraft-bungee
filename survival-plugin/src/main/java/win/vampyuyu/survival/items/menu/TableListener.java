package win.vampyuyu.survival.items.menu;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.items.ItemsSettings;

/**
 * Replaces the vanilla enchanting table and anvil (and optionally the smithing table as blacksmith) with the custom
 * menus, in Skyblock worlds only. Sneaking with an item in hand still places blocks like vanilla.
 */
public final class TableListener implements Listener {
    private final Supplier<ItemsSettings> settings;
    private final BiConsumer<Player, Kind> opener;

    /** Which custom menu to open. */
    public enum Kind { ENCHANT, ANVIL, REFORGE }

    public TableListener(Supplier<ItemsSettings> settings, BiConsumer<Player, Kind> opener) {
        this.settings = settings;
        this.opener = opener;
    }

    private static boolean skyblock(Player player) {
        return player.getWorld() != null && SkyblockWorlds.isSkyblock(player.getWorld().getName());
    }

    /** The menu a block opens, or null. */
    public Kind kindOf(Material material) {
        ItemsSettings s = settings.get();
        if (material == Material.ENCHANTING_TABLE && s.b("menus.replace-enchanting-table", true)) {
            return Kind.ENCHANT;
        }
        if ((material == Material.ANVIL || material == Material.CHIPPED_ANVIL || material == Material.DAMAGED_ANVIL)
                && s.b("menus.replace-anvil", true)) {
            return Kind.ANVIL;
        }
        if (material == Material.SMITHING_TABLE && s.b("menus.smithing-table-is-blacksmith", false)) {
            return Kind.REFORGE;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!skyblock(player)) {
            return;
        }
        Kind kind = kindOf(event.getClickedBlock().getType());
        if (kind == null) {
            return;
        }
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        opener.accept(player, kind);
    }

    /** Safety net for any other way to open the vanilla screens (commands, other plugins). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !skyblock(player)) {
            return;
        }
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.ENCHANTING && settings.get().b("menus.replace-enchanting-table", true)) {
            event.setCancelled(true);
            opener.accept(player, Kind.ENCHANT);
        } else if (type == InventoryType.ANVIL && settings.get().b("menus.replace-anvil", true)) {
            event.setCancelled(true);
            opener.accept(player, Kind.ANVIL);
        }
    }
}
