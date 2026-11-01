package win.vampyuyu.survival.api.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread when an island is deleted (by its owner, when its owner joins another island, or by an
 * administrator), after it left the registry. Features drop what they kept for it (machines...).
 */
public final class IslandDeleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID island;

    public IslandDeleteEvent(UUID island) {
        this.island = island;
    }

    public UUID getIslandId() { return island; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
