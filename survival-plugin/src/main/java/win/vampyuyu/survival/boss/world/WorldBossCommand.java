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
                if (sender.hasPermission(ADMIN)) {
                    core.msg().info(sender, "/worldboss spawn [boss] | cancel | schedule <minutes>");
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
            out.add("status");
            if (sender.hasPermission(ADMIN)) {
                out.addAll(List.of("spawn", "cancel", "schedule"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn") && sender.hasPermission(ADMIN)) {
            out.addAll(bosses.config().bosses().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
