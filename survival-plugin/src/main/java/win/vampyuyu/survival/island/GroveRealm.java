package win.vampyuyu.survival.island;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
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

/**
 * The Whispering Grove (Realm 2, GDD 3.1). Builds the zone once in {@code sb_grove} (marker file
 * {@code island/grove.json} keeps the plan version), registers it as the destination of the Sky Gate, and runs its
 * mechanics: spore haze (a mild hunger, no damage) that the Respiration affinity removes, affinity points from
 * harvesting grown crops, the Growth affinity on bone meal, and a rescue from the void. The Gate lights for the
 * players who defeat the boss named by the setting {@code island.grove.gate-boss} (the Magma Warden by default).
 */
public final class GroveRealm implements Listener {
    public static final String MARKER_FILE = "island/grove-build.json";
    public static final int SURFACE_Y = 100;
    private static final String LANE = "hub";
    private static final int HAZE_TICKS = 100;
    private static final String DEFAULT_GATE_BOSS = "magma_warden";

    /** Persisted build marker. */
    public static final class Marker {
        public int version;
        public boolean complete;
    }

    private final Core core;
    private final IslandService service;
    private final IslandConfig cfg;
    private final SkyGateApi gates;
    private GroveAffinity affinity = new GroveAffinity();
    private volatile boolean ready;
    private Scheduler.Handle task;
    private int tick;

    public GroveRealm(Core core, IslandService service, SkyGateApi gates) {
        this.core = core;
        this.service = service;
        this.cfg = service.config();
        this.gates = gates;
    }

    public boolean ready() {
        return ready;
    }

    public GroveAffinity affinity() {
        return affinity;
    }

    public World world() {
        return Bukkit.getWorld(SkyblockWorlds.GROVE);
    }

    /** Where the Gate lands the player: the platform at the south end of the main island. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        int[] s = GrovePlan.spawn();
        return new Location(world, s[0] + 0.5, SURFACE_Y + s[1], s[2] + 0.5, 180f, 5f);
    }

    static boolean upToDate(Marker marker) {
        return marker != null && marker.complete && marker.version == GrovePlan.VERSION;
    }

    public void start() {
        affinity = GroveAffinity.fromDoc(core.store().read(GroveAffinity.FILE, GroveAffinity.Doc.class, GroveAffinity.Doc::new));
        gates.setDestination(Realm.GROVE, player -> ready ? spawn() : null);
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
        gates.setDestination(Realm.GROVE, null);
        save();
    }

    /** Builds the Grove again over the old one (plan change, admin). */
    public void rebuild() {
        World world = world();
        if (world == null) {
            return;
        }
        ready = false;
        BlockPlan plan = GrovePlan.build(cfg.hubMaxBlocks);
        service.runner().submit(LANE, new PlanJob(core.getPlugin(), "grove", world, plan, 0, SURFACE_Y, 0,
            cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The Whispering Grove could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = GrovePlan.VERSION;
                marker.complete = true;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        service.logger().log(Level.WARNING, "Could not save the Grove marker", e);
                    }
                });
            });
    }

    private void save() {
        GroveAffinity.Doc doc = affinity.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(GroveAffinity.FILE, doc);
            } catch (IOException e) {
                service.logger().log(Level.WARNING, "Could not save the Grove affinity", e);
            }
        });
    }

    static boolean inGrove(World world) {
        return world != null && SkyblockWorlds.GROVE.equals(world.getName());
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
                    core.msg().info(player, "The roots caught you and carried you back to the landing.");
                }
                continue;
            }
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at.clone().add(0, 1.5, 0), 6, 4, 2, 4, 0);
            if (tick % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 1, 5, 2, 5, 0.01);
            }
            if (affinity.tier(player.getUniqueId()) < 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, HAZE_TICKS, 0, true, false, false));
                if (tick % 30 == 0) {
                    core.msg().info(player, "The spore haze makes you hungry. Harvest grown crops here to earn Respiration.");
                }
            }
        }
    }

    @EventHandler
    public void onArrive(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (inGrove(player.getWorld())) {
            event(player, "grove_enter");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!inGrove(block.getWorld()) || !(block.getBlockData() instanceof Ageable crop) || crop.getAge() < crop.getMaximumAge()) {
            return;
        }
        Player player = event.getPlayer();
        int tier = affinity.add(player.getUniqueId(), 1);
        if (tier > 0) {
            core.msg().ok(player, "Grove affinity: " + GroveAffinity.NAMES[tier] + ".");
            for (int t = 1; t <= tier; t++) {
                event(player, "grove_affinity:" + t);
            }
        }
        save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player == null || affinity.tier(player.getUniqueId()) < 2) {
            return;
        }
        Block block = event.getBlock();
        core.scheduler().nextTick(() -> {
            if (block.getBlockData() instanceof Ageable crop && crop.getAge() < crop.getMaximumAge()) {
                crop.setAge(crop.getAge() + 1);
                block.setBlockData(crop, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        Settings settings = Settings.load(core.data(), "island");
        if (!settings.getString("grove.gate-boss", DEFAULT_GATE_BOSS).equalsIgnoreCase(event.getBossId())) {
            return;
        }
        Set<UUID> who = new HashSet<>();
        if (event.getKiller() != null) {
            who.add(event.getKiller().getUniqueId());
        }
        event.getTopDamagers().forEach(d -> who.add(d.player()));
        who.forEach(id -> gates.light(id, Realm.GROVE));
    }
}
