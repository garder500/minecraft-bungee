package win.vampyuyu.survival.classes;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.core.Core;

/** Wires the class effects to the game: health, damage taken, combat speed, arrow speed, the cast key and the first choice prompt. */
final class ClassListener implements Listener {
    private final Core core;
    private final ClassService service;

    ClassListener(Core core, ClassService service) {
        this.core = core;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().nextTick(() -> service.refresh(player));
        if (service.classOf(player.getUniqueId()).isEmpty()) {
            core.scheduler().later(60, () -> {
                if (player.isOnline() && service.classOf(player.getUniqueId()).isEmpty()) {
                    ClassesFeature.sendChoice(core, player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        core.scheduler().nextTick(() -> service.refresh(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }

    /** Bulwark and the Vanguard passive; after the Ward of the armor, both multiply. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && mitigated(event)) {
            event.setDamage(service.damageTaken(player, event.getDamage()));
            if (event instanceof EntityDamageByEntityEvent) {
                service.combatTick(player);
            }
        }
    }

    /** Hits and spells, not the world (same rule as the ladder Ward). */
    private static boolean mitigated(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent || switch (event.getCause()) {
            case MAGIC, CUSTOM, WITHER, POISON, ENTITY_EXPLOSION, BLOCK_EXPLOSION, SONIC_BOOM, DRAGON_BREATH -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDealt(EntityDamageByEntityEvent event) {
        Player shooter = event.getDamager() instanceof Player p ? p
            : event.getDamager() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player p2 ? p2 : null;
        if (shooter != null) {
            service.combatTick(shooter);
        }
    }

    /** Ranger: arrows fly further. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player
                && service.classOf(player.getUniqueId()).filter(c -> c == PlayerClass.RANGER).isPresent()) {
            event.getProjectile().setVelocity(event.getProjectile().getVelocity().multiply(ClassRules.RANGER_ARROW_SPEED));
        }
    }

    /** Sneak + F (swap hands) casts the active; /class cast does the same. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && service.classOf(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            ClassesFeature.cast(core, service, player);
        }
    }
}
