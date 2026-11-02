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
    private final long shareKey;
    private final java.util.Set<java.util.UUID> sharedWith;

    private static final java.util.concurrent.atomic.AtomicLong SHARE_KEYS = new java.util.concurrent.atomic.AtomicLong();

    public QuarryProgressEvent(Player player, String kind, String subject, int amount) {
        this(player, kind, subject, amount, 0, java.util.Set.of());
    }

    private QuarryProgressEvent(Player player, String kind, String subject, int amount, long shareKey,
            java.util.Set<java.util.UUID> sharedWith) {
        this.player = player;
        this.kind = kind;
        this.subject = subject;
        this.amount = amount;
        this.shareKey = shareKey;
        this.sharedWith = sharedWith;
    }

    /**
     * One event per player for a thing the whole island did (a machine mined an ore): every event of the batch carries
     * the same key and the batch's players, so the island's shared quests count it once and credit every player.
     */
    public static java.util.List<QuarryProgressEvent> fanOut(java.util.Collection<? extends Player> players, String kind,
            String subject, int amount) {
        long key = SHARE_KEYS.incrementAndGet();
        java.util.Set<java.util.UUID> ids = new java.util.LinkedHashSet<>();
        players.forEach(p -> ids.add(p.getUniqueId()));
        java.util.Set<java.util.UUID> shared = java.util.Collections.unmodifiableSet(ids);
        java.util.List<QuarryProgressEvent> out = new java.util.ArrayList<>();
        for (Player p : players) {
            out.add(new QuarryProgressEvent(p, kind, subject, amount, key, shared));
        }
        return out;
    }

    /** 0 for an event of one player; otherwise the key shared by the events of one fan-out. */
    public long getShareKey() { return shareKey; }

    /** The players the fan-out reached (empty for an event of one player). */
    public java.util.Set<java.util.UUID> getSharedWith() { return sharedWith; }

    public Player getPlayer() { return player; }

    public String getKind() { return kind; }

    public String getSubject() { return subject; }

    public int getAmount() { return amount; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
