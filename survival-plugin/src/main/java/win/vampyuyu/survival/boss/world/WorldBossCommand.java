package win.vampyuyu.survival.boss.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import win.vampyuyu.survival.boss.BossManager;
import win.vampyuyu.survival.core.Core;

/** {@code /worldboss status} for everybody; {@code spawn}, {@code cancel}, {@code schedule} for admins. */
public final class WorldBossCommand implements CommandExecutor, TabCompleter {
    public static final String ADMIN = "vampyuyu.sb.boss.admin";

    private final Core core;
    private final BossManager bosses;
    private final WorldBossManager manager;

    public WorldBossCommand(Core core, BossManager bosses, WorldBossManager manager) {
        this.core = core;
        this.bosses = bosses;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> manager.status().forEach(line -> core.msg().mini(sender, line));
            case "spawn" -> {
                if (!admin(sender)) {
                    return true;
                }
                String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
                if (id != null && bosses.config().definition(id).isEmpty()) {
                    core.msg().error(sender, "Unknown boss '" + id + "'.");
                } else if (manager.spawnEvent(id)) {
                    core.msg().ok(sender, "World boss event started.");
                } else {
                    core.msg().error(sender, "Could not start an event (one is already running, or the spot is not available).");
                }
            }
            case "cancel" -> {
                if (admin(sender)) {
                    if (manager.cancelEvent()) {
                        core.msg().ok(sender, "The world boss event was cancelled.");
                    } else {
                        core.msg().error(sender, "No event is running.");
                    }
                }
            }
            case "warp" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    core.msg().error(sender, "Only players can teleport.");
                    return true;
                }
                String refusal = manager.warp(player);
                if (refusal != null) {
                    core.msg().error(player, refusal);
                }
            }
            case "addspot" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (!(sender instanceof org.bukkit.entity.Player player) || args.length < 2) {
                    core.msg().info(sender, "Usage (as a player): /worldboss addspot <name>  - saves the spot where you stand");
                    return true;
                }
                String refusal = manager.addSpot(args[1], player.getLocation());
                if (refusal != null) {
                    core.msg().error(sender, refusal);
                } else {
                    core.msg().ok(sender, "Event spot '" + args[1] + "' saved. It is used next to the ones in boss.yml.");
                }
            }
            case "removespot" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 2 || !manager.removeSpot(args[1])) {
                    core.msg().error(sender, "No saved spot with that name. Spots from boss.yml are edited in the file.");
                } else {
                    core.msg().ok(sender, "Event spot removed.");
                }
            }
            case "schedule" -> {
                if (!admin(sender)) {
                    return true;
                }
                try {
                    manager.scheduleIn(Integer.parseInt(args.length > 1 ? args[1] : "x"));
                    core.msg().ok(sender, "The next event is rescheduled.");
                } catch (NumberFormatException e) {
                    core.msg().info(sender, "Usage: /worldboss schedule <minutes from now>");
                }
            }
            default -> {
                core.msg().info(sender, "/worldboss status - next event, location and leaderboard");
                core.msg().info(sender, "/worldboss warp - teleport to the running event");
                if (sender.hasPermission(ADMIN)) {
                    core.msg().info(sender, "/worldboss spawn [boss] | cancel | schedule <minutes>");
                    core.msg().info(sender, "/worldboss addspot <name> | removespot <name> - event spots saved in game");
                }
            }
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return true;
        }
        core.msg().error(sender, "You do not have permission.");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("status", "warp"));
            if (sender.hasPermission(ADMIN)) {
                out.addAll(List.of("spawn", "cancel", "schedule", "addspot", "removespot"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("removespot") && sender.hasPermission(ADMIN)) {
            manager.state().spots.forEach(r -> out.add(r.name));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn") && sender.hasPermission(ADMIN)) {
            out.addAll(bosses.config().bosses().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
