package win.vampyuyu.survival.depths;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The {@code depths} feature: the Abyssal Depths, seventh of the Celestial Realms behind the Sky Gates
 * ({@link DepthsRealm}). Must be listed after the island, economy, boss and realm features (the Pressure Pearl is
 * a Realm resource of the {@code realm} feature).
 */
public final class DepthsFeature implements Feature {
    private DepthsRealm depths;

    @Override
    public String id() {
        return "depths";
    }

    @Override
    public void enable(Core core) {
        depths = new DepthsRealm(core);
        depths.start();
    }

    @Override
    public void disable() {
        if (depths != null) {
            depths.close();
            depths = null;
        }
    }
}
