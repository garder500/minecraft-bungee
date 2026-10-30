package win.vampyuyu.survival.island;

/**
 * The permission matrix: which role may do what on an island. Pure, no Bukkit calls.
 * <ul>
 *   <li>Co-op (owner, co-owner, member): everything.</li>
 *   <li>Trusted guest: build, interact and handle animals; containers only when the island allows chests for guests
 *       (flag {@code visitor_chests}, or the server-wide {@code trusted-containers} option).</li>
 *   <li>Visitor: nothing, except doors/buttons ({@code visitor_interact}) and containers ({@code visitor_chests}).</li>
 *   <li>Banned: nothing.</li>
 * </ul>
 */
public final class IslandAccess {
    /** What a block, item or entity interaction needs. */
    public enum Action {
        /** Break, place, buckets, armor stands, item frames, boats, farmland trampling... */
        BUILD,
        /** Doors, buttons, levers, crafting tables, beds, villagers... */
        INTERACT,
        /** Chests, barrels, furnaces, hoppers, shulker boxes, storage minecarts... */
        CONTAINER,
        /** Hurting, breeding, shearing, milking and leashing passive mobs. */
        ANIMAL
    }

    /** Management abilities (commands and menus). */
    public enum Cap {
        SETHOME, INVITE, KICK, BAN, TRUST, SETTINGS, UPGRADE, LOCK, WARP, SETROLE, TRANSFER, RESET
    }

    private IslandAccess() {}

    public static boolean can(Island island, IslandRole role, Action action, boolean trustedContainers) {
        return switch (role) {
            case OWNER, CO_OWNER, MEMBER -> true;
            case TRUSTED -> switch (action) {
                case BUILD, INTERACT, ANIMAL -> true;
                case CONTAINER -> trustedContainers || island.flag(IslandFlag.VISITOR_CHESTS);
            };
            case VISITOR -> switch (action) {
                case INTERACT -> island.flag(IslandFlag.VISITOR_INTERACT);
                case CONTAINER -> island.flag(IslandFlag.VISITOR_CHESTS);
                default -> false;
            };
            case BANNED -> false;
        };
    }

    public static boolean has(IslandRole role, Cap cap) {
        return switch (role) {
            case OWNER -> true;
            case CO_OWNER -> cap != Cap.SETROLE && cap != Cap.TRANSFER && cap != Cap.RESET;
            default -> false;
        };
    }

    /** Whether {@code actor} may remove or restrict {@code target} (kick, ban): strictly higher in rank. */
    public static boolean outranks(IslandRole actor, IslandRole target) {
        return actor.ordinal() < target.ordinal();
    }
}
