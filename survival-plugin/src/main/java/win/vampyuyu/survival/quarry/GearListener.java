package win.vampyuyu.survival.quarry;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.core.Core;

/** The tier armor set event, the Endless Bucket that never empties, and the Generator Plan that reads /guide generators. */
final class GearListener implements Listener {
    private static final String[] SLOTS = {"helmet", "chestplate", "leggings", "boots"};

    private final Core core;

    GearListener(Core core) {
        this.core = core;
    }

    /** A player wearing four pieces of one tier is "wearing the full set": quests count it once per change. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack[] worn = player.getInventory().getArmorContents(); // boots, leggings, chestplate, helmet
        String tier = null;
        for (int i = 0; i < worn.length; i++) {
            String id = core.items().idOf(worn[i]).orElse(null);
            String slot = SLOTS[3 - i];
            if (id == null || !id.endsWith("_" + slot)) {
                return;
            }
            String found = id.substring(0, id.length() - slot.length() - 1);
            if (tier != null && !tier.equals(found)) {
                return;
            }
            tier = found;
        }
        if (tier != null) {
            Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "EVENT", "armor_set:" + tier, 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (held != null && core.items().idOf(held).map(QuarryItems.ENDLESS_BUCKET::equals).orElse(false)) {
            event.setItemStack(held.clone()); // pours the water, keeps the full bucket
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlan(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack held = event.getItem();
        if (held != null && core.items().idOf(held).map(QuarryItems.GENERATOR_PLAN::equals).orElse(false)) {
            event.setCancelled(true);
            event.getPlayer().performCommand("guide generators");
        }
    }
}
