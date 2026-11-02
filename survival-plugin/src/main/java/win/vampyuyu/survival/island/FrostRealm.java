package win.vampyuyu.survival.island;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.quarry.Sidegrades;

/**
 * The Frozen Heights (Realm 3, GDD 3.1). Builds the zone once in {@code sb_heights} (marker file
 * {@code island/frost-build.json} keeps the plan version), registers it as the destination of the Sky Gate, and runs its
 * mechanics: the cold (freezing damage, removed by the Warmth affinity or by the Frost Starcore set), low gravity (Jump
 * Boost II, capped, falls hurt less), Slow Falling when sneaking in the air with the Stasis affinity, affinity points from
 * cutting ice, and a rescue from the void. The Gate lights for the players who defeat the boss named by the setting
 * {@code island.frost.gate-boss} (the Mycelial Queen by default).
 */
public final class FrostRealm implements Listener {
    public static final String MARKER_FILE = "island/frost-build.json";
    public static final int SURFACE_Y = 100;
    private static final String LANE = "hub";
    private static final int LOW_GRAVITY_TICKS = 60;
    private static final int COLD_STEP_TICKS = 40;
    /** The cold never takes the last hearts: under this health it stops building up. */
    private static final double MIN_HEALTH_FOR_COLD = 6.0;
    private static final String DEFAULT_GATE_BOSS = "sylvan_matriarch";

    /** Persisted build marker. */
    public static final class Marker {
        public int version;
        public boolean complete;
    }

    private final Core core;
    private final IslandService service;
    private final IslandConfig cfg;
    private final SkyGateApi gates;
    private FrostAffinity affinity = new FrostAffinity();
    private volatile boolean ready;
    private Scheduler.Handle task;
    private int tick;

    public FrostRealm(Core core, IslandService service, SkyGateApi gates) {
        this.core = core;
        this.service = service;
        this.cfg = service.config();
        this.gates = gates;
    }

    public boolean ready() {
        return ready;
    }

    public FrostAffinity affinity() {
        return affinity;
    }

    public World world() {
        return Bukkit.getWorld(SkyblockWorlds.HEIGHTS);
    }

    /** Where the Gate lands the player: the platform at the south end of the main island. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        int[] s = FrostPlan.spawn();
        return new Location(world, s[0] + 0.5, SURFACE_Y + s[1], s[2] + 0.5, 180f, 5f);
    }

    static boolean upToDate(Marker marker) {
        return marker != null && marker.complete && marker.version == FrostPlan.VERSION;
    }

    public void start() {
        affinity = FrostAffinity.fromDoc(core.store().read(FrostAffinity.FILE, FrostAffinity.Doc.class, FrostAffinity.Doc::new));
        gates.setDestination(Realm.HEIGHTS, player -> ready ? spawn() : null);
        core.listen(this);
        task = core.scheduler().repeating(20L, 20L, this::tick);
        World world = world();
        if (world == null) {
            return;
        }
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        if (upToDate(marker) && world.getBlockAt(0, SURFACE_Y, 0).getType() != Material.AIR) {
            ready = true;
            return;
        }
        rebuild();
    }

    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        gates.setDestination(Realm.HEIGHTS, null);
        save();
    }

    /** Builds the Frozen Heights again over the old one (plan change, admin). */
    public void rebuild() {
        World world = world();
        if (world == null) {
            return;
        }
        ready = false;
        BlockPlan plan = FrostPlan.build(cfg.hubMaxBlocks);
        service.runner().submit(LANE, new PlanJob(core.getPlugin(), "frost", world, plan, 0, SURFACE_Y, 0,
            cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The Frozen Heights could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = FrostPlan.VERSION;
                marker.complete = true;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        service.logger().log(Level.WARNING, "Could not save the Frozen Heights marker", e);
                    }
                });
            });
    }

    private void save() {
        FrostAffinity.Doc doc = affinity.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(FrostAffinity.FILE, doc);
            } catch (IOException e) {
                service.logger().log(Level.WARNING, "Could not save the Frost affinity", e);
            }
        });
    }

    static boolean inHeights(World world) {
        return world != null && SkyblockWorlds.HEIGHTS.equals(world.getName());
    }

    private static void event(Player player, String key) {
        Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "EVENT", key, 1));
    }

    // ---------------------------------------------------------------- mechanics

    private void tick() {
        tick++;
        World world = world();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (at.getY() < SURFACE_Y - 70) {
                Location back = spawn();
                if (back != null) {
                    player.setFallDistance(0f);
                    player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    core.msg().info(player, "The ice caught you and carried you back to the landing.");
                }
                continue;
            }
            world.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 1.5, 0), 8, 4, 2, 4, 0.01);
            // low gravity (GDD 12.5): Jump Boost II at most, no fall damage (see onFall)
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, LOW_GRAVITY_TICKS, 1, true, false, false));
            int tier = affinity.tier(player.getUniqueId());
            if (tier >= 2 && player.isSneaking() && !player.isOnGround()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, LOW_GRAVITY_TICKS, 0, true, false, false));
            }
            if (coldProtected(player, tier)) {
                player.setFreezeTicks(0);
            } else if (player.getHealth() > MIN_HEALTH_FOR_COLD) {
                player.setFreezeTicks(Math.min(player.getMaxFreezeTicks(), player.getFreezeTicks() + COLD_STEP_TICKS));
                if (tick % 30 == 0) {
                    core.msg().info(player, "The cold bites. Cut ice here to earn Warmth, or wear the Frost Starcore.");
                }
            }
        }
    }

    /** True when the cold cannot touch the player: the Warmth affinity or the full Frost Starcore set. */
    private boolean coldProtected(Player player, int tier) {
        if (tier >= 1) {
            return true;
        }
        List<String> worn = new ArrayList<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            worn.add(core.items().idOf(piece).orElse(null));
        }
        return Sidegrades.FROST.equals(Sidegrades.fullSet(worn));
    }

    @EventHandler
    public void onArrive(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (inHeights(player.getWorld())) {
            event(player, "heights_enter");
        }
    }

    /** Low gravity: falling in the Heights does not hurt (the void rescue is the only danger). */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player
                && inHeights(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIceCut(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!inHeights(block.getWorld()) || !isIce(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        int tier = affinity.add(player.getUniqueId(), 1);
        if (tier > 0) {
            core.msg().ok(player, "Frost affinity: " + FrostAffinity.NAMES[tier] + ".");
            for (int t = 1; t <= tier; t++) {
                event(player, "heights_affinity:" + t);
            }
        }
        save();
    }

    static boolean isIce(Material material) {
        return material == Material.ICE || material == Material.PACKED_ICE || material == Material.BLUE_ICE;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        Settings settings = Settings.load(core.data(), "island");
        if (!settings.getString("frost.gate-boss", DEFAULT_GATE_BOSS).equalsIgnoreCase(event.getBossId())) {
            return;
        }
        Set<UUID> who = new HashSet<>();
        if (event.getKiller() != null) {
            who.add(event.getKiller().getUniqueId());
        }
        event.getTopDamagers().forEach(d -> who.add(d.player()));
        who.forEach(id -> gates.light(id, Realm.HEIGHTS));
    }
}
