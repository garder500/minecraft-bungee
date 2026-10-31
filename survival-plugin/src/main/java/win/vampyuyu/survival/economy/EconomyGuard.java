package win.vampyuyu.survival.economy;

import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Skyblock coins, the market, the Bazaar and the Auction House exist in the Skyblock worlds only, never in Survival. */
final class EconomyGuard implements Listener {
    static final Set<String> COMMANDS = Set.of("balance", "bal", "coins", "purse", "pay", "baltop", "topcoins", "bank",
        "sbbank", "bazaar", "bz", "auctionhouse", "ah", "auction", "shop", "market", "sellall", "sellinventory");

    private final Core core;

    EconomyGuard(Core core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        String label = message.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        Player player = event.getPlayer();
        if (COMMANDS.contains(label) && !SkyblockWorlds.isSkyblock(player.getWorld().getName())) {
            event.setCancelled(true);
            core.msg().warn(player, "Skyblock coins and markets only work in Skyblock.");
        }
    }
}
