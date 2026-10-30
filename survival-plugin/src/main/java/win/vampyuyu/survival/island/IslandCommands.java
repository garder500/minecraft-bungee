package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.island.IslandService.Upgrade;

/** {@code /island} (aliases {@code /is}, {@code /sb-island}), {@code /hub} and {@code /islandadmin}. */
public final class IslandCommands {
    public static final String USE = "vampyuyu.sb.island.use";
    public static final String HUB = "vampyuyu.sb.island.hub";

    /** Player subcommands in help order: name, usage, description. */
    static final String[][] HELP = {
        {"create", "create [type]", "Create your island (opens the type picker)"},
        {"home", "home", "Teleport to your island (alias: go)"},
        {"sethome", "sethome", "Set the island home where you stand"},
        {"visit", "visit <player>", "Visit a player's island"},
        {"invite", "invite <player>", "Invite an online player to your island"},
        {"accept", "accept [player]", "Accept an invitation"},
        {"deny", "deny [player]", "Decline an invitation"},
        {"leave", "leave", "Leave the island you joined"},
        {"kick", "kick <player>", "Remove a member"},
        {"ban", "ban <player>", "Ban a player from the island"},
        {"unban", "unban <player>", "Lift a ban"},
        {"trust", "trust <player>", "Let a guest build (not a member)"},
        {"untrust", "untrust <player>", "Remove a trusted guest"},
        {"setrole", "setrole <player> <co-owner|member>", "Change a member's role"},
        {"members", "members", "Open the member list"},
        {"settings", "settings", "Open the settings menu"},
        {"flag", "flag <flag> <on|off>", "Change one island flag"},
        {"time", "time <time>", "Lock the time of day on the island"},
        {"upgrades", "upgrades", "Open the upgrades menu"},
        {"upgrade", "upgrade <type>", "Buy the next tier (radius, members)"},
        {"reset", "reset [type]", "Wipe and rebuild the island (owner)"},
        {"info", "info [player]", "Show an island"},
        {"level", "level", "Recalculate the island level"},
        {"top", "top", "Open the leaderboard"},
        {"warp", "warp [set <name>|remove|<player>]", "Browse or manage public warps"},
        {"lock", "lock", "Only members and trusted guests may enter"},
        {"unlock", "unlock", "Let visitors in again"},
        {"transfer", "transfer <player>", "Give the island to a member (confirm)"},
        {"border", "border", "Show or hide the island border for you"},
        {"nether", "nether", "Go to your island's Nether platform"},
        {"end", "end", "Go to your island's End platform"},
        {"hub", "hub", "Go to the hub"},
        {"help", "help", "This list"},
    };

    private final IslandService service;
    private final IslandMenus menus;
    private final Core core;
    private final IslandConfig cfg;

    public IslandCommands(IslandService service, IslandMenus menus) {
        this.service = service;
        this.menus = menus;
        this.core = service.core();
        this.cfg = service.config();
    }

    public void register() {
        core.commands().command("island").aliases("is", "sb-island")
            .description("Manage your Skyblock island")
            .usage("/island [create|home|visit|invite|settings|upgrades|top|warp|help]")
            .permission(USE, PermissionDefault.TRUE)
            .executor(this::island).tab(this::tabIsland).register();
        core.commands().command("hub").description("Go to the Skyblock hub").usage("/hub")
            .permission(HUB, PermissionDefault.TRUE)
            .executor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    goHub(player);
                } else {
                    core.msg().error(sender, "Only players can use /hub.");
                }
                return true;
            }).register();
        core.commands().command("islandadmin").aliases("isadmin")
            .description("Island administration")
            .usage("/islandadmin <info|tp|level|rebuild|platforms|delete|list> [player]")
            .permission(IslandService.ADMIN, PermissionDefault.OP)
            .executor(this::admin).tab(this::tabAdmin).register();
    }

    // ---------------------------------------------------------------- helpers

    private void ok(CommandSender sender, String text) {
        core.msg().ok(sender, text);
    }

    private void err(CommandSender sender, String text) {
        core.msg().error(sender, text);
    }

    private boolean reply(CommandSender sender, Result result) {
        if (result.ok()) {
            ok(sender, result.message());
        } else {
            err(sender, result.message());
        }
        return true;
    }

    private void line(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE)));
    }

    private Optional<UUID> target(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            err(sender, "Name a player.");
            return Optional.empty();
        }
        Optional<UUID> found = service.resolve(args[index]);
        if (found.isEmpty()) {
            err(sender, "Unknown player '" + args[index] + "'. They must have played here before.");
        }
        return found;
    }

    private void goHub(Player player) {
        Result result = service.goHub(player);
        if (result.ok()) {
            core.msg().info(player, result.message());
        } else {
            err(player, result.message());
        }
    }

    /** The upgrade families that can be bought right now (minion slots stay hidden until the minions feature reads them). */
    static List<Upgrade> buyable(IslandConfig cfg) {
        List<Upgrade> list = new ArrayList<>();
        for (Upgrade upgrade : Upgrade.values()) {
            if (upgrade != Upgrade.MINIONS || cfg.minionUpgrade) {
                list.add(upgrade);
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- /island

    private boolean island(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            err(sender, "Only players can use /island. Admins: /islandadmin.");
            return true;
        }
        if (args.length == 0) {
            menus.main(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> menus.main(player);
            case "help", "?" -> help(player);
            case "create" -> create(player, args);
            case "home", "go" -> {
                Result result = service.goHome(player);
                if (result.ok()) {
                    core.msg().info(player, result.message());
                } else {
                    err(player, result.message());
                }
            }
            case "nether" -> reply(player, service.shortcut(player, true));
            case "end" -> reply(player, service.shortcut(player, false));
            case "sethome" -> reply(player, service.setHome(player));
            case "visit", "v" -> visit(player, args);
            case "invite" -> target(player, args, 1).ifPresent(t -> reply(player, service.invite(player, t)));
            case "accept" -> reply(player, service.accept(player, args.length > 1 ? args[1] : null));
            case "deny" -> reply(player, service.deny(player, args.length > 1 ? args[1] : null));
            case "leave" -> reply(player, service.leave(player));
            case "kick" -> target(player, args, 1).ifPresent(t -> reply(player, service.kick(player, t)));
            case "ban" -> target(player, args, 1).ifPresent(t -> reply(player, service.ban(player, t)));
            case "unban" -> target(player, args, 1).ifPresent(t -> reply(player, service.unban(player, t)));
            case "trust" -> target(player, args, 1).ifPresent(t -> reply(player, service.trust(player, t)));
            case "untrust" -> target(player, args, 1).ifPresent(t -> reply(player, service.untrust(player, t)));
            case "setrole" -> setRole(player, args);
            case "members" -> menus.members(player);
            case "settings" -> menus.settings(player);
            case "flag" -> flag(player, args);
            case "time" -> time(player, args);
            case "upgrades" -> menus.upgrades(player);
            case "upgrade" -> upgrade(player, args);
            case "reset" -> reset(player, args);
            case "info" -> info(player, args);
            case "level" -> level(player);
            case "top" -> menus.top(player);
            case "warp", "warps" -> warp(player, args);
            case "lock" -> reply(player, service.setLocked(player, true));
            case "unlock" -> reply(player, service.setLocked(player, false));
            case "transfer" -> target(player, args, 1).ifPresent(t -> reply(player, service.transfer(player, t)));
            case "border" -> core.msg().info(player, service.toggleBorder(player)
                ? "The island border is now visible." : "The island border is now hidden.");
            case "hub" -> goHub(player);
            default -> {
                err(player, "Unknown subcommand '" + args[0] + "'.");
                help(player);
            }
        }
        return true;
    }

    private void help(Player player) {
        player.sendMessage(Component.text("Island commands", NamedTextColor.GOLD));
        for (String[] row : HELP) {
            player.sendMessage(Component.text("/island " + row[1], NamedTextColor.YELLOW)
                .append(Component.text(" - " + row[2], NamedTextColor.GRAY)));
        }
    }

    private void create(Player player, String[] args) {
        if (service.registry().hasIsland(player.getUniqueId())) {
            err(player, "You already belong to an island. Use /island home.");
            return;
        }
        if (args.length > 1) {
            reply(player, service.create(player, args[1]));
            return;
        }
        menus.templates(player, choice -> {
            player.closeInventory();
            reply(player, service.create(player, choice));
        }, "<green>Pick your island");
    }

    private void visit(Player player, String[] args) {
        Optional<UUID> who = target(player, args, 1);
        if (who.isEmpty()) {
            return;
        }
        Island island = service.registry().ofPlayer(who.get()).orElse(null);
        if (island == null) {
            err(player, service.nameOf(who.get()) + " has no island.");
            return;
        }
        reply(player, service.visit(player, island, false));
    }

    private void setRole(Player player, String[] args) {
        Optional<UUID> who = target(player, args, 1);
        if (who.isEmpty()) {
            return;
        }
        if (args.length < 3) {
            err(player, "Usage: /island setrole <player> <co-owner|member>");
            return;
        }
        IslandRole role = switch (args[2].toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
            case "coowner", "co", "admin" -> IslandRole.CO_OWNER;
            case "member" -> IslandRole.MEMBER;
            default -> null;
        };
        if (role == null) {
            err(player, "Role must be co-owner or member.");
            return;
        }
        reply(player, service.setRole(player, who.get(), role));
    }

    private void flag(Player player, String[] args) {
        if (args.length < 3) {
            err(player, "Usage: /island flag <flag> <on|off>. Flags: "
                + String.join(", ", Arrays.stream(IslandFlag.values()).map(IslandFlag::id).toList()));
            return;
        }
        IslandFlag flag = IslandFlag.byId(args[1]);
        if (flag == null) {
            err(player, "Unknown flag '" + args[1] + "'.");
            return;
        }
        Boolean value = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "enable" -> Boolean.TRUE;
            case "off", "false", "no", "disable" -> Boolean.FALSE;
            default -> null;
        };
        if (value == null) {
            err(player, "Use on or off.");
            return;
        }
        reply(player, service.setFlag(player, flag, value));
    }

    private void time(Player player, String[] args) {
        if (args.length < 2) {
            err(player, "Usage: /island time <" + String.join("|", Arrays.stream(TimeLock.values())
                .map(t -> t.name().toLowerCase(Locale.ROOT)).toList()) + ">");
            return;
        }
        TimeLock lock = null;
        for (TimeLock candidate : TimeLock.values()) {
            if (candidate.name().equalsIgnoreCase(args[1])) {
                lock = candidate;
            }
        }
        if (lock == null) {
            err(player, "Unknown time '" + args[1] + "'.");
            return;
        }
        reply(player, service.setTimeLock(player, lock));
    }

    private void upgrade(Player player, String[] args) {
        if (args.length < 2) {
            err(player, "Usage: /island upgrade <" + String.join("|", buyable(cfg).stream()
                .map(u -> u.name().toLowerCase(Locale.ROOT)).toList()) + ">");
            return;
        }
        Upgrade type = null;
        for (Upgrade candidate : buyable(cfg)) {
            if (candidate.name().equalsIgnoreCase(args[1])) {
                type = candidate;
            }
        }
        if (type == null) {
            err(player, "Unknown upgrade '" + args[1] + "'.");
            return;
        }
        reply(player, service.upgrade(player, type));
    }

    private void reset(Player player, String[] args) {
        Result check = service.checkReset(player);
        if (!check.ok()) {
            reply(player, check);
            return;
        }
        String type = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!service.confirms().consume(player.getUniqueId(), "reset", type)) {
            service.confirms().arm(player.getUniqueId(), "reset", type, cfg.confirmSeconds * 1000L);
            core.msg().warn(player, "This destroys EVERYTHING on your island. Type /island reset"
                + (type.isEmpty() ? "" : " " + type) + " again within " + cfg.confirmSeconds + " seconds to confirm.");
            return;
        }
        reply(player, service.reset(player, type.isEmpty() ? null : type));
    }

    private void info(Player player, String[] args) {
        Island island;
        if (args.length > 1) {
            Optional<UUID> who = target(player, args, 1);
            if (who.isEmpty()) {
                return;
            }
            island = service.registry().ofPlayer(who.get()).orElse(null);
            if (island == null) {
                err(player, service.nameOf(who.get()) + " has no island.");
                return;
            }
        } else {
            island = service.registry().ofPlayer(player.getUniqueId()).orElse(null);
            if (island == null) {
                err(player, "You do not have an island. Use /island create.");
                return;
            }
        }
        sendInfo(player, island);
    }

    void sendInfo(CommandSender to, Island island) {
        to.sendMessage(Component.text("Island of " + service.registry().nameOf(island.owner), NamedTextColor.GOLD));
        line(to, "Type", island.template);
        line(to, "State", island.state().name().toLowerCase(Locale.ROOT));
        line(to, "Level", island.level + " (value " + Msg.number(island.value) + ")");
        line(to, "Members", island.size() + "/" + cfg.memberSlotsFor(island));
        line(to, "Radius", String.valueOf(cfg.radiusFor(island)));
        line(to, "Access", island.locked ? "locked" : "open");
        line(to, "Cell", island.cellX + ", " + island.cellZ);
        List<String> names = new ArrayList<>();
        for (UUID uuid : island.coop()) {
            names.add(service.nameOf(uuid));
        }
        line(to, "Co-op", String.join(", ", names));
        if (island.warp != null) {
            line(to, "Warp", island.warp.name + " (" + island.warp.visits + " visits)");
        }
    }

    private void level(Player player) {
        Island island = service.registry().ofPlayer(player.getUniqueId()).orElse(null);
        if (island == null) {
            err(player, "You do not have an island.");
            return;
        }
        Result result = service.recalculate(island, true, done -> {
            if (player.isOnline()) {
                ok(player, "Island level: " + done.level + " (value " + Msg.number(done.value) + ")");
            }
        });
        reply(player, result);
    }

    private void warp(Player player, String[] args) {
        if (args.length == 1) {
            menus.warps(player);
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> reply(player, service.setWarp(player,
                args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null));
            case "remove", "delete" -> reply(player, service.removeWarp(player));
            case "list" -> menus.warps(player);
            default -> {
                Optional<UUID> who = service.resolve(args[1]);
                Island island = who.flatMap(u -> service.registry().ofPlayer(u)).orElse(null);
                if (island == null || island.warp == null) {
                    err(player, "No public warp found for '" + args[1] + "'.");
                } else {
                    reply(player, service.visit(player, island, true));
                }
            }
        }
    }

    private List<String> tabIsland(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.stream(HELP).map(r -> r[0]).toList(), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "create", "reset" -> {
                    return filter(service.templates().stream().map(IslandConfig.TemplateCfg::id).toList(), args[1]);
                }
                case "flag" -> {
                    return filter(Arrays.stream(IslandFlag.values()).map(IslandFlag::id).toList(), args[1]);
                }
                case "time" -> {
                    return filter(Arrays.stream(TimeLock.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList(), args[1]);
                }
                case "upgrade" -> {
                    return filter(buyable(cfg).stream().map(u -> u.name().toLowerCase(Locale.ROOT)).toList(), args[1]);
                }
                case "warp", "warps" -> {
                    List<String> options = new ArrayList<>(List.of("set", "remove", "list"));
                    service.publicWarps().forEach(i -> options.add(service.registry().nameOf(i.owner)));
                    return filter(options, args[1]);
                }
                case "accept", "deny" -> {
                    if (sender instanceof Player p) {
                        List<String> inviters = new ArrayList<>();
                        for (Invites.Invite invite : service.invites().pending(p.getUniqueId())) {
                            inviters.add(service.nameOf(invite.inviter()));
                        }
                        return filter(inviters, args[1]);
                    }
                    return List.of();
                }
                case "visit", "v", "invite", "kick", "ban", "unban", "trust", "untrust", "setrole", "transfer", "info" -> {
                    return players(args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3) {
            if (sub.equals("setrole")) {
                return filter(List.of("co-owner", "member"), args[2]);
            }
            if (sub.equals("flag")) {
                return filter(List.of("on", "off"), args[2]);
            }
        }
        return List.of();
    }

    private List<String> players(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return filter(names, prefix);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }

    // ---------------------------------------------------------------- /islandadmin

    private boolean admin(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            err(sender, "Usage: /islandadmin <info|tp|level|rebuild|platforms|delete|list> [player]");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            List<Island> islands = new ArrayList<>(service.registry().all());
            ok(sender, islands.size() + " islands, " + service.runner().pending() + " generation jobs pending.");
            islands.stream().limit(30).forEach(i -> sender.sendMessage(Component.text(
                service.registry().nameOf(i.owner) + " - cell " + i.cellX + "," + i.cellZ + " - " + i.state().name().toLowerCase(Locale.ROOT)
                    + " - level " + i.level, NamedTextColor.GRAY)));
            return true;
        }
        Optional<UUID> who = target(sender, args, 1);
        if (who.isEmpty()) {
            return true;
        }
        Island island = service.registry().ofPlayer(who.get()).orElse(null);
        if (island == null) {
            err(sender, service.nameOf(who.get()) + " has no island.");
            return true;
        }
        switch (sub) {
            case "info" -> sendInfo(sender, island);
            case "tp" -> {
                if (sender instanceof Player p) {
                    service.teleport(p, service.overworldHome(island));
                    ok(sender, "Teleporting to the island.");
                } else {
                    err(sender, "Only players can teleport.");
                }
            }
            case "level" -> reply(sender, service.recalculate(island, false, done ->
                ok(sender, "Island level: " + done.level)));
            case "rebuild" -> reply(sender, service.rebuild(island));
            case "platforms" -> reply(sender, service.rebuildPlatforms(island));
            case "delete" -> {
                if (sender instanceof Player p && !service.confirms().consume(p.getUniqueId(), "admin-delete", who.get().toString())) {
                    service.confirms().arm(p.getUniqueId(), "admin-delete", who.get().toString(), cfg.confirmSeconds * 1000L);
                    core.msg().warn(sender, "This deletes the island and its three dimensions. Repeat the command to confirm.");
                } else {
                    reply(sender, service.delete(who.get()));
                }
            }
            default -> err(sender, "Unknown subcommand '" + args[0] + "'.");
        }
        return true;
    }

    private List<String> tabAdmin(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("info", "tp", "level", "rebuild", "platforms", "delete", "list"), args[0]);
        }
        return args.length == 2 && !args[0].equalsIgnoreCase("list") ? players(args[1]) : List.of();
    }
}
