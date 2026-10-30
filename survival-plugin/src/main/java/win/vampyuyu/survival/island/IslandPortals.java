package win.vampyuyu.survival.island;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Keeps portals inside the island worlds: sb_world to sb_nether (Nether portal) and sb_world to sb_end (End portal),
 * always in the grid cell the portal stands in. Anything else (and every entity that is not a player) is cancelled so
 * nobody reaches the creative or vanilla survival worlds.
 */
public final class IslandPortals implements Listener {
    private final IslandService service;
    private final IslandConfig cfg;
    private final Cooldowns notices = new Cooldowns();

    public IslandPortals(IslandService service) {
        this.service = service;
        this.cfg = service.config();
    }

    private void say(Player player, String text) {
        if (notices.tryUse(player.getUniqueId(), "portal", 1500)) {
            service.core().msg().warn(player, text);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) {
            return;
        }
        String fromName = fromWorld.getName();
        if (!SkyblockWorlds.isSkyblock(fromName) || SkyblockWorlds.isDungeon(fromName)) {
            return;
        }
        PortalMapper.Route route = PortalMapper.route(fromName, event.getCause(), cfg.netherPortals, cfg.endPortals);
        Island cell = service.cellIsland(fromName, event.getFrom().getX(), event.getFrom().getZ());
        if (route == null || cell == null) {
            event.setCancelled(true);
            say(player, "This portal does not lead anywhere.");
            return;
        }
        if (!service.canEnter(player, cell)) {
            event.setCancelled(true);
            say(player, service.enterDenyReason(player, cell));
            return;
        }
        if (!route.home() && !cell.platformsReady) {
            event.setCancelled(true);
            say(player, "The other side of this portal is still being generated. Try again in a moment.");
            return;
        }
        Location to = route.home() ? service.overworldHome(cell) : service.spot(cell, route.world(), route.offset(), 180);
        if (to == null || to.getWorld() == null) {
            event.setCancelled(true);
            say(player, "The destination is not available right now.");
            return;
        }
        event.setTo(to);
        event.setCanCreatePortal(true);
        event.setSearchRadius(8);
        event.setCreationRadius(8);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        World world = event.getFrom().getWorld();
        if (world != null && SkyblockWorlds.isSkyblock(world.getName()) && !SkyblockWorlds.isDungeon(world.getName())) {
            event.setCancelled(true);
        }
    }

    /** Vanilla would build the 5x5 End platform at 100,49,0 when someone arrives; the island End has its own outpost. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(org.bukkit.event.world.PortalCreateEvent event) {
        World world = event.getWorld();
        if (world != null && SkyblockWorlds.END.equals(world.getName())
                && event.getReason() == org.bukkit.event.world.PortalCreateEvent.CreateReason.END_PLATFORM) {
            event.setCancelled(true);
        }
    }

    /** No vanilla dragon fight in sb_end: the boss feature spawns the dragon itself (reason CUSTOM or COMMAND). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null || !SkyblockWorlds.END.equals(world.getName())
                || event.getEntityType() != org.bukkit.entity.EntityType.ENDER_DRAGON) {
            return;
        }
        var reason = event.getSpawnReason();
        if (reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM
                && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.COMMAND) {
            event.setCancelled(true);
        }
    }
}
