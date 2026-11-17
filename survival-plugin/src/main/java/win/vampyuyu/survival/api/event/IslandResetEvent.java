package win.vampyuyu.survival.api.event;

import java.util.Set;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread when an island is reset (same island id, rebuilt from its template). Features drop the
 * progress they keep for the island (its shared quests...): a reset starts over.
 */
public final class IslandResetEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID island;
    private final Set<UUID> members;

    public IslandResetEvent(UUID island, Set<UUID> members) {
        this.island = island;
        this.members = Set.copyOf(members);
    }

    public UUID getIslandId() { return island; }

    /** Owner and members at the time of the reset. */
    public Set<UUID> getMembers() { return members; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
