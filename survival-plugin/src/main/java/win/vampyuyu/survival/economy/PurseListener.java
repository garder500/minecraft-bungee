package win.vampyuyu.survival.economy;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Coins lost from the purse when a player dies (default: only when killed by another player, 50%, capped). */
final class PurseListener implements Listener {
    private final Core core;
    private final EconomyService economy;
    private final CoinCommands commands;

    PurseListener(Core core, EconomyService economy, CoinCommands commands) {
        this.core = core;
        this.economy = economy;
        this.commands = commands;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        EconomySettings s = economy.settings();
        if (s.deathCause().equals("never") || s.deathPercent() <= 0 || victim.hasMetadata("NPC")
                || victim.getWorld() == null || !SkyblockWorlds.isSkyblock(victim.getWorld().getName())) {
            return;
        }
        Player killer = victim.getKiller();
        boolean pvp = killer != null && !killer.equals(victim) && !killer.hasMetadata("NPC");
        if (!pvp && !s.deathCause().equals("any")) {
            return;
        }
        long purse = economy.balanceCents(victim.getUniqueId());
        if (purse < s.deathMinBalanceCents()) {
            return;
        }
        long lose = Math.min(Money.basisPoints(purse, s.deathPercent() * 100), s.deathMaxCents());
        long lost = economy.loseOnDeath(victim.getUniqueId(), lose, pvp ? "death:pvp" : "death");
        if (lost <= 0) {
            return;
        }
        core.msg().mini(victim, "<red>You died and lost <gold>" + Money.coins(lost)
            + "</gold> from your purse. <gray>Coins in the bank are safe.");
        if (pvp && s.deathToKiller()) {
            long got = economy.addCents(killer.getUniqueId(), lost, "death:loot");
            if (got > 0) {
                core.msg().mini(killer, "<green>You took <gold>" + Money.coins(got) + "</gold> from "
                    + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(victim.getName()) + ".");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        commands.forget(event.getPlayer().getUniqueId());
    }
}
