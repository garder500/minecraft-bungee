package win.vampyuyu.survival.marsh;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The {@code marsh} feature: the Putrid Marshes, fifth Realm behind the Sky Gates ({@link MarshRealm}). Must be listed
 * after the island and {@code realm} features (the Rot Resin is a {@code realm} resource).
 */
public final class MarshFeature implements Feature {
    private MarshRealm marsh;

    @Override
    public String id() {
        return "marsh";
    }

    @Override
    public void enable(Core core) {
        marsh = new MarshRealm(core);
        marsh.start();
    }

    @Override
    public void disable() {
        if (marsh != null) {
            marsh.close();
            marsh = null;
        }
    }
}
