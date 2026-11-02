package win.vampyuyu.survival.quarry;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.items.combat.DamageFormula;

/**
 * The defence side of the ore ladder armor when the full stat engine (the items feature) is not loaded: Ward cuts
 * the damage of hits ({@code x 100 / (100 + Ward)}), Vitality adds health ({@code Vitality / 5}, so the 100 Vitality
 * of a bare player is the vanilla 20). The boss hits are tuned with the same rule ({@code PowerCurve.bossHitDamage}).
 */
final class LadderDefense implements Listener {
    static final NamespacedKey VITALITY = new NamespacedKey(win.vampyuyu.survival.core.Items.NAMESPACE, "ladder_vitality");

    private final Core core;

    LadderDefense(Core core) {
        this.core = core;
    }

    private boolean active() {
        return core.service(StatsApi.class).isEmpty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !active() || !mitigated(event)) {
            return;
        }
        double ward = worn(player, Stat.WARD);
        if (ward > 0) {
            event.setDamage(DamageFormula.mitigate(event.getDamage(), ward));
        }
    }

    /** Hits and spells; not the world (falling, the void, drowning, hunger, fire on the ground). */
    private static boolean mitigated(EntityDamageEvent event) {
        return event instanceof EntityDamageByEntityEvent || switch (event.getCause()) {
            case MAGIC, CUSTOM, WITHER, POISON, ENTITY_EXPLOSION, BLOCK_EXPLOSION, SONIC_BOOM, DRAGON_BREATH -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(PlayerArmorChangeEvent event) {
        core.scheduler().nextTick(() -> refresh(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        core.scheduler().nextTick(() -> refresh(event.getPlayer()));
    }

    /** Sets the health bonus of the worn ladder armor (removed when the stat engine handles health itself). */
    void refresh(Player player) {
        if (!player.isOnline()) {
            return;
        }
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) {
            return;
        }
        health.removeModifier(VITALITY);
        double bonus = active() ? worn(player, Stat.VITALITY) / 5.0 : 0;
        if (bonus > 0) {
            health.addModifier(new AttributeModifier(VITALITY, bonus, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (player.getHealth() > health.getValue()) {
            player.setHealth(health.getValue());
        }
    }

    /** A stat of the custom armor worn (definition stats and per-item bonuses such as runes). */
    private double worn(Player player, Stat stat) {
        double total = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            total += core.items().statsOf(piece).getOrDefault(stat, 0.0); // definition stats + engraved runes
        }
        return total;
    }
}
