package win.vampyuyu.survival.island;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.ResonanceApi;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.data.Settings;

/**
 * The Ethereal Necropolis (Realm 8, GDD 3.1). Builds the zone once in {@code sb_necropolis} (marker file
 * {@code island/necropolis-build.json} keeps the plan version), registers it as the destination of the Sky Gate, and runs
 * its mechanics: the fading of the memories (Darkness pulses, no damage) that the Lucidity affinity removes, affinity
 * points from laying the undead to rest, the Recall affinity that pays a Memory Shard every few kills, and a rescue
 * from the void. The Gate lights for the players who defeat the boss named by the setting
 * {@code island.necropolis.gate-boss} (the Abyssal Leviathan by default).
 */
public final class NecropolisRealm implements Listener {
    public static final String MARKER_FILE = "island/necropolis-build.json";
    public static final int SURFACE_Y = 100;
    private static final String LANE = "hub";
    private static final int HAZE_TICKS = 100;
    private static final int DEFAULT_RECALL_KILLS = 10;
    private static final Set<EntityType> UNDEAD = EnumSet.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK,
        EntityType.DROWNED, EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON,
        EntityType.ZOMBIFIED_PIGLIN, EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE);
    private static final String DEFAULT_GATE_BOSS = "abyssal_leviathan";

    /** Persisted build marker. */
    public static final class Marker {
        public int version;
        public boolean complete;
    }

    private final Core core;
    private final IslandService service;
    private final IslandConfig cfg;
    private final SkyGateApi gates;
    private NecropolisAffinity affinity = new NecropolisAffinity();
    private volatile boolean ready;
    private Scheduler.Handle task;
    private int tick;
    private final Map<UUID, Integer> recall = new HashMap<>();

    public NecropolisRealm(Core core, IslandService service, SkyGateApi gates) {
        this.core = core;
        this.service = service;
        this.cfg = service.config();
        this.gates = gates;
    }

    public boolean ready() {
        return ready;
    }

    public NecropolisAffinity affinity() {
        return affinity;
    }

    public World world() {
        return Bukkit.getWorld(SkyblockWorlds.NECROPOLIS);
    }

    /** Where the Gate lands the player: the platform at the south end of the main island. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        int[] s = NecropolisPlan.spawn();
        return new Location(world, s[0] + 0.5, SURFACE_Y + s[1], s[2] + 0.5, 180f, 5f);
    }

    static boolean upToDate(Marker marker) {
        return marker != null && marker.complete && marker.version == NecropolisPlan.VERSION;
    }

    public void start() {
        affinity = NecropolisAffinity.fromDoc(core.store().read(NecropolisAffinity.FILE, NecropolisAffinity.Doc.class, NecropolisAffinity.Doc::new));
        gates.setDestination(Realm.NECROPOLIS, player -> ready ? spawn() : null);
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
        gates.setDestination(Realm.NECROPOLIS, null);
        save();
    }

    /** Builds the Necropolis again over the old one (plan change, admin). */
    public void rebuild() {
        World world = world();
        if (world == null) {
            return;
        }
        ready = false;
        BlockPlan plan = NecropolisPlan.build(cfg.hubMaxBlocks);
        service.runner().submit(LANE, new PlanJob(core.getPlugin(), "necropolis", world, plan, 0, SURFACE_Y, 0,
            cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The Ethereal Necropolis could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = NecropolisPlan.VERSION;
                marker.complete = true;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        service.logger().log(Level.WARNING, "Could not save the Necropolis marker", e);
                    }
                });
            });
    }

    private void save() {
        NecropolisAffinity.Doc doc = affinity.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(NecropolisAffinity.FILE, doc);
            } catch (IOException e) {
                service.logger().log(Level.WARNING, "Could not save the Necropolis affinity", e);
            }
        });
    }

    static boolean inNecropolis(World world) {
        return world != null && SkyblockWorlds.NECROPOLIS.equals(world.getName());
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
                    core.msg().info(player, "The memories gathered you up and carried you back to the landing.");
                }
                continue;
            }
            world.spawnParticle(Particle.SOUL, at.clone().add(0, 1.5, 0), 4, 4, 2, 4, 0.01);
            if (tick % 2 == 0) {
                world.spawnParticle(Particle.ASH, at.clone().add(0, 1.2, 0), 8, 5, 2, 5, 0);
            }
            if (affinity.tier(player.getUniqueId()) < 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, HAZE_TICKS, 0, true, false, false));
                if (tick % 30 == 0) {
                    core.msg().info(player, "The memories here fade your sight. Lay the restless dead to rest to earn Lucidity.");
                }
            }
        }
    }

    @EventHandler
    public void onArrive(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (inNecropolis(player.getWorld())) {
            event(player, "necropolis_enter");
        }
    }

    static boolean undead(EntityType type) {
        return UNDEAD.contains(type);
    }

    /** Kills needed for a Recall Shard, from the setting {@code island.necropolis.recall-kills} (at least 1). */
    int recallKills() {
        return Math.max(1, Settings.load(core.data(), "island").getInt("necropolis.recall-kills", DEFAULT_RECALL_KILLS));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onUndeadKill(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null || !inNecropolis(event.getEntity().getWorld()) || !undead(event.getEntityType())) {
            return;
        }
        UUID id = player.getUniqueId();
        int tier = affinity.add(id, 1);
        if (tier > 0) {
            core.msg().ok(player, "Necropolis affinity: " + NecropolisAffinity.NAMES[tier] + ".");
            for (int t = 1; t <= tier; t++) {
                event(player, "necropolis_affinity:" + t);
            }
        }
        if (affinity.tier(id) >= 2 && recall.merge(id, 1, Integer::sum) >= recallKills()) {
            recall.put(id, 0);
            core.service(ResonanceApi.class).ifPresent(r -> {
                r.grantShards(id, 1);
                core.msg().info(player, "A memory surfaces: +1 Memory Shard.");
            });
        }
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        Settings settings = Settings.load(core.data(), "island");
        if (!settings.getString("necropolis.gate-boss", DEFAULT_GATE_BOSS).equalsIgnoreCase(event.getBossId())) {
            return;
        }
        Set<UUID> who = new HashSet<>();
        if (event.getKiller() != null) {
            who.add(event.getKiller().getUniqueId());
        }
        event.getTopDamagers().forEach(d -> who.add(d.player()));
        who.forEach(id -> gates.light(id, Realm.NECROPOLIS));
    }
}
