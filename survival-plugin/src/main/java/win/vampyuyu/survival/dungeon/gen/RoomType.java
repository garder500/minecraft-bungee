package win.vampyuyu.survival.dungeon.gen;

/** What a room is for. */
public enum RoomType {
    ENTRANCE("Entrance"),
    MOB("Mob room"),
    TRAP("Parkour trap"),
    PUZZLE("Puzzle room"),
    SECRET("Secret room"),
    MINIBOSS("Mini-boss room"),
    BLOOD("Blood room"),
    FAIRY("Fairy grotto"),
    SHOP("Merchant"),
    BOSS("Boss room");

    private final String title;

    RoomType(String title) {
        this.title = title;
    }

    public String title() { return title; }
}
