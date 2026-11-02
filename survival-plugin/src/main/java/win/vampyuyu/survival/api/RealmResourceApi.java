package win.vampyuyu.survival.api;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * The local resources of the Realms (GDD 3.2): Cinderheart, Heartwood... Other features (the Bastions, the Guardians)
 * call {@link #roll} when a source of theirs should feed a resource; the built-in sources (blocks and mobs of the table
 * {@code realm_resource_source}) are handled by the realm feature itself.
 */
public interface RealmResourceApi {
    /** Ids of the resources of this Realm. */
    List<String> resourcesOf(Realm realm);

    /**
     * One draw of the resource with this player's pity counter for that source kind ({@code "block"}, {@code "mob"} or a
     * kind of your own). Returns the drop, or an empty list. The counter moves; the items are not given.
     */
    List<ItemStack> roll(UUID player, String resourceId, String kind);

    /** Draws since the last drop for this resource and kind (the pity counter). */
    int misses(UUID player, String resourceId, String kind);

    /** The chance of the next draw, from the pity curve. */
    double chance(UUID player, String resourceId, String kind);

    /** The Realm a world belongs to, or null. */
    Realm realmOf(String worldName);
}
