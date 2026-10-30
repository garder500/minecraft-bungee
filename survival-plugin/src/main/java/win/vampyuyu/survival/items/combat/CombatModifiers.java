package win.vampyuyu.survival.items.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/** Hooks for features of the items package (pets, set bonuses, abilities) that bend the damage rules. */
public interface CombatModifiers {
    /** Additive fraction added to the damage a player deals to {@code target} (0.1 = +10%). */
    default double outgoingBonus(Player attacker, LivingEntity target) { return 0; }

    /** Multiplier on the damage a player is about to take (1 = unchanged, 0.5 = halved). */
    default double incomingMultiplier(Player victim, EntityDamageEvent event) { return 1; }

    /** Called when the damage would kill the player. Return true to cancel the damage (you must keep them alive). */
    default boolean preventDeath(Player victim, EntityDamageEvent event, double damage) { return false; }

    /** Called after a player took damage from {@code attacker} (may be null for the environment). */
    default void onHurt(Player victim, Entity attacker, double finalDamage) { }

    /** Called after a player dealt {@code damage} to {@code target}. */
    default void onHit(Player attacker, LivingEntity target, double damage) { }
}
