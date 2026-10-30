package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayList;
import java.util.List;

/** Fixed geometry of the dungeon: a grid of 32 block cells, a 30x30 room in each. */
public final class Geo {
    public static final int CELL = 32;
    /** Interior height of normal rooms (floor level to the ceiling block exclusive). */
    public static final int H = 9;
    public static final int BOSS_H = 17;
    public static final int DOOR_W = 4;
    public static final int DOOR_H = 4;
    /** Local start of the doorway along the wall (4 wide, centred). */
    public static final int DOOR_START = 14;

    private Geo() {}

    public static int originX(RoomPlan r) { return r.cx * CELL; }

    public static int originZ(RoomPlan r) { return r.cz * CELL; }

    public static int height(RoomPlan r) { return r.type == RoomType.BOSS ? BOSS_H : H; }

    /** Centre block of the room interior (floor level). */
    public static Pos center(RoomPlan r, int baseY) {
        return new Pos(originX(r) + 15, baseY, originZ(r) + 15);
    }

    /** The blocks of the doorway (both wall layers, 4 wide, 4 tall). */
    public static List<Pos> doorBlocks(Layout layout, DoorPlan d, int baseY) {
        RoomPlan a = layout.room(d.a);
        List<Pos> out = new ArrayList<>();
        for (int layer = 0; layer < 2; layer++) {
            for (int w = 0; w < DOOR_W; w++) {
                for (int h = 0; h < DOOR_H; h++) {
                    if (d.dir == Dir.EAST) {
                        out.add(new Pos(originX(a) + 31 + layer, baseY + h, originZ(a) + DOOR_START + w));
                    } else {
                        out.add(new Pos(originX(a) + DOOR_START + w, baseY + h, originZ(a) + 31 + layer));
                    }
                }
            }
        }
        return out;
    }

    /** Floor level block just inside the room next to the doorway (where a player walks through). */
    public static Pos doorLanding(Layout layout, RoomPlan room, Dir toward, int baseY) {
        int ox = originX(room);
        int oz = originZ(room);
        return switch (toward) {
            case NORTH -> new Pos(ox + 15, baseY, oz + 2);
            case SOUTH -> new Pos(ox + 15, baseY, oz + 29);
            case WEST -> new Pos(ox + 2, baseY, oz + 15);
            case EAST -> new Pos(ox + 29, baseY, oz + 15);
        };
    }

    /** True when the world position lies inside the room's cell box (walls included, any height). */
    public static boolean inRoom(RoomPlan r, double x, double z) {
        double ox = originX(r);
        double oz = originZ(r);
        return x >= ox && x < ox + CELL && z >= oz && z < oz + CELL;
    }

    /** Room whose cell contains the position, or null. */
    public static RoomPlan roomAt(Layout layout, double x, double z) {
        int cx = (int) Math.floor(x / CELL);
        int cz = (int) Math.floor(z / CELL);
        return layout.roomAt(cx, cz);
    }
}
