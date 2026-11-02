package win.vampyuyu.survival.classes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.api.ClassApi;
import win.vampyuyu.survival.api.LadderApi;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;

/** Choice, bonuses and actives of the four Novice classes. */
public final class ClassService implements ClassApi {
    static final NamespacedKey HEALTH = new NamespacedKey(win.vampyuyu.survival.core.Items.NAMESPACE, "class_vitality");
    static final NamespacedKey SPEED = new NamespacedKey(win.vampyuyu.survival.core.Items.NAMESPACE, "class_ranger_speed");

    /** Why a cast did not happen (or OK). */
    public enum Cast { OK, NO_CLASS, COOLDOWN, NO_AETHER, NO_WEAPON, NO_BOW, NO_ENGINE }

    private final Core core;
    private final Cooldowns cooldowns = new Cooldowns();
    private final AetherPool aether = new AetherPool();
    private final Map<UUID, Long> bulwark = new ConcurrentHashMap<>();
    private final Map<UUID, Long> riftSurge = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inCombat = new ConcurrentHashMap<>();

    public ClassService(Core core) {
        this.core = core;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ------------------------------------------------------------ choice

    private ClassData data(UUID player) {
        return core.profiles().section(player, "classes", ClassData.class, ClassData::new);
    }

    @Override
    public Optional<PlayerClass> classOf(UUID player) {
        return Optional.ofNullable(PlayerClass.parse(data(player).vocation));
    }

    @Override
    public boolean choose(UUID player, PlayerClass chosen) {
        if (chosen == null || classOf(player).isPresent()) {
            return false;
        }
        set(player, chosen);
        return true;
    }

    @Override
    public void set(UUID player, PlayerClass chosen) {
        core.profiles().edit(player, "classes", ClassData.class, ClassData::new, d -> d.vocation = chosen == null ? null : chosen.name());
        Player online = org.bukkit.Bukkit.getPlayer(player);
        if (online != null) {
            refresh(online);
        }
    }

    // ------------------------------------------------------------ bonuses

    private ClassBudget.Bonus budget(UUID player) {
        return ClassBudget.of(classOf(player).orElse(null));
    }

    @Override
    public double mightBonus(UUID player) {
        return budget(player).might();
    }

    @Override
    public double dexterityBonus(UUID player) {
        return budget(player).dexterity();
    }

    @Override
    public double attunementBonus(UUID player) {
        return classOf(player).filter(c -> c == PlayerClass.CHANNELER).isPresent() ? ClassRules.CHANNELER_ATTUNEMENT : 0;
    }

    @Override
    public int surgeEvery(UUID player, int normal) {
        return classOf(player).filter(c -> c == PlayerClass.DUELIST).isPresent() ? Math.min(normal, ClassRules.DUELIST_SURGE_EVERY) : normal;
    }

    @Override
    public boolean consumeGuaranteedSurge(UUID player) {
        Long until = riftSurge.remove(player);
        return until != null && until > now();
    }

    /** Applies the health bonus of the class (called on join, on a change and on respawn). */
    public void refresh(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) {
            return;
        }
        health.removeModifier(HEALTH);
        double bonus = budget(player.getUniqueId()).vitality() / 5.0;
        if (bonus > 0) {
            health.addModifier(new AttributeModifier(HEALTH, bonus, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (player.getHealth() > health.getValue()) {
            player.setHealth(health.getValue());
        }
    }

    // ------------------------------------------------------------ Aether

    public double maxAether(Player player) {
        double stat = core.service(StatsApi.class).map(s -> s.stat(player, Stat.AETHER)).orElse(AetherPool.BASE);
        return Math.max(AetherPool.BASE, stat) + classOf(player.getUniqueId()).map(PlayerClass::aetherPool).orElse(0.0);
    }

    public double aether(Player player) {
        return aether.current(player.getUniqueId(), maxAether(player), now());
    }

    // ------------------------------------------------------------ passives

    public boolean bulwarkActive(UUID player) {
        Long until = bulwark.get(player);
        return until != null && until > now();
    }

    /** The damage a player takes after the class effects (Bulwark, the Vanguard ally passive). */
    public double damageTaken(Player player, double damage) {
        Optional<PlayerClass> chosen = classOf(player.getUniqueId());
        if (chosen.isEmpty()) {
            return damage;
        }
        boolean nearAlly = chosen.get() == PlayerClass.VANGUARD && player.getNearbyEntities(ClassRules.ALLY_RADIUS,
            ClassRules.ALLY_RADIUS, ClassRules.ALLY_RADIUS).stream().anyMatch(e -> e instanceof Player other && !other.equals(player));
        return ClassRules.damageTaken(damage, bulwarkActive(player.getUniqueId()), nearAlly);
    }

    /** Ranger: +10% speed for a few seconds after a fight starts or continues. */
    public void combatTick(Player player) {
        if (classOf(player.getUniqueId()).filter(c -> c == PlayerClass.RANGER).isEmpty()) {
            return;
        }
        long end = now() + ClassRules.RANGER_COMBAT_MILLIS;
        inCombat.put(player.getUniqueId(), end);
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED) == null) {
            speed.addModifier(new AttributeModifier(SPEED, ClassRules.RANGER_SPEED, AttributeModifier.Operation.ADD_SCALAR));
        }
        core.scheduler().later(ClassRules.RANGER_COMBAT_MILLIS / 50 + 2, () -> endCombat(player));
    }

    void endCombat(Player player) {
        Long end = inCombat.get(player.getUniqueId());
        if (end != null && end > now()) {
            return;
        }
        inCombat.remove(player.getUniqueId());
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED);
        }
    }

    public boolean inCombat(UUID player) {
        Long end = inCombat.get(player);
        return end != null && end > now();
    }

    // ------------------------------------------------------------ actives

    /** Casts the active of the player's class. */
    public Cast cast(Player player) {
        UUID id = player.getUniqueId();
        PlayerClass chosen = classOf(id).orElse(null);
        if (chosen == null) {
            return Cast.NO_CLASS;
        }
        LadderApi ladder = core.service(LadderApi.class).orElse(null);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (chosen == PlayerClass.CHANNELER || chosen == PlayerClass.RANGER) {
            if (ladder == null) {
                return Cast.NO_ENGINE;
            }
            if (!ladder.isWeapon(held)) {
                return chosen == PlayerClass.RANGER ? Cast.NO_BOW : Cast.NO_WEAPON;
            }
            if (chosen == PlayerClass.RANGER && held.getType() != org.bukkit.Material.BOW) {
                return Cast.NO_BOW;
            }
        }
        if (!cooldowns.ready(id, "active")) {
            return Cast.COOLDOWN;
        }
        if (!aether.spend(id, chosen.aetherCost(), maxAether(player), now())) {
            return Cast.NO_AETHER;
        }
        cooldowns.set(id, "active", chosen.cooldownSeconds() * 1000L);
        switch (chosen) {
            case VANGUARD -> bulwark(player);
            case DUELIST -> riftStep(player);
            case CHANNELER -> burst(player, ladder, held);
            case RANGER -> volley(player, ladder, held);
        }
        return Cast.OK;
    }

    public long cooldownLeft(Player player) {
        return cooldowns.remaining(player.getUniqueId(), "active");
    }

    public long aetherWait(Player player, PlayerClass chosen) {
        return aether.waitFor(player.getUniqueId(), chosen.aetherCost(), maxAether(player), now());
    }

    private void bulwark(Player player) {
        bulwark.put(player.getUniqueId(), now() + ClassRules.BULWARK_MILLIS);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 24, 0.5, 0.8, 0.5, 0.1);
    }

    private void riftStep(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        org.bukkit.util.RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, dir, ClassRules.RIFT_BLOCKS,
            FluidCollisionMode.NEVER, true);
        double limit = wall == null ? ClassRules.RIFT_BLOCKS : Math.max(0, eye.distance(wall.getHitPosition().toLocation(player.getWorld())) - 0.6);
        Location from = player.getLocation();
        double dist = ClassRules.riftDistance(limit, d -> fits(from.clone().add(dir.clone().multiply(d))));
        if (dist > 0) {
            Location to = from.clone().add(dir.clone().multiply(dist));
            to.setYaw(from.getYaw());
            to.setPitch(from.getPitch());
            player.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
            player.teleport(to);
            player.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
            player.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        }
        riftSurge.put(player.getUniqueId(), now() + ClassRules.RIFT_SURGE_MILLIS);
    }

    private static boolean fits(Location feet) {
        return feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private void burst(Player player, LadderApi ladder, ItemStack weapon) {
        List<LivingEntity> targets = player.getNearbyEntities(ClassRules.BURST_RADIUS, ClassRules.BURST_RADIUS, ClassRules.BURST_RADIUS)
            .stream().filter(e -> e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof org.bukkit.entity.ArmorStand))
            .map(e -> (LivingEntity) e).toList();
        ladder.burst(player, weapon, targets, ClassRules.BURST_SHARE);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 3, 1.5, 0.3, 1.5, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
    }

    private void volley(Player player, LadderApi ladder, ItemStack bow) {
        Vector dir = player.getEyeLocation().getDirection().normalize();
        int arrows = ClassRules.VOLLEY_ARROWS;
        for (int i = 0; i < arrows; i++) {
            double angle = Math.toRadians((i - (arrows - 1) / 2.0) * ClassRules.VOLLEY_SPREAD_DEGREES);
            Vector shot = dir.clone().rotateAroundY(angle).multiply(3.0 * ClassRules.RANGER_ARROW_SPEED);
            Arrow arrow = player.launchProjectile(Arrow.class, shot);
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            ladder.tagArrow(player, bow, arrow, ClassRules.VOLLEY_SHARE);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.3f);
    }

    public void forget(UUID player) {
        aether.forget(player);
        bulwark.remove(player);
        riftSurge.remove(player);
        inCombat.remove(player);
    }
}
