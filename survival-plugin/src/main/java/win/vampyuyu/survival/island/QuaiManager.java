package win.vampyuyu.survival.island;

import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Builds the Quai des Huit once, in the hub world. Same idea as {@link HubManager}: a marker file
 * ({@code island/quai.json}) remembers the plan version that was completed, so a restart does not rebuild it. Does
 * nothing while the hub world is missing or the full hub is disabled.
 */
public final class QuaiManager {
    public static final String MARKER_FILE = "island/quai.json";
    private static final String HUB_LANE = "hub";

    /** Persisted marker. */
    public static final class Marker {
        public int version;
        public boolean complete;
        public int hubY;
    }

    private final Core core;
    private final IslandService service;
    private final IslandConfig cfg;
    private volatile boolean ready;

    public QuaiManager(Core core, IslandService service) {
        this.core = core;
        this.service = service;
        this.cfg = service.config();
    }

    static boolean upToDate(Marker marker, int hubY) {
        return marker != null && marker.complete && marker.version == QuaiPlan.VERSION && marker.hubY == hubY;
    }

    /** True once the platform stands (built now or by an earlier run). */
    public boolean ready() {
        return ready;
    }

    public World hub() {
        return Bukkit.getWorld(SkyblockWorlds.HUB);
    }

    /** Centre of the platform surface (y of the surface, not of the player's feet). */
    public Location center() {
        World hub = hub();
        return hub == null ? null : new Location(hub, 0.5, cfg.hubY, QuaiPlan.CENTER_Z + 0.5);
    }

    /** Where players arrive: the platform end of the bridge, looking at the central light. */
    public Location spawn() {
        World hub = hub();
        if (hub == null) {
            return null;
        }
        int[] rel = QuaiPlan.spawn();
        return new Location(hub, rel[0] + 0.5, cfg.hubY + rel[1], QuaiPlan.CENTER_Z + rel[2] + 0.5, 0f, 5f);
    }

    /** Builds the Quai unless the marker says it is already there. */
    public void start() {
        World hub = hub();
        if (hub == null || !cfg.hubEnabled) {
            return;
        }
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        if (upToDate(marker, cfg.hubY) && present(hub)) {
            ready = true;
            return;
        }
        rebuild();
    }

    /** Builds the Quai again over the old one (admin command, layout change). */
    public void rebuild() {
        World hub = hub();
        if (hub == null) {
            return;
        }
        ready = false;
        BlockPlan plan = QuaiPlan.build(cfg.hubMaxBlocks);
        service.runner().submit(HUB_LANE, new PlanJob(core.getPlugin(), "quai", hub, plan, 0, cfg.hubY, QuaiPlan.CENTER_Z,
            cfg.chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    service.logger().warning("The Quai des Huit could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = QuaiPlan.VERSION;
                marker.complete = true;
                marker.hubY = cfg.hubY;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        service.logger().log(Level.WARNING, "Could not save the Quai marker", e);
                    }
                });
            });
    }

    private boolean present(World hub) {
        try {
            return hub.getBlockAt(0, cfg.hubY, QuaiPlan.CENTER_Z).getType() != Material.AIR;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
