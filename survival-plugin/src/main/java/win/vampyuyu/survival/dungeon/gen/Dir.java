package win.vampyuyu.survival.dungeon.gen;

/** Horizontal direction on the room grid (north = -z). */
public enum Dir {
    NORTH(0, -1, "north"),
    EAST(1, 0, "east"),
    SOUTH(0, 1, "south"),
    WEST(-1, 0, "west");

    public final int dx;
    public final int dz;
    /** Name used by block data ({@code facing=north}). */
    public final String key;

    Dir(int dx, int dz, String key) {
        this.dx = dx;
        this.dz = dz;
        this.key = key;
    }

    public Dir opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    public static Dir of(int dx, int dz) {
        for (Dir d : values()) {
            if (d.dx == dx && d.dz == dz) {
                return d;
            }
        }
        throw new IllegalArgumentException("not a unit step: " + dx + "," + dz);
    }
}
