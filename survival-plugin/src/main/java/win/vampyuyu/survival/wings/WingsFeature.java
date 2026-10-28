package win.vampyuyu.survival.wings;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code wings} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class WingsFeature implements Feature {
    @Override
    public String id() {
        return "wings";
    }

    @Override
    public void enable(Core core) {
        core.debug("wings feature stub enabled");
    }
}
