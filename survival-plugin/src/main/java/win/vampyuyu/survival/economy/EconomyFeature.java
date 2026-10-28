package win.vampyuyu.survival.economy;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code economy} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class EconomyFeature implements Feature {
    @Override
    public String id() {
        return "economy";
    }

    @Override
    public void enable(Core core) {
        core.debug("economy feature stub enabled");
    }
}
