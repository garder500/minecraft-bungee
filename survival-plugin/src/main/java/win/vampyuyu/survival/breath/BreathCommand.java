package win.vampyuyu.survival.breath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** {@code /breath}: what is happening, the Fracture door, the Fragments. {@code start} and {@code stop} are for admins. */
final class BreathCommand implements CommandExecutor, TabCompleter {
    static final String ADMIN = "vampyuyu.sb.breath.admin";

    private final Core core;
    private final BreathService service;

    BreathCommand(Core core, BreathService service) {
        this.core = core;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status", "info" -> status(sender);
            case "fracture" -> fracture(sender);
            case "start", "stop" -> admin(sender, sub, args);
            default -> core.msg().info(sender, "/breath [status | fracture]");
        }
        return true;
    }

    private void status(CommandSender sender) {
        BreathEvent event = service.event();
        if (event != null && sender instanceof Player player) {
            service.banner(player, event);
        } else if (event != null) {
            core.msg().info(sender, "Running: " + event.title() + " (" + event.id() + ")");
        } else {
            long wait = Math.max(0, service.state().nextAt - System.currentTimeMillis());
            core.msg().info(sender, "The sky is quiet. The next Breath in about " + Msg.duration(wait) + ".");
        }
        if (sender instanceof Player player) {
            BreathData d = service.data(player.getUniqueId());
            core.msg().info(player, "Interpulsation Fragments: " + d.fragments + "/" + service.config().fragmentsPerKey()
                + ", keys: " + d.keys + ". Fragments fall in Fractures and from immune cells, one per Breath at most.");
        }
    }

    private void fracture(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Players only.");
            return;
        }
        String problem = service.enterFracture(player);
        if (problem != null) {
            core.msg().error(player, problem);
        }
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().error(sender, "You may not do that.");
            return;
        }
        if (sub.equals("stop")) {
            service.end(true);
            core.msg().ok(sender, "The running event is over.");
            return;
        }
        BreathEvent event = args.length < 2 ? null : service.config().events().get(args[1].toLowerCase(Locale.ROOT));
        if (event == null) {
            core.msg().error(sender, "Usage: /breath start <" + String.join("|", service.config().events().keySet()) + ">");
            return;
        }
        service.begin(event);
        core.msg().ok(sender, "Started " + event.id() + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("status", "fracture"));
            if (sender.hasPermission(ADMIN)) {
                out.addAll(List.of("start", "stop"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission(ADMIN)) {
            out.addAll(service.config().events().keySet());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }
}
