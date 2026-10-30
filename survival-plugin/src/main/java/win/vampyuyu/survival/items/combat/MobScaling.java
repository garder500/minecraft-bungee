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
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Gives natural mobs of the Skyblock worlds more health (the damage formula deals hundreds of points) and shows a
 * "Zombie 120/500" name above hurt, non boss mobs that have no name of their own. Mobs are marked in their PDC so
 * they are never scaled twice and the names can be removed again on disable.
 *
 * <p>Levels: natural mobs listed in {@code mobs.table} get their level, health and damage, and a "[Lv3] Zombie" name.
 * Other features opt in without importing this class by writing the INTEGER persistent data key
 * {@code vampyuyu:sb_level} on the mob they spawn (any spawn reason): the first time it is damaged in a Skyblock world
 * it is scaled by {@link #scale}. Mobs without the key and with a spawn reason outside {@code combat.scaled-spawn-reasons}
 * (boss, dungeon and minion mobs) are never touched.
 */
public final class MobScaling implements Listener {
    private static final NamespacedKey SCALED = new NamespacedKey("vampyuyu", "sb_scaled");
    /** The opt-in key other features write on their mobs: INTEGER level. */
    public static final NamespacedKey LEVEL = new NamespacedKey("vampyuyu", "sb_level");
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
        return bar(name, health, max, 0);
    }

    /** Pure: the name line of a health bar with a level prefix (0 = no prefix). */
    public static Component bar(Component name, double health, double max, int level) {
        if (level > 0) {
            name = Component.text("[Lv" + level + "] ", NamedTextColor.GRAY).append(name);
        }
        return name.append(Component.text(" " + Msg.grouped(Math.ceil(Math.max(0, health))) + "/"
            + Msg.grouped(Math.ceil(max)) + "❤", NamedTextColor.RED));
    }

    /** Pure: max health of a mob of {@code level} from its vanilla health (opt-in mobs and unlisted types). */
    public static double leveledHealth(double vanilla, double multiplier, int level, double perLevel) {
        return Math.min(2048, Math.max(1, vanilla * Math.max(1, multiplier) * (1 + Math.max(0, level) * perLevel)));
    }

    /** Pure: attack damage of a mob of {@code level} from its vanilla damage. */
    public static double leveledDamage(double vanilla, int level, double perLevel) {
        return Math.max(0, vanilla * (1 + Math.max(0, level) * perLevel));
    }

    /**
     * Scales a mob once: health, attack damage, level mark. {@code health}/{@code damage} of 0 mean "derive from the
     * vanilla values and the level". Returns false when the mob was already scaled or has no such attributes.
     */
    public boolean scale(LivingEntity entity, int level, double health, double damage) {
        var data = entity.getPersistentDataContainer();
        if (data.has(SCALED, PersistentDataType.BYTE)) {
            return false;
        }
        ItemsSettings s = engine.settings();
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return false;
        }
        double target = health > 0 ? Math.min(2048, health)
            : leveledHealth(max.getBaseValue(), s.mobHealthMultiplier, level, s.mobHealthPerLevel);
        max.setBaseValue(target);
        entity.setHealth(max.getValue());
        AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null && (damage > 0 || level > 0)) {
            attack.setBaseValue(damage > 0 ? damage : leveledDamage(attack.getBaseValue(), level, s.mobDamagePerLevel));
        }
        if (level > 0) {
            data.set(LEVEL, PersistentDataType.INTEGER, level);
        }
        data.set(SCALED, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    /** The level mark of a mob, 0 when it has none. */
    public static int levelOf(LivingEntity entity) {
        Integer level = entity.getPersistentDataContainer().get(LEVEL, PersistentDataType.INTEGER);
        return level == null ? 0 : Math.max(0, level);
    }

    /** Opt-in mobs of other features: scaled right before the first hit lands. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFirstDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !skyblock(mob) || mob.hasMetadata("NPC")) {
            return;
        }
        var data = mob.getPersistentDataContainer();
        Integer level = data.get(LEVEL, PersistentDataType.INTEGER);
        if (level != null && level > 0 && !data.has(SCALED, PersistentDataType.BYTE)) {
            scale(mob, level, 0, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        ItemsSettings settings = engine.settings();
        double factor = settings.mobHealthMultiplier;
        ItemsSettings.MobEntry row = settings.mobTable.get(event.getEntity().getType().name());
        if ((factor <= 1 && row == null) || !(entity instanceof Mob) || entity instanceof Tameable || !skyblock(entity)
                || entity.hasMetadata("NPC")
                || !engine.settings().scaledSpawnReasons.contains(event.getSpawnReason().name())
                || entity.getPersistentDataContainer().has(SCALED, PersistentDataType.BYTE)) {
            return;
        }
        if (row != null) {
            scale(entity, row.level(), row.health(), row.damage());
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
        int level = engine.settings().mobShowLevel ? levelOf(mob) : 0;
        mob.customName(bar(name, mob.getHealth(), max.getValue(), level));
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
