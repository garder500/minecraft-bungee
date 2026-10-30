package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.Plugin;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.IslandAccess.Action;
import win.vampyuyu.survival.island.IslandAccess.Cap;
import win.vampyuyu.survival.island.IslandConfig.ItemSpec;
import win.vampyuyu.survival.island.IslandConfig.Tier;

/**
 * The island feature's brain: registry access, protection decisions, island lifecycle (create, reset, delete,
 * rebuild), co-op management, upgrades, levels and teleports. Implements {@link IslandApi} and the two extension
 * interfaces. Every method runs on the main thread.
 */
public final class IslandService implements IslandApi, IslandAltarApi, IslandUpgradesApi {
    public static final String BYPASS = "vampyuyu.sb.island.bypass";
    public static final String ADMIN = "vampyuyu.sb.island.admin";

    /** Upgrade families bought in the upgrades menu. */
    public enum Upgrade {
        RADIUS("Island radius"), MEMBERS("Member slots"), MINIONS("Minion slots");

        private final String label;

        Upgrade(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A job that also reports block counts once finished (the level scan). */
    interface Counting extends PlanRunner.Job {
        Map<Material, Long> counts();
    }

    /** Creates the block jobs; tests replace it to drive the lifecycle without a real world. */
    interface JobFactory {
        PlanRunner.Job plan(String label, World world, BlockPlan plan, int ox, int oy, int oz);

        PlanRunner.Job wipe(String label, World world, int centerX, int centerZ, int radius);

        Counting scan(String label, World world, int centerX, int centerZ, int radius);
    }

    private final Core core;
    private final Plugin plugin;
    private final Logger log;
    private final IslandConfig cfg;
    private final IslandGrid grid;
    private final IslandRegistry registry;
    private final IslandStorage storage;
    private final PlanRunner runner;
    private final Invites invites = new Invites();
    private final Confirms confirms = new Confirms();
    private final Cooldowns cooldowns = new Cooldowns();
    private final Set<UUID> scanning = new HashSet<>();
    private JobFactory jobs;
    private IslandTracker tracker;
    private boolean saveQueued;
    private boolean closed;
    private boolean hubPadBuilt;
    private boolean hubComplete;

    public IslandService(Core core, IslandConfig cfg) {
        this.core = core;
        this.plugin = core.getPlugin();
        this.log = plugin.getLogger();
        this.cfg = cfg;
        this.grid = new IslandGrid(cfg.cellSize);
        this.storage = new IslandStorage(core.store(), log);
        this.registry = IslandRegistry.load(core.store(), IslandStorage.FILE, log, this::markDirty);
        this.runner = new PlanRunner(log, cfg.blocksPerTick, cfg.maxMillisPerTick);
        this.jobs = new JobFactory() {
            @Override
            public PlanRunner.Job plan(String label, World world, BlockPlan plan, int ox, int oy, int oz) {
                return new PlanJob(plugin, label, world, plan, ox, oy, oz, cfg.chunkTimeoutTicks);
            }

            @Override
            public PlanRunner.Job wipe(String label, World world, int centerX, int centerZ, int radius) {
                return new WipeJob(plugin, label, world, centerX, centerZ, radius, cfg.chunkTimeoutTicks);
            }

            @Override
            public Counting scan(String label, World world, int centerX, int centerZ, int radius) {
                return new ScanJob(plugin, label, world, centerX, centerZ, radius, cfg.levelChunksPerTick,
                    cfg.chunkTimeoutTicks);
            }
        };
    }

    /** Test seam: swaps the block jobs. */
    void jobs(JobFactory factory) {
        this.jobs = factory;
    }

    // ---------------------------------------------------------------- plumbing

    public IslandConfig config() { return cfg; }

    public IslandGrid grid() { return grid; }

    public IslandRegistry registry() { return registry; }

    public PlanRunner runner() { return runner; }

    public Invites invites() { return invites; }

    public Confirms confirms() { return confirms; }

    public Cooldowns cooldowns() { return cooldowns; }

    public Core core() { return core; }

    void tracker(IslandTracker tracker) { this.tracker = tracker; }

    private long now() {
        return System.currentTimeMillis();
    }

    void start() {
        runner.start(core.scheduler());
    }

    private void markDirty() {
        if (saveQueued || closed) {
            return;
        }
        saveQueued = true;
        core.scheduler().later(60, () -> {
            saveQueued = false;
            if (!closed) {
                storage.saveAsync(registry.toJson());
            }
        });
    }

    /** Flushes the registry and stops the batch runner. */
    void close() {
        if (closed) {
            return;
        }
        closed = true; // first: callbacks completed by the shutdown must see a closed service
        runner.shutdown();
        storage.close(registry.toJson());
    }

    PlayerIslandData data(UUID player) {
        return core.profiles().section(player, "island", PlayerIslandData.class, PlayerIslandData::new);
    }

    /**
     * Changes a player's island data without ever reading a profile from disk on the main thread: online players are
     * edited in place, offline ones through the profile store's any-thread {@code edit}.
     */
    private void editData(UUID player, Consumer<PlayerIslandData> change) {
        if (Bukkit.getPlayer(player) != null) {
            change.accept(data(player));
            dirty(player);
            return;
        }
        core.scheduler().async(() -> {
            try {
                core.profiles().edit(player, "island", PlayerIslandData.class, PlayerIslandData::new, change);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Could not update the island data of " + player, e);
            }
        });
    }

    private void dirty(UUID player) {
        core.profiles().markDirty(player);
    }

    // ---------------------------------------------------------------- locations

    private World world(String name) {
        return Bukkit.getWorld(name);
    }

    private static boolean isIslandWorld(String name) {
        return SkyblockWorlds.WORLD.equals(name) || SkyblockWorlds.NETHER.equals(name) || SkyblockWorlds.END.equals(name);
    }

    /** The island that owns the grid cell of the position (no radius check), or null outside the island worlds. */
    public Island cellIsland(String worldName, double x, double z) {
        if (worldName == null || !isIslandWorld(worldName)) {
            return null;
        }
        return registry.byCell(grid.cellOf(x), grid.cellOf(z)).orElse(null);
    }

    /** The island whose current radius contains the position, or null. */
    public Island islandAt(String worldName, double x, double z) {
        Island island = cellIsland(worldName, x, z);
        if (island == null) {
            return null;
        }
        return grid.within(island.cellX, island.cellZ, cfg.radiusFor(island), x, z) ? island : null;
    }

    /** The island whose radius contains the location. */
    public Island islandAtLocation(Location location) {
        World w = location.getWorld();
        return w == null ? null : islandAt(w.getName(), location.getX(), location.getZ());
    }

    public Island cellIslandAt(Location location) {
        World w = location.getWorld();
        return w == null ? null : cellIsland(w.getName(), location.getX(), location.getZ());
    }

    /** Spot relative to an island's centre in one of the island worlds. */
    public Location spot(Island island, String worldName, int[] offset, float yaw) {
        World w = world(worldName);
        if (w == null) {
            return null;
        }
        return new Location(w, grid.centerOf(island.cellX) + offset[0] + 0.5, cfg.islandY + offset[1],
            grid.centerOf(island.cellZ) + offset[2] + 0.5, yaw, 0);
    }

    /** The template spawn of the overworld island. */
    public Location defaultSpawn(Island island) {
        return spot(island, SkyblockWorlds.WORLD, new int[] {0, 1, 0}, 0);
    }

    /** The player-set home when it is still safe and inside the border, else the template spawn. */
    public Location overworldHome(Island island) {
        if (island.home != null) {
            Location home = island.home.toLocation();
            if (home != null && SkyblockWorlds.WORLD.equals(home.getWorld().getName())
                    && grid.within(island.cellX, island.cellZ, cfg.radiusFor(island), home.getX(), home.getZ())
                    && isSafe(home)) {
                return home;
            }
        }
        return defaultSpawn(island);
    }

    public Location hubLocation() {
        return null; // Retained API compatibility; the hub is no longer a destination.
    }

    /** True when the feet and head blocks are free and there is something solid underneath. */
    public static boolean isSafe(Location location) {
        try {
            Block feet = location.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            Block floor = feet.getRelative(0, -1, 0);
            return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                && floor.getType().isSolid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- IslandApi

    /** Snapshot of an island as seen from one of its dimensions. */
    public IslandInfo info(Island island, String worldName) {
        Location center = spot(island, worldName == null ? SkyblockWorlds.WORLD : worldName, new int[] {0, 1, 0}, 0);
        return new IslandInfo(island.id, island.owner, island.coop(),
            worldName == null ? SkyblockWorlds.WORLD : worldName, island.cellX, island.cellZ, center,
            cfg.radiusFor(island));
    }

    @Override
    public Optional<IslandInfo> islandAt(Location location) {
        Island island = islandAtLocation(location);
        return island == null ? Optional.empty() : Optional.of(info(island, location.getWorld().getName()));
    }

    @Override
    public Optional<IslandInfo> islandOf(UUID player) {
        return registry.ofPlayer(player).map(island -> info(island, SkyblockWorlds.WORLD));
    }

    @Override
    public boolean isSkyblockWorld(World world) {
        return world != null && SkyblockWorlds.isSkyblock(world.getName());
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return allowed(player, location, Action.BUILD);
    }

    @Override
    public boolean teleportHome(Player player) {
        Island island = ensurePersonalIsland(player);
        if (island == null || island.state() != Island.State.READY) {
            return false;
        }
        Location home = safeFallback(player);
        if (home == null) {
            return false;
        }
        return core.modes().enter(player, home);
    }

    @Override
    public boolean teleportHub(Player player) {
        return teleportHome(player);
    }

    // ---------------------------------------------------------------- player-initiated teleports

    /** True while the player is in a dungeon run (the dungeon feature's answer, else just the world name). */
    public boolean inDungeon(Player player) {
        World w = player.getWorld();
        if (w != null && SkyblockWorlds.isDungeon(w.getName())) {
            return true;
        }
        try {
            return core.service(DungeonApi.class).map(d -> d.inDungeon(player)).orElse(false);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "DungeonApi failed", e);
            return false;
        }
    }

    private static final String DUNGEON_REFUSAL = "You cannot leave a dungeon run like that.";

    /**
     * /is home and the home buttons. Refused during a dungeon run (the run would lose the player without being told);
     * the dungeon and menu features take players out through {@link #teleportHome} / {@link #teleportHub}, which
     * stay unguarded on purpose.
     */
    public Result goHome(Player player) {
        if (inDungeon(player)) {
            return Result.fail(DUNGEON_REFUSAL);
        }
        if (!teleportHome(player)) {
            return Result.fail(registry.hasIsland(player.getUniqueId()) ? "Your island is not ready yet."
                : "You do not have an island. Use /island create.");
        }
        return Result.ok("Teleporting home...");
    }

    /** /hub, /is hub and the hub buttons (see {@link #goHome}). */
    public Result goHub(Player player) {
        if (inDungeon(player)) {
            return Result.fail(DUNGEON_REFUSAL);
        }
        if (!teleportHub(player)) {
            return Result.fail("Your island is not ready yet. Try again in a moment.");
        }
        return Result.ok("Going to your island...");
    }

    /** /is nether goes to the personal Nether platform. The End shortcut is retired. */
    public Result shortcut(Player player, boolean nether) {
        String where = nether ? "Nether" : "End";
        if (!nether || !cfg.shortcuts || !cfg.netherPortals) {
            return Result.fail("The " + where + " shortcut is disabled.");
        }
        if (inDungeon(player)) {
            return Result.fail(DUNGEON_REFUSAL);
        }
        Island island = registry.ofPlayer(player.getUniqueId()).orElse(null);
        if (island == null) {
            return Result.fail("You do not have an island. Use /island create.");
        }
        if (island.state() != Island.State.READY) {
            return Result.fail("Your island is not ready yet.");
        }
        if (!island.platformsReady) {
            return Result.fail("The " + where + " of your island is still being generated. Try again in a moment.");
        }
        Location to = nether ? spot(island, SkyblockWorlds.NETHER, IslandTemplates.NETHER_SPAWN, 180)
            : spot(island, SkyblockWorlds.END, IslandTemplates.END_SPAWN, 180);
        if (to == null) {
            return Result.fail("The " + where + " is not available right now.");
        }
        if (!cooldowns.tryUse(player.getUniqueId(), "shortcut", cfg.shortcutCooldownSeconds * 1000L)) {
            return Result.fail("Slow down: wait " + Msg.duration(cooldowns.remaining(player.getUniqueId(), "shortcut")) + ".");
        }
        teleport(player, to);
        return Result.ok("Teleporting to your " + where + "...");
    }

    @Override
    public Collection<IslandInfo> all() {
        List<IslandInfo> list = new ArrayList<>();
        for (Island island : registry.all()) {
            list.add(info(island, SkyblockWorlds.WORLD));
        }
        return list;
    }

    // ---------------------------------------------------------------- IslandAltarApi / IslandUpgradesApi

    @Override
    public Optional<Location> dragonAltar(UUID islandId) {
        return registry.byId(islandId).map(i -> spot(i, SkyblockWorlds.END, IslandTemplates.END_ALTAR, 0));
    }

    @Override
    public Optional<Location> dragonAltarAt(Location location) {
        Island island = cellIslandAt(location);
        if (island == null || !SkyblockWorlds.END.equals(location.getWorld().getName())) {
            return Optional.empty();
        }
        return Optional.ofNullable(spot(island, SkyblockWorlds.END, IslandTemplates.END_ALTAR, 0));
    }

    @Override
    public Optional<Location> netherSpawn(UUID islandId) {
        return registry.byId(islandId).map(i -> spot(i, SkyblockWorlds.NETHER, IslandTemplates.NETHER_SPAWN, 180));
    }

    @Override
    public Optional<Location> endSpawn(UUID islandId) {
        return registry.byId(islandId).map(i -> spot(i, SkyblockWorlds.END, IslandTemplates.END_SPAWN, 180));
    }

    @Override
    public Optional<Location> hubSpawn() {
        return Optional.ofNullable(hubLocation());
    }

    @Override
    public Map<String, Location> hubPads() {
        World hub = world(SkyblockWorlds.HUB);
        Map<String, Location> pads = new java.util.LinkedHashMap<>();
        if (hub == null || !hubComplete) {
            return pads;
        }
        for (HubPlan.Pad pad : HubPlan.PADS) {
            pads.put(pad.id(), new Location(hub, pad.x() + 0.5, cfg.hubY + 1, pad.z() + 0.5,
                (float) Math.toDegrees(Math.atan2(pad.x(), -pad.z())), 0));
        }
        return pads;
    }

    @Override
    public int minionSlotBonus(UUID player) {
        if (!cfg.minionUpgrade) {
            return 0;
        }
        return registry.ofPlayer(player).map(cfg::minionSlotsFor).orElse(0);
    }

    @Override
    public int radius(UUID player) {
        return registry.ofPlayer(player).map(cfg::radiusFor).orElse(0);
    }

    @Override
    public int memberSlots(UUID player) {
        return registry.ofPlayer(player).map(cfg::memberSlotsFor).orElse(0);
    }

    void hubState(boolean padBuilt, boolean complete) {
        this.hubPadBuilt = padBuilt;
        this.hubComplete = complete;
    }

    public boolean hubReady() {
        return hubPadBuilt;
    }

    // ---------------------------------------------------------------- protection decisions

    /** Whether a player may do {@code action} at a location (see the matrix in {@link IslandAccess}). */
    public boolean allowed(Player player, Location location, Action action) {
        World w = location.getWorld();
        if (w == null) {
            return true;
        }
        String name = w.getName();
        if (!SkyblockWorlds.isSkyblock(name) || SkyblockWorlds.isDungeon(name)) {
            return true;
        }
        if (player.hasPermission(BYPASS)) {
            return true;
        }
        if (SkyblockWorlds.HUB.equals(name)) {
            return action == Action.INTERACT && cfg.hubInteract;
        }
        Island island = islandAt(name, location.getX(), location.getZ());
        if (island == null || island.state() != Island.State.READY) {
            return false;
        }
        return IslandAccess.can(island, island.roleOf(player.getUniqueId()), action, cfg.trustedContainers);
    }

    /** A short reason for the player after {@link #allowed} said no. */
    public String denyReason(Player player, Location location) {
        World w = location.getWorld();
        if (w != null && SkyblockWorlds.HUB.equals(w.getName())) {
            return "The hub is protected.";
        }
        Island island = w == null ? null : islandAt(w.getName(), location.getX(), location.getZ());
        if (island == null) {
            return "You cannot build outside an island.";
        }
        if (island.state() != Island.State.READY) {
            return "This island is being rebuilt.";
        }
        return "This is " + registry.nameOf(island.owner) + "'s island: you cannot do that here.";
    }

    /** Whether a player may be inside an island (banned players and strangers on locked islands may not). */
    public boolean canEnter(Player player, Island island) {
        if (player.hasPermission(BYPASS)) {
            return true;
        }
        return canEnter(player.getUniqueId(), island);
    }

    public boolean canEnter(UUID player, Island island) {
        IslandRole role = island.roleOf(player);
        if (role == IslandRole.BANNED) {
            return false;
        }
        if (island.state() != Island.State.READY) {
            return role.isCoop();
        }
        return !island.locked || role.isCoop() || role == IslandRole.TRUSTED;
    }

    public String enterDenyReason(Player player, Island island) {
        IslandRole role = island.roleOf(player.getUniqueId());
        if (role == IslandRole.BANNED) {
            return "You are banned from " + registry.nameOf(island.owner) + "'s island.";
        }
        if (island.state() != Island.State.READY) {
            return "That island is being built. Try again in a moment.";
        }
        return registry.nameOf(island.owner) + "'s island is locked.";
    }

    // ---------------------------------------------------------------- teleporting

    /** Teleports without a vertical speed surprise; falls back to a plain teleport on servers without async support. */
    public void teleport(Player player, Location target) {
        try {
            player.teleportAsync(target, TeleportCause.PLUGIN).thenAccept(ok -> {
                if (ok && player.isOnline()) {
                    player.setFallDistance(0);
                }
            });
        } catch (RuntimeException e) {
            player.teleport(target, TeleportCause.PLUGIN);
            player.setFallDistance(0);
        }
    }

    /** Where a player who fell out of the world (or must leave an island) should land. */
    public Location rescueLocation(Player player, Location from) {
        World w = from.getWorld();
        String name = w == null ? "" : w.getName();
        Island cell = cellIsland(name, from.getX(), from.getZ());
        if (cell != null && canEnter(player, cell) && cell.state() == Island.State.READY) {
            if (SkyblockWorlds.NETHER.equals(name) && cell.platformsReady) {
                return spot(cell, name, IslandTemplates.NETHER_SPAWN, 180);
            }
            if (SkyblockWorlds.END.equals(name) && cell.platformsReady) {
                return spot(cell, name, IslandTemplates.END_SPAWN, 180);
            }
            if (SkyblockWorlds.WORLD.equals(name)) {
                return overworldHome(cell);
            }
        }
        return safeFallback(player);
    }

    /** Safe personal island home; never a Survival fallback. */
    public Location safeFallback(Player player) {
        Island own = ensurePersonalIsland(player);
        if (own != null && own.state() == Island.State.READY) {
            Location home = overworldHome(own);
            if (home != null) {
                if (win.vampyuyu.survival.utility.SafeSpot.isSafe(home)) return home;
            }
        }
        return emergencyHome(player);
    }

    /** Sends every online player that matches out of an island (to their own home, or the hub). */
    public void evict(Island island, java.util.function.Predicate<Player> who, String message) {
        for (Player player : playersIn(island)) {
            if (who.test(player) && !player.hasPermission(BYPASS)) {
                evictPlayer(player, island, message);
            }
        }
    }

    /**
     * Moves a player to their personal island, creating a safe landing if necessary. Never crosses modes.
     */
    public boolean evictPlayer(Player player, Island island, String message) {
        Location target = null;
        Island own = ensurePersonalIsland(player);
        if (own != null && own != island && own.state() == Island.State.READY) {
            target = overworldHome(own);
        }
        if (target == null && hubPadBuilt) {
            target = hubLocation();
        }
        if (target == null) {
            target = emergencyHome(player);
        }
        if (target == null) {
            return false;
        }
        teleport(player, target);
        core.msg().warn(player, message);
        return true;
    }

    /**
     * Finds safe ground on the personal island without replacing existing player blocks.
     */
    private Location emergencyHome(Player player) {
        Island own = ensurePersonalIsland(player);
        if (own == null) return null;
        Location at = defaultSpawn(own);
        if (at == null) return null;
        return win.vampyuyu.survival.core.ModeTravel.safeLanding(at);
    }

    /** Creates a missing personal island without altering existing memberships or islands. */
    public Island ensurePersonalIsland(Player player) {
        Island owned = registry.ownedBy(player.getUniqueId()).orElse(null);
        if (owned != null) return owned;
        IslandConfig.TemplateCfg template = cfg.templates.values().stream().filter(IslandConfig.TemplateCfg::enabled)
            .findFirst().orElse(null);
        if (template == null) return null;
        create(player, template.id());
        return registry.ownedBy(player.getUniqueId()).orElse(null);
    }

    /** Online players standing in any of the island's three cells. */
    public List<Player> playersIn(Island island) {
        List<Player> list = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location l = player.getLocation();
            World w = l.getWorld();
            if (w != null && isIslandWorld(w.getName()) && grid.cellOf(l.getX()) == island.cellX
                    && grid.cellOf(l.getZ()) == island.cellZ) {
                list.add(player);
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- name resolution

    /** Online player, then any name seen before, then the server's offline cache. Never blocks on the network. */
    public Optional<UUID> resolve(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        Optional<UUID> known = registry.uuidOf(name);
        if (known.isPresent()) {
            return known;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? Optional.empty() : Optional.of(cached.getUniqueId());
    }

    public String nameOf(UUID player) {
        Player online = Bukkit.getPlayer(player);
        return online != null ? online.getName() : registry.nameOf(player);
    }

    private void notifyCoop(Island island, String text, UUID except) {
        for (UUID member : island.coop()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && !member.equals(except)) {
                core.msg().info(online, text);
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle bookkeeping

    /**
     * True while {@code gen} is still the island's current build token and the island still exists. Every callback of
     * a build, wipe, platform or scan job asks this first, so a result that arrives after a reset, rebuild or delete
     * (or after the plugin closed) is dropped instead of written into the new state.
     */
    private boolean current(Island island, int gen) {
        return !closed && island.generation == gen && registry.byId(island.id).orElse(null) == island;
    }

    /** Jobs on the same island cell of the same world never overlap (see {@link PlanRunner}). */
    private static String lane(String worldName, int cellX, int cellZ) {
        return worldName + ":" + cellX + "," + cellZ;
    }

    private static List<String> lanes(Island island) {
        return List.of(lane(SkyblockWorlds.WORLD, island.cellX, island.cellZ),
            lane(SkyblockWorlds.NETHER, island.cellX, island.cellZ), lane(SkyblockWorlds.END, island.cellX, island.cellZ));
    }

    /** True while generation, wipe or scan jobs of the island are queued or running. */
    public boolean busy(Island island) {
        for (String lane : lanes(island)) {
            if (runner.pendingIn(lane) > 0) {
                return true;
            }
        }
        return false;
    }

    private long retryDelay(int attempt) {
        return Math.min(1200L, cfg.retryDelayTicks * (1L << Math.min(attempt, 4)));
    }

    /** Tells the online co-op members and the online admins (each once). */
    private void tellAboutProblem(Island island, String forCoop, String forAdmins) {
        Set<UUID> told = new HashSet<>();
        for (UUID member : island.coop()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && told.add(member)) {
                core.msg().error(online, forCoop);
            }
        }
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(ADMIN) && told.add(admin.getUniqueId())) {
                core.msg().error(admin, forAdmins);
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle: create

    /** Island templates a player can pick from. */
    public List<IslandConfig.TemplateCfg> templates() {
        List<IslandConfig.TemplateCfg> list = new ArrayList<>();
        for (IslandConfig.TemplateCfg t : cfg.enabledTemplates()) {
            if (IslandTemplates.exists(t.id())) {
                list.add(t);
            }
        }
        return list;
    }

    public Result create(Player player, String templateId) {
        UUID id = player.getUniqueId();
        if (registry.ownedBy(id).isPresent()) {
            return Result.fail("You already own an island. Use /island home.");
        }
        IslandConfig.TemplateCfg template = templateId == null ? null : cfg.templates.get(templateId.toLowerCase(Locale.ROOT));
        if (template == null || !template.enabled() || !IslandTemplates.exists(template.id())) {
            return Result.fail("Unknown island type '" + templateId + "'. Open /island create to pick one.");
        }
        PlayerIslandData data = data(id);
        long wait = data.lastLostAt + cfg.createCooldownMinutes * 60_000L - now();
        if (data.lastLostAt > 0 && cfg.createCooldownMinutes > 0 && wait > 0) {
            return Result.fail("You can create a new island in " + Msg.duration(wait) + ".");
        }
        if (world(SkyblockWorlds.WORLD) == null) {
            return Result.fail("The island world is not available right now.");
        }
        core.modes().awaitIsland(player);
        Island island = registry.create(id, player.getName(), template.id(), now());
        cfg.flagDefaults.forEach(island::flag);
        // The first-time kit is only OWED here; the player is marked as having received it when the island is READY.
        island.kitOwed = !cfg.starterOnce || !data.starterClaimed;
        island.lootOwed = !cfg.starterOnce || !data.lootClaimed;
        island.kitFor = id;
        island.lootFor = id;
        data.islandsCreated++;
        data.lastIsland = island.id.toString();
        dirty(id);
        build(island, template, ++island.generation, 0, id);
        return Result.ok("Building your " + template.name() + " island... you will be teleported when it is ready.");
    }

    /**
     * Generates the overworld island of build {@code gen}; it becomes READY when that succeeds (a failure is retried
     * a few times with a growing delay), and the Nether platform follow.
     */
    private void build(Island island, IslandConfig.TemplateCfg template, int gen, int attempt, UUID notify) {
        if (!current(island, gen)) {
            return;
        }
        World overworld = world(SkyblockWorlds.WORLD);
        if (overworld == null) {
            log.warning("Cannot build island " + island.id + ": sb_world is missing");
            return;
        }
        island.state(Island.State.BUILDING);
        island.platformsReady = false;
        registry.changed();
        List<ItemSpec> chest = island.kitOwed ? template.chest() : cfg.starterRepeat;
        BlockPlan plan = IslandTemplates.overworld(template.id(), IslandTemplates.random(island.id, gen, 0), chest,
            template.biome(), cfg.endGateLit);
        int ox = grid.centerOf(island.cellX);
        int oz = grid.centerOf(island.cellZ);
        runner.submit(lane(SkyblockWorlds.WORLD, island.cellX, island.cellZ),
            jobs.plan("island " + island.id + " overworld", overworld, plan, ox, cfg.islandY, oz)).thenAccept(ok -> {
                if (!current(island, gen)) {
                    return; // reset, rebuilt or deleted meanwhile
                }
                if (!ok) {
                    buildFailed(island, template, gen, attempt, notify);
                    return;
                }
                island.state(Island.State.READY);
                if (island.kitOwed) {
                    island.kitOwed = false;
                    // the player the kit was computed for, not whoever owns the island by now (transfer)
                    editData(island.kitFor != null ? island.kitFor : island.owner, d -> d.starterClaimed = true);
                }
                registry.changed();
                Player owner = Bukkit.getPlayer(notify);
                if (owner != null && owner.isOnline()) {
                    core.msg().ok(owner, "Your island is ready!");
                    if (canAutoTeleport(owner)) {
                        teleportHome(owner);
                    }
                }
                buildPlatforms(island, gen, 0);
            });
    }

    private void buildFailed(Island island, IslandConfig.TemplateCfg template, int gen, int attempt, UUID notify) {
        if (attempt < cfg.buildRetries) {
            log.warning("Island " + island.id + " could not be generated (attempt " + (attempt + 1) + "), retrying");
            core.scheduler().later(retryDelay(attempt), () -> build(island, template, gen, attempt + 1, notify));
            return;
        }
        log.warning("Island " + island.id + " could not be generated (it stays in BUILDING state)");
        tellAboutProblem(island, "Your island could not be built. Ask an admin to run /islandadmin rebuild.",
            "The island of " + registry.nameOf(island.owner) + " could not be built: /islandadmin rebuild "
                + registry.nameOf(island.owner));
    }

    private boolean canAutoTeleport(Player player) {
        return !inDungeon(player) && core.modes().awaitingIsland(player);
    }

    /**
     * Generates the Nether platform of build {@code gen}. Their seeds depend only on (island, generation), so
     * a retry draws exactly the same plan over whatever the failed attempt left.
     */
    private void buildPlatforms(Island island, int gen, int attempt) {
        if (!current(island, gen)) {
            return;
        }
        World nether = world(SkyblockWorlds.NETHER);
        if (nether == null) {
            log.warning("Skyblock Nether missing: island " + island.id + " has no platform");
            return;
        }
        int ox = grid.centerOf(island.cellX);
        int oz = grid.centerOf(island.cellZ);
        boolean loot = island.lootOwed;
        BlockPlan netherPlan = IslandTemplates.nether(IslandTemplates.random(island.id, gen, 1),
            loot ? cfg.netherChest : List.of());
        CompletableFuture<Boolean> a = runner.submit(lane(SkyblockWorlds.NETHER, island.cellX, island.cellZ),
            jobs.plan("island " + island.id + " nether", nether, netherPlan, ox, cfg.islandY, oz));
        a.thenAccept(ok -> {
            if (!current(island, gen)) {
                return;
            }
            if (ok) {
                island.platformsReady = true;
                if (island.lootOwed) {
                    island.lootOwed = false;
                    editData(island.lootFor != null ? island.lootFor : island.owner, d -> d.lootClaimed = true);
                }
                registry.changed();
            } else if (attempt < cfg.buildRetries) {
                log.warning("The Nether platform of island " + island.id + " failed (attempt " + (attempt + 1)
                    + "), retrying");
                core.scheduler().later(retryDelay(attempt), () -> buildPlatforms(island, gen, attempt + 1));
            } else {
                log.warning("The Nether platform of island " + island.id + " could not be generated; "
                    + "/islandadmin platforms or a restart retries");
            }
        });
    }

    /**
     * Admin / recovery: generates the Nether platform of a READY island again (wiping them first, the
     * overworld stays untouched).
     */
    public Result rebuildPlatforms(Island island) {
        if (island.state() != Island.State.READY) {
            return Result.fail("The island is not ready; use rebuild instead.");
        }
        if (busy(island)) {
            return Result.fail("The island still has generation jobs queued; try again in a moment.");
        }
        int gen = ++island.generation;
        island.platformsReady = false;
        registry.changed();
        evict(island, p -> {
            String at = p.getWorld().getName();
            return SkyblockWorlds.NETHER.equals(at) || SkyblockWorlds.END.equals(at);
        }, "The Nether of this island is being rebuilt.");
        wipeWithRetry(() -> wipeDims(island, SkyblockWorlds.NETHER), () -> current(island, gen), 0,
            ok -> {
                if (ok) {
                    buildPlatforms(island, gen, 0);
                } else {
                    log.warning("The Nether/End of island " + island.id + " could not be cleared before rebuilding");
                }
            });
        return Result.ok("Rebuilding the Nether and End of " + registry.nameOf(island.owner) + ".");
    }

    // ---------------------------------------------------------------- lifecycle: reset, delete, recover

    private CompletableFuture<Boolean> wipe(Island island, String worldName) {
        World w = world(worldName);
        if (w == null) {
            return CompletableFuture.completedFuture(true);
        }
        return wipeCell(w, island.cellX, island.cellZ, Math.max(cfg.radiusFor(island), 20) + 4);
    }

    private CompletableFuture<Boolean> wipeCell(World w, int cellX, int cellZ, int radius) {
        return runner.submit(lane(w.getName(), cellX, cellZ),
            jobs.wipe("wipe " + w.getName() + " " + cellX + "," + cellZ, w, grid.centerOf(cellX), grid.centerOf(cellZ),
                radius));
    }

    private CompletableFuture<Boolean> wipeDims(Island island, String... worlds) {
        CompletableFuture<Boolean> all = CompletableFuture.completedFuture(true);
        for (String name : worlds) {
            all = all.thenCombine(wipe(island, name), (x, y) -> x && y);
        }
        return all;
    }

    private CompletableFuture<Boolean> wipeAll(Island island) {
        return wipeDims(island, SkyblockWorlds.WORLD, SkyblockWorlds.NETHER);
    }

    /**
     * Runs a wipe and retries a failed one (chunks that did not load, an exception) a few times with a growing delay.
     * {@code done} gets true once the wipe worked and false when the retries ran out; it is never called when
     * {@code wanted} turned false (the island was reset, deleted or the plugin closed meanwhile). Nothing is ever
     * built on top of a failed wipe: callers build only from {@code done(true)}.
     */
    private void wipeWithRetry(Supplier<CompletableFuture<Boolean>> wipe, BooleanSupplier wanted, int attempt,
            Consumer<Boolean> done) {
        wipe.get().thenAccept(ok -> {
            if (closed || !wanted.getAsBoolean()) {
                return;
            }
            if (ok) {
                done.accept(true);
            } else if (attempt >= cfg.buildRetries) {
                done.accept(false);
            } else {
                log.warning("A wipe failed (attempt " + (attempt + 1) + "), retrying");
                core.scheduler().later(retryDelay(attempt), () -> {
                    if (!closed && wanted.getAsBoolean()) {
                        wipeWithRetry(wipe, wanted, attempt + 1, done);
                    }
                });
            }
        });
    }

    private void wipeFailed(Island island) {
        log.warning("Island " + island.id + " could not be cleared; it stays RESETTING until /islandadmin rebuild");
        tellAboutProblem(island, "Your island could not be cleared and stays locked. An administrator can finish it "
            + "with /islandadmin rebuild.", "The island of " + registry.nameOf(island.owner)
            + " could not be cleared: run /islandadmin rebuild " + registry.nameOf(island.owner));
    }

    /** The checks of {@link #reset}, without doing anything (the command asks before it arms a confirmation). */
    public Result checkReset(Player player) {
        UUID id = player.getUniqueId();
        Island island = registry.ofPlayer(id).orElse(null);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(id), Cap.RESET)) {
            return Result.fail("Only the island owner can reset the island.");
        }
        if (island.state() != Island.State.READY) {
            return Result.fail("The island is busy, try again in a moment.");
        }
        long wait = island.lastResetAt + cfg.resetCooldownMinutes * 60_000L - now();
        if (island.lastResetAt > 0 && wait > 0) {
            return Result.fail("You can reset again in " + Msg.duration(wait) + ".");
        }
        return Result.ok("");
    }

    /** Owner only: wipes the island and generates it again (optionally with another template). */
    public Result reset(Player player, String templateId) {
        UUID id = player.getUniqueId();
        Result check = checkReset(player);
        if (!check.ok()) {
            return check;
        }
        Island island = registry.ofPlayer(id).orElseThrow();
        IslandConfig.TemplateCfg template = cfg.templates.get((templateId == null ? island.template : templateId)
            .toLowerCase(Locale.ROOT));
        if (template == null || !template.enabled() || !IslandTemplates.exists(template.id())) {
            return Result.fail("Unknown island type '" + templateId + "'.");
        }
        // A reset starts over: the full starter chest again, and the player's Skyblock inventory is emptied (no farming).
        island.kitOwed = true;
        island.lootOwed = true;
        island.kitFor = id;
        island.lootFor = id;
        int gen = ++island.generation; // drops every callback of the island's earlier builds, platform jobs and scans
        core.modes().resetSkyblockState(player);
        core.modes().awaitIsland(player);
        island.state(Island.State.RESETTING);
        island.lastResetAt = now();
        island.template = template.id();
        island.home = null;
        island.warp = null;
        island.value = 0;
        island.level = 0;
        island.levelAt = 0;
        island.platformsReady = false;
        registry.changed();
        evict(island, p -> true, "The island is being reset.");
        notifyCoop(island, registry.nameOf(island.owner) + " reset the island. It will be ready in a moment.", id);
        wipeWithRetry(() -> wipeAll(island), () -> current(island, gen), 0, ok -> {
            if (ok) {
                build(island, template, gen, 0, id);
            } else {
                wipeFailed(island);
            }
        });
        return Result.ok("Resetting your island... you will be teleported home when it is ready.");
    }

    /**
     * Admin: regenerates an island that is stuck (keeps members, flags and upgrades). An unfinished first build keeps
     * its owed first-time kit.
     */
    public Result rebuild(Island island) {
        IslandConfig.TemplateCfg template = cfg.templates.get(island.template);
        if (template == null || !IslandTemplates.exists(template.id())) {
            template = templates().stream().findFirst().orElse(null);
        }
        if (template == null) {
            return Result.fail("No island template is enabled.");
        }
        if (busy(island)) {
            return Result.fail("The island still has generation jobs queued; try again in a moment.");
        }
        IslandConfig.TemplateCfg chosen = template;
        int gen = ++island.generation;
        island.state(Island.State.RESETTING);
        island.platformsReady = false;
        registry.changed();
        evict(island, p -> true, "The island is being rebuilt.");
        wipeWithRetry(() -> wipeAll(island), () -> current(island, gen), 0, ok -> {
            if (ok) {
                build(island, chosen, gen, 0, island.owner);
            } else {
                wipeFailed(island);
            }
        });
        return Result.ok("Rebuilding the island of " + registry.nameOf(island.owner) + ".");
    }

    /** Admin: removes an island completely. Its cell is wiped and then reused. */
    public Result delete(UUID owner) {
        Island island = registry.ofPlayer(owner).orElse(null);
        if (island == null) {
            return Result.fail("That player has no island.");
        }
        evict(island, p -> true, "This island was deleted.");
        Set<UUID> former = island.coop();
        island.generation++; // callbacks of jobs still queued for this island must not touch it any more
        registry.delete(island);
        invites.dropIsland(island.id);
        for (UUID member : former) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                core.msg().warn(online, "Your island was deleted by an administrator.");
                data(member).lastLostAt = now();
                dirty(member);
            }
            if (tracker != null) {
                tracker.forget(member);
            }
        }
        int cx = island.cellX;
        int cz = island.cellZ;
        wipeWithRetry(() -> wipeAll(island), () -> registry.isDirty(cx, cz), 0, ok -> {
            if (ok) {
                registry.releaseCell(cx, cz);
            } else {
                log.warning("The cell " + cx + "," + cz + " could not be cleaned; it stays parked until the next start");
            }
        });
        return Result.ok("Deleted the island of " + registry.nameOf(owner) + " (the cell is being cleaned).");
    }

    /**
     * Called at enable: cleans cells left dirty by a crash, regenerates unfinished islands and gives READY islands
     * whose Nether platform never finished another go.
     */
    void recover() {
        for (int[] cell : registry.dirtyCells()) {
            wipeWithRetry(() -> {
                CompletableFuture<Boolean> all = CompletableFuture.completedFuture(true);
                for (String name : List.of(SkyblockWorlds.WORLD, SkyblockWorlds.NETHER)) {
                    all = all.thenCombine(cellWipe(name, cell), (x, y) -> x && y);
                }
                return all;
            }, () -> registry.isDirty(cell[0], cell[1]), 0, ok -> {
                if (ok) {
                    registry.releaseCell(cell[0], cell[1]);
                }
            });
        }
        for (Island island : new ArrayList<>(registry.all())) {
            if (island.state() != Island.State.READY) {
                log.info("Recovering unfinished island " + island.id);
                rebuild(island);
            } else if (!island.platformsReady) {
                log.info("Regenerating the Nether platform of island " + island.id);
                rebuildPlatforms(island);
            }
        }
    }

    private CompletableFuture<Boolean> cellWipe(String worldName, int[] cell) {
        World w = world(worldName);
        return w == null ? CompletableFuture.completedFuture(true)
            : wipeCell(w, cell[0], cell[1], cfg.maxRadius() + 4);
    }

    // ---------------------------------------------------------------- visits and warps

    public Result visit(Player player, Island target, boolean viaWarp) {
        if (inDungeon(player)) {
            return Result.fail(DUNGEON_REFUSAL);
        }
        if (target.state() != Island.State.READY) {
            return Result.fail("That island is being built.");
        }
        IslandRole role = target.roleOf(player.getUniqueId());
        if (!canEnter(player, target)) {
            return Result.fail(enterDenyReason(player, target));
        }
        if (!role.isCoop() && !cooldowns.tryUse(player.getUniqueId(), "visit", cfg.visitCooldownSeconds * 1000L)) {
            return Result.fail("Slow down: wait " + Msg.duration(cooldowns.remaining(player.getUniqueId(), "visit")) + ".");
        }
        Location destination = null;
        if (viaWarp && target.warp != null && target.warp.pos != null) {
            Location warp = target.warp.pos.toLocation();
            if (warp != null && isSafe(warp) && islandAtLocation(warp) == target) {
                destination = warp;
                target.warp.visits++;
            }
        }
        if (destination == null) {
            destination = overworldHome(target);
        }
        if (destination == null) {
            return Result.fail("That island is not available right now.");
        }
        if (!role.isCoop()) {
            target.visits++;
        }
        registry.changed();
        teleport(player, destination);
        return Result.ok("Teleporting to " + registry.nameOf(target.owner) + "'s island...");
    }

    /** Islands with a public warp that visitors may use, best first. */
    public List<Island> publicWarps() {
        List<Island> list = new ArrayList<>();
        for (Island island : registry.all()) {
            if (island.warp != null && island.warp.pos != null && !island.locked
                    && island.state() == Island.State.READY) {
                list.add(island);
            }
        }
        list.sort(Comparator.comparingLong((Island i) -> i.warp.visits).reversed()
            .thenComparing(Comparator.comparingLong((Island i) -> i.level).reversed()));
        return list;
    }

    public Result setWarp(Player player, String rawName) {
        Island island = registry.ofPlayer(player.getUniqueId()).orElse(null);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(player.getUniqueId()), Cap.WARP)) {
            return Result.fail("Only the owner and co-owners can manage the island warp.");
        }
        String name = sanitizeName(rawName == null || rawName.isBlank() ? registry.nameOf(island.owner) + "'s island" : rawName);
        if (name.length() < 3) {
            return Result.fail("The warp name needs at least 3 letters or digits.");
        }
        if (name.length() > cfg.warpNameMax) {
            return Result.fail("The warp name is too long (max " + cfg.warpNameMax + ").");
        }
        Location here = player.getLocation();
        if (islandAtLocation(here) != island || !SkyblockWorlds.WORLD.equals(here.getWorld().getName()) || !isSafe(here)) {
            return Result.fail("Stand on a safe spot of your island (overworld) to set the warp.");
        }
        for (Island other : registry.all()) {
            if (other != island && other.warp != null && other.warp.name.equalsIgnoreCase(name)) {
                return Result.fail("Another island already uses that warp name.");
            }
        }
        Island.Warp warp = island.warp == null ? new Island.Warp() : island.warp;
        warp.name = name;
        warp.pos = Pos.of(here);
        if (warp.since == 0) {
            warp.since = now();
        }
        island.warp = warp;
        registry.changed();
        return Result.ok("Public warp '" + name + "' set. Visitors find it in /island warp.");
    }

    public Result removeWarp(Player player) {
        Island island = registry.ofPlayer(player.getUniqueId()).orElse(null);
        if (island == null || island.warp == null) {
            return Result.fail("Your island has no public warp.");
        }
        if (!IslandAccess.has(island.roleOf(player.getUniqueId()), Cap.WARP)) {
            return Result.fail("Only the owner and co-owners can manage the island warp.");
        }
        island.warp = null;
        registry.changed();
        return Result.ok("Public warp removed.");
    }

    static String sanitizeName(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : raw.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-' || c == '\'') {
                out.append(c);
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    public Result setHome(Player player) {
        Island island = registry.ofPlayer(player.getUniqueId()).orElse(null);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(player.getUniqueId()), Cap.SETHOME)) {
            return Result.fail("Only the owner and co-owners can set the island home.");
        }
        Location here = player.getLocation();
        if (islandAtLocation(here) != island || !SkyblockWorlds.WORLD.equals(here.getWorld().getName())) {
            return Result.fail("The island home must be on your island, in the overworld.");
        }
        if (!isSafe(here)) {
            return Result.fail("That spot is not safe: stand on solid ground with free space above.");
        }
        island.home = Pos.of(here);
        registry.changed();
        return Result.ok("Island home set.");
    }

    /** Flips the player's own "show the island border" preference and applies it right away. */
    public boolean toggleBorder(Player player) {
        PlayerIslandData data = data(player.getUniqueId());
        data.borderVisible = !data.borderVisible;
        dirty(player.getUniqueId());
        if (tracker != null) {
            tracker.reapply(player);
        }
        return data.borderVisible;
    }

    // ---------------------------------------------------------------- co-op

    private Island own(Player actor) {
        Island here = islandAtLocation(actor.getLocation());
        return here != null && here.roleOf(actor.getUniqueId()).isCoop() ? here
            : registry.ofPlayer(actor.getUniqueId()).orElse(null);
    }

    public Result invite(Player actor, UUID target) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.INVITE)) {
            return Result.fail("Only the owner and co-owners can invite players.");
        }
        if (target.equals(actor.getUniqueId())) {
            return Result.fail("You cannot invite yourself.");
        }
        Player online = Bukkit.getPlayer(target);
        if (online == null) {
            return Result.fail("That player must be online to be invited.");
        }
        if (island.coop().contains(target)) {
            return Result.fail(online.getName() + " already belongs to this island.");
        }
        if (island.banned.contains(target)) {
            return Result.fail(online.getName() + " is banned from your island. Unban them first.");
        }
        if (island.size() >= cfg.memberSlotsFor(island)) {
            return Result.fail("Your island is full (" + cfg.memberSlotsFor(island) + " slots). Upgrade the member slots.");
        }
        Invites.Added added = invites.add(target, island.id, actor.getUniqueId(), cfg.inviteExpirySeconds * 1000L,
            cfg.maxPendingInvites);
        if (added == Invites.Added.TOO_MANY) {
            return Result.fail(online.getName() + " has too many pending invitations.");
        }
        core.msg().mini(online, "<yellow>" + escape(actor.getName()) + "</yellow> <gray>invited you to join their island."
            + " Type <green>/island accept</green> or <red>/island deny</red> within "
            + cfg.inviteExpirySeconds + " seconds.");
        return Result.ok("Invitation sent to " + online.getName() + ".");
    }

    private static String escape(String text) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(text);
    }

    public Result accept(Player player, String fromName) {
        UUID id = player.getUniqueId();
        UUID wanted = null;
        if (fromName != null && !fromName.isBlank()) {
            UUID inviter = resolve(fromName).orElse(null);
            if (inviter == null) {
                return Result.fail("Unknown player '" + fromName + "'.");
            }
            Island theirs = registry.ofPlayer(inviter).orElse(null);
            if (theirs == null) {
                return Result.fail("That player has no island.");
            }
            wanted = theirs.id;
        }
        Invites.Invite invite = invites.take(id, wanted).orElse(null);
        if (invite == null) {
            return Result.fail("You have no pending invitation" + (fromName == null ? "" : " from " + fromName) + ".");
        }
        Island island = registry.byId(invite.island()).orElse(null);
        if (island == null || island.state() != Island.State.READY) {
            return Result.fail("That invitation is no longer valid.");
        }
        if (island.banned.contains(id)) {
            return Result.fail("You are banned from that island.");
        }
        if (island.size() >= cfg.memberSlotsFor(island)) {
            return Result.fail("That island is full.");
        }
        if (!registry.addMember(island, id, IslandRole.MEMBER)) {
            return Result.fail("Could not join that island.");
        }
        invites.dropTarget(id);
        data(id).lastIsland = island.id.toString();
        dirty(id);
        notifyCoop(island, player.getName() + " joined the island.", id);
        if (tracker != null) {
            tracker.refreshPlayer(player);
        }
        visit(player, island, false);
        return Result.ok("You joined " + registry.nameOf(island.owner) + "'s island. Your personal island is preserved.");
    }

    public Result deny(Player player, String fromName) {
        UUID wanted = null;
        if (fromName != null && !fromName.isBlank()) {
            UUID inviter = resolve(fromName).orElse(null);
            Island theirs = inviter == null ? null : registry.ofPlayer(inviter).orElse(null);
            if (theirs == null) {
                return Result.fail("Unknown island owner '" + fromName + "'.");
            }
            wanted = theirs.id;
        }
        Invites.Invite invite = invites.take(player.getUniqueId(), wanted).orElse(null);
        if (invite == null) {
            return Result.fail("You have no pending invitation.");
        }
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            core.msg().info(inviter, player.getName() + " declined your island invitation.");
        }
        return Result.ok("Invitation declined.");
    }

    public Result leave(Player player) {
        UUID id = player.getUniqueId();
        Island island = own(player);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (island.owner.equals(id)) {
            return Result.fail("The owner cannot leave. Transfer the island first (/island transfer <player>).");
        }
        if (!confirms.consume(id, "leave", null)) {
            confirms.arm(id, "leave", null, cfg.confirmSeconds * 1000L);
            return Result.ok("Type /island leave again within " + cfg.confirmSeconds + " seconds to confirm.");
        }
        registry.removeMember(island, id);
        PlayerIslandData data = data(id);
        data.lastLostAt = now();
        dirty(id);
        notifyCoop(island, player.getName() + " left the island.", id);
        evict(island, p -> p.getUniqueId().equals(id), "You left the island.");
        if (tracker != null) {
            tracker.refreshPlayer(player);
        }
        return Result.ok("You left the island.");
    }

    public Result kick(Player actor, UUID target) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        IslandRole mine = island.roleOf(actor.getUniqueId());
        if (!IslandAccess.has(mine, Cap.KICK)) {
            return Result.fail("Only the owner and co-owners can kick players.");
        }
        IslandRole theirs = island.roleOf(target);
        if (target.equals(actor.getUniqueId())) {
            return Result.fail("You cannot kick yourself.");
        }
        if (theirs.isCoop()) {
            if (!IslandAccess.outranks(mine, theirs)) {
                return Result.fail("You cannot kick " + nameOf(target) + ": they have the same or a higher role.");
            }
            registry.removeMember(island, target);
            long lostAt = now();
            editData(target, data -> data.lastLostAt = lostAt);
            Player online = Bukkit.getPlayer(target);
            if (online != null) {
                core.msg().warn(online, "You were removed from " + registry.nameOf(island.owner) + "'s island.");
                if (tracker != null) {
                    tracker.refreshPlayer(online);
                }
            }
            evict(island, p -> p.getUniqueId().equals(target), "You were kicked from the island.");
            return Result.ok(nameOf(target) + " was removed from the island.");
        }
        Player online = Bukkit.getPlayer(target);
        if (online == null || !playersIn(island).contains(online)) {
            return Result.fail(nameOf(target) + " is not on your island.");
        }
        island.trusted.remove(target);
        registry.changed();
        evict(island, p -> p.getUniqueId().equals(target), "You were kicked from the island.");
        return Result.ok(nameOf(target) + " was kicked off your island.");
    }

    public Result ban(Player actor, UUID target) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        IslandRole mine = island.roleOf(actor.getUniqueId());
        if (!IslandAccess.has(mine, Cap.BAN)) {
            return Result.fail("Only the owner and co-owners can ban players.");
        }
        if (target.equals(actor.getUniqueId())) {
            return Result.fail("You cannot ban yourself.");
        }
        IslandRole theirs = island.roleOf(target);
        if (theirs == IslandRole.BANNED) {
            return Result.fail(nameOf(target) + " is already banned.");
        }
        if (theirs.isCoop() && !IslandAccess.outranks(mine, theirs)) {
            return Result.fail("You cannot ban " + nameOf(target) + ": they have the same or a higher role.");
        }
        if (theirs.isCoop()) {
            registry.removeMember(island, target);
            Player online = Bukkit.getPlayer(target);
            if (online != null && tracker != null) {
                tracker.refreshPlayer(online);
            }
        }
        island.trusted.remove(target);
        island.banned.add(target);
        invites.dropTarget(target);
        registry.rememberName(target, nameOf(target));
        registry.changed();
        evict(island, p -> p.getUniqueId().equals(target), "You were banned from that island.");
        return Result.ok(nameOf(target) + " is banned from your island.");
    }

    public Result unban(Player actor, UUID target) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.BAN)) {
            return Result.fail("Only the owner and co-owners can unban players.");
        }
        if (!island.banned.remove(target)) {
            return Result.fail(nameOf(target) + " is not banned.");
        }
        registry.changed();
        return Result.ok(nameOf(target) + " is no longer banned.");
    }

    public Result trust(Player actor, UUID target) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.TRUST)) {
            return Result.fail("Only the owner and co-owners can trust players.");
        }
        IslandRole role = island.roleOf(target);
        if (role.isCoop()) {
            return Result.fail(nameOf(target) + " is already part of the island.");
        }
        if (role == IslandRole.BANNED) {
            return Result.fail(nameOf(target) + " is banned. Unban them first.");
        }
        if (!island.trusted.add(target)) {
            return Result.fail(nameOf(target) + " is already trusted.");
        }
        registry.rememberName(target, nameOf(target));
        registry.changed();
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            core.msg().info(online, "You are now a trusted guest on " + registry.nameOf(island.owner) + "'s island.");
        }
        return Result.ok(nameOf(target) + " can now build on your island as a trusted guest.");
    }

    public Result untrust(Player actor, UUID target) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.TRUST)) {
            return Result.fail("Only the owner and co-owners can untrust players.");
        }
        if (!island.trusted.remove(target)) {
            return Result.fail(nameOf(target) + " is not a trusted guest.");
        }
        registry.changed();
        return Result.ok(nameOf(target) + " is no longer trusted.");
    }

    public Result setRole(Player actor, UUID target, IslandRole role) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.SETROLE)) {
            return Result.fail("Only the island owner can change roles.");
        }
        if (role != IslandRole.CO_OWNER && role != IslandRole.MEMBER) {
            return Result.fail("Roles you can give: co_owner, member.");
        }
        if (!island.members.containsKey(target)) {
            return Result.fail(nameOf(target) + " is not a member of your island.");
        }
        island.members.put(target, role.name());
        registry.changed();
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            core.msg().info(online, "Your island role is now: " + role.label() + ".");
        }
        return Result.ok(nameOf(target) + " is now " + role.label() + ".");
    }

    /** Hands the island to a member. Needs an earlier call to arm the confirmation. */
    public Result transfer(Player actor, UUID target) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.TRANSFER)) {
            return Result.fail("Only the island owner can transfer it.");
        }
        if (!island.members.containsKey(target)) {
            return Result.fail(nameOf(target) + " must first be a member of your island (/island invite).");
        }
        String key = target.toString();
        if (!confirms.consume(actor.getUniqueId(), "transfer", key)) {
            confirms.arm(actor.getUniqueId(), "transfer", key, cfg.confirmSeconds * 1000L);
            return Result.ok("This gives the island to " + nameOf(target) + ". Type /island transfer " + nameOf(target)
                + " again within " + cfg.confirmSeconds + " seconds to confirm.");
        }
        registry.transfer(island, target);
        String islandId = island.id.toString();
        editData(target, data -> data.lastIsland = islandId);
        notifyCoop(island, nameOf(target) + " is now the owner of the island.", null);
        return Result.ok("You transferred the island to " + nameOf(target) + ". You are now a co-owner.");
    }

    public Result setLocked(Player actor, boolean locked) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.LOCK)) {
            return Result.fail("Only the owner and co-owners can lock the island.");
        }
        island.locked = locked;
        registry.changed();
        if (locked) {
            evict(island, p -> {
                IslandRole role = island.roleOf(p.getUniqueId());
                return !role.isCoop() && role != IslandRole.TRUSTED;
            }, "The island was locked.");
        }
        return Result.ok(locked ? "Island locked: only members and trusted guests can enter." : "Island unlocked.");
    }

    public Result setFlag(Player actor, IslandFlag flag, boolean value) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.SETTINGS)) {
            return Result.fail("Only the owner and co-owners can change island settings.");
        }
        island.flag(flag, value);
        registry.changed();
        if (tracker != null) {
            tracker.refreshIsland(island);
        }
        return Result.ok(flag.label() + ": " + (value ? "on" : "off"));
    }

    public Result setTimeLock(Player actor, TimeLock lock) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.SETTINGS)) {
            return Result.fail("Only the owner and co-owners can change island settings.");
        }
        island.timeLock = lock.name();
        registry.changed();
        if (tracker != null) {
            tracker.refreshIsland(island);
        }
        return Result.ok("Island time: " + lock.label());
    }

    // ---------------------------------------------------------------- upgrades

    public List<Tier> tiers(Upgrade type) {
        return switch (type) {
            case RADIUS -> cfg.radiusTiers;
            case MEMBERS -> cfg.memberTiers;
            case MINIONS -> cfg.minionTiers;
        };
    }

    public int tierOf(Island island, Upgrade type) {
        int tier = switch (type) {
            case RADIUS -> island.radiusTier;
            case MEMBERS -> island.memberTier;
            case MINIONS -> island.minionTier;
        };
        return Math.min(Math.max(0, tier), tiers(type).size() - 1);
    }

    /** Island expansion remains usable without the retired coins system. */
    public double upgradeCost(Tier tier) { return core.service(EconomyApi.class).isPresent() ? tier.cost() : 0; }

    public Result upgrade(Player actor, Upgrade type) {
        Island island = own(actor);
        if (island == null) {
            return Result.fail("You do not have an island.");
        }
        if (!IslandAccess.has(island.roleOf(actor.getUniqueId()), Cap.UPGRADE)) {
            return Result.fail("Only the owner and co-owners can buy upgrades.");
        }
        if (type == Upgrade.MINIONS && !cfg.minionUpgrade) {
            return Result.fail("Minion slots cannot be bought yet.");
        }
        int current = tierOf(island, type);
        List<Tier> tiers = tiers(type);
        if (current + 1 >= tiers.size()) {
            return Result.fail(type.label() + " is already at the maximum.");
        }
        Tier next = tiers.get(current + 1);
        if (upgradeCost(next) > 0) {
            EconomyApi economy = core.service(EconomyApi.class).orElse(null);
            if (economy == null) {
                return Result.fail("Coins are not available right now, try again later.");
            }
            if (!economy.take(actor.getUniqueId(), upgradeCost(next), "island:upgrade:" + type.name().toLowerCase(Locale.ROOT))) {
                return Result.fail("You need " + Msg.coins(upgradeCost(next)) + " for this upgrade.");
            }
        }
        switch (type) {
            case RADIUS -> island.radiusTier = current + 1;
            case MEMBERS -> island.memberTier = current + 1;
            case MINIONS -> island.minionTier = current + 1;
        }
        registry.changed();
        if (tracker != null) {
            tracker.refreshIsland(island);
        }
        notifyCoop(island, actor.getName() + " upgraded " + type.label() + " to " + next.value() + ".", actor.getUniqueId());
        return Result.ok(type.label() + " upgraded to " + next.value() + ".");
    }

    // ---------------------------------------------------------------- level and ranking

    public Result recalculate(Island island, boolean manual, Consumer<Island> done) {
        if (island.state() != Island.State.READY) {
            return Result.fail("The island is busy.");
        }
        if (scanning.contains(island.id)) {
            return Result.fail("The island level is already being calculated.");
        }
        long wait = island.levelAt + cfg.levelManualCooldownSeconds * 1000L - now();
        if (manual && island.levelAt > 0 && wait > 0) {
            return Result.fail("You can recalculate in " + Msg.duration(wait) + ".");
        }
        World overworld = world(SkyblockWorlds.WORLD);
        if (overworld == null) {
            return Result.fail("The island world is not available.");
        }
        scanning.add(island.id);
        int gen = island.generation;
        Counting job = jobs.scan("level " + island.id, overworld, grid.centerOf(island.cellX),
            grid.centerOf(island.cellZ), cfg.radiusFor(island));
        runner.submit(lane(SkyblockWorlds.WORLD, island.cellX, island.cellZ), job).thenAccept(ok -> {
            scanning.remove(island.id);
            // a scan that started before a reset / rebuild / delete counted blocks that no longer exist
            if (!ok || !current(island, gen) || island.state() != Island.State.READY) {
                return;
            }
            island.value = LevelCalculator.value(job.counts(), cfg.levelWeights, cfg.levelDefaultWeight);
            island.level = LevelCalculator.level(island.value, cfg.levelDivisor);
            island.levelAt = now();
            registry.changed();
            if (done != null) {
                done.accept(island);
            }
        });
        return Result.ok("Calculating the island level...");
    }

    /** Islands ranked by level (value breaks ties), best first. */
    public List<Island> top(int limit) {
        List<Island> list = new ArrayList<>(registry.all());
        list.removeIf(i -> i.level <= 0 && i.value <= 0);
        list.sort(Comparator.comparingLong((Island i) -> i.level).reversed()
            .thenComparing(Comparator.comparingDouble((Island i) -> i.value).reversed())
            .thenComparingLong(i -> i.createdAt));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public void autoLevelOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Island island = registry.ofPlayer(player.getUniqueId()).orElse(null);
            if (island != null && island.owner.equals(player.getUniqueId())) {
                recalculate(island, false, null);
            }
        }
    }

    Logger logger() {
        return log;
    }

    void warn(String text, Throwable t) {
        log.log(Level.WARNING, text, t);
    }
}
