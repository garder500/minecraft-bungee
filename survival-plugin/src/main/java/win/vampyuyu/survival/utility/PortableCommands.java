package win.vampyuyu.survival.utility;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** /ec, /workbench, /anvil, /grindstone, /stonecutter, /loom: remote crafting blocks, each behind its own permission. */
final class PortableCommands {
    private record Portable(String name, String[] aliases, String description, Consumer<Player> open) {}

    private final Core core;
    private final Msg msg;
    private final TeleportService teleports;
    private final org.bukkit.configuration.file.YamlConfiguration raw;

    PortableCommands(Core core, TeleportService teleports, org.bukkit.configuration.file.YamlConfiguration raw) {
        this.core = core;
        this.msg = core.msg();
        this.teleports = teleports;
        this.raw = raw;
    }

    void register() {
        core.permissions().register(Perms.EC_OTHERS, "Open the ender chest of another online player",
            PermissionDefault.OP);
        List<Portable> all = List.of(
            new Portable("ec", new String[] {"enderchest"}, "Open your ender chest",
                p -> p.openInventory(p.getEnderChest())),
            new Portable("workbench", new String[] {"craft", "wb"}, "Open a crafting table",
                p -> p.openWorkbench(null, true)),
            new Portable("anvil", new String[0], "Open an anvil", p -> p.openAnvil(null, true)),
            new Portable("grindstone", new String[0], "Open a grindstone", p -> p.openGrindstone(null, true)),
            new Portable("stonecutter", new String[0], "Open a stonecutter", p -> p.openStonecutter(null, true)),
            new Portable("loom", new String[0], "Open a loom", p -> p.openLoom(null, true)));
        for (Portable portable : all) {
            boolean open = raw.getBoolean("portable." + portable.name() + ".public",
                portable.name().equals("ec") || portable.name().equals("workbench"));
            core.commands().command(portable.name()).aliases(portable.aliases()).description(portable.description())
                .usage("/" + portable.name() + ("ec".equals(portable.name()) ? " [player]" : ""))
                .permission(Perms.PREFIX + portable.name(), open ? PermissionDefault.TRUE : PermissionDefault.OP)
                .executor((s, c, l, a) -> run(s, portable, a))
                .tab((s, c, l, a) -> "ec".equals(portable.name()) && a.length == 1
                    && s.hasPermission(Perms.EC_OTHERS) ? Cmd.match(names(), a[0]) : List.of())
                .register();
        }
    }

    private static List<String> names() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private boolean run(CommandSender sender, Portable portable, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        if (teleports.inDungeon(player) || SkyblockWorlds.isDungeon(player.getWorld().getName())) {
            msg.error(player, "/" + portable.name() + " is disabled inside dungeons.");
            return true;
        }
        if ("ec".equals(portable.name()) && args.length > 0) {
            if (!player.hasPermission(Perms.EC_OTHERS)) {
                msg.error(player, "You can only open your own ender chest.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                msg.error(player, "Player '" + args[0] + "' is not online.");
                return true;
            }
            player.openInventory(target.getEnderChest());
            msg.info(player, "Opened the ender chest of " + target.getName() + ".");
            return true;
        }
        portable.open().accept(player);
        return true;
    }
}
