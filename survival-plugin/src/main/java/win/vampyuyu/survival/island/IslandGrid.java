package win.vampyuyu.survival.island;

/**
 * Pure grid maths. Island {@code n} lives in the cell {@link #spiral(long) spiral(n)}; the centre of cell {@code c} on
 * one axis is {@code c * cellSize}. Cell (0,0) is reserved: the End dimension runs vanilla's dragon fight there, so
 * allocation starts at spiral index 1.
 */
public final class IslandGrid {
    private final int cellSize;

    public IslandGrid(int cellSize) {
        if (cellSize < 64) {
            throw new IllegalArgumentException("cell size must be at least 64, got " + cellSize);
        }
        this.cellSize = cellSize;
    }

    public int cellSize() {
        return cellSize;
    }

    /** True for the cell that is never given to an island. */
    public static boolean reserved(int cellX, int cellZ) {
        return cellX == 0 && cellZ == 0;
    }

    /**
     * Cell of the n-th square spiral starting at (0,0): 0:(0,0) 1:(1,0) 2:(1,1) 3:(0,1) 4:(-1,1) 5:(-1,0) 6:(-1,-1)
     * 7:(0,-1) 8:(1,-1) 9:(2,-1)...
     */
    public static int[] spiral(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("negative index");
        }
        if (n == 0) {
            return new int[] {0, 0};
        }
        long k = (long) Math.ceil((Math.sqrt((double) n + 1) - 1) / 2);
        while ((2 * k - 1) * (2 * k - 1) > n) {
            k--;
        }
        while ((2 * k + 1) * (2 * k + 1) <= n) {
            k++;
        }
        long i = n - (2 * k - 1) * (2 * k - 1);
        long side = i / (2 * k);
        long off = i % (2 * k);
        long x;
        long z;
        switch ((int) side) {
            case 0 -> {
                x = k;
                z = -k + 1 + off;
            }
            case 1 -> {
                x = k - 1 - off;
                z = k;
            }
            case 2 -> {
                x = -k;
                z = k - 1 - off;
            }
            default -> {
                x = -k + 1 + off;
                z = -k;
            }
        }
        return new int[] {(int) x, (int) z};
    }

    /** Cell index on one axis for a world coordinate. */
    public int cellOf(double coordinate) {
        int block = (int) Math.floor(coordinate);
        return Math.floorDiv(block + cellSize / 2, cellSize);
    }

    /** Block coordinate of the island centre on one axis. */
    public int centerOf(int cell) {
        return cell * cellSize;
    }

    /** True when the world coordinate pair lies inside the square of {@code radius} blocks around the cell centre. */
    public boolean within(int cellX, int cellZ, int radius, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int cx = centerOf(cellX);
        int cz = centerOf(cellZ);
        return bx >= cx - radius && bx <= cx + radius && bz >= cz - radius && bz <= cz + radius;
    }

    /** Packs a cell into one map key. */
    public static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }
}
