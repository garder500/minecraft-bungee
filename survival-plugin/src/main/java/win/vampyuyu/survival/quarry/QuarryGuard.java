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

    /**
     * The recipes of the Quarry (pebbles, the Fountain, bars, ...) are the one place a custom material may be an
     * ingredient: their ingredients are exact items, so a look-alike vanilla item never matches them.
     */
    static boolean isQuarryRecipe(org.bukkit.inventory.Recipe recipe) {
        return recipe instanceof org.bukkit.Keyed keyed && !keyed.getKey().getNamespace().equals("minecraft")
            && QuarryFeature.RECIPE_KEYS.contains(keyed.getKey().getKey());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (isQuarryRecipe(event.getRecipe())) {
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
