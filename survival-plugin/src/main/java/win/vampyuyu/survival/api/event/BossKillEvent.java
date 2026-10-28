package win.vampyuyu.survival.api.event;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Fired on the main thread when a boss dies. Fired by the boss feature, consumed by anyone. */
public final class BossKillEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    /** One participant and the damage they dealt. */
    public record Damager(UUID player, double damage) {}

    private final String bossId;
    private final @Nullable Player killer;
    private final List<Damager> topDamagers;
    private final Location location;

    /**
     * @param topDamagers participants sorted by damage, highest first
     * @param killer who landed the last hit, null when the boss died of something else
     */
    public BossKillEvent(String bossId, @Nullable Player killer, List<Damager> topDamagers, Location location) {
        this.bossId = bossId;
        this.killer = killer;
        this.topDamagers = List.copyOf(topDamagers);
        this.location = location.clone();
    }

    public String getBossId() { return bossId; }

    public @Nullable Player getKiller() { return killer; }

    /** Participants sorted by damage, highest first (immutable). */
    public List<Damager> getTopDamagers() { return topDamagers; }

    /** Where the boss died (copy). */
    public Location getLocation() { return location.clone(); }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
