package win.vampyuyu.survival.menu;

import java.util.Optional;

/**
 * Pure decision of where a joining player goes. No Bukkit calls, so every rule is unit tested.
 *
 * <ol>
 *   <li>A player inside a dungeon run is never moved (the dungeon feature owns them).</li>
 *   <li>No key, a stale key or a key for a dungeon the player is not in: the player is not moved
 *       ({@link NoKey#STAY}, the default), or sent to the hub ({@link NoKey#HUB}, the design doc's original rule)
 *       unless they already stand in a Skyblock world.</li>
 *   <li>A Skyblock key: island if the player has one, else the hub with the create-island prompt.</li>
 * </ol>
 */
public final class JoinRouter {
    /** What to do with a joining player who has no usable key. */
    public enum NoKey { STAY, HUB }

    /** The outcome. */
    public enum Action {
        /** Leave the player where the server put them. */
        STAY,
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
        if (ctx.inDungeon()) {
            return new Decision(Action.STAY, "player is in a dungeon run");
        }
        Optional<HandoffPayload> payload = ctx.payload().filter(p -> !p.isStale(ctx.nowMillis(), ctx.maxAgeMillis()));
        if (payload.isEmpty()) {
            String why = ctx.payload().isPresent() ? "stale handoff key" : "no handoff key";
            if (ctx.noKey() == NoKey.HUB && !ctx.inSkyblockWorld()) {
                return hub(ctx, why + ", no-key=hub");
            }
            return new Decision(Action.STAY, why);
        }
        HandoffPayload p = payload.get();
        switch (p.dest()) {
            case DUNGEON:
                return new Decision(Action.STAY, "dungeon key but the player is not in a run");
            case HUB:
                return hub(ctx, "hub key");
            case ISLAND:
                return home(ctx, "island key");
            case SKYBLOCK:
            default:
                if (p.arg().equals("hub")) {
                    return hub(ctx, "skyblock key, arg hub");
                }
                return home(ctx, "skyblock key");
        }
    }

    private static Decision hub(Context ctx, String why) {
        return new Decision(ctx.hasIsland() ? Action.HUB : Action.HUB_CREATE_PROMPT, why);
    }

    private static Decision home(Context ctx, String why) {
        return ctx.hasIsland() ? new Decision(Action.ISLAND, why)
            : new Decision(Action.HUB_CREATE_PROMPT, why + ", no island yet");
    }
}
