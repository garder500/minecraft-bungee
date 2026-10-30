package win.vampyuyu.survival.island;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import win.vampyuyu.survival.api.event.IslandEnterEvent;
import win.vampyuyu.survival.api.event.IslandLeaveEvent;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Follows every player: which island they stand in (Enter/Leave events), the per-player border, time and weather of
 * the island, the server-side border enforcement, entry rules (banned / locked), void rescue and respawn.
 */
public final class IslandTracker implements Listener {
    private final IslandService service;
    private final IslandConfig cfg;
    private final Map<UUID, UUID> inside = new HashMap<>();
    private final Set<UUID> rescuing = new HashSet<>();
    private final Cooldowns notices = new Cooldowns();

    public IslandTracker(IslandService service) {
        this.service = service;
        this.cfg = service.config();
    }

    // ---------------------------------------------------------------- state

    /** The island the tracker believes the player is in (tests). */
    public UUID islandOf(UUID player) {
        return inside.get(player);
    }

    void forget(UUID player) {
        UUID id = inside.remove(player);
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            resetVisuals(online);
        }
        if (id != null && online != null) {
            service.registry().byId(id).ifPresent(island -> fire(online, island, false));
        }
    }

    /** Re-evaluates one player where they stand (membership changed, teleport finished...). */
    void refreshPlayer(Player player) {
        update(player, player.getLocation());
    }

    /** Re-applies the visuals of the island the player stands in (border preference changed). */
    void reapply(Player player) {
        Island here = service.islandAtLocation(player.getLocation());
        if (here != null) {
            apply(player, here);
        }
    }

    /** Re-applies border/time/weather for everybody inside an island (radius, flags or time changed). */
    void refreshIsland(Island island) {
        for (Player player : service.playersIn(island)) {
            Island here = service.islandAtLocation(player.getLocation());
            if (here == island) {
                apply(player, island);
            }
        }
    }

    /** Resets everything the tracker changed on a player (plugin disable). */
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetVisuals(player);
        }
        inside.clear();
    }

    private void fire(Player player, Island island, boolean enter) {
        try {
            String world = player.getWorld().getName();
            var info = service.info(island, world);
            Bukkit.getPluginManager().callEvent(enter ? new IslandEnterEvent(player, info) : new IslandLeaveEvent(player, info));
        } catch (RuntimeException e) {
            service.warn("Island enter/leave listener failed", e);
        }
    }

    private void notice(Player player, String key, String text) {
        if (notices.tryUse(player.getUniqueId(), key, cfg.messageCooldownMs)) {
            service.core().msg().warn(player, text);
        }
    }

    // ---------------------------------------------------------------- visuals

    private void apply(Player player, Island island) {
        try {
            if (cfg.borderVisual && service.data(player.getUniqueId()).borderVisible) {
                WorldBorder border = Bukkit.createWorldBorder();
                border.setCenter(service.grid().centerOf(island.cellX) + 0.5, service.grid().centerOf(island.cellZ) + 0.5);
                border.setSize(2.0 * cfg.radiusFor(island) + 1);
                border.setWarningDistance(4);
                border.setDamageAmount(0);
                border.setDamageBuffer(1000);
                player.setWorldBorder(border);
            } else {
                player.setWorldBorder(null);
            }
            TimeLock lock = island.timeLock();
            if (lock == TimeLock.NONE) {
                player.resetPlayerTime();
            } else {
                player.setPlayerTime(lock.ticks(), false);
            }
            if (island.flag(IslandFlag.WEATHER)) {
                player.resetPlayerWeather();
            } else {
                player.setPlayerWeather(WeatherType.CLEAR);
            }
        } catch (RuntimeException | LinkageError e) {
            service.warn("Could not apply island visuals to " + player.getName(), e);
        }
    }

    private void resetVisuals(Player player) {
        try {
            player.setWorldBorder(null);
            player.resetPlayerTime();
            player.resetPlayerWeather();
        } catch (RuntimeException | LinkageError e) {
            // player is leaving or the server cannot do it
        }
    }

    // ---------------------------------------------------------------- tracking

    /** Moves the bookkeeping to the island at {@code at}, firing events and applying visuals on change. */
    void update(Player player, Location at) {
        Island here = at.getWorld() == null ? null : service.islandAtLocation(at);
        UUID before = inside.get(player.getUniqueId());
        UUID now = here == null ? null : here.id;
        if (java.util.Objects.equals(before, now)) {
            return;
        }
        if (before != null) {
            service.registry().byId(before).ifPresent(old -> fire(player, old, false));
            inside.remove(player.getUniqueId());
            if (here == null) {
                resetVisuals(player);
            }
        }
        if (here != null) {
            inside.put(player.getUniqueId(), here.id);
            apply(player, here);
            fire(player, here, true);
            if (cfg.enterMessages && !here.roleOf(player.getUniqueId()).isCoop()) {
                service.core().msg().info(player, "Welcome to " + service.registry().nameOf(here.owner) + "'s island.");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        service.registry().rememberName(player.getUniqueId(), player.getName());
        service.core().scheduler().nextTick(() -> {
            if (player.isOnline()) {
                update(player, player.getLocation());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = inside.remove(player.getUniqueId());
        rescuing.remove(player.getUniqueId());
        if (id != null) {
            service.registry().byId(id).ifPresent(island -> fire(player, island, false));
        }
        service.confirms().clear(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    // ---------------------------------------------------------------- movement

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY()) {
            return;
        }
        Player player = event.getPlayer();
        World world = to.getWorld();
        String name = world.getName();
        if (!SkyblockWorlds.isSkyblock(name) || SkyblockWorlds.isDungeon(name)) {
            return;
        }
        if (cfg.voidRescue && to.getY() < world.getMinHeight() - 16) {
            rescue(player, to);
            return;
        }
        if (SkyblockWorlds.HUB.equals(name)) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Island here = service.islandAtLocation(to);
        if (here == null) {
            Island was = from.getWorld() == world ? service.islandAtLocation(from) : null;
            if (was != null && cfg.borderEnforce && !player.hasPermission(IslandService.BYPASS)) {
                event.setCancelled(true);
                notice(player, "border", "You reached the edge of your island. Upgrade the island radius to expand.");
                return;
            }
            update(player, to);
            return;
        }
        UUID current = inside.get(player.getUniqueId());
        if (!here.id.equals(current)) {
            if (!service.canEnter(player, here)) {
                event.setCancelled(true);
                notice(player, "enter", service.enterDenyReason(player, here));
                return;
            }
            update(player, to);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Player player = event.getPlayer();
        if (to == null || to.getWorld() == null || player.hasPermission(IslandService.BYPASS)) {
            return;
        }
        String toName = to.getWorld().getName();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        Location from = event.getFrom();
        // portals and gateways are handled (and mapped) by the world listener
        if (cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY && from.getWorld() != null
                && SkyblockWorlds.isSkyblock(from.getWorld().getName())) {
            event.setCancelled(true);
            return;
        }
        if (!isIslandWorld(toName)) {
            return;
        }
        Island target = service.islandAtLocation(to);
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            Island source = from.getWorld() == to.getWorld() ? service.islandAtLocation(from) : null;
            if (target == null || target != source) {
                event.setCancelled(true);
                notice(player, "tp", "You cannot teleport out of the island like that.");
            }
            return;
        }
        if (target != null && !service.canEnter(player, target)) {
            event.setCancelled(true);
            notice(player, "enter", service.enterDenyReason(player, target));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        service.core().scheduler().nextTick(() -> {
            if (player.isOnline()) {
                update(player, player.getLocation());
            }
        });
    }

    private static boolean isIslandWorld(String name) {
        return SkyblockWorlds.WORLD.equals(name) || SkyblockWorlds.NETHER.equals(name) || SkyblockWorlds.END.equals(name);
    }

    // ---------------------------------------------------------------- void and respawn

    /** Pulls a player who fell out of the world back to safety, once. */
    public void rescue(Player player, Location from) {
        if (!rescuing.add(player.getUniqueId())) {
            return;
        }
        Location target = service.rescueLocation(player, from);
        service.core().scheduler().later(40, () -> rescuing.remove(player.getUniqueId()));
        if (target == null) {
            rescuing.remove(player.getUniqueId());
            return;
        }
        player.setFallDistance(0);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        service.teleport(player, target);
        service.core().msg().warn(player, cfg.voidMessage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        String name = player.getWorld().getName();
        if (!SkyblockWorlds.isSkyblock(name) || SkyblockWorlds.isDungeon(name)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            if (cfg.voidRescue) {
                event.setCancelled(true);
                rescue(player, player.getLocation());
            }
            return;
        }
        if (SkyblockWorlds.HUB.equals(name)) {
            event.setCancelled(true); // the hub is a safe zone
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String died = player.getWorld().getName();
        if (!SkyblockWorlds.isSkyblock(died) || SkyblockWorlds.isDungeon(died)) {
            return;
        }
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location target = null;
        if (cfg.respawnHome) {
            target = service.safeFallback(player);
        } else if (service.hubReady()) {
            target = service.hubLocation();
        }
        if (target == null) {
            target = service.hubLocation();
        }
        if (target != null) {
            event.setRespawnLocation(target);
        }
    }
}
