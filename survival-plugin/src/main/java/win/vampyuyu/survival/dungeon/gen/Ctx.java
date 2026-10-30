package win.vampyuyu.survival.dungeon.gen;

import java.util.Random;
import win.vampyuyu.survival.dungeon.DungeonConfig;

/** Drawing context of one room: local coordinates (0..31) and heights relative to the floor level. */
final class Ctx {
    final Canvas canvas;
    final Layout layout;
    final RoomPlan plan;
    final RoomBuild out;
    final Palette pal;
    final Random rng;
    final DungeonConfig.Floor floor;
    final int ox;
    final int oz;
    final int base;
    final int h;
    /** Cells (local x, z) taken by decoration, so spawns and chests avoid them. */
    final boolean[][] occ = new boolean[Geo.CELL][Geo.CELL];

    Ctx(Canvas canvas, Layout layout, RoomPlan plan, RoomBuild out, Palette pal, Random rng,
            DungeonConfig.Floor floor, int base) {
        this.canvas = canvas;
        this.layout = layout;
        this.plan = plan;
        this.out = out;
        this.pal = pal;
        this.rng = rng;
        this.floor = floor;
        this.ox = Geo.originX(plan);
        this.oz = Geo.originZ(plan);
        this.base = base;
        this.h = Geo.height(plan);
    }

    void put(int lx, int dy, int lz, String block) {
        canvas.set(ox + lx, base + dy, oz + lz, block);
    }

    void fill(int lx1, int dy1, int lz1, int lx2, int dy2, int lz2, String block) {
        canvas.box(ox + lx1, base + dy1, oz + lz1, ox + lx2, base + dy2, oz + lz2, block);
    }

    Pos pos(int lx, int dy, int lz) {
        return new Pos(ox + lx, base + dy, oz + lz);
    }

    void occupy(int lx1, int lz1, int lx2, int lz2) {
        for (int x = Math.max(0, Math.min(lx1, lx2)); x <= Math.min(Geo.CELL - 1, Math.max(lx1, lx2)); x++) {
            for (int z = Math.max(0, Math.min(lz1, lz2)); z <= Math.min(Geo.CELL - 1, Math.max(lz1, lz2)); z++) {
                occ[x][z] = true;
            }
        }
    }

    boolean free(int lx1, int lz1, int lx2, int lz2) {
        for (int x = lx1; x <= lx2; x++) {
            for (int z = lz1; z <= lz2; z++) {
                if (x < 1 || z < 1 || x > 30 || z > 30 || occ[x][z]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Keeps the strips just inside the four doorways free of decoration. */
    void occupyLandings() {
        occupy(13, 1, 18, 4);
        occupy(13, 27, 18, 30);
        occupy(1, 13, 4, 18);
        occupy(27, 13, 30, 18);
    }

    boolean chance(int percent) {
        return rng.nextInt(100) < percent;
    }
}
