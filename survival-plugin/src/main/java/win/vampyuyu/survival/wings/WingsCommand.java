package win.vampyuyu.survival.wings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Core;

/** {@code /wings}: menu, fly, toggles, claim, info and the admin tools. */
public final class WingsCommand implements CommandExecutor, TabCompleter {
    static final String ADMIN = "vampyuyu.sb.wings.admin";
    private static final List<String> PLAYER_SUBS = List.of("help", "fly", "toggle", "claim", "info");
    private static final List<String> ADMIN_SUBS = List.of("give", "revoke", "reload");
    private static final List<String> TOGGLES = List.of("trail", "bar", "doublejump", "sounds");

    private final Core core;
    private final WingsService service;
    private final FlightManager flight;
    private final WingsGui gui;
    private final Runnable reload;

    public WingsCommand(Core core, WingsService service, FlightManager flight, WingsGui gui, Runnable reload) {
        this.core = core;
        this.service = service;
        this.flight = flight;
        this.gui = gui;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (sender instanceof Player p) {
                    gui.open(p);
                } else {
                    help(sender);
                }
            }
            case "help", "?" -> help(sender);
            case "fly" -> withPlayer(sender, p -> {
                String refusal = flight.toggleFly(p);
                if (refusal != null) {
                    core.msg().error(p, refusal);
                }
            });
            case "toggle" -> withPlayer(sender, p -> toggle(p, args));
            case "claim" -> withPlayer(sender, p -> claim(p, args));
            case "info" -> info(sender, args);
            case "give" -> admin(sender, () -> give(sender, args));
            case "revoke" -> admin(sender, () -> revoke(sender, args));
            case "reload" -> admin(sender, () -> {
                reload.run();
                core.msg().ok(sender, "Wings configuration reloaded.");
            });
            default -> {
                core.msg().error(sender, "Unknown sub-command. Try /wings help.");
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        core.msg().info(sender, "/wings - open the Wings menu (upgrade, recover, settings)");
        core.msg().info(sender, "/wings fly - start or stop flying (when double jump is off)");
        core.msg().info(sender, "/wings toggle <trail|bar|doublejump|sounds> - change your preferences");
        core.msg().info(sender, "/wings claim [confirm] - recover lost Wings");
        core.msg().info(sender, "/wings info - your Wings status");
        if (sender.hasPermission(ADMIN)) {
            core.msg().info(sender, "/wings info <player> - status of a player");
            core.msg().info(sender, "/wings give <player> [level] - give (or replace) the Wings");
            core.msg().info(sender, "/wings revoke <player> [reset] - take the Wings away");
            core.msg().info(sender, "/wings reload - reload wings.yml");
        }
    }

    private void withPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player p) {
            action.accept(p);
        } else {
            core.msg().error(sender, "Only players can use this.");
        }
    }

    private void admin(CommandSender sender, Runnable action) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().error(sender, "You do not have permission to do that.");
            return;
        }
        action.run();
    }

    private void toggle(Player p, String[] args) {
        if (args.length < 2 || !TOGGLES.contains(args[1].toLowerCase(Locale.ROOT))) {
            core.msg().error(p, "Usage: /wings toggle <trail|bar|doublejump|sounds>");
            return;
        }
        WingsData d = service.data(p.getUniqueId());
        boolean now;
        String name;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "trail" -> { d.trail = !d.trail; now = d.trail; name = "Particle trail"; }
            case "bar" -> { d.bar = !d.bar; now = d.bar; name = "Energy bar"; }
            case "doublejump" -> { d.doubleJump = !d.doubleJump; now = d.doubleJump; name = "Double jump to fly"; }
            default -> { d.sounds = !d.sounds; now = d.sounds; name = "Wing beat sounds"; }
        }
        core.profiles().markDirty(p.getUniqueId());
        core.msg().ok(p, name + ": " + (now ? "ON" : "OFF"));
    }

    private void claim(Player p, String[] args) {
        WingsSettings s = service.settings();
        boolean confirm = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        WingsData d = service.data(p.getUniqueId());
        if (!confirm && d.received && !service.hasValidWings(p) && service.claimRemaining(p) == 0 && !d.pendingReturn) {
            core.msg().warn(p, "Recovering your Wings costs " + win.vampyuyu.survival.core.Msg.coins(s.claimCoins)
                + " and the old pair turns to dust. Type /wings claim confirm to proceed.");
            return;
        }
        gui.reportClaim(p, service.claim(p));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length >= 2 && !(sender instanceof Player p && p.getName().equalsIgnoreCase(args[1]))) {
            if (!sender.hasPermission(ADMIN)) {
                core.msg().error(sender, "You do not have permission to look at other players.");
                return;
            }
            OfflinePlayer target = resolve(args[1]);
            if (target == null) {
                core.msg().error(sender, "Unknown player: " + args[1]);
                return;
            }
            if (target.isOnline()) {
                service.describe(target).forEach(line -> core.msg().info(sender, line));
            } else {
                core.scheduler().async(() -> {
                    List<String> lines = service.describe(target);  // may read the profile from disk
                    core.scheduler().sync(() -> lines.forEach(line -> core.msg().info(sender, line)));
                });
            }
            return;
        }
        if (!(sender instanceof Player p)) {
            core.msg().error(sender, "Usage: /wings info <player>");
            return;
        }
        service.describe(p).forEach(line -> core.msg().info(sender, line));
        core.msg().info(sender, "Flight energy: " + (flight.energyOf(p.getUniqueId()) < 0 ? "-"
            : Math.round(flight.energyOf(p.getUniqueId()))));
        long combat = flight.combatRemaining(p.getUniqueId());
        if (combat > 0) {
            core.msg().info(sender, "Combat tag: " + (combat / 1000 + 1) + "s left");
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.msg().error(sender, "Usage: /wings give <player> [level]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            core.msg().error(sender, "Player not online: " + args[1]);
            return;
        }
        Integer level = null;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                core.msg().error(sender, "The level must be a number from 1 to " + service.settings().maxLevel + ".");
                return;
            }
            if (level < 1 || level > service.settings().maxLevel) {
                core.msg().error(sender, "The level must be between 1 and " + service.settings().maxLevel + ".");
                return;
            }
        }
        service.adminGive(target, level, "admin:" + sender.getName());
        core.msg().ok(sender, "Gave The Wings (level " + service.settings().clampLevel(service.data(target.getUniqueId()).level)
            + ") to " + target.getName() + ". Older copies turned inert.");
        if (!target.equals(sender)) {
            core.msg().ok(target, "You received The Wings!");
        }
    }

    private void revoke(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.msg().error(sender, "Usage: /wings revoke <player> [reset]");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            core.msg().error(sender, "Unknown player: " + args[1]);
            return;
        }
        boolean reset = args.length >= 3 && args[2].equalsIgnoreCase("reset");
        service.revoke(target.getUniqueId(), reset);
        core.msg().ok(sender, "Revoked the Wings of " + args[1] + (reset ? " (they can earn them again)." : "."));
        if (target.isOnline() && target.getPlayer() != null && !target.getPlayer().equals(sender)) {
            core.msg().warn(target.getPlayer(), "Your Wings were taken away.");
        }
    }

    /** An online player by exact name, else a known offline player (never creates a profile for a stranger). */
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (sender.hasPermission(ADMIN)) {
                out.addAll(ADMIN_SUBS);
            }
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 2) {
            switch (sub) {
                case "toggle" -> out.addAll(TOGGLES);
                case "claim" -> out.add("confirm");
                case "info", "give", "revoke" -> {
                    if (admin) {
                        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    }
                }
                default -> { }
            }
        } else if (args.length == 3 && admin) {
            if (sub.equals("give")) {
                for (int i = 1; i <= service.settings().maxLevel; i++) {
                    out.add(Integer.toString(i));
                }
            } else if (sub.equals("revoke")) {
                out.add("reset");
            }
        }
        return filter(out, args[args.length - 1]);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    /** Used by tests: the UUID a name resolves to, or null. */
    UUID resolveId(String name) {
        OfflinePlayer p = resolve(name);
        return p == null ? null : p.getUniqueId();
    }
}
