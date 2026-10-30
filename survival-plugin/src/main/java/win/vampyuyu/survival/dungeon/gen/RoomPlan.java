package win.vampyuyu.survival.dungeon.gen;

import java.util.EnumMap;
import java.util.Map;

/** One room of a {@link Layout}: its cell, type and doors. Mutable only while the generator works. */
public final class RoomPlan {
    public final int index;
    public final int cx;
    public final int cz;
    public RoomType type = RoomType.MOB;
    public PuzzleKind puzzle;
    /** A mob room whose clearing gives a wither key. */
    public boolean keyCarrier;
    /** Hidden chests inside the room (the hidden lever of a secret room is counted by the layout). */
    public int secrets;
    /** Distance in doors from the entrance. */
    public int depth;
    /** Neighbouring room index per direction where a doorway exists. */
    public final Map<Dir, Integer> neighbors = new EnumMap<>(Dir.class);

    RoomPlan(int index, int cx, int cz) {
        this.index = index;
        this.cx = cx;
        this.cz = cz;
    }

    public int degree() { return neighbors.size(); }

    @Override
    public String toString() {
        return "Room#" + index + "(" + cx + "," + cz + " " + type + (puzzle != null ? ":" + puzzle : "") + ")";
    }
}
