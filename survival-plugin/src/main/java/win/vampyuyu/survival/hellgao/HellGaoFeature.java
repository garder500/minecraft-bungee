package win.vampyuyu.survival.hellgao;

import win.vampyuyu.survival.api.HellGaoApi;
import win.vampyuyu.survival.bastion.BastionService;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * Hell Gao, the Anomaly (GDD 19.4, issue #53): the secret post-game superboss, an instanced Bastion of 2 to 5 players that
 * opens only for Paragons, with an Interpulse Key, between two pulses. Must be listed after the {@code bastion} and
 * {@code boss} features. The Paragon rank (issue #52) and the Celestial Breath events (issue #51) plug in through
 * {@link win.vampyuyu.survival.api.ParagonApi} (listed before it) and {@link HellGaoApi}. See docs/skyblock-notes/hellgao.md.
 */
public final class HellGaoFeature implements Feature {
    private HellGaoService service;

    @Override
    public String id() {
        return "hellgao";
    }

    @Override
    public void enable(Core core) {
        HellGaoConfig cfg = HellGaoConfig.load(core.data());
        cfg.problems().forEach(p -> core.getPlugin().getLogger().warning("hellgao data: " + p));
        service = new HellGaoService(core, cfg, System::currentTimeMillis);
        core.register(HellGaoService.class, service);
        core.register(HellGaoApi.class, service);
        core.service(BastionService.class).ifPresentOrElse(b -> b.addHook(service),
            () -> core.getPlugin().getLogger().warning("hellgao: the bastion feature is off, Hell Gao cannot open"));
        new HellGaoCommand(core, service).register();
        core.scheduler().repeating(40, 40, service::tick);
        core.scheduler().repeating(20, 10, service::drawAuras);
        core.onDataChange(domain -> {
            if (domain.equals("hellgao")) {
                service.reload(HellGaoConfig.load(core.data()));
            }
        });
    }

    @Override
    public void disable() {
        service = null;
    }

    /** For tests. */
    public HellGaoService service() { return service; }
}
