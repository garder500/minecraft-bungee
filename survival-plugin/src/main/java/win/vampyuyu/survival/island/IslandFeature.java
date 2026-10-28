package win.vampyuyu.survival.island;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code island} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class IslandFeature implements Feature {
    @Override
    public String id() {
        return "island";
    }

    @Override
    public void enable(Core core) {
        core.debug("island feature stub enabled");
    }
}
