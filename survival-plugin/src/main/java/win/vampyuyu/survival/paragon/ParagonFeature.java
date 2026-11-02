package win.vampyuyu.survival.paragon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.ParagonApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Paragon (GDD 11.5, 19.1): Transcendence ranks and class ultimates for the players who survived the Astral Interior.
 * Listed after classes and promotion. Ranks give titles, auras, perks and lore, never stats. See
 * docs/skyblock-notes/paragon.md.
 */
public final class ParagonFeature implements Feature {
    static final String ADMIN = "vampyuyu.sb.paragon.admin";
    private ParagonService service;
    private Scheduler.Handle aura;

    @Override
    public String id() {
        return "paragon";
    }

    @Override
    public void enable(Core core) {
        ParagonConfig cfg = ParagonConfig.load(core.data());
        cfg.problems().forEach(p -> core.getPlugin().getLogger().warning("paragon data: " + p));
        service = new ParagonService(core, cfg);
        core.register(ParagonApi.class, service);
        core.register(ParagonService.class, service);
        core.permissions().register(ADMIN, "Make a player a Paragon, give Paragon points", PermissionDefault.OP);
        core.onDataChange(domain -> {
            if (domain.equals("paragon")) {
                service.reload(ParagonConfig.load(core.data()));
            }
        });
        aura = core.scheduler().repeating(40, Math.max(1, cfg.auraSeconds) * 20L, () -> service.drawAuras());
        core.commands().command("paragon")
            .aliases("transcendence")
            .description("Your Paragon rank, title and aura")
            .usage("/paragon [aura <id|on|off> | title <title>]")
            .permission("vampyuyu.sb.paragon.use", PermissionDefault.TRUE)
            .tab((CommandSender sender, Command command, String alias, String[] args) -> tab(sender, args))
            .executor((CommandSender sender, Command command, String label, String[] args) -> run(core, sender, args))
            .register();
    }

    @Override
    public void disable() {
        if (aura != null) {
            aura.cancel();
            aura = null;
        }
    }

    public ParagonService service() { return service; }

    private List<String> tab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("aura", "title"));
            if (sender.hasPermission(ADMIN)) {
                subs.addAll(List.of("grant", "astral", "reset", "reload"));
            }
            return subs;
        }
        if (args.length == 2 && sender instanceof Player p) {
            if (args[0].equalsIgnoreCase("aura")) {
                List<String> out = new ArrayList<>(service.aurasOf(p.getUniqueId()));
                out.addAll(List.of("on", "off"));
                return out;
            }
            if (args[0].equalsIgnoreCase("title")) {
                return service.titlesOf(p.getUniqueId());
            }
        }
        return List.of();
    }

    private boolean run(Core core, CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (List.of("grant", "astral", "reset", "reload").contains(sub)) {
            return admin(core, sender, sub, args);
        }
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can use /paragon.");
            return true;
        }
        switch (sub) {
            case "aura" -> {
                String refusal = args.length < 2 ? "Usage: /paragon aura <id|on|off>" : service.chooseAura(player.getUniqueId(), args[1]);
                if (refusal != null) {
                    core.msg().warn(player, refusal);
                } else {
                    core.msg().ok(player, "Aura updated.");
                }
            }
            case "title" -> {
                String refusal = args.length < 2 ? "Usage: /paragon title <title>"
                    : service.chooseTitle(player.getUniqueId(), String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                if (refusal != null) {
                    core.msg().warn(player, refusal);
                } else {
                    core.msg().ok(player, "You are now " + service.title(player.getUniqueId()) + ".");
                }
            }
            default -> status(core, player);
        }
        return true;
    }

    private void status(Core core, Player player) {
        var id = player.getUniqueId();
        if (!service.config().enabled) {
            core.msg().warn(player, "Paragon is closed right now.");
            return;
        }
        if (!service.isParagon(id)) {
            core.msg().info(player, "You are not a Paragon. Survive the Astral Interior, the heart of the Devourer, to absorb its ether.");
            return;
        }
        int rank = service.rank(id);
        var ranks = service.config().ranks();
        core.msg().info(player, (service.title(id).isEmpty() ? "Paragon" : service.title(id)) + " - Transcendence " + rank
            + (rank == 0 ? "" : " (" + ranks.stream().filter(r -> r.number() == rank).findFirst().map(ParagonConfig.Rank::name).orElse("") + ")")
            + ", " + service.points(id) + " points.");
        var next = ParagonRules.next(ranks, rank);
        if (next == null) {
            core.msg().info(player, "You reached the last Transcendence.");
        } else {
            core.msg().info(player, "Next: " + next.name() + " at " + next.points() + " points. " + next.text());
        }
        service.ultimate(id).ifPresentOrElse(
            u -> core.msg().info(player, "Ultimate: " + u.name() + " - " + u.summary() + " (/class ultimate)"),
            () -> core.msg().info(player, "Your ultimate needs a Master of a class (/promotion)."));
        if (!service.aurasOf(id).isEmpty()) {
            core.msg().info(player, "Auras: " + String.join(", ", service.aurasOf(id)) + " (/paragon aura <id|off>)");
        }
        if (service.titlesOf(id).size() > 1) {
            core.msg().info(player, "Titles: " + String.join(", ", service.titlesOf(id)) + " (/paragon title <title>)");
        }
    }

    private boolean admin(Core core, CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().error(sender, "You cannot do that.");
            return true;
        }
        if (sub.equals("reload")) {
            ParagonConfig fresh = ParagonConfig.load(core.data());
            service.reload(fresh);
            core.msg().ok(sender, "Paragon data reloaded" + (fresh.problems().isEmpty() ? "." : " with " + fresh.problems().size() + " problem(s): "
                + String.join("; ", fresh.problems())));
            return true;
        }
        Player target = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            core.msg().error(sender, "Usage: /paragon <grant <player> <points> | astral <player> | reset <player>> (online player)");
            return true;
        }
        switch (sub) {
            case "astral" -> {
                service.markAstralCleared(target.getUniqueId());
                core.msg().ok(sender, target.getName() + " survived the Astral Interior.");
            }
            case "reset" -> {
                service.reset(target.getUniqueId());
                core.msg().ok(sender, target.getName() + " is no Paragon anymore.");
            }
            default -> {
                int points;
                try {
                    points = args.length < 3 ? 0 : Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    points = 0;
                }
                if (points < 1) {
                    core.msg().error(sender, "Usage: /paragon grant <player> <points>");
                } else if (!service.isParagon(target.getUniqueId())) {
                    core.msg().warn(sender, target.getName() + " is not a Paragon yet (/paragon astral).");
                } else {
                    service.addPoints(target.getUniqueId(), points, "staff");
                    core.msg().ok(sender, target.getName() + " has " + service.points(target.getUniqueId()) + " points.");
                }
            }
        }
        return true;
    }
}
