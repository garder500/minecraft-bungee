package win.vampyuyu.survival.boss;

import java.util.Map;

/** Pure rules of {@link BossStatus}: what the active statuses of one player add up to. */
final class StatusMath {
    /** The most a status may slow a player who is not rooted. */
    static final double MAX_SLOW = 0.9;

    private StatusMath() {}

    /** One active status: its level (1 = the weakest) and the server tick it runs out on. */
    record Active(int level, long until, double dps) {
        boolean live(long now) {
            return now < until;
        }
    }

    /**
     * The movement speed multiplier modifier (MULTIPLY_SCALAR_1 amount, 0 = none, -1 = no movement): the strongest status
     * wins (statuses do not stack, like potions did), a root stops the player.
     */
    static double speed(Map<StatusKind, Active> active, long now) {
        double slow = 0;
        for (Map.Entry<StatusKind, Active> e : active.entrySet()) {
            if (!e.getValue().live(now)) {
                continue;
            }
            if (e.getKey().noJump() && e.getKey().slowPerLevel() >= 1.0) {
                return -1.0;
            }
            slow = Math.max(slow, Math.min(MAX_SLOW, e.getKey().slowPerLevel() * Math.max(1, e.getValue().level())));
        }
        return -slow;
    }

    /** The gravity modifier (ADD_NUMBER): the strongest lift wins over a float, nothing else touches gravity. */
    static double gravity(Map<StatusKind, Active> active, long now) {
        double g = 0;
        for (Map.Entry<StatusKind, Active> e : active.entrySet()) {
            if (e.getValue().live(now)) {
                g = Math.min(g, e.getKey().gravity());
            }
        }
        return g;
    }

    static boolean noJump(Map<StatusKind, Active> active, long now) {
        return active.entrySet().stream().anyMatch(e -> e.getValue().live(now) && e.getKey().noJump());
    }

    /**
     * The damage of one tick (once a second) of a damage-over-time status for a player at {@code health}: a status that is
     * not lethal never takes the last health point.
     */
    static double tickDamage(StatusKind kind, double dps, double health) {
        if (dps <= 0 || health <= 0) {
            return 0;
        }
        if (kind.lethal()) {
            return dps;
        }
        return Math.max(0, Math.min(dps, health - 1.0));
    }

    /** A new application against an older one: the longer and the stronger is kept (a refresh never shortens). */
    static Active merge(Active old, Active fresh, long now) {
        if (old == null || !old.live(now)) {
            return fresh;
        }
        return new Active(Math.max(old.level(), fresh.level()), Math.max(old.until(), fresh.until()), Math.max(old.dps(), fresh.dps()));
    }
}
