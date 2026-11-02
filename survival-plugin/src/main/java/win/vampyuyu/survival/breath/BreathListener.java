package win.vampyuyu.survival.breath;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import win.vampyuyu.survival.api.event.DungeonClearEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The effects of the running event on the world: monster damage, immune cells, Fractures, the banner on join. */
final class BreathListener implements Listener {
    private final Core core;
    private final BreathService service;

    BreathListener(Core core, BreathService service) {
        this.core = core;
        this.service = service;
    }

    /** Monsters hit harder during a storm (GDD 19.2). Dungeon and Bastion fights keep their own tuned numbers. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        double factor = service.monsterFactor();
        if (factor == 1 || !(event.getEntity() instanceof Player)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        String world = damager.getWorld().getName();
        if (damager instanceof Monster && SkyblockWorlds.isSkyblock(world) && !SkyblockWorlds.isDungeon(world)
            && !SkyblockWorlds.isBastion(world)) {
            event.setDamage(event.getDamage() * factor);
        }
    }

    /** An immune cell dies without loot or experience; whoever killed it is paid in Memory Shards. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!service.isInvader(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            service.onInvaderKilled(killer);
        }
    }

    /** The immune cells never destroy anything: no explosion, no door broken, no block moved (GDD 19.2). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent event) {
        if (service.isInvader(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDoor(EntityBreakDoorEvent event) {
        if (service.isInvader(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (service.isInvader(event.getEntity())) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onClear(DungeonClearEvent event) {
        service.onDungeonClear(event.player(), event.floor(), event.seed());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        BreathEvent running = service.event();
        if (running != null) {
            Player player = event.getPlayer();
            core.scheduler().later(60, () -> {
                if (player.isOnline() && service.event() == running) {
                    service.banner(player, running);
                }
            });
        }
    }
}
