package win.vampyuyu.survival.dungeon.gen;

/** Where room templates draw. Coordinates are world coordinates of the run world; blocks are block data strings. */
public interface Canvas {
    void set(int x, int y, int z, String block);

    default void set(Pos p, String block) {
        set(p.x(), p.y(), p.z(), block);
    }

    /** Fills the inclusive box. */
    default void box(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, block);
                }
            }
        }
    }
}
