package win.vampyuyu.survival.boss.world;

import java.util.function.IntPredicate;

/** Finds where to stand in a column. Pure: the caller answers "is this block solid". */
public final class GroundSnap {
    private GroundSnap() {}

    /**
     * Scans a column from {@code startY} down to {@code minY} for the highest solid block that has {@code clear}
     * free (non solid) blocks above it, and returns the Y of the free block right above it (feet position), or
     * {@link Integer#MIN_VALUE} when the column has no such place (void, buried, or a low ceiling everywhere).
     */
    public static int find(int startY, int minY, int clear, IntPredicate solid) {
        for (int y = startY; y >= minY; y--) {
            if (!solid.test(y)) {
                continue;
            }
            boolean free = true;
            for (int up = 1; up <= clear; up++) {
                if (solid.test(y + up)) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }
}
