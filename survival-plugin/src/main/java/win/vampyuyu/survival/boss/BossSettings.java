package win.vampyuyu.survival.boss;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Global knobs from the {@code settings:} section of boss.yml.
 *
 * @param hpScale multiplies every boss health pool
 * @param damageScale multiplies every hit a boss deals to a player
 * @param incomingScale multiplies every hit a player deals to a boss (calibrates the items feature's damage numbers)
 * @param tickPeriod ticks between two boss updates
 * @param barRange players closer than this to a boss see its bar
 * @param visualHealth max health attribute of the entity (it only mirrors the percentage)
 * @param hitIframes invulnerability ticks of a boss between two hits
 * @param magicFindCap Magic Find above this is ignored
 * @param lootPickupSeconds how long dropped overflow loot stays reserved for its owner (informational)
 * @param purgeOrphans remove tagged boss entities that have no live session when their chunk loads
 */
public record BossSettings(double hpScale, double damageScale, double incomingScale, int tickPeriod, double barRange,
        double visualHealth, int hitIframes, double magicFindCap, boolean purgeOrphans) {

    public static BossSettings defaults() {
        return parse(null);
    }

    public static BossSettings parse(@Nullable ConfigurationSection s) {
        if (s == null) {
            return new BossSettings(1, 1, 1, 2, 64, 300, 6, 1000, true);
        }
        return new BossSettings(Math.max(0.0001, s.getDouble("hp-scale", 1)),
            Math.max(0, s.getDouble("damage-scale", 1)), Math.max(0.0001, s.getDouble("incoming-damage-scale", 1)),
            Math.max(1, Math.min(10, s.getInt("tick-period", 2))), Math.max(8, s.getDouble("bar-range", 64)),
            Math.max(20, Math.min(1024, s.getDouble("visual-health", 300))), Math.max(0, s.getInt("hit-iframes", 6)),
            Math.max(0, s.getDouble("magic-find-cap", 1000)), s.getBoolean("purge-orphans", true));
    }
}
