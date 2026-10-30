package win.vampyuyu.survival.dungeon.gen;

/** The puzzles a {@link RoomType#PUZZLE} room can hold. */
public enum PuzzleKind {
    LEVER_SEQUENCE("Echo Levers"),
    PLATE_ORDER("Pressure Plates"),
    TIC_TAC_TOE("Crypt Tic-Tac-Toe"),
    WEIRDOS("Three Weirdos"),
    BOULDER_PATH("Boulder Path");

    private final String title;

    PuzzleKind(String title) {
        this.title = title;
    }

    public String title() { return title; }
}
