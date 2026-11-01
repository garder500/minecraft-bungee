package win.vampyuyu.survival.items.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.ability.AbilityRegistry;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.stats.PlayerState;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * The Skyblock damage model, active only in Skyblock worlds.
 * <ul>
 * <li>Outgoing player melee: {@code weapon * (1 + might/100) * (1 + enchants) * element}, then {@code x2} on every Nth
 *     fully charged hit on the same target (Surge). No dice: the same swing always deals the same damage.
 *     Vanilla armor is ignored.</li>
 * <li>Outgoing arrows: the same formula computed when the arrow is shot, element and Surge on impact.</li>
 * <li>Incoming damage on players: vanilla number times a per-cause scale (x5), times {@code 100 / (100 + ward)};
 *     void, starvation and similar causes are true damage as a percentage of max health.</li>
 * </ul>
 */
public final class CombatListener implements Listener, win.vampyuyu.survival.items.ability.ArrowTagger {
    private static final NamespacedKey ARROW_DAMAGE = new NamespacedKey("vampyuyu", "sb_arrow_damage");
    private static final NamespacedKey ARROW_REND = new NamespacedKey("vampyuyu", "sb_arrow_rend");
    private static final NamespacedKey ARROW_ELEMENT = new NamespacedKey("vampyuyu", "sb_arrow_element");
    private static final NamespacedKey ARROW_ATTUNEMENT = new NamespacedKey("vampyuyu", "sb_arrow_attunement");
    private static final NamespacedKey ARROW_FULL = new NamespacedKey("vampyuyu", "sb_arrow_full");

    private final Core core;
    private final StatsEngine engine;
    private final Gear gear;
    private final CombatService service;
    private final DamageIndicators indicators;
    private final AbilityRegistry abilities;

    /** What the MONITOR pass needs to show a number. */
    private record Hit(boolean surge, boolean byPlayer) {}

    private final Map<UUID, Hit> hits = new HashMap<>();
    private final SurgeTracker surge = new SurgeTracker();

    public CombatListener(Core core, StatsEngine engine, Gear gear, CombatService service,
            DamageIndicators indicators, AbilityRegistry abilities) {
        this.core = core;
        this.engine = engine;
        this.gear = gear;
        this.service = service;
        this.indicators = indicators;
        this.abilities = abilities;
    }

    private void putHit(LivingEntity victim, Hit hit) {
        if (hits.size() > 2000) {
            hits.clear();
        }
        hits.put(victim.getUniqueId(), hit);
    }

    private ItemsSettings settings() { return engine.settings(); }

    private static boolean skyblock(Entity entity) {
        return entity.getWorld() != null && SkyblockWorlds.isSkyblock(entity.getWorld().getName());
    }

    private static double maxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1, entity.getHealth()) : attribute.getValue();
    }

    // ================================================================= damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !skyblock(victim) || service.raw) {
            return;
        }
        ItemsSettings s = settings();
        Player attackerPlayer = null;
        Entity attackerEntity = null;
        Projectile projectile = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attackerEntity = byEntity.getDamager();
            if (attackerEntity instanceof Projectile p) {
                projectile = p;
                if (p.getShooter() instanceof Player shooter) {
                    attackerPlayer = shooter;
                } else if (p.getShooter() instanceof Entity shooter) {
                    attackerEntity = shooter;
                }
            } else if (attackerEntity instanceof Player player) {
                attackerPlayer = player;
            }
        }
        if (attackerPlayer != null && !engine.active(attackerPlayer)) {
            attackerPlayer = null;
        }

        boolean playerAttack = false;
        if (attackerPlayer != null) {
            if (event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK && projectile == null) {
                event.setCancelled(true);
                return;
            }
            playerAttack = outgoing(event, victim, attackerPlayer, projectile);
            if (playerAttack && !(victim instanceof Player)) {
                ignoreVanillaMitigation(event);
            }
        }
        if (victim instanceof Player player && engine.active(player)) {
            incoming(event, player, attackerEntity, playerAttack, s);
        }
    }

    /**
     * Formula damage is final: the victim's own vanilla armor, Resistance and enchant protection (armored zombies,
     * Resistance potions) must not shave it, otherwise the kill time would not match the number on the indicator.
     */
    @SuppressWarnings("deprecation")
    private static void ignoreVanillaMitigation(EntityDamageEvent event) {
        for (EntityDamageEvent.DamageModifier modifier : new EntityDamageEvent.DamageModifier[] {
                EntityDamageEvent.DamageModifier.ARMOR, EntityDamageEvent.DamageModifier.RESISTANCE,
                EntityDamageEvent.DamageModifier.MAGIC}) {
            try {
                if (event.isApplicable(modifier)) {
                    event.setDamage(modifier, 0);
                }
            } catch (RuntimeException ignored) {
                // a server build that refuses to edit this modifier: the hit keeps the vanilla reduction
            }
        }
    }

    /** Computes the damage a player deals. Returns true when the formula replaced the damage. */
    private boolean outgoing(EntityDamageEvent event, LivingEntity victim, Player attacker, Projectile projectile) {
        PlayerState st = engine.state(attacker);
        ItemsSettings s = settings();
        CombatService.Pending pending = service.pending;
        if (pending != null && pending.victim().equals(victim.getUniqueId())) {
            event.setDamage(pending.amount());
            putHit(victim, new Hit(pending.crit(), true));
            fireHit(attacker, victim, pending.amount());
            return true;
        }
        if (projectile != null) {
            return arrowHit(event, victim, attacker, projectile, st);
        }
        if (event.getCause() != DamageCause.ENTITY_ATTACK) {
            return false;
        }
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        Optional<GearInfo> info = gear.read(hand);
        double weapon;
        if (info.isPresent() && info.get().damage() > 0) {
            weapon = info.get().damage();
        } else if (info.isPresent() && info.get().kind() != ItemKind.OTHER && !info.get().kind().weapon()
                && !isHeldTool(info.get())) {
            weapon = CombatService.vanillaDamage(null, s.vanillaDamageMultiplier);
        } else {
            weapon = CombatService.vanillaDamage(hand, s.vanillaDamageMultiplier);
        }
        Map<String, Integer> levels = st.enchantLevels();
        EnchantCombat.Target target = new EnchantCombat.Target(victim.getType(), victim.getHealth(), maxHealth(victim),
            maxHealth(attacker), service.firstHit(victim));
        double enchantBonus = EnchantCombat.melee(levels, gear.enchants(), target);
        for (CombatModifiers modifier : service.modifiers()) {
            enchantBonus += modifier.outgoingBonus(attacker, victim);
        }
        if (st.soulEaterBonus > 0) {
            enchantBonus += st.soulEaterBonus;
            st.soulEaterBonus = 0;
        }
        double strength = attackStrength(attacker);
        double damage = DamageFormula.hit(weapon, s.damageBonus, st.stat(Stat.MIGHT), enchantBonus);
        Element element = info.map(i -> i.def().element()).orElse(null);
        damage *= elementMultiplier(element, victim, st, s);
        boolean surged = surge.hit(attacker.getUniqueId(), victim.getUniqueId(), strength >= s.surgeMinCharge,
            (int) s.surgeEvery);
        if (surged) {
            damage *= s.surgeMultiplier;
            surgeFeedback(victim);
        }
        if (s.useAttackCooldown) {
            damage *= strength;
        }
        event.setDamage(damage);
        putHit(victim, new Hit(surged, true));
        fireHit(attacker, victim, damage);
        afterMelee(attacker, victim, st, damage, levels, s);
        return true;
    }

    /** The element multiplier of a hit by {@code element} on {@code victim}. */
    private double elementMultiplier(Element element, LivingEntity victim, PlayerState st, ItemsSettings s) {
        if (element == null) {
            return 1.0;
        }
        ElementTraits.Traits traits = ElementTraits.read(victim);
        return DamageFormula.elementMultiplier(element, traits.weakTo(), traits.resists(), st.stat(Stat.ATTUNEMENT),
            s.elementWeakness, s.elementResistance);
    }

    /** A small spark and a click on the Surge hit, so the pattern can be learned. */
    private static void surgeFeedback(LivingEntity victim) {
        try {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getEyeLocation(), 10, 0.25, 0.25, 0.25, 0.1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.4f);
        } catch (RuntimeException ignored) {
            // a world that cannot show particles: the hit still counts
        }
    }

    private boolean isHeldTool(GearInfo info) {
        String label = info.def().typeLabel();
        return label != null && settings().heldTypes.contains(label.toUpperCase(java.util.Locale.ROOT));
    }

    private static double attackStrength(Player player) {
        return win.vampyuyu.survival.core.SwingCharge.of(player);
    }

    private void fireHit(Player attacker, LivingEntity victim, double damage) {
        for (CombatModifiers modifier : service.modifiers()) {
            try {
                modifier.onHit(attacker, victim, damage);
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().warning("Combat hook failed: " + e);
            }
        }
    }

    /** Cleave and Life Steal, run after the main hit has been decided. */
    private void afterMelee(Player attacker, LivingEntity victim, PlayerState st, double damage,
            Map<String, Integer> levels, ItemsSettings s) {
        EnchantRegistry registry = gear.enchants();
        boolean oneForAll = levels.containsKey("one_for_all");
        if (!oneForAll) {
            int steal = levels.getOrDefault("life_steal", 0);
            if (steal > 0) {
                double max = maxHealth(attacker);
                attacker.setHealth(Math.min(max, attacker.getHealth() + max * registry.value("life_steal", steal) / 100.0));
            }
            int cleave = levels.getOrDefault("cleave", 0);
            if (cleave > 0) {
                double radius = 2.0 + cleave * 0.4;
                double share = registry.value("cleave", cleave) / 100.0;
                core.scheduler().nextTick(() -> {
                    if (!victim.isValid()) {
                        return;
                    }
                    for (Entity near : victim.getNearbyEntities(radius, radius, radius)) {
                        if (near instanceof LivingEntity living && !(near instanceof Player) && !near.equals(attacker)
                                && !(near instanceof org.bukkit.entity.ArmorStand)) {
                            service.strike(attacker, living, damage * share, false, false);
                        }
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------------- arrows

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !engine.active(player)
                || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        ItemsSettings s = settings();
        PlayerState st = engine.state(player);
        ItemStack bow = event.getBow();
        Optional<GearInfo> info = gear.read(bow);
        double weapon = info.filter(i -> i.damage() > 0).map(GearInfo::damage)
            .orElse(CombatService.vanillaDamage(bow, s.vanillaDamageMultiplier) * 2);
        Map<String, Integer> levels = info.map(GearInfo::enchants).orElse(Map.of());
        EnchantRegistry registry = gear.enchants();
        double force = DamageFormula.drawFactor(event.getForce());
        // Power and Rend scale the arrow now; Snipe is added on impact when the distance is known.
        double ench = levels.containsKey("one_for_all") ? 0
            : registry.value("power", levels.getOrDefault("power", 0)) / 100.0
                + registry.value("rend", levels.getOrDefault("rend", 0)) / 100.0;
        double damage = DamageFormula.hit(weapon, s.damageBonus, st.stat(Stat.MIGHT), ench) * force;
        tag(arrow, damage, st, levels, info.map(i -> i.def().element()).orElse(null), force >= s.surgeMinCharge);

        int quiver = levels.getOrDefault("infinite_quiver", 0);
        if (quiver > 0 && !levels.containsKey("one_for_all")
                && ThreadLocalRandom.current().nextDouble() * 100.0 < registry.value("infinite_quiver", quiver)) {
            event.setConsumeItem(false);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        // Triple shot and other bow abilities hook in here.
        abilities.onShoot(player, bow, info.orElse(null), event);
    }

    private void tag(AbstractArrow arrow, double damage, PlayerState st, Map<String, Integer> levels, Element element,
            boolean full) {
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(ARROW_DAMAGE, PersistentDataType.DOUBLE, damage);
        pdc.set(ARROW_ATTUNEMENT, PersistentDataType.DOUBLE, st.stat(Stat.ATTUNEMENT));
        pdc.set(ARROW_FULL, PersistentDataType.INTEGER, full ? 1 : 0);
        if (element != null) {
            pdc.set(ARROW_ELEMENT, PersistentDataType.STRING, element.key());
        }
        if (levels.getOrDefault("rend", 0) > 0) {
            pdc.set(ARROW_REND, PersistentDataType.INTEGER, levels.get("rend"));
        }
        arrow.setDamage(0.0001);
    }

    /** Copies the damage tags of {@code from} onto an extra arrow, scaling the damage. */
    @Override
    public void copy(AbstractArrow from, AbstractArrow to, double factor) {
        PersistentDataContainer a = from.getPersistentDataContainer();
        PersistentDataContainer b = to.getPersistentDataContainer();
        Double damage = a.get(ARROW_DAMAGE, PersistentDataType.DOUBLE);
        if (damage == null) {
            return;
        }
        b.set(ARROW_DAMAGE, PersistentDataType.DOUBLE, damage * factor);
        copy(a, b, ARROW_ATTUNEMENT);
        String element = a.get(ARROW_ELEMENT, PersistentDataType.STRING);
        if (element != null) {
            b.set(ARROW_ELEMENT, PersistentDataType.STRING, element);
        }
        Integer full = a.get(ARROW_FULL, PersistentDataType.INTEGER);
        if (full != null) {
            b.set(ARROW_FULL, PersistentDataType.INTEGER, full);
        }
        Integer rend = a.get(ARROW_REND, PersistentDataType.INTEGER);
        if (rend != null) {
            b.set(ARROW_REND, PersistentDataType.INTEGER, rend);
        }
        to.setDamage(0.0001);
    }

    private static void copy(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        Double value = from.get(key, PersistentDataType.DOUBLE);
        if (value != null) {
            to.set(key, PersistentDataType.DOUBLE, value);
        }
    }

    private boolean arrowHit(EntityDamageEvent event, LivingEntity victim, Player shooter, Projectile projectile,
            PlayerState st) {
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        Double base = pdc.get(ARROW_DAMAGE, PersistentDataType.DOUBLE);
        if (base == null) {
            return false;
        }
        double damage = base;
        Optional<GearInfo> bow = gear.read(shooter.getInventory().getItemInMainHand());
        Map<String, Integer> levels = bow.map(GearInfo::enchants).orElse(Map.of());
        int snipe = levels.getOrDefault("snipe", 0);
        if (snipe > 0 && !levels.containsKey("one_for_all")) {
            double distance = projectile.getOrigin() == null ? 0
                : projectile.getOrigin().distance(victim.getLocation());
            damage *= 1.0 + DamageFormula.snipe(snipe, gear.enchants().perLevel("snipe"), distance);
        }
        Element element = Element.parse(pdc.get(ARROW_ELEMENT, PersistentDataType.STRING));
        if (element != null) {
            ElementTraits.Traits traits = ElementTraits.read(victim);
            Double attunement = pdc.get(ARROW_ATTUNEMENT, PersistentDataType.DOUBLE);
            damage *= DamageFormula.elementMultiplier(element, traits.weakTo(), traits.resists(),
                attunement == null ? 0 : attunement, settings().elementWeakness, settings().elementResistance);
        }
        Integer full = pdc.get(ARROW_FULL, PersistentDataType.INTEGER);
        boolean surged = surge.hit(shooter.getUniqueId(), victim.getUniqueId(), full != null && full == 1,
            (int) settings().surgeEvery);
        if (surged) {
            damage *= settings().surgeMultiplier;
            surgeFeedback(victim);
        }
        event.setDamage(damage);
        putHit(victim, new Hit(surged, true));
        fireHit(shooter, victim, damage);
        Integer rend = pdc.get(ARROW_REND, PersistentDataType.INTEGER);
        if (rend != null && rend > 0) {
            bleed(shooter, victim, damage * 0.1);
        }
        return true;
    }

    private void bleed(Player shooter, LivingEntity victim, double perTick) {
        for (int i = 1; i <= 3; i++) {
            core.scheduler().later(20L * i, () -> {
                if (shooter.isOnline() && victim.isValid() && !victim.isDead()) {
                    service.strike(shooter, victim, perTick, false, false);
                }
            });
        }
    }

    // ---------------------------------------------------------------- incoming

    private void incoming(EntityDamageEvent event, Player victim, Entity attacker, boolean playerAttack, ItemsSettings s) {
        PlayerState st = engine.state(victim);
        DamageCause cause = event.getCause();
        double max = maxHealth(victim);
        double ward = st.stat(Stat.WARD) * (1.0 + lastStand(st, victim, max));
        double damage;
        Double truePercent = s.trueCausePercent.get(cause.name());
        if (truePercent != null) {
            damage = max * truePercent / 100.0; // true damage: Ward does not apply
        } else {
            double vanilla = event.getDamage();
            double scale = playerAttack ? 1.0 : s.incomingScaleByCause.getOrDefault(cause.name(), s.incomingScale);
            damage = DamageFormula.incoming(vanilla, scale, ward);
            if (cause == DamageCause.FALL) {
                damage *= 1.0 - Math.min(0.9, gear.enchants().value("feather_falling", st.enchantLevel("feather_falling")) / 100.0);
            } else if (cause == DamageCause.DROWNING) {
                damage *= 1.0 - Math.min(0.9, gear.enchants().value("respiration", st.enchantLevel("respiration")) / 100.0);
            }
            for (CombatModifiers modifier : service.modifiers()) {
                damage *= Math.max(0, modifier.incomingMultiplier(victim, event));
            }
            long now = System.currentTimeMillis();
            if (st.shieldUntil > now) {
                damage *= 1.0 - settings().d("abilities.wither_shield.reduction", 0.7);
            }
        }
        if (damage >= victim.getHealth() + victim.getAbsorptionAmount()) {
            for (CombatModifiers modifier : service.modifiers()) {
                if (modifier.preventDeath(victim, event, damage)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        event.setDamage(Math.max(0, damage));
        if (!hits.containsKey(victim.getUniqueId())) {
            putHit(victim, new Hit(false, false));
        }
        // Reflect (thorns) once the damage is known; the reflected hit bypasses every rule.
        int thorns = st.enchantLevel("thorns");
        if (thorns > 0 && attacker instanceof LivingEntity target && !(attacker instanceof Player)) {
            double reflected = damage * gear.enchants().value("thorns", thorns) / 100.0;
            core.scheduler().nextTick(() -> service.rawDamage(target, reflected, victim));
        }
        final double finalDamage = damage;
        for (CombatModifiers modifier : service.modifiers()) {
            modifier.onHurt(victim, attacker, finalDamage);
        }
    }

    /** Last Stand: Ward bonus fraction while below 40% health. */
    private double lastStand(PlayerState st, Player victim, double max) {
        int level = st.enchantLevel("last_stand");
        if (level <= 0 || victim.getHealth() > max * 0.4) {
            return 0;
        }
        return gear.enchants().value("last_stand", level) / 100.0;
    }

    // ---------------------------------------------------------------- indicators (after everyone else)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDone(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        Hit hit = hits.remove(entity.getUniqueId());
        if (hit == null || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (hit.byPlayer() || entity instanceof Player) {
            double shown = event.getFinalDamage();
            // a monster with a virtual health pool (dungeon) reports the real damage of the hit
            for (org.bukkit.metadata.MetadataValue value : living.getMetadata("sb_shown_damage")) {
                shown = value.asDouble();
            }
            living.removeMetadata("sb_shown_damage", core.getPlugin());
            indicators.show(living.getEyeLocation().add(0, 0.1, 0), shown, hit.surge());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        hits.remove(dead.getUniqueId());
        Player killer = dead.getKiller();
        if (killer == null || !skyblock(dead) || !engine.active(killer)) {
            return;
        }
        PlayerState st = engine.state(killer);
        int soul = st.enchantLevel("soul_eater");
        if (soul > 0 && !st.enchantLevels().containsKey("one_for_all")) {
            st.soulEaterBonus = gear.enchants().value("soul_eater", soul) / 100.0;
        }
        int looting = st.enchantLevel("looting");
        if (looting > 0 && !(dead instanceof Player) && !st.enchantLevels().containsKey("one_for_all")
                && ThreadLocalRandom.current().nextDouble() * 100.0 < gear.enchants().value("looting", looting)) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack drop : event.getDrops()) {
                if (drop != null && !drop.getType().isAir() && !gear.items().isCustom(drop)) {
                    extra.add(drop.clone());
                }
            }
            event.getDrops().addAll(extra);
        }
        if (st.enchantLevel("telekinesis") > 0 && !(dead instanceof Player)) {
            for (ItemStack drop : new ArrayList<>(event.getDrops())) {
                if (drop == null || drop.getType().isAir()) {
                    continue;
                }
                Map<Integer, ItemStack> left = killer.getInventory().addItem(drop);
                left.values().forEach(rest -> killer.getWorld().dropItemNaturally(killer.getLocation(), rest));
            }
            event.getDrops().clear();
        }
    }
}
