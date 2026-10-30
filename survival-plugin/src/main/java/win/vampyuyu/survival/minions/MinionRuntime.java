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
    /** True while a mob / fishing splash placed for the current cycle is waiting (blocks live in MinionData). */
    boolean placed;
    long lastGuardChat;
    /** Wall clock since when the island has been missing (not saved: a restart must not count downtime), 0 for none. */
    long missingSince;
    @Nullable Entity mob;
    /** Label text last drawn, to redraw only on change. */
    String labelText = "";
    boolean visualNear;
    long lastVisualCheck;
}
