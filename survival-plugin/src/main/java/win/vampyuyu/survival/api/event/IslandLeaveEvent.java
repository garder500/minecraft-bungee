package win.vampyuyu.survival.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import win.vampyuyu.survival.api.IslandInfo;

/** Fired on the main thread when a player walks out of an island's area. */
public final class IslandLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final IslandInfo island;

    public IslandLeaveEvent(Player player, IslandInfo island) {
        this.player = player;
        this.island = island;
    }

    public Player getPlayer() { return player; }

    public IslandInfo getIsland() { return island; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
