package win.vampyuyu.survival.quarry;

import java.util.Optional;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.combat.DamageFormula;
import win.vampyuyu.survival.items.combat.ElementTraits;
import win.vampyuyu.survival.items.combat.SurgeTracker;
import win.vampyuyu.survival.items.gear.Keys;

/**
 * Melee damage of the ore ladder when the full stat engine (the items feature) is not loaded, as on the simple
 * Skyblock: a sword with a damage value hits for {@code (damage + flat bonus) x (1 + Might of the worn armor and the weapon / 100)},
 * scaled by the swing charge, times the element (weakness / resistance of the target) and the Surge (every Nth fully charged
 * hit on the same target). The same formulas as the items feature, so the power curve and the boss health hold either
 * way. A ladder bow works the same when the arrow lands, its damage scaled by the draw. When the items feature is
 * loaded its combat listener does this and this one stays out of the way.
 */
final class LadderCombat implements Listener, win.vampyuyu.survival.api.LadderApi {
    private final Core core;
    private final ItemsSettings settings;
    private final SurgeTracker surges = new SurgeTracker();
    private static final org.bukkit.NamespacedKey ARROW_DAMAGE = new org.bukkit.NamespacedKey("vampyuyu", "ladder_arrow_damage");
    private static final org.bukkit.NamespacedKey ARROW_ELEMENT = new org.bukkit.NamespacedKey("vampyuyu", "ladder_arrow_element");
    private static final org.bukkit.NamespacedKey ARROW_SHARE = new org.bukkit.NamespacedKey("vampyuyu", "ladder_arrow_share");
    private static final org.bukkit.NamespacedKey ARROW_FULL = new org.bukkit.NamespacedKey("vampyuyu", "ladder_arrow_full");

    LadderCombat(Core core) {
        this.core = core;
        this.settings = ItemsSettings.load(core.data());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)
                || event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || core.service(StatsApi.class).isPresent()) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        Optional<CustomItemDef> def = core.items().idOf(weapon).flatMap(core.items()::def);
        Double damage = weaponDamage(weapon, def.orElse(null));
        if (damage == null || weapon.getType() == org.bukkit.Material.BOW) { // a bow's damage is for its arrows, not a punch
            return;
        }
        double charge = attackStrength(player);
        double hit = DamageFormula.base(damage, settings.damageBonus, might(player));
        ElementTraits.Traits traits = ElementTraits.read(target);
        hit *= DamageFormula.elementMultiplier(weaponElement(player, def.get()), traits.weakTo(), traits.resists(), attunement(player),
            settings.elementWeakness, settings.elementResistance);
        if (surge(player, target, charge >= settings.surgeMinCharge)) {
            hit *= surgeMultiplier(player);
            core.service(win.vampyuyu.survival.api.RuneApi.class).ifPresent(r -> r.onSurge(player, target, weapon));
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, target.getHeight() * 0.6, 0), 12,
                0.3, 0.4, 0.3, 0.05);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
        }
        if (settings.useAttackCooldown) {
            hit *= charge;
        }
        event.setDamage(hit);
    }

    /** Remembers on the arrow what the bow and the draw are worth. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof AbstractArrow arrow)
                || event.getBow() == null || core.service(StatsApi.class).isPresent()) {
            return;
        }
        Optional<CustomItemDef> def = core.items().idOf(event.getBow()).flatMap(core.items()::def);
        Double damage = weaponDamage(event.getBow(), def.orElse(null));
        if (damage == null) {
            return;
        }
        var pdc = arrow.getPersistentDataContainer();
        pdc.set(ARROW_DAMAGE, PersistentDataType.DOUBLE, DamageFormula.base(damage, settings.damageBonus, might(player))
            * DamageFormula.drawFactor(event.getForce()));
        pdc.set(ARROW_FULL, PersistentDataType.BOOLEAN, event.getForce() >= settings.surgeMinCharge);
        if (weaponElement(player, def.get()) != null) {
            pdc.set(ARROW_ELEMENT, PersistentDataType.STRING, weaponElement(player, def.get()).name());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !(event.getEntity() instanceof LivingEntity target)
                || !(arrow.getShooter() instanceof Player player)) {
            return;
        }
        var pdc = arrow.getPersistentDataContainer();
        Double damage = pdc.get(ARROW_DAMAGE, PersistentDataType.DOUBLE);
        if (damage == null) {
            return;
        }
        String element = pdc.get(ARROW_ELEMENT, PersistentDataType.STRING);
        ElementTraits.Traits traits = ElementTraits.read(target);
        double hit = damage * DamageFormula.elementMultiplier(element == null ? null : win.vampyuyu.survival.api.Element.valueOf(element),
            traits.weakTo(), traits.resists(), attunement(player), settings.elementWeakness, settings.elementResistance);
        if (surge(player, target, Boolean.TRUE.equals(pdc.get(ARROW_FULL, PersistentDataType.BOOLEAN)))) {
            hit *= surgeMultiplier(player);
            core.service(win.vampyuyu.survival.api.RuneApi.class)
                .ifPresent(r -> r.onSurge(player, target, player.getInventory().getItemInMainHand()));
        }
        Double share = pdc.get(ARROW_SHARE, PersistentDataType.DOUBLE);
        if (share != null) {
            hit *= share;
        }
        event.setDamage(hit);
    }

    private java.util.Optional<win.vampyuyu.survival.api.ClassApi> classes() {
        return core.service(win.vampyuyu.survival.api.ClassApi.class);
    }

    /** Might of the worn gear and the class (the class bonus is already inside the meta cap). */
    private double might(Player player) {
        return worn(player, Stat.MIGHT) + classes().map(c -> c.mightBonus(player.getUniqueId())).orElse(0.0);
    }

    private double attunement(Player player) {
        return worn(player, Stat.ATTUNEMENT) + classes().map(c -> c.attunementBonus(player.getUniqueId())).orElse(0.0);
    }

    /** A Rift Step makes the next hit a Surge; the Duelist gets one every 4th hit. */
    private boolean surge(Player player, LivingEntity target, boolean counted) {
        int every = classes().map(c -> c.surgeEvery(player.getUniqueId(), (int) settings.surgeEvery)).orElse((int) settings.surgeEvery);
        boolean natural = surges.hit(player.getUniqueId(), target.getUniqueId(), counted, every);
        return counted && classes().map(c -> c.consumeGuaranteedSurge(player.getUniqueId())).orElse(false) || natural;
    }

    /** Surge power: the base multiplier plus Dexterity / 200 (GDD 8.2: 1.5 + Dex / 200). */
    private double surgeMultiplier(Player player) {
        return settings.surgeMultiplier + classes().map(c -> c.dexterityBonus(player.getUniqueId())).orElse(0.0) / 200.0;
    }

    @Override
    public boolean isWeapon(ItemStack stack) {
        return weaponDamage(stack, core.items().idOf(stack).flatMap(core.items()::def).orElse(null)) != null;
    }

    @Override
    public int burst(Player player, ItemStack weapon, java.util.Collection<? extends LivingEntity> targets, double share) {
        Optional<CustomItemDef> def = core.items().idOf(weapon).flatMap(core.items()::def);
        Double damage = weaponDamage(weapon, def.orElse(null));
        if (damage == null) {
            return 0;
        }
        double base = DamageFormula.base(damage, settings.damageBonus, might(player)) * share;
        int hit = 0;
        for (LivingEntity target : targets) {
            if (!target.isValid() || target.isDead()) {
                continue;
            }
            ElementTraits.Traits traits = ElementTraits.read(target);
            double amount = base * DamageFormula.elementMultiplier(weaponElement(player, def.get()), traits.weakTo(), traits.resists(),
                attunement(player), settings.elementWeakness, settings.elementResistance);
            target.damage(amount, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MAGIC)
                .withCausingEntity(player).withDirectEntity(player).build());
            hit++;
        }
        return hit;
    }

    @Override
    public boolean tagArrow(Player player, ItemStack bow, AbstractArrow arrow, double share) {
        Optional<CustomItemDef> def = core.items().idOf(bow).flatMap(core.items()::def);
        Double damage = weaponDamage(bow, def.orElse(null));
        if (damage == null) {
            return false;
        }
        var pdc = arrow.getPersistentDataContainer();
        pdc.set(ARROW_DAMAGE, PersistentDataType.DOUBLE, DamageFormula.base(damage, settings.damageBonus, might(player)));
        pdc.set(ARROW_SHARE, PersistentDataType.DOUBLE, share);
        pdc.set(ARROW_FULL, PersistentDataType.BOOLEAN, false); // the volley's arrows never count towards a Surge
        if (weaponElement(player, def.get()) != null) {
            pdc.set(ARROW_ELEMENT, PersistentDataType.STRING, weaponElement(player, def.get()).name());
        }
        return true;
    }

    /** The element a hit carries: an active Alembic oil wins over the weapon's own element. */
    private win.vampyuyu.survival.api.Element weaponElement(Player player, CustomItemDef def) {
        return core.service(win.vampyuyu.survival.api.StationApi.class)
            .flatMap(api -> api.oilElement(player.getUniqueId())).orElse(def.element());
    }

    /** Swing charge, 0.2 to 1 ({@link win.vampyuyu.survival.core.SwingCharge}, same as the items feature). */
    private static double attackStrength(Player player) {
        return win.vampyuyu.survival.core.SwingCharge.of(player);
    }

    /** The weapon's damage value (on the stack, else on its definition); null for anything that is not a ladder weapon. */
    static Double weaponDamage(ItemStack weapon, CustomItemDef def) {
        if (def == null) {
            return null;
        }
        if (weapon.hasItemMeta()) {
            Double onStack = weapon.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(win.vampyuyu.survival.core.Items.NAMESPACE, Keys.DAMAGE), PersistentDataType.DOUBLE);
            if (onStack != null) {
                return onStack;
            }
        }
        return def.pdcDoubles().get(Keys.DAMAGE);
    }

    /** A stat of the custom armor worn and the held weapon (their definition stats and per-item bonuses such as runes), e.g. the Void Fang's own Might. */
    private double worn(Player player, Stat stat) {
        double total = 0;
        java.util.List<ItemStack> pieces = new java.util.ArrayList<>(java.util.Arrays.asList(player.getInventory().getArmorContents()));
        pieces.add(player.getInventory().getItemInMainHand());
        for (ItemStack piece : pieces) {
            total += core.items().statsOf(piece).getOrDefault(stat, 0.0); // definition stats + engraved runes
        }
        return total;
    }
}
