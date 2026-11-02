package win.vampyuyu.survival.realm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.api.BastionApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.IslandConfig;
import win.vampyuyu.survival.island.IslandService;
import win.vampyuyu.survival.island.PlanJob;
import win.vampyuyu.survival.island.WorldSetup;

/**
 * The Tempestuous Archipelago (GDD 3.1, issue 44): Realm 6, home of the Primordial Thunder Dragon.
 *
 * <p><b>World</b>: one shared world, {@code sb_storm}, always under rain. Seven islands in the sky (see {@link StormPlan}).
 *
 * <p><b>Movement</b> (GDD 12.5, prototype): three {@link Mover moving platforms} that run on the world clock and carry
 * the players standing on them, and six propulsion pads ({@link PadMath}) that throw a player to the next island. A fall
 * brings the player back to the last island they stood on, never kills, and the gusts that sometimes blow are announced
 * three seconds ahead and ignored by a player who sneaks.
 *
 * <p><b>Resource</b>: the Thunder Quartz nodes give 1 to 2 Thunder Quartz for sure and grow back; the rest is the
 * generic Realm resource feature ({@link RealmService}). The Lightning Rod machine that burns it is in the quarry feature.
 *
 * <p><b>Opening and fight</b>: defeating the boss named by {@code island.storm.gate-boss} lights Gate 6; the seal on the
 * Eyrie opens the Tempest Bastion ({@link BastionApi}) for a player holding its key.
 */
public final class StormRealm implements Listener {
    public static final String WORLD = SkyblockWorlds.STORM;
    public static final String DEFAULT_GATE_BOSS = "sylvan_matriarch";
    public static final String BASTION = "tempest_bastion";
    public static final String THUNDER_QUARTZ = RealmItems.THUNDER_QUARTZ;
    public static final String MARKER_FILE = "realms/storm-world.json";
    public static final String ADMIN = "vampyuyu.sb.storm.admin";
    public static final String USE = "vampyuyu.sb.storm.use";
    private static final String LANE = "storm";
    private static final long NODE_RESPAWN_TICKS = 90L * 20L;
    private static final long PAD_CHARGE_TICKS = 12;
    private static final long PAD_COOLDOWN_TICKS = 60;
    private static final int PAD_SLOW_FALL_TICKS = 12 * 20;
    private static final double BORDER = 700;
    private static final Material PLATFORM_TOP = Material.WAXED_WEATHERED_CUT_COPPER;
    private static final Material PLATFORM_UNDER = Material.WAXED_OXIDIZED_CUT_COPPER;

    private static final Map<String, String> LORE = Map.of(
        "vane", "The vane turns against the wind. It points where the Dragon last breathed.",
        "field", "A thousand rods once caught a sky that no longer exists. Their copper still sings.",
        "cirrus", "Cloud that forgot how to fall. Sound travels strangely here.",
        "maelstrom", "The storm has an eye. You are standing in it, and it is looking back.",
        "roost", "The Storm Roc builds where the lightning strikes twice.",
        "eyrie", "Under the seal, the Thunder Dragon turns the winds of a world that no longer needs them.");

    /** Persisted marker of the build. */
    public static final class Marker {
        public int version;
        public boolean complete;
        public int originY;
    }

    private final Core core;
    private final Map<Long, StormPlan.Spot> nodeAt = new HashMap<>();
    private final Set<Long> pendingNodes = new HashSet<>();
    private final List<Mover> movers = new ArrayList<>();
    private final Map<Long, StormPlan.Pad> padAt = new HashMap<>();
    private final Map<UUID, Long> charging = new HashMap<>();
    private final Map<UUID, Long> padReadyAt = new HashMap<>();
    private final Map<UUID, String> lastIsland = new HashMap<>();
    private final Set<String> announced = new HashSet<>();
    private final List<Scheduler.Handle> tasks = new ArrayList<>();
    private IslandService islands;
    private volatile boolean ready;
    private long lastWarned = -1;

    public StormRealm(Core core) {
        this.core = core;
        for (StormPlan.Spot node : StormPlan.NODES) {
            nodeAt.put(BlockPlan.key(node.x(), node.y(), node.z()), node);
        }
        for (StormPlan.Pad pad : StormPlan.PADS) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    padAt.put(BlockPlan.key(pad.x() + dx, pad.y(), pad.z() + dz), pad);
                }
            }
        }
        for (StormPlan.Platform platform : StormPlan.PLATFORMS) {
            movers.add(new Mover(platform));
        }
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        core.permissions().register(ADMIN, "Build, reset and enter the Tempestuous Archipelago", PermissionDefault.OP);
        core.permissions().register(USE, "Use /storm", PermissionDefault.TRUE);
        core.listen(this);
        registerCommand();
        islands = core.service(IslandApi.class).filter(IslandService.class::isInstance).map(IslandService.class::cast).orElse(null);
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.ARCHIPELAGO, p -> ready ? spawn() : null));
        core.scheduler().nextTick(this::setupWorld);
        tasks.add(core.scheduler().repeating(1L, 1L, this::platformTick));
        tasks.add(core.scheduler().repeating(4L, 4L, this::padTick));
        tasks.add(core.scheduler().repeating(2L, 2L, this::gustTick));
        tasks.add(core.scheduler().repeating(20L, 20L, this::islandTick));
        tasks.add(core.scheduler().repeating(60L, 60L, this::lightningTick));
    }

    public void close() {
        tasks.forEach(Scheduler.Handle::cancel);
        tasks.clear();
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.ARCHIPELAGO, null));
        World world = world();
        if (world != null) {
            for (Mover mover : movers) {
                mover.remove(world); // nothing stays in the air after a clean stop; a crash is swept at the next start
            }
            for (long k : pendingNodes) {
                world.getBlockAt(BlockPlan.keyX(k), StormPlan.ORIGIN_Y + BlockPlan.keyY(k), BlockPlan.keyZ(k))
                    .setType(Material.CHISELED_QUARTZ_BLOCK, false);
            }
        }
        pendingNodes.clear();
    }

    public boolean ready() {
        return ready;
    }

    public World world() {
        return Bukkit.getWorld(WORLD);
    }

    /** Where the Gate lands the player; null while the world is missing. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world, StormPlan.SPAWN[0] + 0.5, StormPlan.ORIGIN_Y + StormPlan.SPAWN[1],
            StormPlan.SPAWN[2] + 0.5, 180f, 0f);
    }

    private Location rescue(StormPlan.Islet islet) {
        World world = world();
        int[] p = islet.rescue();
        return world == null ? null : new Location(world, p[0] + 0.5, StormPlan.ORIGIN_Y + p[1], p[2] + 0.5, 0f, 0f);
    }

    private void setupWorld() {
        WorldSetup setup = new WorldSetup(core.getPlugin().getLogger());
        World world = setup.ensure(WORLD, World.Environment.NORMAL, Biome.WINDSWEPT_HILLS, StormPlan.ORIGIN_Y + 1);
        if (world == null) {
            core.getPlugin().getLogger().warning("The Tempestuous Archipelago world could not be created: the Realm stays closed");
            return;
        }
        setup.configure(world, new IslandConfig.WorldCfg("CYCLE", BORDER, false, Map.of("mob_griefing", false)),
            StormPlan.SPAWN[0], StormPlan.ORIGIN_Y + StormPlan.SPAWN[1], StormPlan.SPAWN[2]);
        try {
            world.setStorm(true); // rain and a dark sky; the lightning is ours (no fire, no strike damage)
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Could not start the storm of the Archipelago: " + e);
        }
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        boolean current = marker.complete && marker.version == StormPlan.VERSION && marker.originY == StormPlan.ORIGIN_Y;
        if (current && world.getBlockAt(0, StormPlan.ORIGIN_Y, 0).getType() != Material.AIR) {
            restoreNodes(world);
            ready = true;
            return;
        }
        rebuild();
    }

    /** Builds the Realm again over the old one (admin command, layout change). */
    public boolean rebuild() {
        World world = world();
        if (world == null || islands == null) {
            return false;
        }
        ready = false;
        for (Mover mover : movers) {
            mover.remove(world);
        }
        BlockPlan plan = StormPlan.build();
        islands.runner().submit(LANE, new PlanJob(core.getPlugin(), "storm", world, plan, 0, StormPlan.ORIGIN_Y, 0,
            islands.config().chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    core.getPlugin().getLogger().warning("The Tempestuous Archipelago could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = StormPlan.VERSION;
                marker.complete = true;
                marker.originY = StormPlan.ORIGIN_Y;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Archipelago marker", e);
                    }
                });
            });
        return true;
    }

    /** A crash can leave a harvested node as plain stone: put the quartz back. */
    private void restoreNodes(World world) {
        for (StormPlan.Spot node : StormPlan.NODES) {
            Block block = world.getBlockAt(node.x(), StormPlan.ORIGIN_Y + node.y(), node.z());
            if (block.getType() == Material.CALCITE) {
                block.setType(Material.CHISELED_QUARTZ_BLOCK, false);
            }
        }
    }

    boolean inRealm(World world) {
        return world != null && WORLD.equals(world.getName());
    }

    // ---------------------------------------------------------------- opening

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        Settings settings = Settings.load(core.data(), "island");
        if (!settings.getString("storm.gate-boss", DEFAULT_GATE_BOSS).equalsIgnoreCase(event.getBossId())) {
            return;
        }
        Set<UUID> winners = new HashSet<>();
        if (event.getKiller() != null) {
            winners.add(event.getKiller().getUniqueId());
        }
        event.getTopDamagers().forEach(d -> winners.add(d.player()));
        for (UUID id : winners) {
            openFor(id);
        }
    }

    /** Lights Gate 6 for a player and tells them when they are online. */
    public boolean openFor(UUID id) {
        SkyGateApi gates = core.service(SkyGateApi.class).orElse(null);
        if (gates == null || !gates.light(id, Realm.ARCHIPELAGO)) {
            return false;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            core.msg().ok(player, "Gate 6 of the Quai des Huit lights: the Tempestuous Archipelago is open.");
        }
        return true;
    }

    // ---------------------------------------------------------------- moving platforms

    private void platformTick() {
        World world = world();
        if (world == null || !ready || world.getPlayers().isEmpty()) {
            return;
        }
        long now = world.getFullTime();
        for (Mover mover : movers) {
            mover.tick(world, now);
        }
    }

    /** One platform: the blocks it is made of are placed at {@code current}, or nowhere while {@code current} is -1. */
    private final class Mover {
        private final StormPlan.Platform spec;
        private int current = -1;

        Mover(StormPlan.Platform spec) {
            this.spec = spec;
        }

        private int[] centre(int index) {
            return spec.track().at(index);
        }

        private boolean loaded(World world, int[] c) {
            int half = StormPlan.PLATFORM_HALF + 1;
            return world.isChunkLoaded((c[0] - half) >> 4, (c[2] - half) >> 4) && world.isChunkLoaded((c[0] + half) >> 4, (c[2] + half) >> 4);
        }

        void tick(World world, long now) {
            PlatformMath.Track track = spec.track();
            if (!loaded(world, centre(0)) || !loaded(world, centre(track.steps()))) {
                return;
            }
            if (current < 0) {
                sweep(world); // blocks a crash left in the air
            }
            int target = PlatformMath.index(now, track.steps(), StormPlan.PLATFORM_STEPS_PER_SECOND, StormPlan.PLATFORM_DWELL_TICKS);
            if (target != current) {
                moveTo(world, target);
            }
        }

        private void sweep(World world) {
            for (int i = 0; i <= spec.track().steps(); i++) {
                int[] c = centre(i);
                if (loaded(world, c)) {
                    forEachCell(c, (x, y, z) -> {
                        Block block = world.getBlockAt(x, StormPlan.ORIGIN_Y + y, z);
                        if (block.getType() == PLATFORM_TOP || block.getType() == PLATFORM_UNDER) {
                            block.setType(Material.AIR, false);
                        }
                    });
                }
            }
        }

        /** Takes the platform down (it is placed again by the next tick). */
        void remove(World world) {
            if (current >= 0) {
                int[] c = centre(current);
                if (loaded(world, c)) {
                    forEachCell(c, (x, y, z) -> world.getBlockAt(x, StormPlan.ORIGIN_Y + y, z).setType(Material.AIR, false));
                }
            }
            current = -1;
        }

        private void moveTo(World world, int target) {
            int[] to = centre(target);
            int[] from = current < 0 ? null : centre(current);
            if (!free(world, to, from)) {
                return; // something is in the way: wait, the next tick tries again
            }
            List<Player> riders = from == null ? List.of() : ridersOn(world, from);
            if (from != null) {
                forEachCell(from, (x, y, z) -> world.getBlockAt(x, StormPlan.ORIGIN_Y + y, z).setType(Material.AIR, false));
            }
            forEachCell(to, (x, y, z) -> world.getBlockAt(x, StormPlan.ORIGIN_Y + y, z)
                .setType(y == to[1] ? PLATFORM_TOP : PLATFORM_UNDER, false));
            current = target;
            if (from != null) {
                for (Player rider : riders) {
                    rider.teleport(rider.getLocation().add(to[0] - from[0], to[1] - from[1], to[2] - from[2]),
                        PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }
        }

        /** True when every cell of the new footprint is air, or part of the old one. */
        private boolean free(World world, int[] to, int[] from) {
            boolean[] ok = {true};
            forEachCell(to, (x, y, z) -> {
                Material type = world.getBlockAt(x, StormPlan.ORIGIN_Y + y, z).getType();
                if (!type.isAir() && !(from != null && inFootprint(from, x, y, z))) {
                    ok[0] = false;
                }
            });
            return ok[0];
        }

        boolean inFootprint(int[] c, int x, int y, int z) {
            int h = StormPlan.PLATFORM_HALF;
            return Math.abs(x - c[0]) <= h && Math.abs(z - c[2]) <= h && (y == c[1] || y == c[1] - 1);
        }

        boolean covers(int x, int y, int z) {
            return current >= 0 && inFootprint(centre(current), x, y, z);
        }

        private List<Player> ridersOn(World world, int[] c) {
            List<Player> list = new ArrayList<>();
            double top = StormPlan.ORIGIN_Y + c[1];
            for (Player p : world.getPlayers()) {
                Location at = p.getLocation();
                if (at.getY() >= top + 0.9 && at.getY() <= top + 3
                        && PlatformMath.onFootprint(c[0], c[2], StormPlan.PLATFORM_HALF, at.getX(), at.getZ())) {
                    list.add(p);
                }
            }
            return list;
        }

        private void forEachCell(int[] c, Cell cell) {
            int h = StormPlan.PLATFORM_HALF;
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    cell.accept(c[0] + dx, c[1], c[2] + dz);
                    cell.accept(c[0] + dx, c[1] - 1, c[2] + dz);
                }
            }
        }
    }

    @FunctionalInterface
    private interface Cell {
        void accept(int x, int y, int z);
    }

    // ---------------------------------------------------------------- propulsion pads

    private void padTick() {
        World world = world();
        if (world == null || !ready) {
            return;
        }
        long now = world.getFullTime();
        Set<UUID> on = new HashSet<>();
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = player.getLocation();
            StormPlan.Pad pad = padAt.get(BlockPlan.key(at.getBlockX(), at.getBlockY() - 1 - StormPlan.ORIGIN_Y, at.getBlockZ()));
            UUID id = player.getUniqueId();
            if (pad == null || player.isSneaking() || padReadyAt.getOrDefault(id, 0L) > now) {
                continue;
            }
            on.add(id);
            Long since = charging.get(id);
            if (since == null) {
                charging.put(id, now);
                player.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
                player.sendActionBar(Component.text("Wind pad charging... sneak to stay put", NamedTextColor.AQUA));
            } else if (now - since >= PAD_CHARGE_TICKS) {
                launch(player, pad);
                charging.remove(id);
                on.remove(id);
                padReadyAt.put(id, now + PAD_COOLDOWN_TICKS);
            } else {
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.2, 0), 6, 0.4, 0.1, 0.4, 0.05);
            }
        }
        charging.keySet().retainAll(on);
    }

    private void launch(Player player, StormPlan.Pad pad) {
        PadMath.Launch launch = pad.launch();
        if (launch == null) {
            return;
        }
        Location at = player.getLocation();
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 20, 0.4, 0.1, 0.4, 0.1);
        player.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.8f);
        player.setFallDistance(0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, PAD_SLOW_FALL_TICKS, 0, true, false, false));
        player.setVelocity(new Vector(launch.vx(), launch.vy(), launch.vz()));
    }

    // ---------------------------------------------------------------- wind, lightning, islands

    private void gustTick() {
        World world = world();
        if (world == null || !ready) {
            return;
        }
        long now = world.getFullTime();
        StormMath.Gust gust = StormMath.gust(now);
        if (gust == StormMath.Gust.CALM) {
            return;
        }
        double[] dir = StormMath.gustDirection(now);
        long cycle = Math.floorDiv(now, StormMath.GUST_CYCLE_TICKS);
        boolean announce = gust == StormMath.Gust.WARNING && lastWarned != cycle;
        if (announce) {
            lastWarned = cycle;
        }
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (gust == StormMath.Gust.WARNING) {
                if (announce) {
                    player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.4f);
                    player.sendActionBar(Component.text("A gust is coming. Sneak to brace.", NamedTextColor.AQUA));
                }
                continue;
            }
            Location at = player.getLocation();
            if (player.isSneaking() || StormPlan.LANDING.contains(at.getX(), at.getZ(), 2)) {
                continue; // bracing, or on the Landing which is sheltered
            }
            Vector v = player.getVelocity();
            double[] pushed = StormMath.pushed(v.getX(), v.getZ(), dir);
            player.setVelocity(new Vector(pushed[0], v.getY(), pushed[1]));
        }
    }

    private void lightningTick() {
        World world = world();
        if (world == null || !ready) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (Math.random() > 0.2) {
                continue;
            }
            double angle = Math.random() * Math.PI * 2;
            double dist = 18 + Math.random() * 22;
            Location at = player.getLocation();
            world.strikeLightningEffect(new Location(world, at.getX() + Math.cos(angle) * dist, at.getY() - 6,
                at.getZ() + Math.sin(angle) * dist));
        }
    }

    /** Remembers the last island each player stood on (where a fall brings them back) and announces new ones. */
    private void islandTick() {
        World world = world();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            StormPlan.Islet islet = StormPlan.isletUnder(at.getX(), at.getY() - StormPlan.ORIGIN_Y, at.getZ());
            if (islet == null) {
                continue;
            }
            UUID id = player.getUniqueId();
            lastIsland.put(id, islet.id());
            String lore = LORE.get(islet.id());
            if (lore != null && announced.add(id + ":" + islet.id())) {
                player.showTitle(Title.title(Component.text(islet.name(), NamedTextColor.AQUA),
                    Component.text("Tempestuous Archipelago", NamedTextColor.GRAY)));
                player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.9f);
                core.msg().info(player, lore);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getBlockY() == event.getFrom().getBlockY() || !inRealm(to.getWorld())
                || to.getY() >= StormPlan.ORIGIN_Y + StormPlan.VOID_Y) {
            return;
        }
        Player player = event.getPlayer();
        String id = lastIsland.getOrDefault(player.getUniqueId(), StormPlan.LANDING.id());
        Location back = rescue(StormPlan.at(id));
        if (back == null) {
            return;
        }
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setHealth(Math.max(2.0, player.getHealth() - 4.0)); // a fall hurts but never kills
        core.msg().warn(player, "The wind carries you back to " + StormPlan.at(id).name() + ".");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        charging.remove(id);
        padReadyAt.remove(id);
        lastIsland.remove(id);
        announced.removeIf(key -> key.startsWith(id + ":"));
    }

    // ---------------------------------------------------------------- harvesting and protection

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!inRealm(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        int ry = block.getY() - StormPlan.ORIGIN_Y;
        long k = BlockPlan.key(block.getX(), ry, block.getZ());
        if (nodeAt.containsKey(k) && block.getType() == Material.CHISELED_QUARTZ_BLOCK) {
            event.setCancelled(true);
            if (!player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")) {
                core.msg().warn(player, "You need a pickaxe to cut Thunder Quartz out of the stone.");
                return;
            }
            harvest(player, block, k);
            return;
        }
        if (protectedSpot(block.getX(), ry, block.getZ()) && !player.hasPermission(IslandService.BYPASS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (inRealm(block.getWorld()) && protectedSpot(block.getX(), block.getY() - StormPlan.ORIGIN_Y, block.getZ())
                && !event.getPlayer().hasPermission(IslandService.BYPASS)) {
            event.setCancelled(true);
        }
    }

    /** The Landing, the pads, the platforms and the seal of the Sarcophagus cannot be built on or broken. */
    boolean protectedSpot(int x, int y, int z) {
        if (y >= -12 && Math.hypot(x, z) <= StormPlan.LANDING_RADIUS + 1) {
            return true;
        }
        if (padAt.containsKey(BlockPlan.key(x, y, z))) {
            return true;
        }
        for (Mover mover : movers) {
            if (mover.covers(x, y, z)) {
                return true;
            }
        }
        return y >= StormPlan.SEAL[1] - 1 && y <= StormPlan.SEAL[1] + 6 && Math.abs(x - StormPlan.SEAL[0]) <= 3
            && Math.abs(z - StormPlan.SEAL[2]) <= 1;
    }

    private void harvest(Player player, Block block, long k) {
        if (!pendingNodes.add(k)) {
            return;
        }
        block.setType(Material.CALCITE, false);
        int amount = 1 + (Math.random() < 0.4 ? 1 : 0);
        ItemStack drop = core.items().create(THUNDER_QUARTZ, amount);
        player.getInventory().addItem(drop).values()
            .forEach(left -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), left));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.6f);
        block.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
        core.scheduler().later(NODE_RESPAWN_TICKS, () -> {
            if (pendingNodes.remove(k) && block.getType() == Material.CALCITE) {
                block.setType(Material.CHISELED_QUARTZ_BLOCK, false);
            }
        });
    }

    // ---------------------------------------------------------------- the seal

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || !inRealm(block.getWorld())) {
            return;
        }
        if (block.getX() != StormPlan.SEAL[0] || block.getY() - StormPlan.ORIGIN_Y != StormPlan.SEAL[1]
                || block.getZ() != StormPlan.SEAL[2] + 1) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        BastionApi api = core.service(BastionApi.class).orElse(null);
        if (api == null) {
            core.msg().warn(player, "The seal does not answer: Bastions are not available.");
            return;
        }
        String refusal = api.enter(player, BASTION);
        if (refusal != null) {
            core.msg().warn(player, refusal);
        }
    }

    // ---------------------------------------------------------------- /storm

    private void registerCommand() {
        core.commands().command("storm").description("The Tempestuous Archipelago")
            .usage("/storm [info | go | build]")
            .permission(USE, PermissionDefault.TRUE)
            .tab((CommandSender sender, Command command, String alias, String[] args) ->
                args.length == 1 ? List.of("info", "go", "build") : List.<String>of())
            .executor(this::run).register();
    }

    private boolean run(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> {
                core.msg().info(sender, "Tempestuous Archipelago (Gate 6): moving platforms and wind pads join seven islands.");
                core.msg().info(sender, "Cut Thunder Quartz from the chiseled quartz nodes; it fuels the Lightning Rod and opens the way to the Thunder Dragon.");
                core.msg().info(sender, "Gusts blow every 30 seconds: sneak to brace. A fall brings you back to your last island.");
            }
            case "go" -> {
                if (!(sender instanceof Player player)) {
                    core.msg().error(sender, "Only players can travel.");
                } else if (player.hasPermission(ADMIN) && args.length > 1 && args[1].equalsIgnoreCase("force")) {
                    Location spawn = ready ? spawn() : null;
                    if (spawn == null) {
                        core.msg().error(player, "The Archipelago is not built yet.");
                    } else {
                        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.COMMAND);
                    }
                } else {
                    boolean lit = core.service(SkyGateApi.class).map(g -> g.isLit(player.getUniqueId(), Realm.ARCHIPELAGO)).orElse(false);
                    if (!lit) {
                        core.msg().warn(player, "Gate 6 is dark. Defeat the Mycelial Queen to open the Tempestuous Archipelago.");
                    } else {
                        core.service(SkyGateApi.class).ifPresent(g -> g.travel(player, Realm.ARCHIPELAGO));
                    }
                }
            }
            case "build" -> {
                if (!sender.hasPermission(ADMIN)) {
                    core.msg().error(sender, "You cannot do that.");
                } else if (rebuild()) {
                    core.msg().ok(sender, "Building the Tempestuous Archipelago. This takes a minute.");
                } else {
                    core.msg().error(sender, "The Archipelago world is missing.");
                }
            }
            default -> core.msg().error(sender, "Usage: /storm [info | go | build]");
        }
        return true;
    }
}
