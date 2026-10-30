package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.dungeon.puzzle.PuzzleSpec;

/** What the template of one room produced: where things are, in world coordinates. */
public final class RoomBuild {
    public final RoomPlan plan;
    public final int minX;
    public final int minZ;
    public final int baseY;
    /** Where mobs of fights may spawn (floor level). */
    public final List<Pos> mobSpawns = new ArrayList<>();
    /** Hidden chest positions (floor level), exactly {@code plan.secrets} of them. */
    public final List<Pos> secretChests = new ArrayList<>();
    /** Hidden levers placed in this room: layout door index to lever position. */
    public final Map<Integer, Pos> hiddenLevers = new LinkedHashMap<>();
    /** Where a player is put when sent into the room (or the run starts, for the entrance). */
    public Pos spawn;
    /** Landings next to each doorway (fall recovery points of trap rooms, approach points). */
    public final List<Pos> landings = new ArrayList<>();
    public Pos bossSpawn;
    public Pos miniBossSpawn;
    public Pos merchant;
    public Pos fairy;
    public Pos bloodSign;
    public PuzzleSpec puzzle;
    /** Y below which a player has fallen out of a trap room. */
    public int fallY = Integer.MIN_VALUE;

    RoomBuild(RoomPlan plan, int baseY) {
        this.plan = plan;
        this.minX = Geo.originX(plan);
        this.minZ = Geo.originZ(plan);
        this.baseY = baseY;
    }

    public boolean contains(double x, double z) {
        return x >= minX && x < minX + Geo.CELL && z >= minZ && z < minZ + Geo.CELL;
    }
}
