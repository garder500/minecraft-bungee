package win.vampyuyu.survival.api.event;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a coin balance changed. Informational (not cancellable). The economy API is thread safe, so
 * this event is asynchronous when it is fired off the main thread: check {@link #isAsynchronous()}.
 */
public final class CoinsChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final double delta;
    private final double newBalance;
    private final String reason;

    public CoinsChangeEvent(UUID player, double delta, double newBalance, String reason) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.delta = delta;
        this.newBalance = newBalance;
        this.reason = reason;
    }

    /** Whose balance changed. */
    public UUID getPlayer() { return player; }

    /** Signed change: positive for a gain, negative for a spend. */
    public double getDelta() { return delta; }

    /** Balance after the change. */
    public double getNewBalance() { return newBalance; }

    /** Cause given by the caller, e.g. "shop:buy". */
    public String getReason() { return reason; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
