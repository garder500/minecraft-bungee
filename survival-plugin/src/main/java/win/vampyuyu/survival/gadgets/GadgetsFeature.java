package win.vampyuyu.survival.gadgets;

import java.util.List;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.data.Settings;

/**
 * Six utility items for the sky islands: Skyhook, Cloud Anchor, Ore Compass, Void Net, Rain Totem and Satchel. Numbers are
 * the {@code gadgets.*} settings; the Forge recipes are rows of the quarry seed.
 */
public final class GadgetsFeature implements Feature {
    private Core core;
    private List<String> itemIds = List.of();
    private Gadgets gadgets;
    private SatchelService satchel;
    private Scheduler.Handle netTask;

    @Override
    public String id() {
        return "gadgets";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        GadgetSettings settings = GadgetSettings.from(Settings.load(core.data(), "gadgets"));
        itemIds = GadgetItems.register(core.items(), settings.voidNetCharges());
        gadgets = new Gadgets(core, settings);
        satchel = new SatchelService(core);
        core.listen(new GadgetListener(core, gadgets, satchel));
        netTask = core.scheduler().repeating(10, 10, gadgets::tickNets);
        core.register(Gadgets.class, gadgets);
        core.register(SatchelService.class, satchel);
    }

    @Override
    public void disable() {
        if (netTask != null) {
            netTask.cancel();
        }
        if (gadgets != null) {
            gadgets.stop();
        }
        if (satchel != null) {
            satchel.saveAll();
        }
        if (core != null) {
            itemIds.forEach(core.items()::unregister);
        }
    }
}
