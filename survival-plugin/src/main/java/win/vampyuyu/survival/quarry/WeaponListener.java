package win.vampyuyu.survival.quarry;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.core.Core;

/** The two small weapon effects of the first gear: Rend (Bronze Cleaver) and Frostbite (Rimeblade). Skyblock only. */
final class WeaponListener implements Listener {
    private final Core core;

    WeaponListener(Core core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || !CobbleListener.skyblock(player.getLocation().getBlock())) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = core.items().idOf(held).orElse("");
        if (id.equals(QuarryItems.RIMEBLADE)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        } else if (id.equals(QuarryItems.BRONZE_CLEAVER)) {
            for (int i = 1; i <= 3; i++) {
                core.scheduler().later(i * 20L, () -> {
                    if (victim.isValid() && !victim.isDead()) {
                        victim.damage(1.0);
                    }
                });
            }
        }
    }
}
