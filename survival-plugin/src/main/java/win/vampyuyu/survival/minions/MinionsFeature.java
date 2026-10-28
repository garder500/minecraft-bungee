package win.vampyuyu.survival.minions;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code minions} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class MinionsFeature implements Feature {
    @Override
    public String id() {
        return "minions";
    }

    @Override
    public void enable(Core core) {
        core.debug("minions feature stub enabled");
    }
}
