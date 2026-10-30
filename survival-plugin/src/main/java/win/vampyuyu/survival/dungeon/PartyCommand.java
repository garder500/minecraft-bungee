package win.vampyuyu.survival.dungeon;

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
import win.vampyuyu.survival.dungeon.party.Party;
import win.vampyuyu.survival.dungeon.party.PartyService;
import win.vampyuyu.survival.dungeon.run.RunManager;

/** {@code /party} and {@code /pc}. */
public final class PartyCommand implements CommandExecutor, TabCompleter {
    public static final String PERMISSION = "vampyuyu.sb.dungeon.party";
    private static final List<String> SUB = List.of("create", "invite", "accept", "deny", "leave", "kick", "promote",
        "disband", "list", "warp", "chat", "ready", "notready", "readycheck", "help");

    private final DungeonService svc;

    public PartyCommand(DungeonService svc) {
        this.svc = svc;
    }

    public void register() {
        svc.core.commands().command("party").aliases("dparty").description("Form a dungeon party")
            .usage("/party [create|invite|accept|deny|leave|kick|promote|disband|list|warp|chat|ready|help]")
            .permission(PERMISSION, PermissionDefault.TRUE).executor(this).tab(this).register();
        svc.core.commands().command("pc").aliases("partychat").description("Talk to your party")
            .usage("/pc [message]").permission(PERMISSION, PermissionDefault.TRUE)
            .executor(new ChatCommand()).register();
    }

    private void fail(CommandSender to, String text) {
        svc.core.msg().error(to, text);
    }

    private void show(CommandSender to, PartyService.Result r) {
        if (r.ok()) {
            svc.core.msg().ok(to, r.message());
        } else {
            svc.core.msg().error(to, r.message());
        }
    }

    private static Player online(String name) {
        return name == null ? null : Bukkit.getPlayerExact(name);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            svc.core.msg().error(sender, "Only players can use parties.");
            return true;
        }
        PartyService parties = svc.parties;
        UUID id = p.getUniqueId();
        if (args.length == 0) {
            svc.guis.party(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String arg = args.length > 1 ? args[1] : null;
        switch (sub) {
            case "create" -> show(p, parties.create(id, p.getName()));
            case "invite", "add" -> {
                Player t = online(arg);
                if (arg == null) {
                    fail(p, "Usage: /party invite <player>");
                } else if (t == null) {
                    fail(p, arg + " is not online.");
                } else {
                    show(p, parties.invite(id, p.getName(), t.getUniqueId(), t.getName()));
                }
            }
            case "accept", "join" -> show(p, parties.accept(id, p.getName(), arg));
            case "deny", "decline" -> show(p, parties.deny(id, arg));
            case "leave" -> {
                if (svc.runs.runOf(id) != null) {
                    fail(p, "You are in a dungeon run. Use /dungeon leave first.");
                } else {
                    show(p, parties.leave(id));
                }
            }
            case "kick" -> {
                Party party = parties.partyOf(id);
                UUID target = party == null ? null : memberByName(party, arg);
                if (arg == null) {
                    fail(p, "Usage: /party kick <player>");
                } else if (party == null) {
                    fail(p, "You are not in a party.");
                } else if (target == null) {
                    fail(p, arg + " is not in your party.");
                } else {
                    show(p, parties.kick(id, target));
                }
            }
            case "promote", "transfer" -> {
                Party party = parties.partyOf(id);
                UUID target = party == null ? null : memberByName(party, arg);
                if (arg == null) {
                    fail(p, "Usage: /party promote <player>");
                } else if (party == null) {
                    fail(p, "You are not in a party.");
                } else if (target == null) {
                    fail(p, arg + " is not in your party.");
                } else {
                    show(p, parties.promote(id, target));
                }
            }
            case "disband" -> show(p, parties.disband(id));
            case "list", "info" -> list(p);
            case "warp" -> warp(p);
            case "chat" -> {
                if (args.length > 1) {
                    chat(p, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                } else {
                    toggleChat(p);
                }
            }
            case "ready" -> show(p, parties.answerReady(id, true));
            case "notready" -> show(p, parties.answerReady(id, false));
            case "readycheck" -> show(p, parties.startReadyCheck(id, outcome -> { }));
            case "help" -> help(p);
            default -> {
                // "/party <name>" invites a player, like on the big servers
                Player t = online(args[0]);
                if (t != null && args.length == 1) {
                    show(p, parties.invite(id, p.getName(), t.getUniqueId(), t.getName()));
                } else {
                    help(p);
                }
            }
        }
        return true;
    }

    private static UUID memberByName(Party party, String name) {
        if (name == null) {
            return null;
        }
        for (UUID m : party.members()) {
            if (party.nameOf(m).equalsIgnoreCase(name)) {
                return m;
            }
        }
        return null;
    }

    private void help(Player p) {
        svc.core.msg().mini(p, "<light_purple><bold>Dungeon parties");
        for (String line : List.of(
            "/party create <dark_gray>- start a party",
            "/party invite <player> <dark_gray>- invite someone (up to " + svc.parties.maxSize() + " players)",
            "/party accept [player] / deny [player] <dark_gray>- answer an invite",
            "/party list <dark_gray>- who is in your party",
            "/party kick|promote <player> <dark_gray>- leader tools",
            "/party warp <dark_gray>- pull the party to you",
            "/party readycheck, ready, notready <dark_gray>- before a run",
            "/party chat [message] and /pc <dark_gray>- party chat",
            "/party leave | disband <dark_gray>- break up",
            "/dungeon <dark_gray>- pick a floor and start")) {
            svc.core.msg().mini(p, "<gray>" + line);
        }
    }

    private void list(Player p) {
        Party party = svc.parties.partyOf(p.getUniqueId());
        if (party == null) {
            fail(p, "You are not in a party. Create one with /party create.");
            return;
        }
        svc.core.msg().mini(p, "<light_purple>Party <gray>(" + party.size() + "/" + svc.parties.maxSize() + ")"
            + (party.inRun() ? " <red>in a run" : ""));
        for (UUID m : party.members()) {
            Player mp = Bukkit.getPlayer(m);
            svc.core.msg().mini(p, " <gray>- <white>" + party.nameOf(m) + (party.isLeader(m) ? " <gold>(leader)" : "")
                + (mp == null ? " <dark_gray>offline" : ""));
        }
    }

    private void warp(Player p) {
        PartyService.Result r = svc.parties.checkWarp(p.getUniqueId());
        if (!r.ok()) {
            fail(p, r.message());
            return;
        }
        if (RunManager.inDungeonWorld(p)) {
            fail(p, "You cannot warp from inside a dungeon.");
            return;
        }
        Party party = svc.parties.partyOf(p.getUniqueId());
        int moved = 0;
        for (UUID m : party.members()) {
            Player mp = Bukkit.getPlayer(m);
            if (mp == null || mp.equals(p) || svc.runs.runOf(m) != null || RunManager.inDungeonWorld(mp)) {
                continue;
            }
            mp.teleportAsync(p.getLocation());
            svc.core.msg().info(mp, "You were warped to " + p.getName() + ".");
            moved++;
        }
        svc.core.msg().ok(p, moved == 0 ? "Nobody could be warped." : "Warped " + moved + " party member(s).");
    }

    private void toggleChat(Player p) {
        if (svc.parties.partyOf(p.getUniqueId()) == null) {
            fail(p, "You are not in a party.");
            return;
        }
        boolean on = svc.parties.togglePartyChat(p.getUniqueId());
        svc.core.msg().ok(p, on ? "Party chat is ON: everything you type goes to your party. /pc to switch back."
            : "Party chat is OFF.");
    }

    private void chat(Player p, String text) {
        if (!svc.parties.chat(p.getUniqueId(), text)) {
            fail(p, "You are not in a party.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) {
            return List.of();
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            SUB.stream().filter(s -> s.startsWith(last)).forEach(out::add);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Party party = svc.parties.partyOf(p.getUniqueId());
        switch (sub) {
            case "invite", "add" -> {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.equals(p) && o.getName().toLowerCase(Locale.ROOT).startsWith(last)
                            && svc.parties.partyOf(o.getUniqueId()) == null) {
                        out.add(o.getName());
                    }
                }
            }
            case "kick", "promote", "transfer" -> {
                if (party != null) {
                    for (UUID m : party.members()) {
                        String n = party.nameOf(m);
                        if (!m.equals(p.getUniqueId()) && n.toLowerCase(Locale.ROOT).startsWith(last)) {
                            out.add(n);
                        }
                    }
                }
            }
            case "accept", "deny", "join", "decline" -> {
                for (String n : svc.parties.pendingInvites(p.getUniqueId())) {
                    if (n.toLowerCase(Locale.ROOT).startsWith(last)) {
                        out.add(n);
                    }
                }
            }
            default -> { }
        }
        return out;
    }

    /** {@code /pc}: one message, or toggles the sticky party chat. */
    private final class ChatCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player p)) {
                svc.core.msg().error(sender, "Only players can use party chat.");
                return true;
            }
            if (args.length == 0) {
                toggleChat(p);
            } else {
                chat(p, String.join(" ", args));
            }
            return true;
        }
    }
}
