package win.vampyuyu.survival.items.combat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.stats.PlayerState;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Shared combat helpers: scripted damage (abilities, ferocity, cleave, reflected damage) that bypasses the melee
 * formula, and the modifier hook registry.
 */
public final class CombatService {
    /** A damage number that the next damage event of {@code victim} must use as is. */
    record Pending(UUID victim, double amount, boolean crit, boolean ability) {}

    private final StatsEngine engine;
    private final List<CombatModifiers> modifiers = new CopyOnWriteArrayList<>();
    volatile Pending pending;
    volatile boolean raw;

    public CombatService(StatsEngine engine) {
        this.engine = engine;
    }

    public void addModifiers(CombatModifiers hook) { modifiers.add(hook); }

    List<CombatModifiers> modifiers() { return modifiers; }

    /**
     * Deals scripted damage as {@code attacker}: the number is final (no strength / crit / enchants), only the
     * victim's own defense still applies. Shows an indicator through the normal damage listener.
     */
    public void strike(Player attacker, LivingEntity victim, double amount, boolean crit, boolean ability) {
        if (!victim.isValid() || victim.isDead() || amount <= 0 || victim.equals(attacker)) {
            return;
        }
        Pending previous = pending;
        try {
            pending = new Pending(victim.getUniqueId(), amount, crit, ability);
            victim.setNoDamageTicks(0);
            victim.damage(amount, attacker);
        } finally {
            pending = previous;
        }
    }

    /** Applies damage that skips every rule of the listener (reflected thorns damage, healing side effects). */
    public void rawDamage(LivingEntity victim, double amount, org.bukkit.entity.@Nullable Entity source) {
        if (!victim.isValid() || victim.isDead() || amount <= 0) {
            return;
        }
        boolean previous = raw;
        try {
            raw = true;
            if (source != null) {
                victim.damage(amount, source);
            } else {
                victim.damage(amount);
            }
        } finally {
            raw = previous;
        }
    }

    /** Ability damage of a player: {@code base * (1 + int/100) * (1 + abilityDamage%)}. */
    public double abilityDamage(Player player, double base) {
        PlayerState st = engine.state(player);
        ItemsSettings s = engine.settings();
        return DamageFormula.ability(base, st.stat(Stat.INTELLIGENCE), s.abilityIntelligenceDivisor,
            st.stat(Stat.ABILITY_DAMAGE));
    }

    /** True the first time a victim is seen (full health, untouched). Used by First Strike. */
    boolean firstHit(LivingEntity victim) {
        double max = victim.getAttribute(Attribute.MAX_HEALTH) == null ? victim.getHealth()
            : victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        return victim.getHealth() >= max - 1e-6;
    }

    /** Base damage of whatever the player holds when it is not a custom weapon. */
    public static double vanillaDamage(@Nullable ItemStack stack, double multiplier) {
        if (stack == null || stack.getType().isAir()) {
            return 1 * multiplier;
        }
        String name = stack.getType().name();
        double base;
        if (name.endsWith("_SWORD")) {
            base = tier(name, 4, 5, 6, 7, 8);
        } else if (name.endsWith("_AXE")) {
            base = tier(name, 7, 9, 9, 9, 10) - 1;
        } else if (name.endsWith("_PICKAXE")) {
            base = tier(name, 2, 3, 4, 5, 6);
        } else if (name.endsWith("_SHOVEL")) {
            base = tier(name, 2.5, 3.5, 4.5, 5.5, 6.5);
        } else if (name.endsWith("_HOE")) {
            base = tier(name, 1, 1, 1, 1, 1);
        } else if (stack.getType() == Material.TRIDENT) {
            base = 9;
        } else if (stack.getType() == Material.MACE) {
            base = 6;
        } else {
            base = 1;
        }
        return base * multiplier;
    }

    private static double tier(String name, double wood, double stone, double iron, double diamond, double netherite) {
        if (name.startsWith("WOODEN") || name.startsWith("GOLDEN")) {
            return wood;
        } else if (name.startsWith("STONE")) {
            return stone;
        } else if (name.startsWith("IRON")) {
            return iron;
        } else if (name.startsWith("DIAMOND")) {
            return diamond;
        }
        return netherite;
    }

    static ThreadLocalRandom random() { return ThreadLocalRandom.current(); }
}
