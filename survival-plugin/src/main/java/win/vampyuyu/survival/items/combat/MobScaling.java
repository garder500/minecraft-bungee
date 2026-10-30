package win.vampyuyu.survival.items.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Gives natural mobs of the Skyblock worlds more health (the damage formula deals hundreds of points) and shows a
 * "Zombie 120/500" name above hurt, non boss mobs that have no name of their own. Mobs are marked in their PDC so
 * they are never scaled twice and the names can be removed again on disable.
 */
public final class MobScaling implements Listener {
    private static final NamespacedKey SCALED = new NamespacedKey("vampyuyu", "sb_scaled");
    private static final NamespacedKey BAR = new NamespacedKey("vampyuyu", "sb_bar");

    private final Core core;
    private final StatsEngine engine;

    public MobScaling(Core core, StatsEngine engine) {
        this.core = core;
        this.engine = engine;
    }

    private static boolean skyblock(Entity entity) {
        return entity.getWorld() != null && SkyblockWorlds.isSkyblock(entity.getWorld().getName());
    }

    /** Pure: the name line of a health bar. */
    public static Component bar(Component name, double health, double max) {
        return name.append(Component.text(" " + Msg.grouped(Math.ceil(Math.max(0, health))) + "/"
            + Msg.grouped(Math.ceil(max)) + "❤", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        double factor = engine.settings().mobHealthMultiplier;
        if (factor <= 1 || !(entity instanceof Mob) || entity instanceof Tameable || !skyblock(entity)
                || entity.hasMetadata("NPC")
                || !engine.settings().scaledSpawnReasons.contains(event.getSpawnReason().name())
                || entity.getPersistentDataContainer().has(SCALED, PersistentDataType.BYTE)) {
            return;
        }
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        max.setBaseValue(Math.min(2048, max.getBaseValue() * factor));
        entity.setHealth(max.getValue());
        entity.getPersistentDataContainer().set(SCALED, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!engine.settings().healthBars || !(event.getEntity() instanceof Mob mob) || mob instanceof Boss
                || mob instanceof Tameable || !skyblock(mob) || mob.hasMetadata("NPC")) {
            return;
        }
        core.scheduler().nextTick(() -> refresh(mob));
    }

    private void refresh(Mob mob) {
        if (!mob.isValid()) {
            return;
        }
        var data = mob.getPersistentDataContainer();
        boolean ours = data.has(BAR, PersistentDataType.BYTE);
        if (mob.customName() != null && !ours) {
            return; // someone else's name (name tag, another feature)
        }
        AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        Component name = Component.translatable(mob.getType().translationKey(), NamedTextColor.WHITE);
        mob.customName(bar(name, mob.getHealth(), max.getValue()));
        mob.setCustomNameVisible(true);
        data.set(BAR, PersistentDataType.BYTE, (byte) 1);
    }

    /** Removes the bars this feature added (disable). */
    public void clear() {
        for (World world : Bukkit.getWorlds()) {
            if (!SkyblockWorlds.isSkyblock(world.getName())) {
                continue;
            }
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Player) && entity.getPersistentDataContainer().has(BAR, PersistentDataType.BYTE)) {
                    entity.customName(null);
                    entity.setCustomNameVisible(false);
                    entity.getPersistentDataContainer().remove(BAR);
                }
            }
        }
    }
}
