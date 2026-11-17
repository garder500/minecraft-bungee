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
    private static final String HUB_LANE = "hub";
    /** Square wiped on a plan change: x -45..45, z -49..41 (the old plans reached 44 in x and 44 in z). */
    static final int WIPE_CENTER_X = 0;
    static final int WIPE_CENTER_Z = -4;
    static final int WIPE_RADIUS = 45;

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

    /**
     * Starts the hub build (or just marks it ready). Safe to call once at enable. With {@code hub.enabled: false} only
     * the small spawn pad is built: islandless players, evictions and the void rescue still need somewhere to land.
     */
    public void start() {
        World hub = org.bukkit.Bukkit.getWorld(SkyblockWorlds.HUB);
        if (hub == null) {
            return;
        }
        if (!cfg.hubEnabled) {
            if (padPresent(hub)) {
                service.hubState(true, false);
            } else {
                service.hubState(false, false);
                buildPad(hub, 0, false);
            }
            return;
        }
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        if (upToDate(marker, cfg.hubY) && padPresent(hub)) {
            service.hubState(true, true);
            return;
        }
        service.hubState(false, false);
        if (needsWipe(marker, cfg.hubY, padPresent(hub))) {
            wipeOldHub(hub);
        } else {
            buildPad(hub, 0, true);
        }
    }

    /** True when something was built under an older (or unfinished) plan: its blocks would show through the new fragment. */
    static boolean needsWipe(Marker marker, int hubY, boolean padPresent) {
        return padPresent && !upToDate(marker, hubY);
    }

    /**
     * Clears the square of the previous hub before the new plan goes in. The square stops at z = 41: the Quai bridge
     * starts at z = 43 and must survive (the new plan clears the scraps of the old ring road beyond it itself).
     */
    private void wipeOldHub(World hub) {
        service.logger().info("The hub layout changed (plan " + HubPlan.VERSION + "): clearing the old hub before the rebuild");
        service.runner().submit(HUB_LANE, new WipeJob(core.getPlugin(), "hub (old plan)", hub, WIPE_CENTER_X, WIPE_CENTER_Z,
            WIPE_RADIUS, cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The old hub could not be cleared completely; building the new one over it");
                }
                buildPad(hub, 0, true);
            });
    }

    private void buildPad(World hub, int attempt, boolean thenFull) {
        service.runner().submit(HUB_LANE, new PlanJob(core.getPlugin(), "hub spawn pad", hub, HubPlan.emergencySpawn(), 0,
            cfg.hubY, 0, cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    if (attempt < cfg.buildRetries) {
                        service.logger().warning("The hub spawn pad could not be built (attempt " + (attempt + 1)
                            + "), retrying");
                        core.scheduler().later(200L * (attempt + 1), () -> buildPad(hub, attempt + 1, thenFull));
                    } else {
                        service.logger().warning("The hub spawn pad could not be built; the hub stays unavailable");
                    }
                    return;
                }
                service.hubState(true, false);
                if (thenFull) {
                    buildAll(hub);
                }
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
        service.runner().submit(HUB_LANE, new PlanJob(core.getPlugin(), "hub", hub, plan, 0, cfg.hubY, 0,
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
