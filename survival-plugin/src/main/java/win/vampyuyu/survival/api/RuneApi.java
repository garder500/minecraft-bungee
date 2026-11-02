package win.vampyuyu.survival.api;

import java.util.Map;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The runes engraved on equipment (GDD 10). The stat part of a rune is written into the item's own bonuses
 * ({@code bonus_<stat>}), so every system that reads {@code Items.statsOf} already counts it; this API is for the effect
 * runes of the Realms, which need a hook in the combat code.
 */
public interface RuneApi {
    /** Rune id to rank (0 for a Realm rune, which has no rank) engraved on a stack; empty when there is none. */
    Map<String, Integer> runesOf(ItemStack stack);

    /** Called by the combat code when {@code attacker} lands a Surge on {@code target} with {@code weapon}. */
    void onSurge(Player attacker, LivingEntity target, ItemStack weapon);
}
