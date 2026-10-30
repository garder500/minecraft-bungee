package win.vampyuyu.survival.dungeon.gen;

import java.util.List;
import java.util.Map;

/** Everything produced by {@link DungeonBuilder}: the blocks to place and where things are. */
public final class BuiltDungeon {
    /** A doorway as built. */
    public record DoorBuild(int index, DoorPlan plan, List<Pos> blocks, String closedBlock, Pos sideA, Pos sideB) {}

    public final Layout layout;
    public final BlockBuffer buffer;
    public final Map<Integer, RoomBuild> rooms;
    public final List<DoorBuild> doors;
    public final int baseY;

    BuiltDungeon(Layout layout, BlockBuffer buffer, Map<Integer, RoomBuild> rooms, List<DoorBuild> doors, int baseY) {
        this.layout = layout;
        this.buffer = buffer;
        this.rooms = rooms;
        this.doors = doors;
        this.baseY = baseY;
    }

    public RoomBuild room(int index) { return rooms.get(index); }

    /** Where everybody appears at the start. */
    public Pos spawn() { return rooms.get(layout.entrance().index).spawn; }

    /** The room whose cell contains the position, or null. */
    public RoomBuild roomAt(double x, double z) {
        RoomPlan p = Geo.roomAt(layout, x, z);
        return p == null ? null : rooms.get(p.index);
    }
}
