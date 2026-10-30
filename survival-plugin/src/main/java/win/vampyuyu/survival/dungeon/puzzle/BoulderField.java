package win.vampyuyu.survival.dungeon.puzzle;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * A single boulder to push onto a target pad across a field with obstacles (one cell = one block).
 * The generator only returns fields a BFS solver proves solvable with at least {@code minPushes} pushes.
 */
public final class BoulderField {
    public final int w;
    public final int h;
    private final boolean[][] wall;
    public final int startX;
    public final int startZ;
    public final int targetX;
    public final int targetZ;
    /** Where the player enters (always free). */
    public final int entryX;
    public final int entryZ;
    private int boulderX;
    private int boulderZ;
    private int pushes;

    private BoulderField(int w, int h, boolean[][] wall, int bx, int bz, int tx, int tz, int ex, int ez) {
        this.w = w;
        this.h = h;
        this.wall = wall;
        this.startX = bx;
        this.startZ = bz;
        this.targetX = tx;
        this.targetZ = tz;
        this.entryX = ex;
        this.entryZ = ez;
        this.boulderX = bx;
        this.boulderZ = bz;
    }

    public boolean isWall(int x, int z) {
        return x < 0 || z < 0 || x >= w || z >= h || wall[x][z];
    }

    public int boulderX() { return boulderX; }

    public int boulderZ() { return boulderZ; }

    public int pushes() { return pushes; }

    public boolean solved() { return boulderX == targetX && boulderZ == targetZ; }

    public void reset() {
        boulderX = startX;
        boulderZ = startZ;
    }

    /** Pushes the boulder one cell by (dx, dz) when that cell is free. Returns true when it moved. */
    public boolean push(int dx, int dz) {
        if (solved() || Math.abs(dx) + Math.abs(dz) != 1) {
            return false;
        }
        int nx = boulderX + dx;
        int nz = boulderZ + dz;
        if (isWall(nx, nz)) {
            return false;
        }
        boulderX = nx;
        boulderZ = nz;
        pushes++;
        return true;
    }

    /** Minimal number of pushes from the start, or -1 when unsolvable (player may walk anywhere free). */
    public int solve() {
        return solve(wall, w, h, startX, startZ, targetX, targetZ, entryX, entryZ);
    }

    private static int solve(boolean[][] wall, int w, int h, int bx, int bz, int tx, int tz, int px, int pz) {
        int cells = w * h;
        int[] dist = new int[cells * cells];
        java.util.Arrays.fill(dist, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        // state: boulder cell, player cell. Player steps cost 0 pushes, so use a 0-1 BFS with two deques.
        ArrayDeque<int[]> front = new ArrayDeque<>();
        int[] startState = {bx * h + bz, px * h + pz};
        dist[startState[0] * cells + startState[1]] = 0;
        front.add(startState);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!front.isEmpty()) {
            int[] s = front.pollFirst();
            int b = s[0];
            int p = s[1];
            int d = dist[b * cells + p];
            int bxx = b / h;
            int bzz = b % h;
            if (bxx == tx && bzz == tz) {
                return d;
            }
            int pxx = p / h;
            int pzz = p % h;
            for (int[] dir : dirs) {
                int nx = pxx + dir[0];
                int nz = pzz + dir[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h || wall[nx][nz]) {
                    continue;
                }
                if (nx == bxx && nz == bzz) {
                    int bx2 = bxx + dir[0];
                    int bz2 = bzz + dir[1];
                    if (bx2 < 0 || bz2 < 0 || bx2 >= w || bz2 >= h || wall[bx2][bz2]) {
                        continue;
                    }
                    int idx = (bx2 * h + bz2) * cells + (bxx * h + bzz);
                    if (dist[idx] < 0 || dist[idx] > d + 1) {
                        dist[idx] = d + 1;
                        queue.add(new int[] {bx2 * h + bz2, bxx * h + bzz});
                    }
                } else {
                    int idx = b * cells + (nx * h + nz);
                    if (dist[idx] < 0 || dist[idx] > d) {
                        dist[idx] = d;
                        front.addFirst(new int[] {b, nx * h + nz});
                    }
                }
            }
            if (front.isEmpty()) {
                front.addAll(queue);
                queue.clear();
            }
        }
        return -1;
    }

    /** Generates a field; falls back to an open field when no random one reaches {@code minPushes}. */
    public static BoulderField generate(Random rng, int w, int h, int obstacles, int minPushes) {
        for (int attempt = 0; attempt < 400; attempt++) {
            boolean[][] wall = new boolean[w][h];
            int ex = w / 2;
            int ez = h - 1;
            int bx = 1 + rng.nextInt(w - 2);
            int bz = 1 + rng.nextInt(h - 3);
            int tx = rng.nextInt(w);
            int tz = rng.nextInt(h - 2);
            if ((tx == bx && tz == bz) || (tx == ex && tz == ez)) {
                continue;
            }
            for (int i = 0; i < obstacles; i++) {
                int ox = rng.nextInt(w);
                int oz = rng.nextInt(h);
                if ((ox == bx && oz == bz) || (ox == tx && oz == tz) || (ox == ex && oz == ez)) {
                    continue;
                }
                wall[ox][oz] = true;
            }
            BoulderField f = new BoulderField(w, h, wall, bx, bz, tx, tz, ex, ez);
            int pushes = f.solve();
            if (pushes >= minPushes) {
                return f;
            }
        }
        boolean[][] open = new boolean[w][h];
        return new BoulderField(w, h, open, w / 2, h / 2, 1, 1, w / 2, h - 1);
    }
}
