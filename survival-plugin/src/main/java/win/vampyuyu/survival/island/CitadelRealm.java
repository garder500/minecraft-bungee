package win.vampyuyu.survival.island;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.api.TimeTide;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.data.Settings;

/**
 * The Citadel of Golden Sands (Realm 4, GDD 3.1). Builds the zone once in {@code sb_citadel} (marker file
 * {@code island/citadel-build.json} keeps the plan version), registers it as the destination of the Sky Gate and runs
 * its mechanic: the Time Tide ({@link TimeTide}). In a surge, time runs fast for the players here (Speed and Haste); in a
 * stall it crawls (Slowness). The same tide drives the Chrono Module of the Miners, so the Citadel is where players learn
 * to read it. Dust rises from the four columns, and a fall into the void brings the player back to the landing. The Gate
 * lights for the players who defeat the boss named by the setting {@code island.citadel.gate-boss} (the Mycelial Queen).
 */
public final class CitadelRealm implements Listener {
    public static final String MARKER_FILE = "island/citadel-build.json";
    public static final int SURFACE_Y = 100;
    private static final String LANE = "hub";
    private static final int EFFECT_TICKS = 60;
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
    private volatile boolean ready;
    private Scheduler.Handle task;
    private TimeTide.Phase lastPhase;
    private int tick;

    public CitadelRealm(Core core, IslandService service, SkyGateApi gates) {
        this.core = core;
        this.service = service;
        this.cfg = service.config();
        this.gates = gates;
    }

    public boolean ready() {
        return ready;
    }

    public World world() {
        return Bukkit.getWorld(SkyblockWorlds.CITADEL);
    }

    /** Where the Gate lands the player: the platform at the south end of the main island. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        int[] s = CitadelPlan.spawn();
        return new Location(world, s[0] + 0.5, SURFACE_Y + s[1], s[2] + 0.5, 180f, 5f);
    }

    static boolean upToDate(Marker marker) {
        return marker != null && marker.complete && marker.version == CitadelPlan.VERSION;
    }

    /** The tide as configured by the {@code island.citadel.tide.*} settings. */
    public static TimeTide tide(Settings island) {
        return new TimeTide(island.getInt("citadel.tide.calm-seconds", TimeTide.DEFAULT_CALM),
            island.getInt("citadel.tide.surge-seconds", TimeTide.DEFAULT_SURGE),
            island.getInt("citadel.tide.stall-seconds", TimeTide.DEFAULT_STALL));
    }

    public void start() {
        gates.setDestination(Realm.CITADEL, player -> ready ? spawn() : null);
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
        gates.setDestination(Realm.CITADEL, null);
    }

    /** Builds the Citadel again over the old one (plan change, admin). */
    public void rebuild() {
        World world = world();
        if (world == null) {
            return;
        }
        ready = false;
        BlockPlan plan = CitadelPlan.build(cfg.hubMaxBlocks);
        service.runner().submit(LANE, new PlanJob(core.getPlugin(), "citadel", world, plan, 0, SURFACE_Y, 0,
            cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The Citadel of Golden Sands could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = CitadelPlan.VERSION;
                marker.complete = true;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        service.logger().log(Level.WARNING, "Could not save the Citadel marker", e);
                    }
                });
            });
    }

    static boolean inCitadel(World world) {
        return world != null && SkyblockWorlds.CITADEL.equals(world.getName());
    }

    private static void event(Player player, String key) {
        Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "EVENT", key, 1));
    }

    // ---------------------------------------------------------------- mechanics

    /** The effects of a phase on a player standing in the Citadel; empty for the calm. */
    static PotionEffect[] effects(TimeTide.Phase phase) {
        return switch (phase) {
            case SURGE -> new PotionEffect[] {new PotionEffect(PotionEffectType.SPEED, EFFECT_TICKS, 0, true, false, false),
                new PotionEffect(PotionEffectType.HASTE, EFFECT_TICKS, 0, true, false, false)};
            case STALL -> new PotionEffect[] {new PotionEffect(PotionEffectType.SLOWNESS, EFFECT_TICKS, 0, true, false, false)};
            case CALM -> new PotionEffect[0];
        };
    }

    private void tick() {
        tick++;
        World world = world();
        if (world == null) {
            return;
        }
        TimeTide.Reading reading = tide(Settings.load(core.data(), "island")).at(System.currentTimeMillis());
        TimeTide.Phase phase = reading.phase();
        boolean changed = lastPhase != null && lastPhase != phase;
        lastPhase = phase;
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (at.getY() < SURFACE_Y - 70) {
                Location back = spawn();
                if (back != null) {
                    player.setFallDistance(0f);
                    player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    core.msg().info(player, "A thread of golden dust caught you and carried you back to the landing.");
                }
                continue;
            }
            for (PotionEffect effect : effects(phase)) {
                player.addPotionEffect(effect);
            }
            player.sendActionBar(Component.text("Time Tide: " + phase.title() + " (" + reading.remainingSeconds() + " s)",
                switch (phase) {
                    case SURGE -> NamedTextColor.GOLD;
                    case STALL -> NamedTextColor.DARK_AQUA;
                    case CALM -> NamedTextColor.YELLOW;
                }));
            if (changed) {
                core.msg().info(player, switch (phase) {
                    case SURGE -> "Time surges: you move faster, and Chrono Modules run fast.";
                    case STALL -> "Time stalls: you move slower. Chrono Modules bank the lost cycles.";
                    case CALM -> "The tide is calm.";
                });
                event(player, "citadel_tide:" + phase.name().toLowerCase(java.util.Locale.ROOT));
            }
            world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 1, 5, 2, 5, 0.01);
        }
        if (tick % 2 == 0) {
            for (int[] c : CitadelPlan.dustColumns()) {
                Location top = new Location(world, c[0] + 0.5, SURFACE_Y + 2, c[1] + 0.5);
                world.spawnParticle(phase == TimeTide.Phase.STALL ? Particle.SNOWFLAKE : Particle.WAX_ON, top, 6, 0.3, 4, 0.3, 0.2);
            }
        }
    }

    @EventHandler
    public void onArrive(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (inCitadel(player.getWorld())) {
            event(player, "citadel_enter");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        Settings settings = Settings.load(core.data(), "island");
        if (!settings.getString("citadel.gate-boss", DEFAULT_GATE_BOSS).equalsIgnoreCase(event.getBossId())) {
            return;
        }
        Set<UUID> who = new HashSet<>();
        if (event.getKiller() != null) {
            who.add(event.getKiller().getUniqueId());
        }
        event.getTopDamagers().forEach(d -> who.add(d.player()));
        who.forEach(id -> gates.light(id, Realm.CITADEL));
    }
}
