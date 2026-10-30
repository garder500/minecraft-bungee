package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;

/**
 * Sea creatures: a catch can summon a tagged vanilla mob next to the bobber. The chance grows with the Fishing level
 * and the {@code SEA_CREATURE_CHANCE} stat; the kill pays Fishing XP through {@link ActivitySources} (the tags
 * {@code sea_creature} and {@code skill_xp} are set before the mob joins the world). Every creature is tracked: at most
 * {@code max_alive_per_player} per fisher, removed after {@code despawn_seconds}, on quit and on disable.
 */
final class SeaCreatures implements Listener {
    private final Core core;
    private final SkillsService service;
    private final Map<UUID, Set<UUID>> alive = new HashMap<>();

    SeaCreatures(Core core, SkillsService service) {
        this.core = core;
        this.service = service;
    }

    // ---------------------------------------------------------------- pure rules

    /** Chance of a sea creature for one catch, 0..max_chance. */
    static double chance(SkillsConfig.SeaCreatureCfg cfg, int fishingLevel, double chanceStat) {
        if (!cfg.enabled()) {
            return 0;
        }
        double value = cfg.baseChance() + cfg.perLevel() * Math.max(0, fishingLevel) + cfg.perStat() * Math.max(0, chanceStat);
        return Math.max(0, Math.min(cfg.maxChance(), value));
    }

    /** Weighted pick among the creatures the fisher's level allows; {@code roll} is in [0, 1). */
    static SkillsConfig.SeaCreatureDef pick(List<SkillsConfig.SeaCreatureDef> table, int fishingLevel, double roll) {
        double total = 0;
        for (SkillsConfig.SeaCreatureDef def : table) {
            if (def.minLevel() <= fishingLevel) {
                total += def.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        double target = roll * total;
        SkillsConfig.SeaCreatureDef last = null;
        for (SkillsConfig.SeaCreatureDef def : table) {
            if (def.minLevel() > fishingLevel || def.weight() <= 0) {
                continue;
            }
            last = def;
            target -= def.weight();
            if (target < 0) {
                return def;
            }
        }
        return last;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        FishHook hook = event.getHook();
        SkillsConfig cfg = service.config();
        if ((mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) || hook == null || hook.getWorld() == null
                || !cfg.worldAllowed(hook.getWorld().getName()) || !cfg.seaCreatures.enabled()) {
            return;
        }
        trySpawn(player, hook.getLocation(), () -> ThreadLocalRandom.current().nextDouble(),
            () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Rolls the chance and spawns a creature. Returns it, or null when nothing came. Public to tests via the package. */
    LivingEntity trySpawn(Player player, Location at, DoubleSupplier chanceRoll, DoubleSupplier pickRoll) {
        SkillsConfig.SeaCreatureCfg cfg = service.config().seaCreatures;
        int level = service.level(player.getUniqueId(), Skill.FISHING);
        double stat = core.service(StatsApi.class).map(api -> api.stat(player, Stat.SEA_CREATURE_CHANCE)).orElse(0.0);
        if (chanceRoll.getAsDouble() >= chance(cfg, level, stat)) {
            return null;
        }
        Set<UUID> mine = alive.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        mine.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });
        if (mine.size() >= cfg.maxAlivePerPlayer()) {
            return null;
        }
        SkillsConfig.SeaCreatureDef def = pick(cfg.creatures(), level, pickRoll.getAsDouble());
        World world = at.getWorld();
        if (def == null || world == null || def.type().getEntityClass() == null) {
            return null;
        }
        Location spot = at.clone().add(0, 0.2, 0);
        if (spot.getBlock().getType() != Material.WATER) {
            return null; // the bobber is not in water: no sea to spawn in
        }
        Class<? extends LivingEntity> cls = def.type().getEntityClass().asSubclass(LivingEntity.class);
        LivingEntity mob = world.spawn(spot, cls, (java.util.function.Consumer<LivingEntity>) e -> prepare(e, def, player));
        mine.add(mob.getUniqueId());
        UUID id = mob.getUniqueId();
        core.scheduler().later(cfg.despawnSeconds() * 20, () -> {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) {
                e.remove();
            }
            forget(player.getUniqueId(), id);
        });
        core.msg().info(player, def.name() + " rises from the water!");
        return mob;
    }

    private void prepare(LivingEntity mob, SkillsConfig.SeaCreatureDef def, Player target) {
        var pdc = mob.getPersistentDataContainer();
        pdc.set(ActivitySources.SEA_CREATURE, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ActivitySources.FORCE_XP, PersistentDataType.BYTE, (byte) 1);
        AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(def.health());
        }
        mob.setHealth(Math.min(def.health(), health == null ? def.health() : health.getValue()));
        mob.customName(Component.text(def.name(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        mob.setCustomNameVisible(true);
        mob.setPersistent(false);
        if (mob instanceof Mob m) {
            m.setTarget(target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        for (Set<UUID> set : alive.values()) {
            set.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Set<UUID> mine = alive.remove(event.getPlayer().getUniqueId());
        if (mine != null) {
            removeAll(mine);
        }
    }

    private void forget(UUID player, UUID creature) {
        Set<UUID> mine = alive.get(player);
        if (mine != null) {
            mine.remove(creature);
        }
    }

    private static void removeAll(Set<UUID> ids) {
        for (UUID id : new ArrayList<>(ids)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
    }

    /** Removes every live creature (feature disable). */
    void clear() {
        for (Set<UUID> ids : alive.values()) {
            removeAll(ids);
        }
        alive.clear();
    }

    int aliveCount() {
        return alive.values().stream().mapToInt(Set::size).sum();
    }
}
