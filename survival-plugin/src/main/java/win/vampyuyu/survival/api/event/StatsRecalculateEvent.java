package win.vampyuyu.survival.api.event;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import win.vampyuyu.survival.api.Stat;

/**
 * Fired on the main thread while a player's stat totals are recomputed, before they are cached.
 * A listener may adjust {@link #totals()} for last-minute effects (potions, set bonuses...).
 */
public final class StatsRecalculateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Map<Stat, Double> totals;

    public StatsRecalculateEvent(Player player, Map<Stat, Double> totals) {
        this.player = player;
        this.totals = new EnumMap<>(Stat.class);
        this.totals.putAll(totals);
    }

    public Player getPlayer() { return player; }

    /** Mutable totals (base + modifiers) that will become the player's stats. */
    public Map<Stat, Double> totals() { return totals; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
