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
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * The two small weapon effects of the first gear: Rend (Bronze Cleaver) and Frostbite (Rimeblade), everywhere in the
 * Skyblock, dungeons included. Rend's ticks are a share of the weapon's damage, so they count against the big health
 * pools of dungeon monsters and bosses; they are magic damage, so they never count as a new swing.
 */
final class WeaponListener implements Listener {
    private final Core core;
    private final double rendShare;

    WeaponListener(Core core) {
        this.core = core;
        this.rendShare = win.vampyuyu.survival.data.Settings.load(core.data(), "quarry").getDouble("rend-tick-percent", 5.0) / 100.0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !SkyblockWorlds.isSkyblock(player.getWorld().getName())) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        String id = core.items().idOf(held).orElse("");
        if (id.equals(QuarryItems.RIMEBLADE)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        } else if (id.equals(QuarryItems.BRONZE_CLEAVER)) {
            Double weapon = LadderCombat.weaponDamage(held, core.items().def(id).orElse(null));
            double tick = Math.max(1.0, (weapon == null ? 0 : weapon) * rendShare);
            for (int i = 1; i <= 3; i++) {
                core.scheduler().later(i * 20L, () -> {
                    if (victim.isValid() && !victim.isDead() && player.isOnline()) {
                        // a bleed tick neither waits for the hit's invulnerability window nor opens one for the next hit
                        int window = victim.getNoDamageTicks();
                        double last = victim.getLastDamage();
                        victim.setNoDamageTicks(0);
                        victim.damage(tick, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MAGIC)
                            .withCausingEntity(player).withDirectEntity(player).build());
                        victim.setNoDamageTicks(window);
                        victim.setLastDamage(last);
                    }
                });
            }
        }
    }
}
