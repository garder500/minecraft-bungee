package win.vampyuyu.survival.quarry;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;

/** Quarry items borrow the look of a vanilla material: they must never act as that material in a recipe or a furnace. */
final class QuarryGuard implements Listener {
    private final Core core;

    QuarryGuard(Core core) {
        this.core = core;
    }

    private boolean custom(ItemStack stack) {
        return core.items().idOf(stack).isPresent();
    }

    /** The pebble recipe is the one place a Quarry item may be an ingredient: 9 pebbles make a cobblestone. */
    static boolean isPebbleRecipe(org.bukkit.inventory.Recipe recipe) {
        return recipe instanceof org.bukkit.Keyed keyed && keyed.getKey().getKey().equals(QuarryFeature.PEBBLE_RECIPE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (isPebbleRecipe(event.getRecipe())) {
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && custom(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        if (custom(event.getSource())) {
            event.setCancelled(true);
        }
    }
}
