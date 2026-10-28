package win.vampyuyu.survival.wings;

/** Profile section {@code wings} of a player. Plain Gson class: public fields with defaults. */
public final class WingsData {
    /** True once the player was given Wings through {@code grantOnce}, an admin or a first bind. Never reset by claims. */
    public boolean received;
    /** Who granted the Wings first, e.g. "boss:voidwyrm" (support only). */
    public String source = "";
    /** Epoch millis of the first grant. */
    public long grantedAt;
    /** UUID of the only Wings item that works. Every other copy is inert and is removed when seen. */
    public String activeId = "";
    /** Wings level, 1 to max-level. Authoritative: the item is rebuilt to match it. */
    public int level = 1;
    /** Epoch millis of the last /wings claim. */
    public long lastClaim;
    public int claims;
    /** A world copy was destroyed or kept from the player: hand a fresh copy back on the next safe moment. */
    public boolean pendingReturn;
    /** Flight energy when the player left (-1 = full). */
    public double energy = -1;
    /** Total number of Wing Cores consumed (statistics). */
    public int coresUsed;
    // Preferences.
    public boolean trail = true;
    public boolean bar = true;
    public boolean doubleJump = true;
    public boolean sounds = true;
    /** Set to true after the preference defaults of the config were applied once. */
    public boolean prefsInitialised;
}
