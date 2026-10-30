package win.vampyuyu.survival.island;

import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.World;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Builds the hub once. A marker file ({@code island/hub.json}) remembers which plan version was completed, so a
 * restart does not rebuild it (and still marks the hub as ready, otherwise {@code teleportHub} would stay false).
 * The spawn pad is built first so the hub is usable within a few ticks, then the full plan follows in batches.
 */
public final class HubManager {
    public static final String MARKER_FILE = "island/hub.json";

    /** Persisted marker. */
    public static final class Marker {
        public int version;
        public boolean complete;
        public int hubY;
    }

    private final Core core;
    private final IslandService service;
    private final IslandConfig cfg;

    public HubManager(Core core, IslandService service) {
        this.core = core;
        this.service = service;
        this.cfg = service.config();
    }

    /** True when the marker says the current plan was fully built at the configured height. */
    static boolean upToDate(Marker marker, int hubY) {
        return marker != null && marker.complete && marker.version == HubPlan.VERSION && marker.hubY == hubY;
    }

    /** Starts the hub build (or just marks it ready). Safe to call once at enable. */
    public void start() {
        World hub = org.bukkit.Bukkit.getWorld(SkyblockWorlds.HUB);
        if (!cfg.hubEnabled || hub == null) {
            return;
        }
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        if (upToDate(marker, cfg.hubY) && padPresent(hub)) {
            service.hubState(true, true);
            return;
        }
        service.hubState(false, false);
        service.runner().submit(new PlanJob(core.getPlugin(), "hub spawn pad", hub, HubPlan.emergencySpawn(), 0,
            cfg.hubY, 0, cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The hub spawn pad could not be built; the hub stays unavailable");
                    return;
                }
                service.hubState(true, false);
                buildAll(hub);
            });
    }

    private boolean padPresent(World hub) {
        try {
            return hub.getBlockAt(HubPlan.SPAWN_X, cfg.hubY, HubPlan.SPAWN_Z).getType() != Material.AIR;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void buildAll(World hub) {
        BlockPlan plan = HubPlan.build(cfg.hubMaxBlocks);
        service.runner().submit(new PlanJob(core.getPlugin(), "hub", hub, plan, 0, cfg.hubY, 0,
            cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The hub could not be completed (the spawn pad still works)");
                    return;
                }
                service.hubState(true, true);
                Marker marker = new Marker();
                marker.version = HubPlan.VERSION;
                marker.complete = true;
                marker.hubY = cfg.hubY;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        service.logger().log(Level.WARNING, "Could not save the hub marker", e);
                    }
                });
            });
    }
}
