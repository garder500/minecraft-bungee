package win.vampyuyu.survival.dungeon.gen;

/** Integer block position. In a built dungeon these are world coordinates of the run world. */
public record Pos(int x, int y, int z) {
    public Pos add(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    public Pos step(Dir d) {
        return new Pos(x + d.dx, y, z + d.dz);
    }

    /** Packs x/y/z into one long (x, z: 26 bits signed, y: 12 bits signed). */
    public long key() {
        return key(x, y, z);
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public double distanceSquared(double ox, double oy, double oz) {
        double dx = x + 0.5 - ox;
        double dy = y + 0.5 - oy;
        double dz = z + 0.5 - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
