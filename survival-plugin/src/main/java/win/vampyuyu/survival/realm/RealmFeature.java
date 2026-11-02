package win.vampyuyu.survival.realm;

import win.vampyuyu.survival.api.RealmResourceApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * Local resources of the Realms (GDD 3.2, 15.1): Cinderheart in the Cracked Ashes, Heartwood in the Whispering Grove.
 * Obtained by breaking the Realm's blocks and killing its mobs, with a pity curve per player. Their real uses
 * (sidegrades, modules, runes) come in phase 3; for now they can be sold to the adventurer.
 */
public final class RealmFeature implements Feature {
    @Override
    public String id() {
        return "realm";
    }

    @Override
    public void enable(Core core) {
        RealmItems.register(core.items());
        RealmService service = new RealmService(core, RealmRepo.load(core.data(), core.getPlugin().getLogger()));
        core.register(RealmResourceApi.class, service);
        core.register(RealmService.class, service);
        core.listen(new RealmListener(core, service));
        core.onDataChange(domain -> {
            if (domain.equals("realm")) {
                service.reload(RealmRepo.load(core.data(), core.getPlugin().getLogger()));
            }
        });
    }
}
