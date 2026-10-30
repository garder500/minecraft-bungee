package win.vampyuyu.survival.api;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The Wings (a chestplate-slot elytra-like item). Implemented by the {@code wings} feature. */
public interface WingsApi {
    /** A fresh Wings item. */
    ItemStack create();

    /** True when the stack is a Wings item. */
    boolean isWings(ItemStack stack);

    /** True when the player currently wears Wings. */
    boolean isWearing(Player player);

    /**
     * Gives the player Wings once in their lifetime, whatever the source. Persisted. If the inventory is
     * full the item is dropped at the player's feet.
     *
     * @param source who grants them, e.g. "boss:grave_king" (stored for support)
     * @return false when the player already received Wings before
     */
    boolean grantOnce(Player player, String source);

    /** True when the player was ever given Wings through {@link #grantOnce}. */
    boolean hasReceived(UUID player);
}
