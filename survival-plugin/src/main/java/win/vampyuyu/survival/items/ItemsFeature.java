package win.vampyuyu.survival.items;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code items} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class ItemsFeature implements Feature {
    @Override
    public String id() {
        return "items";
    }

    @Override
    public void enable(Core core) {
        core.debug("items feature stub enabled");
    }
}
