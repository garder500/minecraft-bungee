package win.vampyuyu.survival.realm;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.core.Core;

/** Gives the resources of the Realm a player is in when they break one of its blocks or kill one of its mobs. */
final class RealmListener implements Listener {
    private final Core core;
    private final RealmService service;

    RealmListener(Core core, RealmService service) {
        this.core = core;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Realm realm = service.realmOf(event.getBlock().getWorld().getName());
        if (realm == null) {
            return;
        }
        String block = event.getBlock().getType().name();
        for (RealmResource r : service.resources().values()) {
            if (r.realm() == realm && r.blocks().contains(block)) {
                give(event.getPlayer(), r, "block", event.getBlock().getLocation().add(0.5, 0.5, 0.5));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Realm realm = service.realmOf(event.getEntity().getWorld().getName());
        if (realm == null) {
            return;
        }
        String mob = event.getEntityType().name().toUpperCase(Locale.ROOT);
        for (RealmResource r : service.resources().values()) {
            if (r.realm() == realm && r.mobs().contains(mob)) {
                give(killer, r, "mob", event.getEntity().getLocation());
            }
        }
    }

    private void give(Player player, RealmResource resource, String kind, Location at) {
        for (ItemStack stack : service.roll(player.getUniqueId(), resource.id(), kind)) {
            player.getInventory().addItem(stack).values().forEach(rest -> at.getWorld().dropItemNaturally(at, rest));
            core.msg().ok(player, "You found " + stack.getAmount() + " " + resource.id().substring(0, 1).toUpperCase(Locale.ROOT)
                + resource.id().substring(1) + "!");
        }
    }
}
