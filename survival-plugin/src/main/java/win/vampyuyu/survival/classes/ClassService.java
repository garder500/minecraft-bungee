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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.api.ClassApi;
import win.vampyuyu.survival.api.LadderApi;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.api.PromotionApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;

/** Choice, bonuses and actives of the four Novice classes. */
public final class ClassService implements ClassApi {
    static final NamespacedKey HEALTH = new NamespacedKey(win.vampyuyu.survival.core.Items.NAMESPACE, "class_vitality");
    static final NamespacedKey SPEED = new NamespacedKey(win.vampyuyu.survival.core.Items.NAMESPACE, "class_ranger_speed");

    /** Why a cast did not happen (or OK). */
    public enum Cast { OK, NO_CLASS, COOLDOWN, NO_AETHER, NO_WEAPON, NO_BOW, NO_ENGINE, NO_MASTER, NO_PARAGON }

    /** The Duelist Afterimage: this many hits are still Surges until the time. */
    private record Flurry(int left, long until) {}

    private final Core core;
    private final Cooldowns cooldowns = new Cooldowns();
    private final AetherPool aether = new AetherPool();
    private final Map<UUID, Long> bulwark = new ConcurrentHashMap<>();
    private final Map<UUID, Long> riftSurge = new ConcurrentHashMap<>();
    private final Map<UUID, Flurry> flurry = new ConcurrentHashMap<>();
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
        core.profiles().edit(player, "classes", ClassData.class, ClassData::new, d -> {
            if (!java.util.Objects.equals(d.vocation, chosen == null ? null : chosen.name())) {
                d.rank = "NOVICE";
                d.spec = null;
            }
            d.vocation = chosen == null ? null : chosen.name();
        });
        Player online = org.bukkit.Bukkit.getPlayer(player);
        if (online != null) {
            refresh(online);
        }
    }

    // ------------------------------------------------------------ promotion

    /** NOVICE, ADEPT or MASTER. */
    public String rank(UUID player) {
        String rank = data(player).rank;
        return rank == null ? "NOVICE" : rank;
    }

    /** Id of the Adept specialization, if any. */
    public Optional<String> spec(UUID player) {
        return Optional.ofNullable(data(player).spec);
    }

    /** Sets the rank and the Adept specialization (null keeps the one the player has). Called by the promotion feature. */
    public void promote(UUID player, String rank, String spec) {
        core.profiles().edit(player, "classes", ClassData.class, ClassData::new, d -> {
            d.rank = rank;
            if (spec != null) {
                d.spec = spec;
            }
        });
        Player online = org.bukkit.Bukkit.getPlayer(player);
        if (online != null) {
            refresh(online);
        }
    }

    private Optional<PromotionApi> promotion() {
        return core.service(PromotionApi.class);
    }

    private PromotionApi.Mods mods(UUID player) {
        return promotion().map(p -> p.modifiers(player)).orElse(PromotionApi.Mods.NONE);
    }

    // ------------------------------------------------------------ bonuses

    private ClassBudget.Bonus budget(UUID player) {
        PromotionApi.Mods m = mods(player);
        return ClassBudget.with(classOf(player).orElse(null), m.vitality(), m.might(), m.dexterity());
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
        double base = classOf(player).filter(c -> c == PlayerClass.CHANNELER).isPresent() ? ClassRules.CHANNELER_ATTUNEMENT : 0;
        return classOf(player).isPresent() ? base + mods(player).attunement() : 0;
    }

    @Override
    public int surgeEvery(UUID player, int normal) {
        return classOf(player).filter(c -> c == PlayerClass.DUELIST).isPresent() ? Math.min(normal, ClassRules.DUELIST_SURGE_EVERY) : normal;
    }

    @Override
    public boolean consumeGuaranteedSurge(UUID player) {
        Flurry f = flurry.get(player);
        if (f != null) {
            if (f.until() <= now() || f.left() <= 1) {
                flurry.remove(player);
            } else {
                flurry.put(player, new Flurry(f.left() - 1, f.until()));
            }
            if (f.until() > now()) {
                return true;
            }
        }
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

    /** The refusal when the held item cannot cast, or null. */
    private Cast weaponCheck(PlayerClass chosen, LadderApi ladder, ItemStack held) {
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
        return null;
    }

    /** What the active costs this player (the specialization may lower it). */
    public double activeCost(UUID player, PlayerClass chosen) {
        return chosen.aetherCost() * mods(player).costFactor();
    }

    /** Casts the active of the player's class. */
    public Cast cast(Player player) {
        UUID id = player.getUniqueId();
        PlayerClass chosen = classOf(id).orElse(null);
        if (chosen == null) {
            return Cast.NO_CLASS;
        }
        LadderApi ladder = core.service(LadderApi.class).orElse(null);
        ItemStack held = player.getInventory().getItemInMainHand();
        Cast refused = weaponCheck(chosen, ladder, held);
        if (refused != null) {
            return refused;
        }
        if (!cooldowns.ready(id, "active")) {
            return Cast.COOLDOWN;
        }
        PromotionApi.Mods m = mods(id);
        if (!aether.spend(id, activeCost(id, chosen), maxAether(player), now())) {
            return Cast.NO_AETHER;
        }
        cooldowns.set(id, "active", Math.round(chosen.cooldownSeconds() * 1000L * m.cooldownFactor()));
        double power = m.powerFactor();
        switch (chosen) {
            case VANGUARD -> bulwark(player, power);
            case DUELIST -> riftStep(player, power);
            case CHANNELER -> burst(player, ladder, held, ClassRules.BURST_RADIUS * power, ClassRules.BURST_SHARE);
            case RANGER -> volley(player, ladder, held, ClassRules.VOLLEY_ARROWS, ClassRules.VOLLEY_SPREAD_DEGREES,
                ClassRules.VOLLEY_SHARE * power);
        }
        promotion().ifPresent(p -> p.recordCast(id));
        return Cast.OK;
    }

    /** Casts the third capacity of a Master. */
    public Cast castMaster(Player player) {
        UUID id = player.getUniqueId();
        PlayerClass chosen = classOf(id).orElse(null);
        if (chosen == null) {
            return Cast.NO_CLASS;
        }
        PromotionApi.Ability ability = promotion().flatMap(p -> p.masterAbility(id)).orElse(null);
        if (ability == null) {
            return Cast.NO_MASTER;
        }
        LadderApi ladder = core.service(LadderApi.class).orElse(null);
        ItemStack held = player.getInventory().getItemInMainHand();
        Cast refused = weaponCheck(chosen, ladder, held);
        if (refused != null) {
            return refused;
        }
        if (!cooldowns.ready(id, "master")) {
            return Cast.COOLDOWN;
        }
        if (!aether.spend(id, ability.aetherCost(), maxAether(player), now())) {
            return Cast.NO_AETHER;
        }
        cooldowns.set(id, "master", ability.cooldownSeconds() * 1000L);
        switch (chosen) {
            case VANGUARD -> rally(player, ability, 0, 1);
            case DUELIST -> afterimage(player, ability);
            case CHANNELER -> burst(player, ladder, held, ability.radius(), ability.power() / 100.0);
            case RANGER -> volley(player, ladder, held, (int) Math.max(1, ability.power()), ability.radius(), ClassRules.VOLLEY_SHARE);
        }
        return Cast.OK;
    }

    /** Casts the ultimate of a Paragon who is a Master (GDD 11.5): same shapes as the Master capacity, far longer cooldown. */
    public Cast castUltimate(Player player) {
        UUID id = player.getUniqueId();
        PlayerClass chosen = classOf(id).orElse(null);
        if (chosen == null) {
            return Cast.NO_CLASS;
        }
        PromotionApi.Ability ability = core.service(win.vampyuyu.survival.api.ParagonApi.class).flatMap(p -> p.ultimate(id)).orElse(null);
        if (ability == null) {
            return Cast.NO_PARAGON;
        }
        LadderApi ladder = core.service(LadderApi.class).orElse(null);
        ItemStack held = player.getInventory().getItemInMainHand();
        Cast refused = weaponCheck(chosen, ladder, held);
        if (refused != null) {
            return refused;
        }
        if (!cooldowns.ready(id, "ultimate")) {
            return Cast.COOLDOWN;
        }
        if (!aether.spend(id, ability.aetherCost(), maxAether(player), now())) {
            return Cast.NO_AETHER;
        }
        cooldowns.set(id, "ultimate", ability.cooldownSeconds() * 1000L);
        switch (chosen) {
            case VANGUARD -> rally(player, ability, 1, 3);
            case DUELIST -> afterimage(player, ability);
            case CHANNELER -> burst(player, ladder, held, ability.radius(), ability.power() / 100.0);
            case RANGER -> volley(player, ladder, held, (int) Math.max(1, ability.power()), ability.radius(), ClassRules.VOLLEY_SHARE);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.6f);
        return Cast.OK;
    }

    public long ultimateCooldownLeft(Player player) {
        return cooldowns.remaining(player.getUniqueId(), "ultimate");
    }

    public long masterCooldownLeft(Player player) {
        return cooldowns.remaining(player.getUniqueId(), "master");
    }

    /** Time to wait before the player can pay {@code cost} Aether. */
    public long aetherWaitFor(Player player, double cost) {
        return aether.waitFor(player.getUniqueId(), cost, maxAether(player), now());
    }

    public long cooldownLeft(Player player) {
        return cooldowns.remaining(player.getUniqueId(), "active");
    }

    public long aetherWait(Player player, PlayerClass chosen) {
        return aetherWaitFor(player, activeCost(player.getUniqueId(), chosen));
    }

    private void bulwark(Player player, double power) {
        bulwark.put(player.getUniqueId(), now() + Math.round(ClassRules.BULWARK_MILLIS * power));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 24, 0.5, 0.8, 0.5, 0.1);
    }

    private void riftStep(Player player, double power) {
        double blocks = ClassRules.RIFT_BLOCKS * power;
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        org.bukkit.util.RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, dir, blocks,
            FluidCollisionMode.NEVER, true);
        double limit = wall == null ? blocks : Math.max(0, eye.distance(wall.getHitPosition().toLocation(player.getWorld())) - 0.6);
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

    private void burst(Player player, LadderApi ladder, ItemStack weapon, double radius, double share) {
        List<LivingEntity> targets = player.getNearbyEntities(radius, radius, radius)
            .stream().filter(e -> e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof org.bukkit.entity.ArmorStand))
            .map(e -> (LivingEntity) e).toList();
        ladder.burst(player, weapon, targets, share);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 3, 1.5, 0.3, 1.5, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
    }

    private void volley(Player player, LadderApi ladder, ItemStack bow, int arrows, double spreadDegrees, double share) {
        Vector dir = player.getEyeLocation().getDirection().normalize();
        for (int i = 0; i < arrows; i++) {
            double angle = Math.toRadians((i - (arrows - 1) / 2.0) * spreadDegrees);
            Vector shot = dir.clone().rotateAroundY(angle).multiply(3.0 * ClassRules.RANGER_ARROW_SPEED);
            Arrow arrow = player.launchProjectile(Arrow.class, shot);
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
            ladder.tagArrow(player, bow, arrow, share);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.3f);
    }

    /** Vanguard Master: Resistance and Absorption for the player and the allies around. */
    private void rally(Player player, PromotionApi.Ability ability, int resistance, int absorption) {
        int ticks = (int) Math.round(ability.seconds() * 20);
        List<Player> group = new java.util.ArrayList<>();
        group.add(player);
        for (org.bukkit.entity.Entity e : player.getNearbyEntities(ability.radius(), ability.radius(), ability.radius())) {
            if (e instanceof Player ally) {
                group.add(ally);
            }
        }
        for (Player p : group) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, resistance));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, absorption));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 40, 1.5, 0.8, 1.5, 0.1);
    }

    /** Duelist Master: the next hits are all Surges for a while. */
    private void afterimage(Player player, PromotionApi.Ability ability) {
        flurry.put(player.getUniqueId(), new Flurry((int) Math.max(1, ability.power()), now() + Math.round(ability.seconds() * 1000)));
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.4);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
    }

    public void forget(UUID player) {
        flurry.remove(player);
        aether.forget(player);
        bulwark.remove(player);
        riftSurge.remove(player);
        inCombat.remove(player);
    }
}
