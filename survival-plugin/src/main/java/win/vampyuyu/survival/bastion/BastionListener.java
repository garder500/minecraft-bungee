package win.vampyuyu.survival.bastion;

import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** The rules inside a Bastion: no building, no escape by teleport, lives, and what happens when somebody quits. */
public final class BastionListener implements Listener {
    public static final String ADMIN = "vampyuyu.sb.bastion.admin";
    private static final Set<String> ALLOWED = Set.of("bastion", "party", "pc", "p", "msg", "tell", "w", "r", "reply", "me",
        "help", "boss");

    private final Core core;
    private final BastionService service;

    public BastionListener(Core core, BastionService service) {
        this.core = core;
        this.service = service;
    }

    private static boolean in(Player p) {
        return p.getWorld() != null && SkyblockWorlds.isBastion(p.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (SkyblockWorlds.isBastion(e.getBlock().getWorld().getName()) && !e.getPlayer().hasPermission(ADMIN)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (SkyblockWorlds.isBastion(e.getBlock().getWorld().getName()) && !e.getPlayer().hasPermission(ADMIN)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (SkyblockWorlds.isBastion(e.getEntity().getWorld().getName())) {
            e.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!in(e.getPlayer()) || e.getPlayer().hasPermission(ADMIN)) {
            return;
        }
        switch (e.getCause()) {
            case ENDER_PEARL, CHORUS_FRUIT, COMMAND -> e.setCancelled(true);
            default -> { } // our own arrival, exit and return from the void are plugin teleports
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!in(p) || p.hasPermission(ADMIN)) {
            return;
        }
        String line = e.getMessage();
        String head = line.length() > 1 ? line.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(head)) {
            e.setCancelled(true);
            core.msg().warn(p, "You cannot use that inside a Bastion. Leave with /bastion leave.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        BastionRun run = service.runOf(p.getUniqueId());
        if (run == null || !in(p)) {
            return;
        }
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        String line = service.died(run, p);
        core.scheduler().nextTick(() -> {
            if (p.isOnline()) {
                core.msg().warn(p, line);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        BastionRun run = service.runOf(e.getPlayer().getUniqueId());
        if (run != null && run.world != null && !run.closed) {
            e.setRespawnLocation(BastionArena.arrival(run.world, run.bastion.radius()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        service.leave(e.getPlayer().getUniqueId(), true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (in(p) && service.runOf(p.getUniqueId()) == null) {
            core.scheduler().nextTick(() -> {
                if (p.isOnline() && in(p) && service.runOf(p.getUniqueId()) == null) {
                    service.sendOutStray(p);
                    core.msg().info(p, "The Bastion you were in is gone. You were sent back.");
                }
            });
        }
    }
}
