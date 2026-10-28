package win.vampyuyu.survival.utility;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** /utility help (lists the commands the sender may use) and /utility reload (admin). */
final class UtilityHelp {
    private record Entry(String usage, String text, String permission) {}

    private static final int PAGE = 8;
    private static final List<Entry> ENTRIES = List.of(
        new Entry("/home [name]", "Teleport to a home", Perms.HOME),
        new Entry("/sethome [name]", "Save your position as a home", Perms.HOME),
        new Entry("/delhome <name>", "Delete a home", Perms.HOME),
        new Entry("/homes", "Menu with all your homes", Perms.HOME),
        new Entry("/spawn", "Go to the spawn (the hub in Skyblock)", Perms.SPAWN),
        new Entry("/back", "Return to where you died (5 minute cooldown)", "vampyuyu.back"),
        new Entry("/tpa <player>", "Ask to teleport to a player", Perms.TPA),
        new Entry("/tpahere <player>", "Ask a player to teleport to you", Perms.TPA),
        new Entry("/tpaccept [player]", "Accept a request", Perms.TPA),
        new Entry("/tpdeny [player]", "Deny a request", Perms.TPA),
        new Entry("/tpcancel", "Cancel the request you sent", Perms.TPA),
        new Entry("/tptoggle", "Stop or resume receiving requests", Perms.TPA),
        new Entry("/rtp", "Random place in the survival world (10 minute cooldown)", Perms.RTP),
        new Entry("/ec", "Open your ender chest", Perms.PREFIX + "ec"),
        new Entry("/workbench", "Open a crafting table", Perms.PREFIX + "workbench"),
        new Entry("/anvil", "Open an anvil", Perms.PREFIX + "anvil"),
        new Entry("/grindstone", "Open a grindstone", Perms.PREFIX + "grindstone"),
        new Entry("/stonecutter", "Open a stonecutter", Perms.PREFIX + "stonecutter"),
        new Entry("/loom", "Open a loom", Perms.PREFIX + "loom"),
        new Entry("/hat", "Wear the item in your hand", Perms.HAT),
        new Entry("/sit", "Sit down (sneak to get up)", Perms.SIT),
        new Entry("/trash", "Menu that destroys what you put in", Perms.TRASH),
        new Entry("/playtime [player]", "How long a player has played", Perms.INFO),
        new Entry("/near [radius]", "Players close to you", Perms.INFO),
        new Entry("/seen <player>", "When a player was last online", Perms.INFO),
        new Entry("/ping [player]", "Your latency", Perms.INFO),
        new Entry("/setspawn", "Set the survival world spawn (admin)", Perms.ADMIN),
        new Entry("/utility reload", "Reload utility.yml (admin)", Perms.ADMIN));

    private final Core core;
    private final Msg msg;
    private final Runnable reload;

    UtilityHelp(Core core, Runnable reload) {
        this.core = core;
        this.msg = core.msg();
        this.reload = reload;
    }

    void register() {
        core.commands().command("utility").aliases("utils").description("Help for the quality of life commands")
            .usage("/utility help [page]").permission(Perms.INFO, PermissionDefault.TRUE)
            .executor(this::run).tab(this::tab).register();
    }

    private List<String> tab(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("help"));
        if (sender.hasPermission(Perms.ADMIN)) {
            options.add("reload");
        }
        return Cmd.match(options, args[0]);
    }

    private boolean run(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender, args.length > 1 ? args[1] : "1");
            case "reload" -> {
                if (!sender.hasPermission(Perms.ADMIN)) {
                    msg.error(sender, "You do not have permission to reload the utility configuration.");
                } else {
                    reload.run();
                    msg.ok(sender, "utility.yml reloaded.");
                }
            }
            default -> msg.error(sender, "Unknown option '" + args[0] + "'. Use /utility help.");
        }
        return true;
    }

    private void help(CommandSender sender, String pageArg) {
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (sender.hasPermission(entry.permission())) {
                visible.add(entry);
            }
        }
        int pages = Math.max(1, (visible.size() + PAGE - 1) / PAGE);
        int page;
        try {
            page = Math.max(1, Math.min(pages, Integer.parseInt(pageArg)));
        } catch (NumberFormatException e) {
            page = 1;
        }
        msg.info(sender, "Utility commands (page " + page + "/" + pages + ")");
        for (int i = (page - 1) * PAGE; i < Math.min(visible.size(), page * PAGE); i++) {
            Entry entry = visible.get(i);
            msg.mini(sender, "<aqua>" + entry.usage().replace("<", "\\<") + " <dark_gray>- <gray>" + entry.text());
        }
        if (page < pages) {
            msg.info(sender, "Next page: /utility help " + (page + 1));
        }
    }

    static List<String> commandUsages() {
        return ENTRIES.stream().map(Entry::usage).toList();
    }
}
