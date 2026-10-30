package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import win.vampyuyu.survival.dungeon.DungeonConfig;

/**
 * Pure, seeded floor plan generator. The same (floor, seed) always gives the same layout: one {@link Random} is
 * threaded through, and no hash-ordered collection is iterated.
 * <p>
 * Guarantees (checked by tests): the entrance is on the bottom row and the boss room on the top row, a path of
 * doorways joins them through the blood room (the blood room is the only neighbour of the boss room), every room is
 * connected, and {@link Layout#solvable()} holds (every wither door has a key source reachable before it).
 */
public final class LayoutGenerator {
    private LayoutGenerator() {}

    public static Layout generate(DungeonConfig.Floor f, long seed) {
        Random rng = new Random(seed * 0x9E3779B97F4A7C15L + f.number() * 31L + 7);
        int n = f.grid();
        int entranceCell = (n - 1) * n + rng.nextInt(n);
        int bossCell = rng.nextInt(n);

        int shortest = Math.abs(entranceCell % n - bossCell % n) + (n - 1) + 1;
        int leafSpecials = f.secretRooms() + f.miniBosses() + (f.fairy() ? 1 : 0) + (f.shop() ? 1 : 0);
        int maxLen = Math.max(shortest, f.rooms() - Math.max(2, Math.min(leafSpecials, f.rooms() / 3)));
        int minLen = Math.max(shortest, Math.min(maxLen, (int) Math.ceil(f.rooms() * 0.5)));

        int[] path = null;
        for (int attempt = 0; attempt < 4 && path == null; attempt++) {
            int lo = attempt == 0 ? minLen : attempt == 1 ? (minLen + shortest) / 2 : shortest;
            int hi = attempt < 3 ? maxLen : n * n;
            path = findPath(n, entranceCell, bossCell, lo, hi, rng);
        }
        if (path == null) {
            path = shortestPath(n, entranceCell, bossCell);
        }

        boolean[] inSet = new boolean[n * n];
        List<int[]> edges = new ArrayList<>(); // pairs of cells
        for (int i = 0; i < path.length; i++) {
            inSet[path[i]] = true;
            if (i > 0) {
                edges.add(new int[] {path[i - 1], path[i]});
            }
        }
        int bloodCell = path[path.length - 2];
        int roomTarget = Math.max(f.rooms(), path.length);
        int count = path.length;

        // grow side branches (never from the boss or blood room)
        while (count < roomTarget) {
            List<int[]> frontier = new ArrayList<>();
            for (int cell = 0; cell < n * n; cell++) {
                if (!inSet[cell] || cell == bossCell || cell == bloodCell) {
                    continue;
                }
                for (Dir d : Dir.values()) {
                    int nb = neighbor(cell, d, n);
                    if (nb >= 0 && !inSet[nb]) {
                        frontier.add(new int[] {cell, nb});
                    }
                }
            }
            if (frontier.isEmpty()) {
                break;
            }
            int[] pick = frontier.get(rng.nextInt(frontier.size()));
            inSet[pick[1]] = true;
            edges.add(new int[] {pick[0], pick[1]});
            count++;
        }

        // extra loops between existing rooms
        for (int l = 0; l < f.loops(); l++) {
            List<int[]> options = new ArrayList<>();
            for (int cell = 0; cell < n * n; cell++) {
                if (!inSet[cell] || cell == bossCell || cell == bloodCell) {
                    continue;
                }
                for (Dir d : new Dir[] {Dir.EAST, Dir.SOUTH}) {
                    int nb = neighbor(cell, d, n);
                    if (nb >= 0 && inSet[nb] && nb != bossCell && nb != bloodCell && !hasEdge(edges, cell, nb)) {
                        options.add(new int[] {cell, nb});
                    }
                }
            }
            if (options.isEmpty()) {
                break;
            }
            edges.add(options.get(rng.nextInt(options.size())));
        }

        // rooms in cell order
        int[] cellToRoom = new int[n * n];
        java.util.Arrays.fill(cellToRoom, -1);
        List<RoomPlan> rooms = new ArrayList<>();
        for (int cell = 0; cell < n * n; cell++) {
            if (inSet[cell]) {
                cellToRoom[cell] = rooms.size();
                rooms.add(new RoomPlan(rooms.size(), cell % n, cell / n));
            }
        }
        List<DoorPlan> doors = new ArrayList<>();
        for (int[] e : edges) {
            int lo = Math.min(e[0], e[1]);
            int hi = Math.max(e[0], e[1]);
            Dir dir = hi == lo + 1 ? Dir.EAST : Dir.SOUTH;
            int ra = cellToRoom[lo];
            int rb = cellToRoom[hi];
            doors.add(new DoorPlan(ra, rb, dir, DoorKind.OPEN));
            Dir fromB = dir.opposite();
            rooms.get(ra).neighbors.put(dir, rb);
            rooms.get(rb).neighbors.put(fromB, ra);
        }
        // keep door order stable (by room indexes)
        doors.sort((x, y) -> x.a != y.a ? Integer.compare(x.a, y.a) : Integer.compare(x.b, y.b));

        List<Integer> roomPath = new ArrayList<>();
        for (int cell : path) {
            roomPath.add(cellToRoom[cell]);
        }
        int entrance = cellToRoom[entranceCell];
        int blood = cellToRoom[bloodCell];
        int boss = cellToRoom[bossCell];

        Layout layout = new Layout(f.number(), seed, n, rooms, doors, cellToRoom, roomPath, entrance, blood, boss);
        computeDepth(layout);
        assignTypes(layout, f, rng);
        assignDoorKinds(layout, f);
        assignSecrets(layout, f, rng);
        return layout;
    }

    // ------------------------------------------------------------------ path

    private static int neighbor(int cell, Dir d, int n) {
        int x = cell % n + d.dx;
        int z = cell / n + d.dz;
        return x < 0 || z < 0 || x >= n || z >= n ? -1 : z * n + x;
    }

    private static boolean hasEdge(List<int[]> edges, int a, int b) {
        for (int[] e : edges) {
            if ((e[0] == a && e[1] == b) || (e[0] == b && e[1] == a)) {
                return true;
            }
        }
        return false;
    }

    private static int manhattan(int a, int b, int n) {
        return Math.abs(a % n - b % n) + Math.abs(a / n - b / n);
    }

    private static int[] findPath(int n, int start, int goal, int minLen, int maxLen, Random rng) {
        boolean[] used = new boolean[n * n];
        int[] buf = new int[n * n];
        used[start] = true;
        int[] budget = {40000};
        if (dfs(start, goal, n, used, buf, 1, minLen, maxLen, rng, budget)) {
            int len = 0;
            while (len < buf.length && (len == 0 || buf[len - 1] != goal)) {
                len++;
            }
            return java.util.Arrays.copyOf(buf, len);
        }
        return null;
    }

    private static boolean dfs(int cur, int goal, int n, boolean[] used, int[] buf, int len, int minLen, int maxLen,
            Random rng, int[] budget) {
        buf[len - 1] = cur;
        if (cur == goal) {
            return len >= minLen;
        }
        if (--budget[0] < 0) {
            return false;
        }
        Dir[] dirs = Dir.values().clone();
        for (int i = dirs.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Dir t = dirs[i];
            dirs[i] = dirs[j];
            dirs[j] = t;
        }
        for (Dir d : dirs) {
            int nb = neighbor(cur, d, n);
            if (nb < 0 || used[nb] || len + 1 + manhattan(nb, goal, n) > maxLen) {
                continue;
            }
            used[nb] = true;
            if (dfs(nb, goal, n, used, buf, len + 1, minLen, maxLen, rng, budget)) {
                return true;
            }
            used[nb] = false;
        }
        return false;
    }

    /** L shaped path: along the row then up the column (always exists, last resort). */
    static int[] shortestPath(int n, int start, int goal) {
        List<Integer> cells = new ArrayList<>();
        int x = start % n;
        int z = start / n;
        cells.add(start);
        int gx = goal % n;
        // move vertically first to the row below... keep the last step vertical so blood != entrance column trick
        while (x != gx) {
            x += Integer.signum(gx - x);
            cells.add(z * n + x);
        }
        while (z > goal / n) {
            z--;
            cells.add(z * n + x);
        }
        int[] out = new int[cells.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = cells.get(i);
        }
        return out;
    }

    // ------------------------------------------------------------------ rooms

    private static void computeDepth(Layout l) {
        int[] depth = new int[l.rooms().size()];
        java.util.Arrays.fill(depth, -1);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        depth[l.entrance().index] = 0;
        queue.add(l.entrance().index);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (int nb : l.room(cur).neighbors.values()) {
                if (depth[nb] < 0) {
                    depth[nb] = depth[cur] + 1;
                    queue.add(nb);
                }
            }
        }
        for (RoomPlan r : l.rooms()) {
            r.depth = Math.max(depth[r.index], 0);
        }
    }

    private static void assignTypes(Layout l, DungeonConfig.Floor f, Random rng) {
        RoomPlan entrance = l.entrance();
        RoomPlan blood = l.blood();
        RoomPlan boss = l.boss();
        entrance.type = RoomType.ENTRANCE;
        blood.type = RoomType.BLOOD;
        boss.type = RoomType.BOSS;

        List<RoomPlan> free = new ArrayList<>();
        for (RoomPlan r : l.rooms()) {
            if (r != entrance && r != blood && r != boss) {
                free.add(r);
            }
        }
        List<RoomPlan> leaves = new ArrayList<>();
        for (RoomPlan r : free) {
            if (r.degree() == 1) {
                leaves.add(r);
            }
        }
        Collections.shuffle(leaves, rng);

        // secret rooms: leaves only
        take(l, leaves, free, f.secretRooms(), RoomType.SECRET, false);
        // mini-bosses: the deepest leaves, then the deepest rooms
        leaves.sort((x, y) -> Integer.compare(y.depth, x.depth));
        take(l, leaves, free, f.miniBosses(), RoomType.MINIBOSS, false);
        List<RoomPlan> deep = new ArrayList<>(free);
        Collections.shuffle(deep, rng);
        deep.sort((x, y) -> Integer.compare(y.depth, x.depth));
        int missing = f.miniBosses() - (int) l.count(RoomType.MINIBOSS);
        for (int i = 0; i < deep.size() && missing > 0; i++) {
            RoomPlan r = deep.get(i);
            if (free.contains(r)) {
                r.type = RoomType.MINIBOSS;
                free.remove(r);
                missing--;
            }
        }
        // fairy and shop: leaf first, else any
        Collections.shuffle(leaves, rng);
        if (f.fairy()) {
            takeOne(free, leaves, RoomType.FAIRY, rng);
        }
        if (f.shop()) {
            takeOne(free, leaves, RoomType.SHOP, rng);
        }
        // puzzles and traps anywhere left
        List<RoomPlan> rest = new ArrayList<>(free);
        Collections.shuffle(rest, rng);
        PuzzleKind[] kinds = PuzzleKind.values();
        List<PuzzleKind> bag = new ArrayList<>();
        int puzzles = Math.min(f.puzzles(), Math.max(0, rest.size() - 1));
        for (int i = 0; i < puzzles; i++) {
            if (bag.isEmpty()) {
                bag.addAll(List.of(kinds));
                Collections.shuffle(bag, rng);
            }
            RoomPlan r = rest.remove(0);
            r.type = RoomType.PUZZLE;
            r.puzzle = bag.remove(0);
        }
        int traps = Math.min(f.traps(), Math.max(0, rest.size() - 1));
        for (int i = 0; i < traps; i++) {
            rest.remove(0).type = RoomType.TRAP;
        }
        // anything untouched stays MOB (default)
    }

    private static void take(Layout l, List<RoomPlan> leaves, List<RoomPlan> free, int count, RoomType type, boolean any) {
        // 'any' is reserved for callers that accept non-leaf rooms
        int done = 0;
        for (int i = 0; i < leaves.size() && done < count; ) {
            RoomPlan r = leaves.get(i);
            if (!free.contains(r)) {
                leaves.remove(i);
                continue;
            }
            r.type = type;
            free.remove(r);
            leaves.remove(i);
            done++;
        }
    }

    private static void takeOne(List<RoomPlan> free, List<RoomPlan> leaves, RoomType type, Random rng) {
        for (int i = 0; i < leaves.size(); i++) {
            RoomPlan r = leaves.get(i);
            if (free.contains(r)) {
                r.type = type;
                free.remove(r);
                leaves.remove(i);
                return;
            }
        }
        if (!free.isEmpty()) {
            RoomPlan r = free.remove(rng.nextInt(free.size()));
            r.type = type;
        }
    }

    // ------------------------------------------------------------------ doors

    private static void assignDoorKinds(Layout l, DungeonConfig.Floor f) {
        for (DoorPlan d : l.doors()) {
            RoomType ta = l.room(d.a).type;
            RoomType tb = l.room(d.b).type;
            if ((ta == RoomType.BLOOD && tb == RoomType.BOSS) || (ta == RoomType.BOSS && tb == RoomType.BLOOD)) {
                d.kind = DoorKind.BLOOD;
            } else if (ta == RoomType.SECRET || tb == RoomType.SECRET) {
                d.kind = DoorKind.SECRET;
            }
        }
        // wither door candidates: doors into mini-boss rooms, then doors along the path (not the first one)
        List<DoorPlan> candidates = new ArrayList<>();
        for (DoorPlan d : l.doors()) {
            if (d.kind == DoorKind.OPEN && (l.room(d.a).type == RoomType.MINIBOSS || l.room(d.b).type == RoomType.MINIBOSS)) {
                candidates.add(d);
            }
        }
        List<Integer> path = l.path();
        for (int i = 2; i < path.size() - 2; i++) {
            DoorPlan d = l.doorBetween(path.get(i - 1), path.get(i));
            if (d != null && d.kind == DoorKind.OPEN && !candidates.contains(d)) {
                candidates.add(d);
            }
        }
        List<DoorPlan> chosen = new ArrayList<>();
        for (int i = 0; i < candidates.size() && chosen.size() < f.witherDoors(); i++) {
            chosen.add(candidates.get(i));
        }
        for (DoorPlan d : chosen) {
            d.kind = DoorKind.WITHER;
        }
        // make it solvable: a key source must be reachable before each wither door
        List<DoorPlan> opened = new ArrayList<>();
        List<RoomPlan> carriers = new ArrayList<>();
        int guard = 0;
        while (guard++ < 64) {
            final List<DoorPlan> open = opened;
            boolean[] seen = l.reachable(l.entrance().index,
                d -> d.kind != DoorKind.BLOOD && (d.kind != DoorKind.WITHER || open.contains(d)));
            DoorPlan next = null;
            for (DoorPlan d : l.doors()) {
                if (d.kind == DoorKind.WITHER && !opened.contains(d) && (seen[d.a] || seen[d.b])) {
                    next = d;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            int available = 0;
            for (RoomPlan c : carriers) {
                if (seen[c.index]) {
                    available++;
                }
            }
            if (available - opened.size() < 1) {
                RoomPlan source = null;
                for (RoomPlan r : l.rooms()) {
                    if (seen[r.index] && r.type == RoomType.MOB && !r.keyCarrier) {
                        source = r;
                        break;
                    }
                }
                if (source == null) {
                    next.kind = DoorKind.OPEN; // no way to pay for it
                    continue;
                }
                source.keyCarrier = true;
                carriers.add(source);
            }
            opened.add(next);
        }
    }

    private static void assignSecrets(Layout l, DungeonConfig.Floor f, Random rng) {
        for (RoomPlan r : l.rooms()) {
            r.secrets = switch (r.type) {
                case MOB -> rng.nextInt(100) < 55 + f.number() * 4 ? 1 + (f.number() >= 5 && rng.nextBoolean() ? 1 : 0) : 0;
                case TRAP -> 1;
                case SECRET -> f.number() >= 3 ? 2 : 1;
                case BLOOD -> 1;
                case MINIBOSS -> rng.nextInt(100) < 35 ? 1 : 0;
                default -> 0;
            };
        }
    }
}
