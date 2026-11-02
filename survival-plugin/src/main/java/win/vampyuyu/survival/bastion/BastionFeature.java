package win.vampyuyu.survival.bastion;

import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.BastionApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.dungeon.run.RunManager;

/**
 * Bastions (GDD 12.1, 12.3, 14.4, 15.4, 15.5): instanced fortresses of the Kingdom Guardians for parties of 1 to 5,
 * opened with a key. Shards and Resonance ranks are the resonance feature's. Needs the boss feature (listed before it); uses the dungeon
 * feature's parties when it is loaded. See docs/skyblock-notes/bastion.md.
 */
public final class BastionFeature implements Feature {
    private BastionService service;

    @Override
    public String id() {
        return "bastion";
    }

    @Override
    public void enable(Core core) {
        enable(core, RunManager.voidFactory());
    }

    /** Test entry point: a fake world factory keeps the unit tests away from real world creation. */
    public void enable(Core core, RunManager.WorldFactory worlds) {
        BastionConfig cfg = BastionConfig.load(core.data());
        cfg.problems().forEach(p -> core.getPlugin().getLogger().warning("bastion data: " + p));
        core.permissions().register(BastionListener.ADMIN, "Bastion administration", PermissionDefault.OP);
        service = new BastionService(core, cfg, worlds);
        core.listen(new BastionListener(core, service));
        new BastionCommand(core, service).register();
        core.scheduler().repeating(10, 10, service::tick);
        core.register(BastionService.class, service);
        core.register(BastionApi.class, new BastionApiImpl(service));
    }

    @Override
    public void disable() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }

    /** The wired service (tests). */
    public BastionService service() { return service; }
}
