package win.vampyuyu.survival.api;

import java.util.List;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.event.BossKillEvent;

/** Lets another feature add drops to a boss kill (for example the wings drop). */
@FunctionalInterface
public interface BossLootHook {
    /**
     * Called once per participant after a boss dies.
     *
     * @param event the kill (boss id, top damagers, location)
     * @param recipient a player who dealt damage; the returned stacks go to their inventory
     * @return extra items for this recipient, possibly empty, never null
     */
    List<org.bukkit.inventory.ItemStack> roll(BossKillEvent event, Player recipient);
}
