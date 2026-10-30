package win.vampyuyu.survival.dungeon.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The abstract plan of one floor: rooms on a grid, the doorways between them and the main path. */
public final class Layout {
    public final int floor;
    public final long seed;
    public final int size;
    private final List<RoomPlan> rooms;
    private final List<DoorPlan> doors;
    private final int[] cellToRoom;
    private final List<Integer> path;
    private final int entrance;
    private final int blood;
    private final int boss;

    Layout(int floor, long seed, int size, List<RoomPlan> rooms, List<DoorPlan> doors, int[] cellToRoom,
            List<Integer> path, int entrance, int blood, int boss) {
        this.floor = floor;
        this.seed = seed;
        this.size = size;
        this.rooms = rooms;
        this.doors = doors;
        this.cellToRoom = cellToRoom;
        this.path = path;
        this.entrance = entrance;
        this.blood = blood;
        this.boss = boss;
    }

    public List<RoomPlan> rooms() { return Collections.unmodifiableList(rooms); }

    public List<DoorPlan> doors() { return Collections.unmodifiableList(doors); }

    /** Room indexes from the entrance to the boss room (blood room second to last). */
    public List<Integer> path() { return Collections.unmodifiableList(path); }

    public RoomPlan room(int index) { return rooms.get(index); }

    public RoomPlan entrance() { return rooms.get(entrance); }

    public RoomPlan blood() { return rooms.get(blood); }

    public RoomPlan boss() { return rooms.get(boss); }

    /** Room at the grid cell, or null when the cell is empty or outside the grid. */
    public RoomPlan roomAt(int cx, int cz) {
        if (cx < 0 || cz < 0 || cx >= size || cz >= size) {
            return null;
        }
        int i = cellToRoom[cz * size + cx];
        return i < 0 ? null : rooms.get(i);
    }

    public DoorPlan doorBetween(int a, int b) {
        for (DoorPlan d : doors) {
            if ((d.a == a && d.b == b) || (d.a == b && d.b == a)) {
                return d;
            }
        }
        return null;
    }

    /** Doors touching a room. */
    public List<DoorPlan> doorsOf(int room) {
        List<DoorPlan> out = new ArrayList<>();
        for (DoorPlan d : doors) {
            if (d.touches(room)) {
                out.add(d);
            }
        }
        return out;
    }

    public long count(RoomType type) {
        return rooms.stream().filter(r -> r.type == type).count();
    }

    /** Secrets to find: hidden chests plus one hidden lever per secret room. */
    public int totalSecrets() {
        int n = 0;
        for (RoomPlan r : rooms) {
            n += r.secrets;
            if (r.type == RoomType.SECRET) {
                n++;
            }
        }
        return n;
    }

    public int puzzleCount() { return (int) count(RoomType.PUZZLE); }

    /** Rooms that must be cleared of mobs for a full clear (mob rooms, mini-bosses). */
    public int fightRoomCount() {
        return (int) rooms.stream().filter(r -> r.type == RoomType.MOB || r.type == RoomType.MINIBOSS).count();
    }

    /** Rooms reachable from {@code from} through doors accepted by {@code passable}. */
    public boolean[] reachable(int from, java.util.function.Predicate<DoorPlan> passable) {
        boolean[] seen = new boolean[rooms.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen[from] = true;
        queue.add(from);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (DoorPlan d : doors) {
                if (d.touches(cur) && passable.test(d)) {
                    int o = d.other(cur);
                    if (!seen[o]) {
                        seen[o] = true;
                        queue.add(o);
                    }
                }
            }
        }
        return seen;
    }

    /**
     * Simulates a perfect player: opens secret doors, spends one wither key per wither door, earns a key from every
     * key-carrier room reached, and finally needs the blood door. True when the boss room can be reached.
     */
    public boolean solvable() {
        int keys = 0;
        boolean[] opened = new boolean[doors.size()];
        boolean[] spent = new boolean[rooms.size()];
        for (int guard = 0; guard < doors.size() + 2; guard++) {
            final boolean[] open = opened;
            boolean[] seen = reachable(entrance, d -> d.kind != DoorKind.BLOOD
                && (d.kind != DoorKind.WITHER || open[doors.indexOf(d)]));
            int earned = 0;
            for (RoomPlan r : rooms) {
                if (seen[r.index] && r.keyCarrier) {
                    earned++;
                }
            }
            keys = earned - countTrue(opened);
            boolean progress = false;
            if (keys > 0) {
                for (int i = 0; i < doors.size(); i++) {
                    DoorPlan d = doors.get(i);
                    if (d.kind == DoorKind.WITHER && !opened[i] && (seen[d.a] || seen[d.b])) {
                        opened[i] = true;
                        progress = true;
                        break;
                    }
                }
            }
            if (!progress) {
                RoomPlan b = rooms.get(blood);
                return seen[b.index] && boss >= 0 && doorBetween(blood, boss) != null;
            }
        }
        return false;
    }

    private static int countTrue(boolean[] a) {
        int n = 0;
        for (boolean b : a) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /** ASCII sketch for logs and tests. */
    public String ascii() {
        StringBuilder sb = new StringBuilder();
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                RoomPlan r = roomAt(x, z);
                sb.append(r == null ? '.' : switch (r.type) {
                    case ENTRANCE -> 'E';
                    case MOB -> 'm';
                    case TRAP -> 't';
                    case PUZZLE -> 'p';
                    case SECRET -> 's';
                    case MINIBOSS -> 'M';
                    case BLOOD -> 'r';
                    case FAIRY -> 'f';
                    case SHOP -> '$';
                    case BOSS -> 'B';
                });
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
