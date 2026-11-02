package win.vampyuyu.survival.realm;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The {@code storm} feature: the Tempestuous Archipelago, sixth Realm behind the Sky Gates ({@link StormRealm}). Must be
 * listed after the island, boss, bastion and realm features (the Thunder Quartz item is the {@code realm} feature's).
 */
public final class StormFeature implements Feature {
    private StormRealm storm;

    @Override
    public String id() {
        return "storm";
    }

    @Override
    public void enable(Core core) {
        storm = new StormRealm(core);
        storm.start();
    }

    @Override
    public void disable() {
        if (storm != null) {
            storm.close();
            storm = null;
        }
    }
}
