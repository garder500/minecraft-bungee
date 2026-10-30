package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Code templates of every room type. Pure: they only draw on a {@link Canvas} and record positions. */
final class RoomTemplates {
    private RoomTemplates() {}

    static final String AIR = "air";

    static void build(Ctx c) {
        RoomPlan plan = c.plan;
        if (plan.type == RoomType.TRAP) {
            parkour(c);
        } else {
            shell(c);
            c.occupyLandings();
        }
        switch (plan.type) {
            case ENTRANCE -> entrance(c);
            case MOB -> mobRoom(c);
            case PUZZLE -> PuzzleTemplates.build(c);
            case SECRET -> secretRoom(c);
            case MINIBOSS -> miniBoss(c);
            case BLOOD -> blood(c);
            case FAIRY -> fairy(c);
            case SHOP -> shop(c);
            case BOSS -> boss(c);
            case TRAP -> { }
        }
        hiddenLevers(c);
        secretChests(c);
        if (plan.type != RoomType.TRAP) {
            lights(c);
        }
        // entry points per door
        for (Dir d : plan.neighbors.keySet()) {
            c.out.landings.add(Geo.doorLanding(c.layout, plan, d, c.base));
        }
        if (c.out.spawn == null) {
            c.out.spawn = plan.type == RoomType.TRAP && !c.out.landings.isEmpty() ? c.out.landings.get(0) : c.pos(15, 0, 15);
        }
        if (plan.type != RoomType.TRAP && plan.type != RoomType.BOSS) {
            pickSpawns(c);
        }
    }

    // ------------------------------------------------------------------ shared pieces

    /** Floor, walls, ceiling, corner pillars. */
    static void shell(Ctx c) {
        Palette p = c.pal;
        int top = c.h;
        for (int x = 0; x < Geo.CELL; x++) {
            for (int z = 0; z < Geo.CELL; z++) {
                c.put(x, -1, z, c.chance(14) ? p.floorAlt() : p.floor());
                c.put(x, top, z, p.ceiling());
            }
        }
        for (int i = 0; i < Geo.CELL; i++) {
            for (int y = -1; y < top; y++) {
                c.put(i, y, 0, wallBlock(c));
                c.put(i, y, 31, wallBlock(c));
                c.put(0, y, i, wallBlock(c));
                c.put(31, y, i, wallBlock(c));
            }
        }
        // baseboard and ceiling trim
        for (int i = 1; i < 31; i++) {
            if (i >= 13 && i <= 18) {
                continue; // keep the doorway lanes flat
            }
            c.put(i, 0, 1, p.slab() + "[type=bottom]");
            c.put(i, 0, 30, p.slab() + "[type=bottom]");
            c.put(1, 0, i, p.slab() + "[type=bottom]");
            c.put(30, 0, i, p.slab() + "[type=bottom]");
        }
        // corner pillars
        int[][] corners = {{1, 1}, {29, 1}, {1, 29}, {29, 29}};
        for (int[] k : corners) {
            c.fill(k[0], 0, k[1], k[0] + 1, top - 1, k[1] + 1, p.pillar());
            c.occupy(k[0], k[1], k[0] + 1, k[1] + 1);
        }
        // doorways are drawn by DungeonBuilder (it needs both neighbours)
    }

    private static String wallBlock(Ctx c) {
        return c.chance(12) ? c.pal.wallAlt() : c.pal.wall();
    }

    /** Wall torches, hanging lanterns. */
    static void lights(Ctx c) {
        Palette p = c.pal;
        int density = switch (c.plan.type) {
            case SECRET -> 1;
            case BLOOD, BOSS -> 3;
            default -> 2;
        };
        int[] along = density == 1 ? new int[] {8, 23} : density == 2 ? new int[] {6, 10, 21, 25} : new int[] {5, 8, 11, 20, 23, 26};
        int ty = Math.min(5, c.h - 3);
        for (int a : along) {
            c.put(a, ty, 1, p.wallTorch() + "[facing=south]");
            c.put(a, ty, 30, p.wallTorch() + "[facing=north]");
            c.put(1, ty, a, p.wallTorch() + "[facing=east]");
            c.put(30, ty, a, p.wallTorch() + "[facing=west]");
        }
        String lantern = p.lantern() + "[hanging=true]";
        int[][] hang = density == 1 ? new int[][] {{15, 15}} : new int[][] {{8, 8}, {23, 8}, {8, 23}, {23, 23}, {15, 15}};
        for (int[] l : hang) {
            if (!c.occ[l[0]][l[1]]) {
                c.put(l[0], c.h - 1, l[1], lantern);
            }
        }
    }

    /** Picks mob spawn cells on free floor. */
    static void pickSpawns(Ctx c) {
        List<Pos> all = new ArrayList<>();
        for (int x = 4; x <= 27; x += 2) {
            for (int z = 4; z <= 27; z += 2) {
                if (c.free(x, z, x, z)) {
                    all.add(c.pos(x, 0, z));
                }
            }
        }
        Collections.shuffle(all, c.rng);
        int max = Math.min(all.size(), 24);
        c.out.mobSpawns.addAll(all.subList(0, max));
        if (c.out.mobSpawns.size() < 6) {
            for (int[] f : new int[][] {{10, 10}, {20, 10}, {10, 20}, {20, 20}, {15, 8}, {15, 22}}) {
                c.out.mobSpawns.add(c.pos(f[0], 0, f[1]));
            }
        }
    }

    /** Hidden levers for the secret rooms next to this one (in the corners, on the sides of the corner pillars). */
    static void hiddenLevers(Ctx c) {
        int k = 0;
        List<DoorPlan> doors = c.layout.doors();
        for (int i = 0; i < doors.size(); i++) {
            DoorPlan d = doors.get(i);
            if (d.kind != DoorKind.SECRET || !d.touches(c.plan.index)) {
                continue;
            }
            RoomPlan other = c.layout.room(d.other(c.plan.index));
            if (other.type != RoomType.SECRET || c.plan.type == RoomType.SECRET) {
                continue;
            }
            if (c.plan.type == RoomType.TRAP) {
                // no corner to hide in over the void: the lever sits on the wall above the landing pad of that door
                Dir toward = d.a == c.plan.index ? d.dir : d.dir.opposite();
                switch (toward) {
                    case NORTH -> c.put(13, 1, 1, "lever[face=wall,facing=south,powered=false]");
                    case SOUTH -> c.put(13, 1, 30, "lever[face=wall,facing=north,powered=false]");
                    case WEST -> c.put(1, 1, 13, "lever[face=wall,facing=east,powered=false]");
                    default -> c.put(30, 1, 13, "lever[face=wall,facing=west,powered=false]");
                }
                c.out.hiddenLevers.put(i, switch (toward) {
                    case NORTH -> c.pos(13, 1, 1);
                    case SOUTH -> c.pos(13, 1, 30);
                    case WEST -> c.pos(1, 1, 13);
                    default -> c.pos(30, 1, 13);
                });
                continue;
            }
            int corner = (k++ + c.rng.nextInt(4)) % 4;
            switch (corner) {
                case 0 -> {
                    c.put(3, 1, 1, "lever[face=wall,facing=east,powered=false]");
                    c.out.hiddenLevers.put(i, c.pos(3, 1, 1));
                    c.occupy(3, 1, 3, 1);
                }
                case 1 -> {
                    c.put(28, 1, 1, "lever[face=wall,facing=west,powered=false]");
                    c.out.hiddenLevers.put(i, c.pos(28, 1, 1));
                    c.occupy(28, 1, 28, 1);
                }
                case 2 -> {
                    c.put(3, 1, 30, "lever[face=wall,facing=east,powered=false]");
                    c.out.hiddenLevers.put(i, c.pos(3, 1, 30));
                    c.occupy(3, 30, 3, 30);
                }
                default -> {
                    c.put(28, 1, 30, "lever[face=wall,facing=west,powered=false]");
                    c.out.hiddenLevers.put(i, c.pos(28, 1, 30));
                    c.occupy(28, 30, 28, 30);
                }
            }
        }
    }

    /** Hidden chests in alcoves along the walls. */
    static void secretChests(Ctx c) {
        int want = c.plan.secrets;
        if (want <= 0) {
            return;
        }
        if (c.plan.type == RoomType.TRAP) {
            c.put(15, 0, 15, "chest[facing=south]");
            c.out.secretChests.add(c.pos(15, 0, 15));
            return;
        }
        int placed = 0;
        for (int attempt = 0; attempt < 80 && placed < want; attempt++) {
            int side = c.rng.nextInt(4);
            int o = c.chance(50) ? 5 + c.rng.nextInt(7) : 20 + c.rng.nextInt(7);
            int x;
            int z;
            String facing;
            switch (side) {
                case 0 -> { x = o; z = 1; facing = "south"; }
                case 1 -> { x = o; z = 30; facing = "north"; }
                case 2 -> { x = 1; z = o; facing = "east"; }
                default -> { x = 30; z = o; facing = "west"; }
            }
            boolean alongX = side < 2;
            int x1 = alongX ? x - 1 : Math.min(x, x + (side == 2 ? 1 : -1));
            int x2 = alongX ? x + 1 : Math.max(x, x + (side == 2 ? 1 : -1));
            int z1 = alongX ? Math.min(z, z + (side == 0 ? 1 : -1)) : z - 1;
            int z2 = alongX ? Math.max(z, z + (side == 0 ? 1 : -1)) : z + 1;
            if (!c.free(Math.max(1, x1), Math.max(1, z1), Math.min(30, x2), Math.min(30, z2))) {
                continue;
            }
            c.put(x, 0, z, "chest[facing=" + facing + "]");
            c.put(x, 1, z, c.pal.wall());
            if (alongX) {
                c.fill(x - 1, 0, z, x - 1, 1, z, c.pal.pillar());
                c.fill(x + 1, 0, z, x + 1, 1, z, c.pal.pillar());
            } else {
                c.fill(x, 0, z - 1, x, 1, z - 1, c.pal.pillar());
                c.fill(x, 0, z + 1, x, 1, z + 1, c.pal.pillar());
            }
            c.occupy(Math.max(1, x1), Math.max(1, z1), Math.min(30, x2), Math.min(30, z2));
            c.out.secretChests.add(c.pos(x, 0, z));
            placed++;
        }
    }

    private static void cluster(Ctx c, int lx, int lz, int w, int d, int height) {
        c.fill(lx, 0, lz, lx + w - 1, height - 1, lz + d - 1, c.pal.pillar());
        c.fill(lx, height, lz, lx + w - 1, height, lz + d - 1, c.pal.slab() + "[type=bottom]");
        c.occupy(lx - 1, lz - 1, lx + w, lz + d);
    }

    // ------------------------------------------------------------------ room types

    static void entrance(Ctx c) {
        Palette p = c.pal;
        // dais
        c.fill(12, -1, 12, 18, -1, 18, p.accent());
        c.fill(13, -1, 13, 17, -1, 17, p.pillar());
        c.fill(14, 0, 14, 16, 0, 16, p.slab() + "[type=bottom]");
        c.put(15, 0, 15, "enchanting_table");
        c.occupy(11, 11, 19, 19);
        // four brazier columns around the dais
        for (int[] b : new int[][] {{9, 9}, {21, 9}, {9, 21}, {21, 21}}) {
            c.fill(b[0], 0, b[1], b[0], 2, b[1], p.fence());
            c.put(b[0], 3, b[1], "campfire[lit=true]");
            c.occupy(b[0] - 1, b[1] - 1, b[0] + 1, b[1] + 1);
        }
        // ring of floor accent
        for (int a = 0; a < 360; a += 6) {
            int x = 15 + (int) Math.round(8 * Math.cos(Math.toRadians(a)));
            int z = 15 + (int) Math.round(8 * Math.sin(Math.toRadians(a)));
            c.put(x, -1, z, p.accent());
        }
        c.out.spawn = c.pos(15, 1, 15);
    }

    static void mobRoom(Ctx c) {
        Palette p = c.pal;
        int clusters = 5 + c.rng.nextInt(3);
        for (int i = 0; i < clusters; i++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = 5 + c.rng.nextInt(21);
                int z = 5 + c.rng.nextInt(21);
                int w = 1 + c.rng.nextInt(2);
                int d = 1 + c.rng.nextInt(2);
                if (c.free(x - 1, z - 1, x + w, z + d)) {
                    cluster(c, x, z, w, d, 2 + c.rng.nextInt(3));
                    break;
                }
            }
        }
        // floor medallion
        for (int a = 0; a < 360; a += 4) {
            int x = 15 + (int) Math.round(5 * Math.cos(Math.toRadians(a)));
            int z = 15 + (int) Math.round(5 * Math.sin(Math.toRadians(a)));
            c.put(x, -1, z, p.accent());
        }
        if (c.chance(40)) {
            c.put(15, -1, 15, "chiseled_stone_bricks");
        }
        // bone piles and webs for mood
        for (int i = 0; i < 6; i++) {
            int x = 3 + c.rng.nextInt(26);
            int z = 3 + c.rng.nextInt(26);
            if (c.free(x, z, x, z)) {
                c.put(x, 0, z, c.chance(50) ? "bone_block" : "skeleton_skull[rotation=" + c.rng.nextInt(16) + "]");
            }
        }
    }

    static void miniBoss(Ctx c) {
        Palette p = c.pal;
        for (int[] b : new int[][] {{8, 8}, {22, 8}, {8, 22}, {22, 22}}) {
            c.fill(b[0], 0, b[1], b[0] + 1, c.h - 1, b[1] + 1, p.pillar());
            c.put(b[0], 0, b[1] - 1, "soul_lantern[hanging=false]");
            c.occupy(b[0] - 1, b[1] - 1, b[0] + 2, b[1] + 2);
        }
        c.fill(11, -1, 11, 19, -1, 19, p.accent());
        c.fill(12, -1, 12, 18, -1, 18, p.pillar());
        c.out.miniBossSpawn = c.pos(15, 0, 15);
        c.occupy(11, 11, 19, 19);
    }

    static void secretRoom(Ctx c) {
        Palette p = c.pal;
        for (int i = 0; i < 40; i++) {
            int x = 2 + c.rng.nextInt(28);
            int z = 2 + c.rng.nextInt(28);
            if (c.free(x, z, x, z)) {
                c.put(x, 0, z, switch (c.rng.nextInt(4)) {
                    case 0 -> "candle[candles=" + (1 + c.rng.nextInt(4)) + ",lit=true]";
                    case 1 -> "skeleton_skull[rotation=" + c.rng.nextInt(16) + "]";
                    case 2 -> "cobweb";
                    default -> "bone_block";
                });
            }
        }
        // altar
        c.fill(14, 0, 14, 16, 0, 16, p.pillar());
        c.put(15, 1, 15, "soul_lantern[hanging=false]");
        c.occupy(13, 13, 17, 17);
    }

    static void blood(Ctx c) {
        Palette p = c.pal;
        c.fill(10, -1, 10, 20, -1, 20, "red_nether_bricks");
        c.fill(12, -1, 12, 18, -1, 18, "netherrack");
        c.fill(13, -1, 13, 17, -1, 17, "red_concrete");
        c.fill(14, -1, 14, 16, -1, 16, "redstone_block");
        c.fill(12, 0, 12, 18, 0, 12, p.fence());
        c.fill(12, 0, 18, 18, 0, 18, p.fence());
        c.fill(12, 0, 13, 12, 0, 17, p.fence());
        c.fill(18, 0, 13, 18, 0, 17, p.fence());
        c.occupy(10, 10, 20, 20);
        for (int[] b : new int[][] {{6, 6}, {25, 6}, {6, 25}, {25, 25}}) {
            c.put(b[0], 0, b[1], "red_candle[candles=4,lit=true]");
        }
    }

    static void fairy(Ctx c) {
        for (int a = 0; a < 360; a += 5) {
            int x = 15 + (int) Math.round(7 * Math.cos(Math.toRadians(a)));
            int z = 15 + (int) Math.round(7 * Math.sin(Math.toRadians(a)));
            c.put(x, -1, z, "moss_block");
            if (a % 30 == 0) {
                c.put(x, 0, z, c.chance(50) ? "flowering_azalea" : "azalea");
            }
        }
        c.fill(13, -1, 13, 17, -1, 17, "glowstone");
        c.fill(14, -1, 14, 16, -1, 16, "amethyst_block");
        for (int i = 0; i < 24; i++) {
            int x = 3 + c.rng.nextInt(26);
            int z = 3 + c.rng.nextInt(26);
            if (c.free(x, z, x, z)) {
                c.put(x, 0, z, switch (c.rng.nextInt(4)) {
                    case 0 -> "pink_petals";
                    case 1 -> "moss_carpet";
                    case 2 -> "large_amethyst_bud[facing=up]";
                    default -> "short_grass";
                });
            }
        }
        for (int i = 0; i < 8; i++) {
            c.put(5 + c.rng.nextInt(21), c.h - 1, 5 + c.rng.nextInt(21), "spore_blossom");
        }
        c.out.fairy = c.pos(15, 2, 15);
        c.occupy(8, 8, 22, 22);
    }

    static void shop(Ctx c) {
        Palette p = c.pal;
        c.fill(12, -1, 12, 18, -1, 18, p.accent());
        c.fill(13, -1, 13, 17, -1, 17, "red_carpet");
        // stalls along the north side
        for (int x : new int[] {7, 15, 23}) {
            c.put(x, 0, 6, "barrel[facing=up]");
            c.put(x - 1, 0, 6, p.fence());
            c.put(x + 1, 0, 6, p.fence());
            c.put(x, 1, 6, "lantern[hanging=false]");
            c.occupy(x - 2, 5, x + 2, 8);
        }
        for (int x : new int[] {7, 23}) {
            c.put(x, 0, 25, "lectern[facing=north]");
            c.occupy(x - 1, 24, x + 1, 26);
        }
        c.out.merchant = c.pos(15, 0, 15);
        c.occupy(12, 12, 18, 18);
    }

    static void boss(Ctx c) {
        Palette p = c.pal;
        c.fill(8, -1, 8, 22, -1, 22, "obsidian");
        c.fill(10, -1, 10, 20, -1, 20, "crying_obsidian");
        c.fill(12, -1, 12, 18, -1, 18, p.accent());
        for (int a = 0; a < 360; a += 45) {
            int x = 15 + (int) Math.round(11 * Math.cos(Math.toRadians(a)));
            int z = 15 + (int) Math.round(11 * Math.sin(Math.toRadians(a)));
            x = Math.max(3, Math.min(27, x));
            z = Math.max(3, Math.min(27, z));
            c.fill(x, 0, z, x + 1, c.h - 1, z + 1, p.pillar());
            c.put(x - 1, 0, z, "soul_lantern[hanging=false]");
            c.occupy(x - 1, z - 1, x + 2, z + 2);
        }
        for (int a = 0; a < 360; a += 20) {
            int x = 15 + (int) Math.round(9 * Math.cos(Math.toRadians(a)));
            int z = 15 + (int) Math.round(9 * Math.sin(Math.toRadians(a)));
            c.put(x, 0, z, "soul_lantern[hanging=false]");
        }
        c.out.bossSpawn = c.pos(15, 0, 15);
        c.occupy(8, 8, 22, 22);
    }

    /** Stepping stones over the void, from every doorway to a central island. */
    static void parkour(Ctx c) {
        Palette p = c.pal;
        int top = c.h;
        for (int x = 0; x < Geo.CELL; x++) {
            for (int z = 0; z < Geo.CELL; z++) {
                c.put(x, top, z, p.ceiling());
            }
        }
        for (int i = 0; i < Geo.CELL; i++) {
            for (int y = -1; y < top; y++) {
                c.put(i, y, 0, wallBlock(c));
                c.put(i, y, 31, wallBlock(c));
                c.put(0, y, i, wallBlock(c));
                c.put(31, y, i, wallBlock(c));
            }
        }
        int[][] corners = {{1, 1}, {29, 1}, {1, 29}, {29, 29}};
        for (int[] k : corners) {
            c.fill(k[0], -1, k[1], k[0] + 1, top - 1, k[1] + 1, p.pillar());
        }
        // island
        c.fill(13, -1, 13, 18, -1, 18, p.floor());
        c.fill(14, -1, 14, 17, -1, 17, p.accent());
        c.occupy(1, 1, 30, 30);
        c.out.fallY = c.base - 3;
        int hazard = Math.min(35, c.floor.number() * 7);
        for (Dir d : c.plan.neighbors.keySet()) {
            // pad next to the door
            switch (d) {
                case NORTH -> c.fill(13, -1, 1, 18, -1, 3, p.floor());
                case SOUTH -> c.fill(13, -1, 28, 18, -1, 30, p.floor());
                case WEST -> c.fill(1, -1, 13, 3, -1, 18, p.floor());
                default -> c.fill(28, -1, 13, 30, -1, 18, p.floor());
            }
            int[] ring = d == Dir.NORTH || d == Dir.WEST ? new int[] {6, 10} : new int[] {25, 21};
            for (int step : ring) {
                int jitter = 14 + c.rng.nextInt(3);
                String block = c.chance(hazard) ? "magma_block" : c.chance(30) ? p.floorAlt() : p.pillar();
                if (d == Dir.NORTH || d == Dir.SOUTH) {
                    int z = d == Dir.NORTH ? step : step - 1;
                    c.fill(jitter, -1, z, jitter + 1, -1, z + 1, block);
                } else {
                    int x = d == Dir.WEST ? step : step - 1;
                    c.fill(x, -1, jitter, x + 1, -1, jitter + 1, block);
                }
            }
        }
        // ceiling lights low enough to see the stones
        String lantern = p.lantern() + "[hanging=true]";
        for (int[] l : new int[][] {{8, 8}, {23, 8}, {8, 23}, {23, 23}, {15, 15}, {15, 8}, {15, 23}, {8, 15}, {23, 15}}) {
            c.put(l[0], c.h - 1, l[1], lantern);
        }
        c.put(15, 0, 12, "soul_lantern[hanging=false]");
        int tp = Math.min(5, c.h - 3);
        for (int a : new int[] {6, 24}) {
            c.put(a, tp, 1, p.wallTorch() + "[facing=south]");
            c.put(a, tp, 30, p.wallTorch() + "[facing=north]");
            c.put(1, tp, a, p.wallTorch() + "[facing=east]");
            c.put(30, tp, a, p.wallTorch() + "[facing=west]");
        }
    }
}
