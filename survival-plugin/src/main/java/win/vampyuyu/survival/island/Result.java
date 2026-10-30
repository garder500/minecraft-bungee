package win.vampyuyu.survival.island;

/** Outcome of a player action: a message for the chat and whether it worked. */
public record Result(boolean ok, String message) {
    public static Result ok(String message) {
        return new Result(true, message);
    }

    public static Result fail(String message) {
        return new Result(false, message);
    }
}
