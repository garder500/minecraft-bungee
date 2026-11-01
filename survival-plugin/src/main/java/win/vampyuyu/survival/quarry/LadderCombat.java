package win.vampyuyu.survival.quarry;

import java.util.Optional;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
 * way. When the items feature is loaded its combat listener does this and this one stays out of the way.
 */
final class LadderCombat implements Listener {
    private final Core core;
    private final ItemsSettings settings;
    private final SurgeTracker surges = new SurgeTracker();

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
        if (damage == null) {
            return;
        }
        double charge = attackStrength(player);
        double hit = DamageFormula.base(damage, settings.damageBonus, worn(player, Stat.MIGHT));
        ElementTraits.Traits traits = ElementTraits.read(target);
        hit *= DamageFormula.elementMultiplier(def.get().element(), traits.weakTo(), traits.resists(), worn(player, Stat.ATTUNEMENT),
            settings.elementWeakness, settings.elementResistance);
        if (surges.hit(player.getUniqueId(), target.getUniqueId(), charge >= settings.surgeMinCharge, (int) settings.surgeEvery)) {
            hit *= settings.surgeMultiplier;
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, target.getHeight() * 0.6, 0), 12,
                0.3, 0.4, 0.3, 0.05);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
        }
        if (settings.useAttackCooldown) {
            hit *= charge;
        }
        event.setDamage(hit);
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

    /** A stat of the custom armor worn and the held weapon (their definition stats), e.g. the Void Fang's own Might. */
    private double worn(Player player, Stat stat) {
        double total = 0;
        java.util.List<ItemStack> pieces = new java.util.ArrayList<>(java.util.Arrays.asList(player.getInventory().getArmorContents()));
        pieces.add(player.getInventory().getItemInMainHand());
        for (ItemStack piece : pieces) {
            total += core.items().idOf(piece).flatMap(core.items()::def)
                .map(d -> d.stats().getOrDefault(stat, 0.0)).orElse(0.0);
        }
        return total;
    }
}
