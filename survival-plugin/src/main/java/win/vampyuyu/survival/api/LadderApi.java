package win.vampyuyu.survival.api;

import java.util.Collection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The ore ladder weapon damage, for abilities that deal weapon damage without a swing (classes). */
public interface LadderApi {
    /** True when the stack is a ladder weapon (a sword, a bow). */
    boolean isWeapon(ItemStack stack);

    /**
     * Hits every target for {@code share} of the weapon's damage (Might and the weapon's element against each target's
     * weakness included), as magic damage caused by the player. Returns how many were hit.
     */
    int burst(Player player, ItemStack weapon, Collection<? extends LivingEntity> targets, double share);

    /** Marks an arrow so that it lands for {@code share} of the bow's damage at full draw. False when this is not a ladder bow. */
    boolean tagArrow(Player player, ItemStack bow, AbstractArrow arrow, double share);
}
