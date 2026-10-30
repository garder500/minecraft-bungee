package win.vampyuyu.survival.island;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** "Type it again to confirm" for destructive actions: armed for a short time, consumed once. Main thread. */
public final class Confirms {
    private final LongSupplier clock;
    private final Map<String, Long> armed = new HashMap<>();

    public Confirms() {
        this(System::currentTimeMillis);
    }

    public Confirms(LongSupplier clock) {
        this.clock = clock;
    }

    private static String key(UUID player, String action, String argument) {
        return player + "|" + action + "|" + (argument == null ? "" : argument);
    }

    /** Arms the confirmation for {@code millis}. */
    public void arm(UUID player, String action, String argument, long millis) {
        armed.put(key(player, action, argument), clock.getAsLong() + millis);
    }

    /** True (once) when the action was armed and has not expired. */
    public boolean consume(UUID player, String action, String argument) {
        Long until = armed.remove(key(player, action, argument));
        return until != null && until >= clock.getAsLong();
    }

    public void clear(UUID player) {
        armed.keySet().removeIf(k -> k.startsWith(player.toString()));
    }
}
