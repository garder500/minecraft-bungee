package win.vampyuyu.survival.bastion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** {@code /bastion [list|info <id>|enter <id>|leave]} and {@code /bastion admin <list|abort|reload>}. */
public final class BastionCommand implements CommandExecutor, TabCompleter {
    public static final String ENTER = "vampyuyu.sb.bastion.enter";
    private final Core core;
    private final BastionService service;

    public BastionCommand(Core core, BastionService service) {
        this.core = core;
        this.service = service;
    }

    public void register() {
        core.commands().command("bastion").aliases("bst").description("Bastions: the fortresses of the Kingdom Guardians")
            .usage("/bastion [info <id>|enter <id>|leave]").permission(ENTER, PermissionDefault.TRUE)
            .executor(this).tab(this).register();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "enter" -> enter(sender, args);
            case "leave" -> leave(sender);
            case "admin" -> admin(sender, args);
            default -> core.msg().info(sender, "/bastion [info <id>|enter <id>|leave]");
        }
        return true;
    }

    private void list(CommandSender sender) {
        core.msg().mini(sender, "<gold><bold>Bastions");
        for (BastionConfig.Bastion b : service.cfg().bastions().values()) {
            String key = core.items().def(b.keyItem()).map(d -> d.name()).orElse(b.keyItem());
            int have = sender instanceof Player p ? service.keysOf(p, b.keyItem()) : 0;
            Component line = Msg.component("<yellow>" + b.name() + " <gray>(" + b.realm() + ", " + b.minPlayers() + "-"
                + b.maxPlayers() + " players) " + (have > 0 ? "<green>" + key + " x" + have : "<red>needs: " + key))
                .clickEvent(ClickEvent.runCommand("/bastion info " + b.id()))
                .hoverEvent(HoverEvent.showText(Msg.component("<gray>" + b.tagline())));
            sender.sendMessage(line);
        }
        if (service.cfg().bastions().isEmpty()) {
            core.msg().warn(sender, "No Bastion is set up.");
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            list(sender);
            return;
        }
        var found = service.cfg().get(args[1]);
        if (found.isEmpty()) {
            core.msg().error(sender, "There is no Bastion called " + args[1] + ".");
            return;
        }
        BastionConfig.Bastion b = found.get();
        String key = core.items().def(b.keyItem()).map(d -> d.name()).orElse(b.keyItem());
        core.msg().mini(sender, "<gold><bold>" + b.name() + " <gray>- " + b.realm());
        core.msg().mini(sender, "<gray>" + b.tagline());
        core.msg().mini(sender, "<gray>Key: <white>" + key + " <gray>(spent by the party leader when the Guardian wakes)");
        core.msg().mini(sender, "<gray>Party: <white>" + b.minPlayers() + "-" + b.maxPlayers() + " <gray>- lives: <white>" + b.deathsAllowed()
            + " <gray>- time: <white>" + b.timeLimitSeconds() / 60 + " min");
        core.msg().mini(sender, "<gray>Pays Memory Shards by contribution at your Resonance rank (/resonance). "
            + "A lost fight pays a small part.");
        sender.sendMessage(Msg.component("<yellow><bold>[Click to enter]").clickEvent(ClickEvent.runCommand("/bastion enter " + b.id())));
    }

    private void enter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            core.msg().error(sender, "Only a player can enter a Bastion.");
            return;
        }
        if (args.length < 2) {
            list(sender);
            return;
        }
        String problem = service.enter(p, args[1]);
        if (problem != null) {
            core.msg().error(p, problem);
        }
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player p) || !service.inBastion(p.getUniqueId())) {
            core.msg().error(sender, "You are not in a Bastion.");
            return;
        }
        service.leave(p.getUniqueId(), false);
        core.msg().info(p, "You left the Bastion. Its rewards are forfeited.");
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(BastionListener.ADMIN)) {
            core.msg().error(sender, "You are not allowed to do that.");
            return;
        }
        String sub = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                List<String> runs = service.describeRuns();
                core.msg().info(sender, runs.isEmpty() ? "No Bastion is running." : String.join("\n", runs));
            }
            case "abort" -> core.msg().ok(sender, "Closed " + service.abort(args.length > 2 && !args[2].equals("all") ? args[2] : null)
                + " Bastion(s).");
            case "reload" -> {
                BastionConfig fresh = BastionConfig.load(core.data());
                service.reload(fresh);
                core.msg().ok(sender, "Reloaded " + fresh.bastions().size() + " Bastions, " + fresh.problems().size() + " problems.");
                fresh.problems().forEach(x -> core.msg().warn(sender, x));
            }
            default -> core.msg().info(sender, "/bastion admin <list|abort [world|all]|reload>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("list", "info", "enter", "leave"));
            if (sender.hasPermission(BastionListener.ADMIN)) {
                out.add("admin");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("enter"))) {
            out.addAll(service.cfg().bastions().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission(BastionListener.ADMIN)) {
            out.addAll(List.of("list", "abort", "reload"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
