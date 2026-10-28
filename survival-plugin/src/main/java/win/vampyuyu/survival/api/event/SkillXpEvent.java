package win.vampyuyu.survival.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import win.vampyuyu.survival.api.Skill;

/** Fired on the main thread before skill experience is granted. Listeners may change or cancel the amount. */
public final class SkillXpEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Skill skill;
    private final String source;
    private double amount;
    private boolean cancelled;

    public SkillXpEvent(Player player, Skill skill, double amount, String source) {
        this.player = player;
        this.skill = skill;
        this.amount = amount;
        this.source = source;
    }

    public Player getPlayer() { return player; }

    public Skill getSkill() { return skill; }

    /** Cause given to {@code SkillsApi.addXp}. */
    public String getSource() { return source; }

    public double getAmount() { return amount; }

    /** Changes the amount that will be granted (negative values are clamped to 0 by the skills feature). */
    public void setAmount(double amount) { this.amount = amount; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
