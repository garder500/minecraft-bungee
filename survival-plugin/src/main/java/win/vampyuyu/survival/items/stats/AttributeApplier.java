package win.vampyuyu.survival.items.stats;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.ItemsSettings;

/**
 * Maps stats onto Paper attributes with our own, transient modifier keys (never saved with the player, always
 * removable). Health: the stat is the real max health (so damage numbers are in stat units) clamped to the vanilla
 * cap of 1024, and the health bar is rescaled to 20 hearts with {@code setHealthScale}.
 */
public final class AttributeApplier {
    /** Namespace of every attribute modifier key created here. */
    public static final String NAMESPACE = "vampyuyu";

    static final NamespacedKey K_HEALTH = new NamespacedKey(NAMESPACE, "sb_health");
    static final NamespacedKey K_SPEED = new NamespacedKey(NAMESPACE, "sb_speed");
    static final NamespacedKey K_ATTACK = new NamespacedKey(NAMESPACE, "sb_attack_speed");
    static final NamespacedKey K_MINING = new NamespacedKey(NAMESPACE, "sb_mining_speed");
    static final NamespacedKey K_ARMOR = new NamespacedKey(NAMESPACE, "sb_zero_armor");
    static final NamespacedKey K_TOUGH = new NamespacedKey(NAMESPACE, "sb_zero_toughness");
    static final NamespacedKey K_KB = new NamespacedKey(NAMESPACE, "sb_zero_knockback");

    private final Logger log;
    private boolean warned;

    public AttributeApplier(Logger log) {
        this.log = log;
    }

    /** Pure mapping of the health stat to the max health attribute value. */
    public static double maxHealth(double healthStat, double cap) {
        return Math.max(1, Math.min(cap, healthStat));
    }

    /** Pure mapping of Speed (100 = normal) to the multiplier added on the base movement speed. */
    public static double speedAmount(double speedStat, double maxPercent) {
        double clamped = Math.max(10, Math.min(maxPercent, speedStat));
        return (clamped - 100.0) / 100.0;
    }

    public void apply(Player player, Map<Stat, Double> totals, ItemsSettings settings, boolean firstTime) {
        try {
            AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                double max = maxHealth(totals.getOrDefault(Stat.HEALTH, Stat.HEALTH.base()), settings.maxHealth);
                set(health, K_HEALTH, max - health.getBaseValue(), AttributeModifier.Operation.ADD_NUMBER);
                player.setHealthScale(settings.healthScale);
                double now = player.getHealth();
                double cap = health.getValue();
                if (firstTime) {
                    player.setHealth(Math.max(1, cap));
                } else if (now > cap) {
                    player.setHealth(Math.max(1, cap));
                }
            }
            set(player.getAttribute(Attribute.MOVEMENT_SPEED), K_SPEED,
                speedAmount(totals.getOrDefault(Stat.SPEED, 100.0), settings.maxSpeedPercent),
                AttributeModifier.Operation.ADD_SCALAR);
            double bonusAttack = Math.max(0, Math.min(settings.maxAttackSpeedPercent,
                totals.getOrDefault(Stat.ATTACK_SPEED, 0.0))) / 100.0;
            set(player.getAttribute(Attribute.ATTACK_SPEED), K_ATTACK, bonusAttack, AttributeModifier.Operation.ADD_SCALAR);
            double mining = Math.max(0, Math.min(settings.maxMiningSpeedPercent,
                totals.getOrDefault(Stat.MINING_SPEED, 0.0))) / 100.0;
            set(player.getAttribute(Attribute.BLOCK_BREAK_SPEED), K_MINING, mining, AttributeModifier.Operation.ADD_SCALAR);
            if (settings.b("attributes.zero-vanilla-armor", true)) {
                set(player.getAttribute(Attribute.ARMOR), K_ARMOR, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                set(player.getAttribute(Attribute.ARMOR_TOUGHNESS), K_TOUGH, -1,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                set(player.getAttribute(Attribute.KNOCKBACK_RESISTANCE), K_KB, -1,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            }
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                log.warning("Could not apply stat attributes to " + player.getName() + ": " + e);
            }
        }
    }

    /** Removes every modifier this class added and restores the vanilla health bar. */
    public void clear(Player player) {
        try {
            remove(player.getAttribute(Attribute.MAX_HEALTH), K_HEALTH);
            remove(player.getAttribute(Attribute.MOVEMENT_SPEED), K_SPEED);
            remove(player.getAttribute(Attribute.ATTACK_SPEED), K_ATTACK);
            remove(player.getAttribute(Attribute.BLOCK_BREAK_SPEED), K_MINING);
            remove(player.getAttribute(Attribute.ARMOR), K_ARMOR);
            remove(player.getAttribute(Attribute.ARMOR_TOUGHNESS), K_TOUGH);
            remove(player.getAttribute(Attribute.KNOCKBACK_RESISTANCE), K_KB);
            player.setHealthScale(20.0);
            player.setHealthScaled(false);
            AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
            if (health != null && player.getHealth() > health.getValue()) {
                player.setHealth(health.getValue());
            }
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                log.warning("Could not clear stat attributes of " + player.getName() + ": " + e);
            }
        }
    }

    private static void remove(AttributeInstance instance, NamespacedKey key) {
        if (instance != null) {
            instance.removeModifier(key);
        }
    }

    private static void set(AttributeInstance instance, NamespacedKey key, double amount,
            AttributeModifier.Operation operation) {
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(key);
        if (existing != null) {
            if (existing.getOperation() == operation && Math.abs(existing.getAmount() - amount) < 1e-9) {
                return;
            }
            instance.removeModifier(existing);
        }
        if (Math.abs(amount) < 1e-9 && operation != AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
            return;
        }
        instance.addTransientModifier(new AttributeModifier(key, amount, operation));
    }
}
