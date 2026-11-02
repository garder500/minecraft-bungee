package win.vampyuyu.survival.promotion;

import java.util.UUID;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import win.vampyuyu.survival.api.RealmResourceApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.core.Core;

/** Feeds the history of the players and the steps of their trials from what happens in the game. */
final class PromotionListener implements Listener {
    private final Core core;
    private final PromotionService service;

    PromotionListener(Core core, PromotionService service) {
        this.core = core;
        this.service = service;
    }

    /** Hostile creatures killed, alone or with another player close by. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !(event.getEntity() instanceof Monster)) {
            return;
        }
        UUID id = killer.getUniqueId();
        service.bump(id, PromotionRules.KILLS, 1);
        service.record(id, StepKind.KILL, "any", 1);
        boolean withOthers = killer.getNearbyEntities(PromotionRules.COOP_RADIUS, PromotionRules.COOP_RADIUS, PromotionRules.COOP_RADIUS)
            .stream().anyMatch(e -> e instanceof Player other && !other.equals(killer));
        if (withOthers) {
            service.bump(id, PromotionRules.COOP, 1);
            service.record(id, StepKind.COOP, "any", 1);
        }
    }

    /** Items crafted (one per craft click, a shift-click counts once). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            service.bump(player.getUniqueId(), PromotionRules.CRAFTS, 1);
            service.record(player.getUniqueId(), StepKind.CRAFT, "any", 1);
        }
    }

    /** Bosses and Bastions: every player who dealt damage gets the kill, alone or in a group. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBoss(BossKillEvent event) {
        boolean solo = event.getTopDamagers().size() <= 1;
        boolean bastion = event.getResonance() > 0;
        for (BossKillEvent.Damager damager : event.getTopDamagers()) {
            UUID id = damager.player();
            service.bump(id, solo ? PromotionRules.BOSS_SOLO : PromotionRules.BOSS_GROUP, 1);
            service.record(id, solo ? StepKind.BOSS_SOLO : StepKind.BOSS_GROUP, event.getBossId(), 1);
            service.record(id, StepKind.BOSS_ANY, event.getBossId(), 1);
            if (bastion) {
                service.bump(id, PromotionRules.BASTIONS, 1);
                service.record(id, StepKind.BASTION, event.getBossId(), 1);
            }
        }
    }

    /** Entries into a Realm world. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        core.service(RealmResourceApi.class).ifPresent(realms -> {
            Realm realm = realms.realmOf(event.getPlayer().getWorld().getName());
            if (realm != null) {
                service.enteredRealm(event.getPlayer().getUniqueId(), realm.id());
            }
        });
    }
}
