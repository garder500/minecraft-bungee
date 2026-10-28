package win.vampyuyu.survival.dungeon;

import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/** Entry point of the {@code dungeon} feature (see package-info). Foundation stub: replace the body of enable(). */
public final class DungeonFeature implements Feature {
    @Override
    public String id() {
        return "dungeon";
    }

    @Override
    public void enable(Core core) {
        core.debug("dungeon feature stub enabled");
    }
}
