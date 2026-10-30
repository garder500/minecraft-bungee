package win.vampyuyu.survival.boss;

import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** Builds and configures the vanilla entity behind a boss or a minion. */
public final class BossEntities {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private BossEntities() {}

    /** Spawns the entity, applies everything of the definition and tags it. Null when the spawn is refused. */
    public static @Nullable LivingEntity spawnBoss(BossDefinition def, BossSettings settings, Location at,
            String sessionId, double speedMultiplier) {
        World world = at.getWorld();
        Class<? extends LivingEntity> cls = livingClass(def.entity());
        if (world == null || cls == null) {
            return null;
        }
        return world.spawn(at, cls, entity -> configure(def, settings, entity, sessionId, speedMultiplier));
    }

    @SuppressWarnings("unchecked")
    static @Nullable Class<? extends LivingEntity> livingClass(EntityType type) {
        Class<?> cls = type.getEntityClass();
        return cls != null && LivingEntity.class.isAssignableFrom(cls) ? (Class<? extends LivingEntity>) cls : null;
    }

    static void configure(BossDefinition def, BossSettings settings, LivingEntity e, String sessionId,
            double speedMultiplier) {
        e.getPersistentDataContainer().set(Tags.BOSS, PersistentDataType.STRING, def.id());
        e.getPersistentDataContainer().set(Tags.SESSION, PersistentDataType.STRING, sessionId);
        e.customName(MINI.deserialize(def.name()));
        e.setCustomNameVisible(true);
        quiet(() -> e.setRemoveWhenFarAway(false));
        quiet(() -> e.setPersistent(false));
        quiet(() -> e.setCanPickupItems(false));
        quiet(() -> e.setMaximumNoDamageTicks(Math.max(1, settings.hitIframes())));
        e.setSilent(false);
        base(e, Attribute.MAX_HEALTH, Math.min(1024, settings.visualHealth()));
        e.setHealth(Math.min(1024, settings.visualHealth()));
        base(e, Attribute.ARMOR, 0);
        base(e, Attribute.KNOCKBACK_RESISTANCE, def.knockbackResistance());
        base(e, Attribute.SCALE, def.scale());
        base(e, Attribute.FOLLOW_RANGE, 64);
        base(e, Attribute.ATTACK_DAMAGE, Math.max(0.01, def.damage() * settings.damageScale()));
        multiply(e, Attribute.MOVEMENT_SPEED, def.speed() * speedMultiplier);
        multiply(e, Attribute.FLYING_SPEED, def.speed() * speedMultiplier);
        Map<String, Object> o = def.options();
        if (e instanceof Slime slime && o.get("size") instanceof Number n) {
            slime.setSize(Math.max(1, Math.min(32, n.intValue())));
        }
        if (e instanceof Zombie zombie) {
            zombie.setBaby(Boolean.TRUE.equals(o.get("baby")));
        } else if (e instanceof Ageable ageable && !Boolean.TRUE.equals(o.get("baby"))) {
            ageable.setAdult();
        }
        e.setGlowing(Boolean.TRUE.equals(o.get("glow")));
        applyEquipment(e, def.equipment());
        if (e instanceof Mob mob) {
            mob.setAware(true);
        }
    }

    /** Applies (or re-applies) the damage of the current phase and enrage and an absolute movement speed. */
    public static void retune(BossDefinition def, BossSettings settings, LivingEntity e, double damageMult,
            double moveSpeed) {
        base(e, Attribute.ATTACK_DAMAGE, Math.max(0.01, def.damage() * settings.damageScale() * damageMult));
        if (moveSpeed > 0) {
            base(e, Attribute.MOVEMENT_SPEED, moveSpeed);
        }
    }

    /** Current base movement speed of the entity, 0 when it has none. */
    public static double moveSpeed(LivingEntity e) {
        AttributeInstance instance = e.getAttribute(Attribute.MOVEMENT_SPEED);
        return instance == null ? 0 : instance.getBaseValue();
    }

    /** Runs a cosmetic or best effort setter; a server that does not support it must not abort the spawn. */
    static void quiet(Runnable setter) {
        try {
            setter.run();
        } catch (RuntimeException ignored) {
            // optional behaviour, the boss still works without it
        }
    }

    static void base(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance instance = e.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    static void multiply(LivingEntity e, Attribute attribute, double factor) {
        AttributeInstance instance = e.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    static void applyEquipment(LivingEntity e, Map<String, BossDefinition.EquipSpec> specs) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        for (Map.Entry<String, BossDefinition.EquipSpec> entry : specs.entrySet()) {
            ItemStack item = new ItemStack(entry.getValue().material());
            Integer rgb = entry.getValue().color();
            if (rgb != null && item.getItemMeta() instanceof LeatherArmorMeta meta) {
                meta.setColor(Color.fromRGB(rgb));
                item.setItemMeta(meta);
            }
            EquipmentSlot slot = switch (entry.getKey()) {
                case "head" -> EquipmentSlot.HEAD;
                case "chest" -> EquipmentSlot.CHEST;
                case "legs" -> EquipmentSlot.LEGS;
                case "feet" -> EquipmentSlot.FEET;
                case "offhand" -> EquipmentSlot.OFF_HAND;
                default -> EquipmentSlot.HAND;
            };
            eq.setItem(slot, item);
            eq.setDropChance(slot, 0f);
        }
    }

    /** A plain item for equipment tests and minions. */
    public static ItemStack leather(Material material, int rgb) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(rgb));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Spawns a tagged minion (no drops, no xp). */
    public static @Nullable LivingEntity spawnMinion(Location at, EntityType type, String sessionId,
            @Nullable String name, double health, double damage, Consumer<LivingEntity> extra) {
        World world = at.getWorld();
        Class<? extends LivingEntity> cls = livingClass(type);
        if (world == null || cls == null) {
            return null;
        }
        return world.spawn(at, cls, e -> {
            e.getPersistentDataContainer().set(Tags.MINION, PersistentDataType.STRING, sessionId);
            if (name != null) {
                e.customName(MINI.deserialize(name));
                e.setCustomNameVisible(true);
            }
            e.setRemoveWhenFarAway(false);
            e.setPersistent(false);
            e.setCanPickupItems(false);
            base(e, Attribute.MAX_HEALTH, Math.max(1, Math.min(1024, health)));
            e.setHealth(Math.max(1, Math.min(1024, health)));
            base(e, Attribute.ATTACK_DAMAGE, Math.max(0.01, damage));
            if (e instanceof Zombie zombie) {
                zombie.setBaby(false);
            }
            EntityEquipment eq = e.getEquipment();
            if (eq != null) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    try {
                        eq.setDropChance(slot, 0f);
                    } catch (RuntimeException ignored) {
                        // slots that cannot drop (body) are skipped
                    }
                }
            }
            extra.accept(e);
        });
    }
}
