package win.vampyuyu.survival.dungeon.run;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.core.Msg;

/** Creates the dungeon monsters: attributes, gear, name and persistent metadata. */
public final class MobFactory {
    private final Keys keys;

    public MobFactory(Keys keys) {
        this.keys = keys;
    }

    /** Armour material prefix by floor. */
    static String armorTier(int floor) {
        if (floor <= 1) {
            return "LEATHER";
        }
        if (floor <= 3) {
            return "CHAINMAIL";
        }
        if (floor <= 5) {
            return "IRON";
        }
        if (floor == 6) {
            return "DIAMOND";
        }
        return "NETHERITE";
    }

    /**
     * Spawns a monster.
     *
     * @param hpMult floor multiplier times party scale
     * @param dmgMult floor multiplier times party scale (applied to every hit it deals, see the listener)
     * @return the entity, or null when the spawn was refused
     */
    public LivingEntity spawn(World world, Location at, MobSpec spec, String runId, int room, String kind, int floor,
            int level, double hpMult, double dmgMult, Player target) {
        Entity raw;
        try {
            raw = world.spawnEntity(at, spec.type(), CreatureSpawnEvent.SpawnReason.CUSTOM);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(raw instanceof LivingEntity e)) {
            raw.remove();
            return null;
        }
        try {
            configure(e, spec, runId, room, kind, floor, level, hpMult, dmgMult, target);
        } catch (RuntimeException ex) {
            e.remove(); // never leave a half configured monster behind
            return null;
        }
        return e;
    }

    private void configure(LivingEntity e, MobSpec spec, String runId, int room, String kind, int floor, int level,
            double hpMult, double dmgMult, Player target) {
        double hp = Math.max(1, spec.health() * hpMult);
        AttributeInstance maxHealth = e.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.min(hp, 1024));
            e.setHealth(Math.min(hp, 1024));
            if (hp > 1024) {
                e.getPersistentDataContainer().set(keys.factor, PersistentDataType.DOUBLE, hp / 1024.0);
            }
        }
        AttributeInstance speed = e.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && spec.speed() != 1.0) {
            speed.setBaseValue(speed.getBaseValue() * spec.speed());
        }
        AttributeInstance follow = e.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(48);
        }
        if (kind.equals("boss") || kind.equals("miniboss")) {
            AttributeInstance kb = e.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.setBaseValue(0.8);
            }
        }
        gear(e, spec, floor);
        e.setRemoveWhenFarAway(false);
        e.setCanPickupItems(false);
        e.setPersistent(false);
        if (spec.invisible()) {
            e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        e.customName(Msg.component(spec.color() + "<bold>" + spec.name() + "</bold> <dark_gray>[<gray>Lv." + level
            + "<dark_gray>]"));
        e.setCustomNameVisible(true);
        var pdc = e.getPersistentDataContainer();
        pdc.set(keys.run, PersistentDataType.STRING, runId);
        pdc.set(keys.room, PersistentDataType.INTEGER, room);
        pdc.set(keys.mob, PersistentDataType.STRING, spec.id());
        pdc.set(keys.kind, PersistentDataType.STRING, kind);
        pdc.set(keys.damage, PersistentDataType.DOUBLE, Math.max(0.1, spec.damage() * dmgMult));
        pdc.set(keys.level, PersistentDataType.INTEGER, level);
        if (e instanceof Mob mob && target != null) {
            mob.setTarget(target);
        }
    }

    private void gear(LivingEntity e, MobSpec spec, int floor) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return;
        }
        if (spec.weapon() != Material.AIR) {
            Material weapon = spec.weapon();
            if (weapon.name().endsWith("_SWORD") || weapon.name().endsWith("_AXE")) {
                String tier = floor >= 7 ? "NETHERITE" : floor >= 5 ? "DIAMOND" : floor >= 3 ? "IRON" : "STONE";
                Material upgraded = Material.matchMaterial(tier + (weapon.name().endsWith("_SWORD") ? "_SWORD" : "_AXE"));
                if (spec.id().equals("bone_warlord") || spec.id().equals("crypt_lord")) {
                    upgraded = weapon;
                }
                weapon = upgraded == null ? weapon : upgraded;
            }
            eq.setItemInMainHand(new ItemStack(weapon));
        }
        if (spec.armored()) {
            String tier = armorTier(floor);
            set(eq, EquipmentSlot.HEAD, tier + "_HELMET");
            set(eq, EquipmentSlot.CHEST, tier + "_CHESTPLATE");
            set(eq, EquipmentSlot.LEGS, tier + "_LEGGINGS");
            set(eq, EquipmentSlot.FEET, tier + "_BOOTS");
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            try {
                eq.setDropChance(slot, 0f);
            } catch (IllegalArgumentException ignored) {
                // body/saddle slots are not valid for every entity
            }
        }
    }

    private static void set(EntityEquipment eq, EquipmentSlot slot, String material) {
        Material m = Material.matchMaterial(material);
        if (m != null) {
            eq.setItem(slot, new ItemStack(m));
        }
    }

    /** A spawned entity is a dungeon mob of this run. */
    public boolean isMobOf(Entity e, String runId) {
        return runId.equals(keys.runOf(e)) && keys.kindOf(e) != null && keys.npcOf(e) == null;
    }

    public Component nameOf(MobSpec spec) {
        return Msg.component(spec.color() + spec.name());
    }
}
