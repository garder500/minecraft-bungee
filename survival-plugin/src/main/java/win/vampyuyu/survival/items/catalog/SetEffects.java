package win.vampyuyu.survival.items.catalog;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.combat.CombatModifiers;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** The non-stat effects of the armor set bonuses. */
public final class SetEffects implements CombatModifiers, Listener {
    private final StatsEngine engine;
    private final SetBonuses sets;
    private final Map<UUID, Location> lastSafe = new ConcurrentHashMap<>();
    private final Map<UUID, Long> anchorReadyAt = new ConcurrentHashMap<>();
    private final Map<UUID, int[]> timber = new ConcurrentHashMap<>();
    private final Map<UUID, Long> timberUntil = new ConcurrentHashMap<>();

    public SetEffects(StatsEngine engine, SetBonuses sets) {
        this.engine = engine;
        this.sets = sets;
    }

    // ---------------------------------------------------------------- combat

    @Override
    public void onHit(Player attacker, LivingEntity target, double damage) {
        if (sets.has(attacker.getUniqueId(), "hunter")) {
            double percent = sets.param("hunter", "lifesteal");
            var attribute = attacker.getAttribute(Attribute.MAX_HEALTH);
            double max = attribute == null ? 20 : attribute.getValue();
            attacker.setHealth(Math.min(max, attacker.getHealth() + damage * percent / 100.0));
        }
    }

    @Override
    public double incomingMultiplier(Player victim, EntityDamageEvent event) {
        if (sets.has(victim.getUniqueId(), "frostborne")) {
            return 1.0 - sets.param("frostborne", "reduction") / 100.0;
        }
        return 1;
    }

    @Override
    public void onHurt(Player victim, Entity attacker, double finalDamage) {
        if (attacker instanceof LivingEntity living && !(attacker instanceof Player)
                && sets.has(victim.getUniqueId(), "frostborne")) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                (int) (sets.param("frostborne", "slow") * 20), 1));
        }
    }

    @Override
    public boolean preventDeath(Player victim, EntityDamageEvent event, double damage) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !sets.has(victim.getUniqueId(), "void_walker")) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (anchorReadyAt.getOrDefault(victim.getUniqueId(), 0L) > now) {
            return false;
        }
        Location safe = lastSafe.get(victim.getUniqueId());
        if (safe == null || safe.getWorld() == null) {
            return false;
        }
        anchorReadyAt.put(victim.getUniqueId(), now + (long) (sets.param("void_walker", "cooldown") * 1000));
        victim.setFallDistance(0);
        victim.teleport(safe);
        victim.getWorld().playSound(safe, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        return true;
    }

    // ---------------------------------------------------------------- periodic

    /** Called once a second for every active player. */
    public void tick(Player player) {
        UUID id = player.getUniqueId();
        var active = sets.activeOf(id);
        if (active.isEmpty()) {
            timber.remove(id);
            return;
        }
        String set = active.get().set().id();
        switch (set) {
            case "miner" -> {
                if (player.getLocation().getY() < 40) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 15 * 20, 0, true, false, false));
                }
            }
            case "void_walker" -> {
                if (player.isOnGround() && player.getLocation().getBlock().isPassable()
                        && !player.getLocation().getBlock().isLiquid()
                        && player.getLocation().getY() > player.getWorld().getMinHeight() + 4) {
                    lastSafe.put(id, player.getLocation());
                }
            }
            case "lumberjack" -> {
                Long until = timberUntil.get(id);
                if (until != null && until < System.currentTimeMillis()) {
                    timberUntil.remove(id);
                    timber.remove(id);
                    engine.removeModifiers(id, "set:timber");
                    engine.recompute(player);
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- block based

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!engine.active(player)) {
            return;
        }
        Block block = event.getBlock();
        UUID id = player.getUniqueId();
        if (sets.has(id, "lumberjack") && Tag.LOGS.isTagged(block.getType())) {
            int[] stacks = timber.computeIfAbsent(id, k -> new int[1]);
            stacks[0] = Math.min(5, stacks[0] + 1);
            timberUntil.put(id, System.currentTimeMillis() + (long) (sets.param("lumberjack", "seconds") * 1000));
            engine.addModifier(id, "set:timber", Stat.STRENGTH, stacks[0] * sets.param("lumberjack", "timber"));
            engine.recompute(player);
        } else if (sets.has(id, "farmer") && block.getBlockData() instanceof Ageable crop
                && crop.getAge() >= crop.getMaximumAge()
                && ThreadLocalRandom.current().nextDouble() * 100 < sets.param("farmer", "chance")) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block near = block.getRelative(dx, 0, dz);
                    if (near.getBlockData() instanceof Ageable neighbour && neighbour.getAge() < neighbour.getMaximumAge()) {
                        neighbour.setAge(neighbour.getAge() + 1);
                        near.setBlockData(neighbour, false);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastSafe.remove(id);
        timber.remove(id);
        timberUntil.remove(id);
        sets.forget(id);
    }
}
