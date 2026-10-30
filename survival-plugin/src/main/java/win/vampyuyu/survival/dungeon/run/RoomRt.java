package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import win.vampyuyu.survival.dungeon.gen.RoomBuild;

/** Runtime state of one room. */
final class RoomRt {
    enum State { UNVISITED, ACTIVE, CLEARED }

    final RoomBuild build;
    State state = State.UNVISITED;
    boolean done;
    boolean visited;
    /** Entities of this room's fight that are still alive. */
    final Set<UUID> mobs = new HashSet<>();
    final ArrayDeque<MobSpec> queue = new ArrayDeque<>();
    List<List<MobSpec>> waves = new ArrayList<>();
    int wave;
    long nextWaveAtTick;
    int spawnedTotal;
    BossBar bar;
    PuzzleRuntime puzzle;
    boolean fairyUsed;
    int level;
    long bossStartAtTick = -1;
    int fightTotal;
    int removed;

    RoomRt(RoomBuild build) {
        this.build = build;
    }

    int index() { return build.plan.index; }
}
