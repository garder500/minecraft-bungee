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
    private SkyGates gates;
    private GroveRealm grove;
    private NecropolisRealm necropolis;
    private FrostRealm frost;
    private CitadelRealm citadel;
    private Scheduler.Handle levelTask;
    private HubNpcs hubNpcs;

    @Override
    public String id() {
        return "island";
    }

    @Override
    public void enable(Core core) throws Exception {
        IslandConfig cfg = IslandRepo.load(core.data(), core.getPlugin().getLogger());
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
        new HubManager(core, service).start();
        hubNpcs = new HubNpcs(core, service);
        hubNpcs.enable();
        // after the hub build is queued: the Quai goes on the same build lane, so it is placed once the hub is
        gates = new SkyGates(core, service);
        gates.start();
        grove = new GroveRealm(core, service, gates);
        grove.start();
        necropolis = new NecropolisRealm(core, service, gates);
        necropolis.start();
        frost = new FrostRealm(core, service, gates);
        frost.start();
        citadel = new CitadelRealm(core, service, gates);
        citadel.start();
        for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            service.notifyLegacy(online);
        }
        if (cfg.levelAutoMinutes > 0) {
            long period = cfg.levelAutoMinutes * 60L * 20L;
            levelTask = core.scheduler().repeating(period, period, service::autoLevelOnline);
        }

        // publish last: other features must never see a half-built island service
        core.register(IslandApi.class, service);
        core.register(IslandAltarApi.class, service);
        core.register(IslandUpgradesApi.class, service);
        core.register(win.vampyuyu.survival.api.SkyGateApi.class, gates);
    }

    private static void setupWorlds(Core core, IslandConfig cfg) {
        WorldSetup setup = new WorldSetup(core.getPlugin().getLogger());
        int islandSpawnY = cfg.islandY + 1;
        prepare(setup, cfg, "hub", SkyblockWorlds.HUB, World.Environment.NORMAL, Biome.PLAINS, cfg.hubY + 1, 0,
            HubPlan.SPAWN_Z);
        prepare(setup, cfg, "world", SkyblockWorlds.WORLD, World.Environment.NORMAL, Biome.PLAINS, islandSpawnY, 0, 0);
        prepare(setup, cfg, "nether", SkyblockWorlds.NETHER, World.Environment.NETHER, Biome.NETHER_WASTES,
            islandSpawnY, 0, 0);
        prepare(setup, cfg, "end", SkyblockWorlds.END, World.Environment.THE_END, Biome.THE_END, islandSpawnY, 0, 0);
        prepare(setup, cfg, "grove", SkyblockWorlds.GROVE, World.Environment.NORMAL, Biome.LUSH_CAVES, GroveRealm.SURFACE_Y + 1, 0,
            GrovePlan.spawn()[2]);
        prepare(setup, cfg, "necropolis", SkyblockWorlds.NECROPOLIS, World.Environment.NORMAL, Biome.DEEP_DARK,
            NecropolisRealm.SURFACE_Y + 1, 0, NecropolisPlan.spawn()[2]);
        prepare(setup, cfg, "frost", SkyblockWorlds.HEIGHTS, World.Environment.NORMAL, Biome.FROZEN_PEAKS, FrostRealm.SURFACE_Y + 1, 0,
            FrostPlan.spawn()[2]);
        prepare(setup, cfg, "citadel", SkyblockWorlds.CITADEL, World.Environment.NORMAL, Biome.DESERT, CitadelRealm.SURFACE_Y + 1, 0,
            CitadelPlan.spawn()[2]);
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
        if (hubNpcs != null) {
            hubNpcs.disable();
            hubNpcs = null;
        }
        if (necropolis != null) {
            necropolis.close();
            necropolis = null;
        }
        if (frost != null) {
            frost.close();
            frost = null;
        }
        if (citadel != null) {
            citadel.close();
            citadel = null;
        }
        if (grove != null) {
            grove.close();
            grove = null;
        }
        if (gates != null) {
            gates.close();
            gates = null;
        }
        if (tracker != null) {
            tracker.shutdown();
        }
        if (service != null) {
            service.close();
        }
    }
}
