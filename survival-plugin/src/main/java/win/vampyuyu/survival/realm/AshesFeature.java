package win.vampyuyu.survival.realm;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The {@code ashes} feature: the Realm of the Cracked Ashes, first of the Celestial Realms behind the Sky Gates
 * ({@link AshesRealm}). Must be listed after the island, economy and
 * boss features (the resources of the Realm are the {@code realm} feature).
 */
public final class AshesFeature implements Feature {
    private AshesRealm ashes;

    @Override
    public String id() {
        return "ashes";
    }

    @Override
    public void enable(Core core) {
        ashes = new AshesRealm(core);
        ashes.start();
    }

    @Override
    public void disable() {
        if (ashes != null) {
            ashes.close();
            ashes = null;
        }
    }
}
