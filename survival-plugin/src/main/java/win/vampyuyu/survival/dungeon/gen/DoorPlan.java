package win.vampyuyu.survival.dungeon.gen;

/**
 * A doorway between room {@code a} and room {@code b}. Normalised so that {@code b} lies east or south of {@code a}
 * ({@link #dir} is EAST or SOUTH, seen from a).
 */
public final class DoorPlan {
    public final int a;
    public final int b;
    public final Dir dir;
    public DoorKind kind;

    DoorPlan(int a, int b, Dir dir, DoorKind kind) {
        this.a = a;
        this.b = b;
        this.dir = dir;
        this.kind = kind;
    }

    public int other(int room) { return room == a ? b : a; }

    public boolean touches(int room) { return room == a || room == b; }

    @Override
    public String toString() { return "Door(" + a + "-" + b + " " + kind + ")"; }
}
