package win.vampyuyu.survival.hellgao;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.BastionApi;
import win.vampyuyu.survival.api.BreathApi;
import win.vampyuyu.survival.boss.BossItems;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/**
 * {@code /hellgao [status|claim|enter|ascension <0-5>|title <id|none>|aura|lore]} and
 * {@code /hellgao admin <give|window|reload>}. The command never announces itself: a player with nothing to do with
 * Hell Gao is told they feel nothing (GDD 19.4: it does not announce itself).
 */
public final class HellGaoCommand implements CommandExecutor, TabCompleter {
    public static final String USE = "vampyuyu.sb.hellgao.use";
    public static final String ADMIN = "vampyuyu.sb.hellgao.admin";
    private static final String NOTHING = "You feel nothing out of the ordinary.";
    private final Core core;
    private final HellGaoService service;

    HellGaoCommand(Core core, HellGaoService service) {
        this.core = core;
        this.service = service;
    }

    void register() {
        core.permissions().register(ADMIN, "Hell Gao administration (keys, window)", PermissionDefault.OP);
        core.commands().command("hellgao").aliases("anomaly").description("The Anomaly between two pulses")
            .usage("/hellgao [status|claim|enter|ascension <0-5>|title <id|none>|aura|lore]")
            .permission(USE, PermissionDefault.TRUE).executor(this).tab(this).register();
    }

    private boolean involved(Player p) {
        HellGaoData d = service.data(p.getUniqueId());
        return p.hasPermission(ADMIN) || service.keysOf(p) > 0 || d.clears > 0
            || core.service(BreathApi.class).map(b -> b.keys(p.getUniqueId()) > 0 || b.fragments(p.getUniqueId()) > 0).orElse(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
                admin(sender, args);
            } else {
                core.msg().error(sender, "Only a player can use this.");
            }
            return true;
        }
        if (!service.cfg().enabled || !core.profiles().isLoaded(p.getUniqueId())) {
            core.msg().info(p, NOTHING);
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(p, args);
            return true;
        }
        if (!involved(p)) {
            core.msg().info(p, NOTHING);
            return true;
        }
        switch (sub) {
            case "status" -> status(p);
            case "claim" -> {
                String problem = service.claim(p);
                if (problem != null) {
                    core.msg().error(p, problem);
                } else {
                    core.msg().mini(p, "<light_purple>The key turns into something that points at nothing.");
                }
            }
            case "enter" -> {
                String problem = core.service(BastionApi.class).map(b -> b.enter(p, service.cfg().bastionId)).orElse("Nothing answers.");
                if (problem != null) {
                    core.msg().error(p, problem);
                }
            }
            case "ascension" -> ascension(p, args);
            case "title" -> title(p, args);
            case "aura" -> aura(p);
            case "lore" -> lore(p);
            default -> core.msg().info(p, "/hellgao [status|claim|enter|ascension <0-5>|title <id|none>|aura|lore]");
        }
        return true;
    }

    private void status(Player p) {
        HellGaoConfig cfg = service.cfg();
        HellGaoData d = service.data(p.getUniqueId());
        core.msg().mini(p, "<dark_purple><bold>The Anomaly");
        var breath = core.service(BreathApi.class);
        core.msg().mini(p, "<gray>Interpulsation Fragments: <white>" + breath.map(b -> b.fragments(p.getUniqueId())).orElse(0)
            + "<gray>, Breath keys: <white>" + breath.map(b -> b.keys(p.getUniqueId())).orElse(0)
            + "<gray>, Interpulse Keys: <white>" + service.keysOf(p));
        core.msg().mini(p, "<gray>Paragon: " + (service.paragon(p.getUniqueId()) ? "<green>yes" : "<red>not yet"));
        long wait = service.secondsToOpen();
        core.msg().mini(p, "<gray>The Rift: " + (wait == 0 ? "<green>open, between two pulses"
            : wait < 0 ? "<red>shut" : "<red>shut <gray>(opens in about " + Msg.duration(wait * 1000) + ")"));
        boolean resonance = service.resonanceVDone(p.getUniqueId());
        core.msg().mini(p, "<gray>Ascension: <white>" + service.effectiveAscension(p.getUniqueId()) + "<gray> (best "
            + d.bestAscension + (resonance ? "" : ", needs " + cfg.ascensionResonanceMin + " Guardians at Resonance V") + ")");
        core.msg().mini(p, "<gray>Clears: <white>" + d.clears + "<gray>, titles: <white>" + d.titles.size() + "<gray>, lore: <white>"
            + d.lore.size() + "/" + cfg.lore.size());
    }

    private void ascension(Player p, String[] args) {
        if (args.length < 2) {
            core.msg().info(p, "/hellgao ascension <0-" + HellGaoMath.MAX_ASCENSION + ">");
            return;
        }
        int rank;
        try {
            rank = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            core.msg().error(p, "That is not a number.");
            return;
        }
        String problem = service.select(p, rank);
        if (problem != null) {
            core.msg().error(p, problem);
        } else {
            core.msg().ok(p, rank == 0 ? "Plain fight." : "Ascension " + rank + ": Hell Gao x"
                + Msg.number(HellGaoMath.hpMultiplier(rank)) + " health, x" + Msg.number(HellGaoMath.damageMultiplier(rank)) + " damage.");
        }
    }

    private void title(Player p, String[] args) {
        HellGaoData d = service.data(p.getUniqueId());
        if (args.length < 2) {
            if (d.titles.isEmpty()) {
                core.msg().info(p, "You hold no title.");
            }
            for (HellGaoConfig.Title t : service.cfg().titles) {
                if (d.titles.contains(t.id())) {
                    core.msg().mini(p, "<gray>" + t.id() + ": <reset>" + t.name());
                }
            }
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals("none")) {
            service.edit(p.getUniqueId(), x -> x.shownTitle = "");
            core.msg().ok(p, "No title shown.");
        } else if (d.titles.contains(id) && service.cfg().title(id).isPresent()) {
            service.edit(p.getUniqueId(), x -> x.shownTitle = id);
            core.msg().ok(p, "Title set.");
        } else {
            core.msg().error(p, "You do not hold that title.");
        }
    }

    private void aura(Player p) {
        HellGaoData d = service.data(p.getUniqueId());
        if (!d.titles.contains(service.cfg().auraTitle)) {
            core.msg().error(p, "You have not earned the aura.");
            return;
        }
        boolean on = !d.aura;
        service.edit(p.getUniqueId(), x -> x.aura = on);
        core.msg().ok(p, "Interpulse aura " + (on ? "on" : "off") + ".");
    }

    private void lore(Player p) {
        HellGaoData d = service.data(p.getUniqueId());
        boolean any = false;
        for (HellGaoConfig.Lore l : service.cfg().lore) {
            if (d.lore.contains(l.id())) {
                core.msg().mini(p, "<dark_purple>- <gray>" + l.text());
                any = true;
            }
        }
        if (!any) {
            core.msg().info(p, "You remember nothing yet.");
        }
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().info(sender, NOTHING);
            return;
        }
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                Player target = args.length < 4 ? null : Bukkit.getPlayerExact(args[2]);
                String what = args.length < 4 ? "" : args[3].toLowerCase(Locale.ROOT);
                int n = 1;
                try {
                    n = args.length > 4 ? Math.max(1, Math.min(64, Integer.parseInt(args[4]))) : 1;
                } catch (NumberFormatException e) {
                    n = 1;
                }
                if (target == null || !what.equals("key")) {
                    core.msg().info(sender, "/hellgao admin give <player> key [amount]");
                    return;
                }
                service.give(target, BossItems.INTERPULSE_KEY, n);
                core.msg().ok(sender, "Gave " + n + " " + what + " to " + target.getName() + ".");
            }
            case "window" -> {
                String action = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
                if (action.equals("open")) {
                    int minutes = 5;
                    try {
                        minutes = args.length > 3 ? Math.max(1, Integer.parseInt(args[3])) : 5;
                    } catch (NumberFormatException e) {
                        minutes = 5;
                    }
                    service.openWindow(minutes * 60);
                    core.msg().ok(sender, "The window is open for " + minutes + " minutes.");
                } else if (action.equals("close")) {
                    service.closeWindow();
                    core.msg().ok(sender, "The manual window is closed (the clock still applies).");
                } else {
                    core.msg().info(sender, "The window is " + (service.windowOpen() ? "open" : service.secondsToOpen() < 0 ? "shut" : "shut, opens in "
                        + Msg.duration(service.secondsToOpen() * 1000)) + ". /hellgao admin window <open [minutes]|close>");
                }
            }
            case "reload" -> {
                HellGaoConfig fresh = HellGaoConfig.load(core.data());
                service.reload(fresh);
                core.msg().ok(sender, "Reloaded, " + fresh.problems().size() + " problems.");
                fresh.problems().forEach(x -> core.msg().warn(sender, x));
            }
            default -> core.msg().info(sender, "/hellgao admin <give|window|reload>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("status", "claim", "enter", "ascension", "title", "aura", "lore"));
            if (sender.hasPermission(ADMIN)) {
                out.add("admin");
            }
        } else if (sender.hasPermission(ADMIN) && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                out.addAll(List.of("give", "window", "reload"));
            } else if (args.length == 3 && args[1].equalsIgnoreCase("window")) {
                out.addAll(List.of("open", "close"));
            } else if (args.length == 3) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
                out.add("key");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("title") && sender instanceof Player p
                && core.profiles().isLoaded(p.getUniqueId())) {
            out.add("none");
            out.addAll(service.data(p.getUniqueId()).titles);
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
