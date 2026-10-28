package win.vampyuyu.survival.boss;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code boss} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class BossFeature implements Feature {
    @Override
    public String id() {
        return "boss";
    }

    @Override
    public void enable(Core core) {
        core.debug("boss feature stub enabled");
    }
}
