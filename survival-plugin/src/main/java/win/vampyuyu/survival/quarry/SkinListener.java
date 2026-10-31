package win.vampyuyu.survival.quarry;

import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Right-click a skin token to unlock the skin for the account. */
final class SkinListener implements Listener {
    private final Core core;
    private final SkinService skins;

    SkinListener(Core core, SkinService skins) {
        this.core = core;
        this.skins = skins;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Optional<Skin> skin = core.items().idOf(event.getItem()).flatMap(Skin::ofToken);
        if (skin.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!SkyblockWorlds.isSkyblock(player.getWorld().getName())) {
            core.msg().warn(player, "Skins can only be unlocked in Skyblock.");
            return;
        }
        if (!skins.unlock(player.getUniqueId(), skin.get())) {
            core.msg().warn(player, "You already own the " + skin.get().title + " skin.");
            return;
        }
        event.getItem().setAmount(event.getItem().getAmount() - 1);
        core.msg().ok(player, "Skin unlocked: " + skin.get().title + ". Use /skins to put it on a sword.");
    }
}
