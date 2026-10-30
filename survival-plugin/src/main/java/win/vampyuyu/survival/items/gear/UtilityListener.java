package win.vampyuyu.survival.items.gear;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** The Warp Pearl: an ender pearl whose landing teleport hurts nobody. */
public final class UtilityListener implements Listener {
    private static final NamespacedKey WARP = new NamespacedKey("vampyuyu", "sb_warp_pearl");
    private final Gear gear;
    private final StatsEngine engine;
    private final Map<UUID, Long> warpUntil = new ConcurrentHashMap<>();

    public UtilityListener(Gear gear, StatsEngine engine) {
        this.gear = gear;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player
                && engine.active(player) && gear.items().is(pearl.getItem(), "warp_pearl")) {
            pearl.getPersistentDataContainer().set(WARP, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player
                && pearl.getPersistentDataContainer().has(WARP, PersistentDataType.BYTE)) {
            warpUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || event.getTo() == null) {
            return;
        }
        Long until = warpUntil.remove(event.getPlayer().getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            return;
        }
        // Cancel the vanilla teleport (it deals the fall damage) and perform the same move as a plain teleport.
        event.setCancelled(true);
        Player player = event.getPlayer();
        org.bukkit.Location to = event.getTo().clone();
        player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setFallDistance(0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warpUntil.remove(event.getPlayer().getUniqueId());
    }
}
