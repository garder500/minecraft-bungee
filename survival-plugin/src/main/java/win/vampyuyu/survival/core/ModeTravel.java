package win.vampyuyu.survival.core;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.island.IslandService;
import win.vampyuyu.survival.island.Pos;

/** The only explicit mode crossing, and independent persistent respawn points. Main thread only. */
public final class ModeTravel implements Listener {
    public static final class Points {
        public Point skyblock;
        public Point survival;
        public boolean imported;
        /** Where the player last stood in Survival, so coming back does not send them to the world spawn. */
        public Pos lastSurvival;
        public String mode;
    }
    public static final class Point {
        public Pos position;
        public String kind;
        public boolean forced;
    }
    /** One saved player state per mode: Skyblock and Survival never share items, ender chest, XP or health. */
    public static final class Stash {
        public String inventory;
        public String enderChest;
        public int level;
        public float exp;
        public double health = 20;
        public int food = 20;
        public float saturation = 5;
    }
    public static final class Stashes {
        public Stash skyblock;
        public Stash survival;
        /** Mode the live inventory currently belongs to (null = never recorded). */
        public String live;
        /** An admin reset happened while the player was offline with the Skyblock inventory live: empty it on join. */
        public boolean wipeLive;
    }
    private final Core core;
    private final Set<UUID> crossing = new HashSet<>();
    private final Set<UUID> restoringSpawn = new HashSet<>();
    private final Set<UUID> waiting = new HashSet<>();
    private final java.util.Map<UUID, Long> selections = new java.util.HashMap<>();
    public long selection(Player player) { return selections.getOrDefault(player.getUniqueId(), 0L); }
    private void selected(Player player) { selections.merge(player.getUniqueId(), 1L, Long::sum); }

    ModeTravel(Core core) { this.core = core; }

    public static boolean sky(World world) {
        return world != null && SkyblockWorlds.isSkyblock(world.getName());
    }
    public static boolean retired(World world) {
        return world != null && sky(world) && !SkyblockWorlds.WORLD.equals(world.getName())
            && !SkyblockWorlds.NETHER.equals(world.getName());
    }

    /** Used only by the explicit mode selector and island-home entry. No asynchronous permit can leak. */
    public boolean enter(Player player, Location target) {
        if (target == null || target.getWorld() == null || retired(target.getWorld())) return false;
        if (sky(target.getWorld())) rememberSurvivalSpot(player);
        crossing.add(player.getUniqueId());
        try {
            boolean ok = player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            if (ok) {
                selected(player); waiting.remove(player.getUniqueId()); player.setFallDistance(0);
                rememberMode(player, sky(target.getWorld()));
            }
            return ok;
        } finally {
            crossing.remove(player.getUniqueId());
        }
    }

    public boolean enterIsland(Player player) {
        rememberMode(player, true);
        awaitIsland(player);
        return core.service(IslandApi.class).map(api -> api.teleportHome(player)).orElse(false);
    }
    public void awaitIsland(Player player) { if (waiting.add(player.getUniqueId())) selected(player); }
    public boolean awaitingIsland(Player player) { return waiting.contains(player.getUniqueId()); }
    public boolean enterSurvival(Player player) {
        selected(player);
        waiting.remove(player.getUniqueId());
        Pos last = points(player).lastSurvival;
        Location back = last == null ? null : last.toLocation();
        if (back != null && back.getWorld() != null && !sky(back.getWorld())) {
            Location landing = safeLanding(back);
            if (landing != null && enter(player, landing)) return true;
        }
        return enter(player, survivalSpawn());
    }

    private void rememberSurvivalSpot(Player player) {
        if (sky(player.getWorld())) return;
        points(player).lastSurvival = Pos.of(player.getLocation());
        core.profiles().markDirty(player.getUniqueId());
    }

    public Location survivalSpawn() {
        String configured = core.featureConfig("utility").getString("spawn.world", "");
        World world = configured.isBlank() ? null : Bukkit.getWorld(configured);
        if (world != null && !sky(world) && world.getEnvironment() == World.Environment.NORMAL)
            return safeLanding(world.getSpawnLocation());
        return Bukkit.getWorlds().stream().filter(w -> !sky(w) && w.getEnvironment() == World.Environment.NORMAL)
            .map(World::getSpawnLocation).map(ModeTravel::safeLanding).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /** Finds safe ground nearby, or adds a small pad in empty air above the terrain without replacing player blocks. */
    public static Location safeLanding(Location origin) {
        if (origin == null || origin.getWorld() == null) return null;
        World world = origin.getWorld();
        if (win.vampyuyu.survival.utility.SafeSpot.isSafe(origin)) return origin;
        for (int radius = 0; radius <= 6; radius++) for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) for (int dy = -2; dy <= 3; dy++) {
                Location at = origin.clone().add(dx, dy, dz);
                if (world.getWorldBorder().isInside(at) && win.vampyuyu.survival.utility.SafeSpot.isSafe(at)) return at;
            }
        int floor = Math.max(world.getMinHeight() + 1, origin.getBlockY());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            floor = Math.max(floor, world.getHighestBlockYAt(origin.getBlockX()+dx, origin.getBlockZ()+dz)+1);
        if (floor + 2 >= world.getMaxHeight()) return null;
        Location stand = origin.clone(); stand.setY(floor + 1);
        if (!world.getWorldBorder().isInside(stand)) return null;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            world.getBlockAt(origin.getBlockX()+dx, floor, origin.getBlockZ()+dz).setType(Material.STONE);
        return stand;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        World from = event.getFrom().getWorld();
        World to = event.getTo() == null ? null : event.getTo().getWorld();
        if (to == null) return;
        if (retired(to) || (sky(from) != sky(to) && !crossing.contains(event.getPlayer().getUniqueId()))) {
            event.setCancelled(true);
            core.msg().warn(event.getPlayer(), "Use /menu to select a game mode.");
        }
    }

    // PlayerPortalEvent has its own Bukkit handler list; a teleport listener alone does not protect portals.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(org.bukkit.event.player.PlayerPortalEvent event) { onTeleport(event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getTo() != null && sky(event.getFrom().getWorld()) != sky(event.getTo().getWorld()))
            event.setCancelled(true);
    }

    private Points points(Player player) {
        return core.profiles().section(player.getUniqueId(), "mode_respawns", Points.class, Points::new);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        // Vanilla clears its single slot after a failed respawn. That must never clear our other mode's point.
        if (event.getCause() == PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN) return;
        Player player = event.getPlayer();
        if (restoringSpawn.contains(player.getUniqueId())) return;
        Location location = event.getLocation();
        if (location != null && (retired(location.getWorld()) || sky(location.getWorld()) != sky(player.getWorld()))) return;
        Points points = points(player);
        Point point = null;
        if (location != null) {
            point = new Point();
            point.position = Pos.of(location);
            point.kind = event.getCause().name();
            point.forced = event.isForced();
        }
        if (sky(player.getWorld())) points.skyblock = point; else points.survival = point;
        core.profiles().markDirty(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Stashes stashes = core.profiles().section(player.getUniqueId(), "mode_inventories", Stashes.class, Stashes::new);
        if (stashes.live == null) {
            // The shared pre-split inventory stays with the mode the player logs into.
            stashes.live = sky(player.getWorld()) ? "skyblock" : "survival";
            core.profiles().markDirty(player.getUniqueId());
        }
        if (stashes.wipeLive) {
            stashes.wipeLive = false;
            if ("skyblock".equals(stashes.live)) clearLive(player);
            core.profiles().markDirty(player.getUniqueId());
        }
        Points points = points(player);
        if (!points.imported) {
            // Preserve the one pre-upgrade vanilla point in the mode its WORLD belongs to.
            Location old = player.getRespawnLocation(false); // Raw block position, not the adjacent resolved standing spot.
            if (old != null && !retired(old.getWorld())) {
                Point point = new Point();
                point.position = Pos.of(old);
                if (Tag.BEDS.isTagged(old.getBlock().getType())) point.kind = "BED";
                else if (old.getBlock().getType() == Material.RESPAWN_ANCHOR) point.kind = "RESPAWN_ANCHOR";
                else point.kind = player.getRespawnLocation() == null ? "BED" : "PLUGIN";
                if (sky(old.getWorld())) { if (points.skyblock == null) points.skyblock = point; }
                else if (points.survival == null) points.survival = point;
            }
            points.imported = true;
            core.profiles().markDirty(player.getUniqueId());
        }
        if (retired(player.getWorld()) || ("skyblock".equals(points.mode) && !sky(player.getWorld()))) {
            enterIsland(player);
        } else if ("survival".equals(points.mode) && sky(player.getWorld())) {
            enterSurvival(player);
        } else restoreNativeSpawn(player, sky(player.getWorld()));
    }

    private void restoreNativeSpawn(Player player, boolean skyblock) {
        Point point = skyblock ? points(player).skyblock : points(player).survival;
        Location location = point == null || point.position == null ? null : point.position.toLocation();
        if (location != null && (retired(location.getWorld()) || sky(location.getWorld()) != skyblock)) location = null;
        restoringSpawn.add(player.getUniqueId());
        try {
            // Keep vanilla's bed/anchor validation and charge consumption in the current mode too.
            player.setRespawnLocation(location, point != null && point.forced);
        } finally { restoringSpawn.remove(player.getUniqueId()); }
    }

    private void rememberMode(Player player, boolean skyblock) {
        points(player).mode = skyblock ? "skyblock" : "survival";
        core.profiles().markDirty(player.getUniqueId());
        restoreNativeSpawn(player, skyblock);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        swapInventory(event.getPlayer(), sky(event.getPlayer().getWorld()));
        rememberMode(event.getPlayer(), sky(event.getPlayer().getWorld()));
    }

    private static String encode(org.bukkit.inventory.ItemStack[] items) {
        return java.util.Base64.getEncoder().encodeToString(org.bukkit.inventory.ItemStack.serializeItemsAsBytes(items));
    }
    private static org.bukkit.inventory.ItemStack[] decode(String data) {
        return data == null ? new org.bukkit.inventory.ItemStack[0]
            : org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(java.util.Base64.getDecoder().decode(data));
    }

    /** Island reset: empties the player's Skyblock items, ender chest, XP, health and food; Survival is never touched. */
    public void resetSkyblockState(Player player) {
        Stashes stashes = core.profiles().section(player.getUniqueId(), "mode_inventories", Stashes.class, Stashes::new);
        boolean liveSky = stashes.live != null ? "skyblock".equals(stashes.live) : sky(player.getWorld());
        if (liveSky) {
            clearLive(player);
        } else {
            stashes.skyblock = null; // Skyblock state is in the stash; Survival is live
        }
        core.profiles().markDirty(player.getUniqueId());
    }

    private static void clearLive(Player player) {
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0);
        player.setFoodLevel(20);
        player.setSaturation(5);
        var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
    }

    /** Admin reset of an online player: empties the Skyblock state and forgets the Skyblock bed or anchor. */
    public void resetSkyblockProgress(Player player) {
        resetSkyblockState(player);
        points(player).skyblock = null;
        if (sky(player.getWorld())) restoreNativeSpawn(player, true);
        core.profiles().markDirty(player.getUniqueId());
    }

    /**
     * Admin reset of a player who may be offline: the Skyblock stash is dropped, and a Skyblock inventory that is
     * still live is emptied at the next join. Any thread (an offline profile is read from disk).
     */
    public void resetSkyblockProgressOffline(java.util.UUID id) {
        core.profiles().edit(id, "mode_inventories", Stashes.class, Stashes::new, stashes -> {
            stashes.skyblock = null;
            if ("skyblock".equals(stashes.live)) stashes.wipeLive = true;
        });
        core.profiles().edit(id, "mode_respawns", Points.class, Points::new, points -> points.skyblock = null);
    }

    /** Puts the live state in the stash of the mode it belongs to and loads the other mode's stash (empty the first time). */
    void swapInventory(Player player, boolean skyblock) {
        Stashes stashes = core.profiles().section(player.getUniqueId(), "mode_inventories", Stashes.class, Stashes::new);
        String target = skyblock ? "skyblock" : "survival";
        if (stashes.live == null) {
            // The shared pre-split inventory stays with the mode the player is in right now.
            stashes.live = target;
            core.profiles().markDirty(player.getUniqueId());
            return;
        }
        if (stashes.live.equals(target)) return;
        Stash out = new Stash();
        out.inventory = encode(player.getInventory().getContents());
        out.enderChest = encode(player.getEnderChest().getContents());
        out.level = player.getLevel();
        out.exp = player.getExp();
        out.health = player.getHealth();
        out.food = player.getFoodLevel();
        out.saturation = player.getSaturation();
        if (skyblock) stashes.survival = out; else stashes.skyblock = out;
        Stash in = skyblock ? stashes.skyblock : stashes.survival;
        var inventory = player.getInventory();
        inventory.clear();
        player.getEnderChest().clear();
        if (in == null) {
            player.setLevel(0);
            player.setExp(0);
            player.setFoodLevel(20);
            player.setSaturation(5);
            var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            player.setHealth(max == null ? 20 : max.getValue());
        } else {
            var contents = decode(in.inventory);
            for (int i = 0; i < contents.length && i < inventory.getSize(); i++) inventory.setItem(i, contents[i]);
            var ender = decode(in.enderChest);
            for (int i = 0; i < ender.length && i < player.getEnderChest().getSize(); i++) player.getEnderChest().setItem(i, ender[i]);
            player.setLevel(in.level);
            player.setExp(in.exp);
            player.setFoodLevel(in.food);
            player.setSaturation(in.saturation);
            var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            player.setHealth(Math.max(1, Math.min(in.health, max == null ? 20 : max.getValue())));
        }
        stashes.live = target;
        core.profiles().markDirty(player.getUniqueId());
        core.profiles().saveDirty(); // the stash must reach the disk before the world's player data can
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rememberSurvivalSpot(event.getPlayer());
        waiting.remove(event.getPlayer().getUniqueId());
        selections.remove(event.getPlayer().getUniqueId());
    }

    /** Return a verified location for the selected mode, never vanilla's other-mode point. */
    public Location respawn(Player player, boolean skyblock) {
        Point point = skyblock ? points(player).skyblock : points(player).survival;
        Location saved = validPoint(player, point, skyblock);
        if (saved != null) return saved;
        if (!skyblock) return survivalSpawn();
        return core.service(IslandApi.class).filter(IslandService.class::isInstance)
            .map(IslandService.class::cast).map(api -> api.safeFallback(player)).orElse(null);
    }

    private Location validPoint(Player player, Point point, boolean skyblock) {
        if (point == null || point.position == null) return null;
        Location at = point.position.toLocation();
        if (at == null || retired(at.getWorld()) || sky(at.getWorld()) != skyblock) return null;
        if (!Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ())
                || at.getY() < at.getWorld().getMinHeight() || at.getY() >= at.getWorld().getMaxHeight()
                || !at.getWorld().getWorldBorder().isInside(at)) return null;
        if (skyblock && !core.service(IslandApi.class).map(api -> api.islandAt(at)
                .map(i -> i.members().contains(player.getUniqueId())).orElse(false)).orElse(false)) return null;
        if ("BED".equals(point.kind)) {
            if (!(at.getBlock().getBlockData() instanceof Bed bed)) return null;
            var other = at.getBlock().getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace());
            if (!(other.getBlockData() instanceof Bed otherBed) || otherBed.getPart() == bed.getPart()
                    || otherBed.getFacing() != bed.getFacing()) return null;
        } else if ("RESPAWN_ANCHOR".equals(point.kind)) {
            if (!(at.getBlock().getBlockData() instanceof RespawnAnchor anchor) || anchor.getCharges() <= 0) return null;
        } else {
            return win.vampyuyu.survival.utility.SafeSpot.isSafe(at) ? at : null;
        }
        // A bed/anchor stores its block, not the standing location. Check the surrounding space on every death.
        for (int dy = 0; dy <= 1; dy++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            Location stand = at.clone().add(x, dy, z);
            stand.setX(stand.getBlockX() + .5); stand.setZ(stand.getBlockZ() + .5);
            if (at.getWorld().getWorldBorder().isInside(stand)
                    && win.vampyuyu.survival.utility.SafeSpot.isSafe(stand)) return stand;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        boolean skyblock = sky(player.getWorld());
        Location nativeTarget = event.getRespawnLocation();
        Point saved = skyblock ? points(player).skyblock : points(player).survival;
        if ((event.isBedSpawn() || event.isAnchorSpawn()) && saved != null && saved.position != null
                && java.util.Objects.equals(saved.position.world, nativeTarget.getWorld().getName())
                && sky(nativeTarget.getWorld()) == skyblock && !retired(nativeTarget.getWorld())
                && win.vampyuyu.survival.utility.SafeSpot.isSafe(nativeTarget)
                && (!skyblock || core.service(IslandApi.class).map(api -> api.islandAt(nativeTarget)
                    .map(i -> i.members().contains(player.getUniqueId())).orElse(false)).orElse(false))) {
            // Vanilla already resolved this mode's bed/anchor, including consuming an anchor's last charge.
            return;
        }
        Location target = respawn(player, skyblock);
        if (target == null) {
            // A missing island service/world must fail closed instead of respawning in Survival.
            event.setRespawnLocation(event.getPlayer().getLocation());
            event.getPlayer().kick(net.kyori.adventure.text.Component.text("Your mode's respawn is unavailable. Please reconnect shortly."));
            return;
        }
        event.setRespawnLocation(target);
    }
}
