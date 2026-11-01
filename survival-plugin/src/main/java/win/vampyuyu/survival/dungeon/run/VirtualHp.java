package win.vampyuyu.survival.dungeon.run;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

/**
 * The virtual health pool of a dungeon monster, the same idea as the bosses': the monster has far more health than a
 * vanilla entity can hold, so the real number lives in its data ({@link Keys#hpMax}, {@link Keys#hp}) and the entity's
 * own health only mirrors the percentage. A hit takes its real damage from the pool, so the number a player reads on the
 * damage indicator is the number that was really dealt.
 */
final class VirtualHp {
    private VirtualHp() {}

    /** Real health attribute of a pooled monster; the pool is mirrored onto it. */
    static final double VISUAL = 100;

    /** Pure: the damage the entity itself should take so that it mirrors a pool of {@code left} out of {@code max}. */
    static double mirrored(double left, double max, double entityMax, double entityHealth) {
        if (left <= 0) {
            return entityHealth + entityMax * 4; // lethal whatever the mitigation
        }
        double wanted = Math.max(0.01, left / max * entityMax);
        return Math.max(0.01, entityHealth - wanted);
    }

    /**
     * Takes {@code dealt} (the real damage of the hit) from the pool of {@code victim} and returns the damage the event
     * should carry. Returns {@code dealt} unchanged for a monster without a pool.
     */
    static double hit(Keys keys, LivingEntity victim, double dealt) {
        Double now = keys.hpOf(victim);
        if (now == null) {
            return dealt;
        }
        double left = Math.max(0, now - Math.max(0, dealt));
        keys.setHp(victim, left);
        AttributeInstance attribute = victim.getAttribute(Attribute.MAX_HEALTH);
        double entityMax = attribute == null ? VISUAL : attribute.getValue();
        return mirrored(left, keys.hpMaxOf(victim), entityMax, victim.getHealth());
    }

    /** Puts the entity's own health back in line with the pool (after the damage event has been applied). */
    static void sync(Keys keys, LivingEntity victim) {
        Double left = keys.hpOf(victim);
        Double max = keys.hpMaxOf(victim);
        if (left == null || max == null || left <= 0 || !victim.isValid()) {
            return;
        }
        AttributeInstance attribute = victim.getAttribute(Attribute.MAX_HEALTH);
        double entityMax = attribute == null ? VISUAL : attribute.getValue();
        victim.setHealth(Math.max(0.01, Math.min(entityMax, left / max * entityMax)));
    }
}
