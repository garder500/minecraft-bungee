package win.vampyuyu.survival.wings;

import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.WingsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The Wings: a soulbound, non craftable chestplate-slot item that lets its owner fly in the Skyblock worlds, fuelled
 * by Flight Energy and upgraded with Wing Cores. See docs/skyblock-notes/wings.md.
 */
public final class WingsFeature implements Feature {
    private WingsItems items;
    private FlightManager flight;

    @Override
    public String id() {
        return "wings";
    }

    @Override
    public void enable(Core core) {
        WingsSettings settings = WingsSettings.from(core.featureConfig("wings"));
        items = new WingsItems(core.items());
        items.register(settings);
        try {
            WingsService service = new WingsService(core, items, settings);
            flight = new FlightManager(core, service, settings);
            service.flight(flight);
            WingsGui gui = new WingsGui(core, service, flight);
            WingsGuard guard = new WingsGuard(service);

            core.permissions().register(WingsCommand.ADMIN, "Administer the Wings", PermissionDefault.OP);
            Runnable reload = () -> {
                WingsSettings fresh = WingsSettings.from(core.featureConfig("wings"));
                items.register(fresh);
                service.settings(fresh);
                flight.settings(fresh);
                service.rebuildAll();
            };
            WingsCommand command = new WingsCommand(core, service, flight, gui, reload);
            core.commands().command("wings")
                .description("Your Wings: upgrade, recover, settings")
                .usage("/wings [fly|toggle|claim|info|help]")
                .permission("vampyuyu.sb.wings.use", PermissionDefault.TRUE)
                .executor(command)
                .tab(command)
                .register();

            core.listen(flight);
            core.listen(guard);
            flight.start();
            core.register(WingsApi.class, service);  // last: other features may use it from now on
        } catch (RuntimeException e) {
            disable();
            throw e;
        }
    }

    @Override
    public void disable() {
        if (flight != null) {
            flight.shutdown();
            flight = null;
        }
        if (items != null) {
            items.unregister();
            items = null;
        }
    }
}
