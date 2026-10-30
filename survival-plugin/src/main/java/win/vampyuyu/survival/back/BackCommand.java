package win.vampyuyu.survival.back;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class BackCommand implements CommandExecutor, Listener {
    private final BackService back;
    private final TeleportGate gate;

    public BackCommand(BackService back) {
        this(back, TeleportGate.DIRECT);
    }

    public BackCommand(BackService back, TeleportGate gate) {
        this.back = back;
        this.gate = gate;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Location l = event.getPlayer().getLocation();
        back.recordDeath(event.getPlayer().getUniqueId(),
            new BackService.Spot(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /back.");
            return true;
        }
        boolean bypass = player.hasPermission("vampyuyu.back.bypass");
        switch (back.peek(player.getUniqueId(), bypass)) {
            case BackService.Result.NoDeath n -> player.sendMessage("You have no death location to return to.");
            case BackService.Result.Cooldown c -> {
                long s = (c.remainingMs() + 999) / 1000;
                player.sendMessage("/back is on cooldown: " + s / 60 + "m " + s % 60 + "s left.");
            }
            case BackService.Result.Ok ok -> {
                World world = Bukkit.getWorld(ok.spot().world());
                if (world == null) {
                    player.sendMessage("That world is no longer available.");
                    return true;
                }
                Location target = new Location(world, ok.spot().x(), ok.spot().y(), ok.spot().z(), ok.spot().yaw(), ok.spot().pitch());
                if (!safe(target)) {
                    player.sendMessage("Your death spot is unsafe (lava, fire or void). Clear it first; the cooldown was not used.");
                    return true;
                }
                String denial = gate.denial(player, target);
                if (denial != null) {
                    player.sendMessage(denial);
                    return true;
                }
                // The cooldown starts when the teleport really happens, not when the command is typed.
                gate.teleport(player, "back", target, () -> {
                    back.confirm(player.getUniqueId(), bypass);
                    player.sendMessage("Returned to your last death location.");
                });
            }
        }
        return true;
    }

    static boolean safe(Location l) {
        World w = l.getWorld();
        if (l.getY() < w.getMinHeight()) {
            return false;
        }
        Material feet = l.getBlock().getType();
        Material below = l.clone().subtract(0, 1, 0).getBlock().getType();
        return feet != Material.LAVA && feet != Material.FIRE && below != Material.LAVA && below != Material.MAGMA_BLOCK;
    }
}
