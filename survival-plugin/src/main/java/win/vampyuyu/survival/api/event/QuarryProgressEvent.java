package win.vampyuyu.survival.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread when a player does something the quests may count in the Quarry: finds an ore in a
 * generator ({@code ORE_FOUND}, subject = ore id), collects a forged item ({@code FORGE_ITEM}, subject = item id) or
 * upgrades the generator ({@code GENERATOR_UPGRADE}, subject = the new tier). The quest feature maps {@code kind} to
 * its quest type by name, so the two features never import each other.
 */
public final class QuarryProgressEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String kind;
    private final String subject;
    private final int amount;

    public QuarryProgressEvent(Player player, String kind, String subject, int amount) {
        this.player = player;
        this.kind = kind;
        this.subject = subject;
        this.amount = amount;
    }

    public Player getPlayer() { return player; }

    public String getKind() { return kind; }

    public String getSubject() { return subject; }

    public int getAmount() { return amount; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
