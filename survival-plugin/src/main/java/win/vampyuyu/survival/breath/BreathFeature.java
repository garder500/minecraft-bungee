package win.vampyuyu.survival.breath;

import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.BreathApi;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The Celestial Breath (GDD 4.7, 19.2-19.4). Listed after the dungeon, realm, quarry and island features: it hooks
 * the Sky Gates, and the realm and quarry features read its factors lazily.
 */
public final class BreathFeature implements Feature {
    private BreathService service;

    @Override
    public String id() {
        return "breath";
    }

    @Override
    public void enable(Core core) {
        BreathConfig config = BreathConfig.load(core.data());
        config.problems().forEach(p -> core.getPlugin().getLogger().warning("breath data: " + p));
        service = new BreathService(core, config);
        core.listen(new BreathListener(core, service));
        core.permissions().register(BreathCommand.ADMIN, "Celestial Breath administration (start and stop events)", PermissionDefault.OP);
        BreathCommand command = new BreathCommand(core, service);
        core.commands().command("breath").aliases("respiration")
            .description("The Celestial Breath: events, Fractures, Interpulsation Fragments")
            .usage("/breath [status|fracture]")
            .permission("vampyuyu.sb.breath.use", PermissionDefault.TRUE)
            .executor(command).tab(command).register();
        core.service(SkyGateApi.class).ifPresent(g -> g.addTravelGuard((player, realm) -> service.routeGuard(realm)));
        core.register(BreathApi.class, service);
        core.register(BreathService.class, service);
        core.onDataChange(domain -> {
            if (domain.equals("breath") && service != null) {
                BreathConfig fresh = BreathConfig.load(core.data());
                fresh.problems().forEach(p -> core.getPlugin().getLogger().warning("breath data: " + p));
                service.reload(fresh);
            }
        });
        service.start();
    }

    @Override
    public void disable() {
        if (service != null) {
            service.stop();
            service = null;
        }
    }

    /** For tests. */
    public BreathService service() {
        return service;
    }
}
