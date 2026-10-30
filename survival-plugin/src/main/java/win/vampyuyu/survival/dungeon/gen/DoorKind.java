package win.vampyuyu.survival.dungeon.gen;

/** How a doorway between two rooms behaves. */
public enum DoorKind {
    /** Open, but locked while a mob room or mini-boss fight is running. */
    OPEN,
    /** Needs a wither key. */
    WITHER,
    /** Between the blood room and the boss room: opens once the Watcher's guards are dead. */
    BLOOD,
    /** Looks like a wall, opens with a hidden lever in the neighbouring room. */
    SECRET
}
