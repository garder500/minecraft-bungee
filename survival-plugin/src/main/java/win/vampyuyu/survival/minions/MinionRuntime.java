package win.vampyuyu.survival.minions;

import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

/** The not-saved state of one placed minion. */
final class MinionRuntime {
    Status status = Status.WORKING;
    long lastScan;
    long lastChat;
    long lastIslandCheck;
    int islandStrikes;
    /** True while a block / crop / sapling / mob placed for the current cycle is waiting to be harvested. */
    boolean placed;
    int spotX;
    int spotY;
    int spotZ;
    @Nullable Entity mob;
    /** Label text last drawn, to redraw only on change. */
    String labelText = "";
    boolean visualNear;
    long lastVisualCheck;
}
