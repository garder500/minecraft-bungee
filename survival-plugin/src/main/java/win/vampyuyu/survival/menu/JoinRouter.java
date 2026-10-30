package win.vampyuyu.survival.menu;

import java.util.Optional;

/**
 * Pure decision of where a joining player goes. No Bukkit calls, so every rule is unit tested.
 *
 * <ol>
 *   <li>A player inside a dungeon run is never moved (the dungeon feature owns them).</li>
 *   <li>No key, a stale key or a key for a dungeon the player is not in: a normal join, so the player is sent to
 *       the hub ({@link NoKey#HUB}, the design contract section 12.3 and the default) unless they already stand in a
 *       Skyblock world. {@link NoKey#STAY} (leave them where the server put them) is an opt-in.</li>
 *   <li>A Skyblock key: island if the player has one, else the hub with the create-island prompt.</li>
 * </ol>
 */
public final class JoinRouter {
    /** What to do with a joining player who has no usable key. */
    public enum NoKey { HUB, STAY }

    /** The outcome. */
    public enum Action {
        /** Leave the player where the server put them. */
        STAY,
        SURVIVAL,
        /** Teleport to their island. */
        ISLAND,
        /** Teleport to the hub. */
        HUB,
        /** Teleport to the hub and show the create-island prompt (the player has no island). */
        HUB_CREATE_PROMPT
    }

    public record Decision(Action action, String reason) {}

    /** Everything the decision depends on. */
    public record Context(Optional<HandoffPayload> payload, long nowMillis, long maxAgeMillis, boolean hasIsland,
            boolean inDungeon, boolean inSkyblockWorld, NoKey noKey) {}

    private JoinRouter() {}

    public static Decision decide(Context ctx) {
        Optional<HandoffPayload> payload = ctx.payload().filter(p -> !p.isStale(ctx.nowMillis(), ctx.maxAgeMillis()));
        if (payload.isEmpty()) return new Decision(Action.STAY, "no current explicit mode selection");
        return switch (payload.get().dest()) {
            case SURVIVAL -> new Decision(Action.SURVIVAL, "explicit Survival selection");
            case DUNGEON -> new Decision(Action.STAY, "retired destination");
            default -> new Decision(Action.ISLAND, "explicit Skyblock selection");
        };
    }
}
