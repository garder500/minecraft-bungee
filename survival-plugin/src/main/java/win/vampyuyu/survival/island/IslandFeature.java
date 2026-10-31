package win.vampyuyu.survival.island;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.api.IslandApi;

/**
 * The {@code island} feature: the two void worlds and the island grid with protection, co-op, upgrades and
 * levels, the {@code /island}, {@code /hub} and {@code /islandadmin} commands. The APIs are published last.
 */
public final class IslandFeature implements Feature {
    private IslandService service;
    private IslandTracker tracker;
    private IslandMenus menus;
    private Scheduler.Handle levelTask;

    @Override
    public String id() {
        return "island";
    }

    @Override
    public void enable(Core core) throws Exception {
        IslandConfig cfg = new IslandConfig(core.featureConfig("island"), core.getPlugin().getLogger());
        setupWorlds(core, cfg);

        service = new IslandService(core, cfg);
        tracker = new IslandTracker(service);
        service.tracker(tracker);
        menus = new IslandMenus(service);

        core.permissions().register(IslandService.BYPASS, "Ignore island protection and borders", PermissionDefault.OP);
        core.listen(tracker);
        core.listen(new IslandProtection(service));
        core.listen(new IslandPortals(service));
        new IslandCommands(service, menus).register();

        service.start();
        service.recover();
        if (cfg.levelAutoMinutes > 0) {
            long period = cfg.levelAutoMinutes * 60L * 20L;
            levelTask = core.scheduler().repeating(period, period, service::autoLevelOnline);
        }

        // publish last: other features must never see a half-built island service
        core.register(IslandApi.class, service);
        core.register(IslandAltarApi.class, service);
        core.register(IslandUpgradesApi.class, service);
    }

    private static void setupWorlds(Core core, IslandConfig cfg) {
        WorldSetup setup = new WorldSetup(core.getPlugin().getLogger());
        int islandSpawnY = cfg.islandY + 1;
        prepare(setup, cfg, "world", SkyblockWorlds.WORLD, World.Environment.NORMAL, Biome.PLAINS, islandSpawnY, 0, 0);
        prepare(setup, cfg, "nether", SkyblockWorlds.NETHER, World.Environment.NETHER, Biome.NETHER_WASTES,
            islandSpawnY, 0, 0);
        prepare(setup, cfg, "end", SkyblockWorlds.END, World.Environment.THE_END, Biome.THE_END, islandSpawnY, 0, 0);
    }

    private static void prepare(WorldSetup setup, IslandConfig cfg, String key, String name, World.Environment env,
            Biome biome, int spawnY, int spawnX, int spawnZ) {
        World world = setup.ensure(name, env, biome, spawnY);
        if (world != null) {
            setup.configure(world, cfg.worlds.get(key), spawnX, spawnY, spawnZ);
        }
    }

    @Override
    public void disable() {
        if (levelTask != null) {
            levelTask.cancel();
            levelTask = null;
        }
        if (tracker != null) {
            tracker.shutdown();
        }
        if (service != null) {
            service.close();
        }
    }
}
