package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

/** Registers the commands of the menu feature. */
final class MenuCommands {
    static final String USE = "vampyuyu.sb.menu.use";
    static final String ADMIN = "vampyuyu.sb.menu.admin";

    private final MenuRuntime rt;
    private final MainMenu mainMenu;
    private final QuestsGui questsGui;
    private final QuestService quests;
    private final WarpsGui warpsGui;
    private final WarpService warps;
    private final SettingsGui settings;
    private final FairySouls fairy;
    private final Supplier<Boolean> reloader;

    MenuCommands(MenuRuntime rt, MainMenu mainMenu, QuestsGui questsGui, QuestService quests, WarpsGui warpsGui,
            WarpService warps, SettingsGui settings, FairySouls fairy, Supplier<Boolean> reloader) {
        this.rt = rt;
        this.mainMenu = mainMenu;
        this.questsGui = questsGui;
        this.quests = quests;
        this.warpsGui = warpsGui;
        this.warps = warps;
        this.settings = settings;
        this.fairy = fairy;
        this.reloader = reloader;
    }

    void register() {
        rt.core.permissions().register(ADMIN, "Menu administration (reload, fairy souls)", PermissionDefault.OP);
        rt.core.commands().command("sbmenu").aliases("skyblockmenu")
            .description("Open the Skyblock Menu")
            .usage("/<command> [reload]")
            .permission(USE, PermissionDefault.TRUE)
            .executor(this::menu).tab((s, c, l, a) -> a.length == 1 && s.hasPermission(ADMIN) ? filter(List.of("reload"), a[0]) : List.of())
            .register();
        rt.core.commands().command("quests").aliases("quest")
            .description("Tutorial and daily quests")
            .usage("/<command> [tutorial|daily|track <id>|list]")
            .permission("vampyuyu.sb.menu.quests", PermissionDefault.TRUE)
            .executor(this::quests).tab(this::questsTab).register();
        rt.core.commands().command("fairy").aliases("fairysouls", "souls")
            .description("Fairy Souls: progress and a hint to the nearest one")
            .usage("/<command> [admin add [id]|admin remove <id>|admin list]")
            .permission("vampyuyu.sb.menu.fairy", PermissionDefault.TRUE)
            .executor(this::fairy).tab(this::fairyTab).register();
        rt.core.commands().command("sbwarps").aliases("skyblockwarps")
            .description("Travel around Skyblock")
            .usage("/<command> [hub|island|nether|end|visit <player>|public <on|off|list> [note]]")
            .permission("vampyuyu.sb.menu.warps", PermissionDefault.TRUE)
            .executor(this::warps).tab(this::warpsTab).register();
        rt.core.commands().command("hub")
            .description("Go to the Skyblock hub")
            .usage("/<command>")
            .permission("vampyuyu.sb.menu.warps", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> player(s).map(p -> {
                warps.warp(p, WarpService.Target.HUB);
                return true;
            }).orElse(true)).register();
        rt.core.commands().command("sbsettings").aliases("skyblocksettings")
            .description("Your Skyblock preferences")
            .usage("/<command> [sidebar|quest-tracker|damage-indicators|sounds] [on|off]")
            .permission("vampyuyu.sb.menu.settings", PermissionDefault.TRUE)
            .executor(this::settings).tab(this::settingsTab).register();
    }

    private Optional<Player> player(CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        rt.core.msg().error(sender, "Only players can use this command.");
        return Optional.empty();
    }

    private static List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    // ---------------------------------------------------------------- /sbmenu

    private boolean menu(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(ADMIN)) {
                rt.core.msg().error(sender, "You do not have permission to reload the menu.");
                return true;
            }
            boolean clean = reloader.get();
            if (clean) {
                rt.core.msg().ok(sender, "Menu configuration reloaded.");
            } else {
                rt.core.msg().warn(sender, "Menu configuration reloaded with problems; see the console.");
            }
            return true;
        }
        player(sender).ifPresent(mainMenu::open);
        return true;
    }

    // ---------------------------------------------------------------- /quests

    private boolean quests(CommandSender sender, Command command, String label, String[] args) {
        Optional<Player> who = player(sender);
        if (who.isEmpty()) {
            return true;
        }
        Player player = who.get();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> questsGui.open(player);
            case "tutorial" -> questsGui.tutorial(player);
            case "daily" -> questsGui.daily(player);
            case "list" -> {
                QuestData data = rt.quests(player.getUniqueId());
                QuestEngine engine = rt.engine();
                rt.core.msg().info(player, "Tutorial: " + data.completed.size() + "/" + engine.catalog().tutorial().size()
                    + " done. Streak: " + engine.liveStreak(data, this.quests.today()) + ".");
                for (QuestDef def : engine.activeTutorial(data)) {
                    rt.core.msg().info(player, "  " + def.name() + " (" + engine.progress(data, def, false) + "/" + def.amount()
                        + ") [" + def.id() + "]");
                }
                for (QuestDef def : engine.dailyQuests(data)) {
                    if (!data.dailyDone.contains(def.id())) {
                        rt.core.msg().info(player, "  Daily: " + def.name() + " (" + engine.progress(data, def, true) + "/"
                            + def.amount() + ") [daily:" + def.id() + "]");
                    }
                }
            }
            case "track" -> {
                if (args.length < 2) {
                    rt.core.msg().error(player, "Usage: /quests track <quest id>");
                } else if (this.quests.track(player, args[1].toLowerCase(Locale.ROOT))) {
                    rt.core.msg().ok(player, "Now tracking " + args[1] + " in the sidebar.");
                } else {
                    rt.core.msg().error(player, "You cannot track '" + args[1] + "' (unknown, locked or already done).");
                }
            }
            default -> rt.core.msg().error(player, "Unknown option. Use /quests [tutorial|daily|track <id>|list].");
        }
        return true;
    }

    private List<String> questsTab(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("tutorial", "daily", "track", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("track") && sender instanceof Player player) {
            QuestData data = rt.quests(player.getUniqueId());
            List<String> ids = new ArrayList<>();
            rt.engine().activeTutorial(data).forEach(d -> ids.add(d.id()));
            rt.engine().dailyQuests(data).stream().filter(d -> !data.dailyDone.contains(d.id()))
                .forEach(d -> ids.add("daily:" + d.id()));
            return filter(ids, args[1]);
        }
        return List.of();
    }

    // ---------------------------------------------------------------- /fairy

    private boolean fairy(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            return fairyAdmin(sender, args);
        }
        player(sender).ifPresent(fairy::hint);
        return true;
    }

    private boolean fairyAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            rt.core.msg().error(sender, "You do not have permission for Fairy Soul administration.");
            return true;
        }
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> player(sender).ifPresent(p -> {
                Optional<String> id = fairy.addAdmin(p.getLocation(), args.length > 2 ? args[2] : null);
                if (id.isPresent()) {
                    rt.core.msg().ok(p, "Fairy Soul '" + id.get() + "' placed here.");
                } else {
                    rt.core.msg().error(p, "Could not place a soul: use a free id (a-z, 0-9, _) inside a Skyblock world.");
                }
            });
            case "remove" -> {
                if (args.length < 3) {
                    rt.core.msg().error(sender, "Usage: /fairy admin remove <id>");
                } else if (fairy.removeAdmin(args[2].toLowerCase(Locale.ROOT))) {
                    rt.core.msg().ok(sender, "Fairy Soul '" + args[2] + "' removed.");
                } else {
                    rt.core.msg().error(sender, "No admin placed soul called '" + args[2] + "'.");
                }
            }
            case "list" -> {
                List<SoulLogic.SoulDef> souls = fairy.adminSouls();
                rt.core.msg().info(sender, souls.size() + " admin placed souls" + (souls.isEmpty() ? "." : ":"));
                for (SoulLogic.SoulDef def : souls) {
                    rt.core.msg().info(sender, "  " + def.id() + " in " + def.world() + " at " + Math.round(def.x()) + ", "
                        + Math.round(def.y()) + ", " + Math.round(def.z()));
                }
            }
            default -> rt.core.msg().error(sender, "Usage: /fairy admin <add [id]|remove <id>|list>");
        }
        return true;
    }

    private List<String> fairyTab(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(List.of("add", "remove", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("remove")) {
            return filter(fairy.adminSouls().stream().map(SoulLogic.SoulDef::id).toList(), args[2]);
        }
        return List.of();
    }

    // ---------------------------------------------------------------- /sbwarps

    private boolean warps(CommandSender sender, Command command, String label, String[] args) {
        Optional<Player> who = player(sender);
        if (who.isEmpty()) {
            return true;
        }
        Player player = who.get();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> warpsGui.open(player);
            case "hub" -> warps.warp(player, WarpService.Target.HUB);
            case "island", "home" -> warps.warp(player, WarpService.Target.ISLAND);
            case "nether" -> warps.warp(player, WarpService.Target.NETHER);
            case "end" -> warps.warp(player, WarpService.Target.END);
            case "visit" -> {
                if (args.length < 2) {
                    warpsGui.publicIslands(player);
                } else {
                    UUID owner = warps.publicWarps().list().stream()
                        .filter(e -> e.getValue().name.equalsIgnoreCase(args[1])).map(java.util.Map.Entry::getKey)
                        .findFirst().orElse(null);
                    if (owner == null) {
                        rt.core.msg().error(player, "No public island belongs to '" + args[1] + "'.");
                    } else {
                        warps.visit(player, owner);
                    }
                }
            }
            case "public" -> publicCommand(player, args);
            default -> rt.core.msg().error(player, "Unknown destination. Use /sbwarps for the menu.");
        }
        return true;
    }

    private void publicCommand(Player player, String[] args) {
        String mode = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "on" -> {
                if (rt.islands().flatMap(api -> api.islandOf(player.getUniqueId())).isEmpty()) {
                    rt.core.msg().error(player, "You need an island first.");
                    return;
                }
                String note = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                warps.publicWarps().publish(player.getUniqueId(), player.getName(), note);
                rt.core.msg().ok(player, "Your island is now open to visitors.");
            }
            case "off" -> {
                warps.publicWarps().unpublish(player.getUniqueId());
                rt.core.msg().ok(player, "Your island is private.");
            }
            case "list" -> warpsGui.publicIslands(player);
            default -> rt.core.msg().error(player, "Usage: /sbwarps public <on [note]|off|list>");
        }
    }

    private List<String> warpsTab(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("hub", "island", "nether", "end", "visit", "public"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("visit")) {
            return filter(warps.publicWarps().list().stream().map(e -> e.getValue().name).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("public")) {
            return filter(List.of("on", "off", "list"), args[1]);
        }
        return List.of();
    }

    // ---------------------------------------------------------------- /sbsettings

    private boolean settings(CommandSender sender, Command command, String label, String[] args) {
        Optional<Player> who = player(sender);
        if (who.isEmpty()) {
            return true;
        }
        Player player = who.get();
        if (args.length == 0) {
            settings.open(player);
            return true;
        }
        SettingsGui.Setting chosen = null;
        for (SettingsGui.Setting setting : SettingsGui.Setting.values()) {
            if (setting.key().equals(args[0].toLowerCase(Locale.ROOT))) {
                chosen = setting;
            }
        }
        if (chosen == null) {
            rt.core.msg().error(player, "Unknown setting. Options: sidebar, quest-tracker, damage-indicators, sounds.");
            return true;
        }
        MenuData data = rt.prefs(player.getUniqueId());
        boolean now = chosen.get(data);
        if (args.length >= 2) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "on", "true" -> {
                    if (!now) {
                        settings.toggle(player, chosen);
                    }
                }
                case "off", "false" -> {
                    if (now) {
                        settings.toggle(player, chosen);
                    }
                }
                default -> {
                    rt.core.msg().error(player, "Use on or off.");
                    return true;
                }
            }
        } else {
            settings.toggle(player, chosen);
        }
        rt.core.msg().info(player, chosen.title + " is now " + (chosen.get(rt.prefs(player.getUniqueId())) ? "ON" : "OFF") + ".");
        return true;
    }

    private List<String> settingsTab(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(java.util.Arrays.stream(SettingsGui.Setting.values()).map(SettingsGui.Setting::key).toList(), args[0]);
        }
        if (args.length == 2) {
            return filter(List.of("on", "off"), args[1]);
        }
        return List.of();
    }
}
