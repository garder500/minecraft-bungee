package win.vampyuyu.survival.menu;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code menu} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class MenuFeature implements Feature {
    @Override
    public String id() {
        return "menu";
    }

    @Override
    public void enable(Core core) {
        core.debug("menu feature stub enabled");
    }
}
