package win.vampyuyu.survival.api.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the main thread for each player who is paid for a cleared dungeon run. */
public final class DungeonClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final int floor;
    private final long seed;

    public DungeonClearEvent(UUID player, int floor, long seed) {
        this.player = player;
        this.floor = floor;
        this.seed = seed;
    }

    public UUID player() {
        return player;
    }

    public int floor() {
        return floor;
    }

    /** The seed the run was generated from: the way to tell a given run (e.g. a Fracture) from the others. */
    public long seed() {
        return seed;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
