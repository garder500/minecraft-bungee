package win.vampyuyu.survival.items.ability;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.stats.PlayerState;

/** Everything an ability executor needs. */
public record AbilityContext(AbilityEnv env, Player player, ItemStack item, GearInfo info, PlayerState state,
        Ability ability, @Nullable Event event) {

    /** Resolved parameter (config override, else the ability default, else {@code fallback}). */
    public double param(String key) {
        return env.param(ability, key);
    }

    /** True when {@code entity} may be hurt or moved by an ability of this player. */
    public boolean targetable(@Nullable LivingEntity entity) {
        return env.targetable(player, entity);
    }

    public Location eye() { return player.getEyeLocation(); }
}
