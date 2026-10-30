package win.vampyuyu.survival.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** /skills, /collections, /skilltop and /skilladmin. */
final class SkillsCommands {
    static final String PERM_USE = "vampyuyu.sb.skills.use";
    static final String PERM_COLLECTIONS = "vampyuyu.sb.skills.collections";
    static final String PERM_TOP = "vampyuyu.sb.skills.top";
    static final String PERM_RECIPES = "vampyuyu.sb.skills.recipes";
    static final String PERM_ADMIN = "vampyuyu.sb.skills.admin";
    private static final List<String> ADMIN_SUBS = List.of("setxp", "addxp", "setlevel", "reset", "setcollection", "info",
        "boost", "boosts", "unboost", "reload", "help");

    private final Core core;
    private final SkillsService service;
    private final SkillsMenus menus;
    private final Leaderboard leaderboard;
    private final Runnable reload;
    private final RecipeViewer viewer;
    private final SkillRecipes recipes;

    SkillsCommands(Core core, SkillsService service, SkillsMenus menus, Leaderboard leaderboard, Runnable reload,
            RecipeViewer viewer, SkillRecipes recipes) {
        this.viewer = viewer;
        this.recipes = recipes;
        this.core = core;
        this.service = service;
        this.menus = menus;
        this.leaderboard = leaderboard;
        this.reload = reload;
    }

    void register() {
        core.commands().command("skills").aliases("skill").description("Open your skills")
            .usage("/skills [skill]").permission(PERM_USE, PermissionDefault.TRUE)
            .executor(this::skills).tab((s, c, l, a) -> a.length == 1 ? matching(skillNames(), a[0]) : List.of())
            .register();
        core.commands().command("collections").aliases("collection").description("Open your item collections")
            .usage("/collections [category]").permission(PERM_COLLECTIONS, PermissionDefault.TRUE)
            .executor(this::collections).tab((s, c, l, a) -> a.length == 1 ? matching(categoryNames(), a[0]) : List.of())
            .register();
        core.commands().command("recipes").aliases("sbrecipes").description("Browse and quick-craft enchanted recipes")
            .usage("/recipes [recipe | craft <recipe>]").permission(PERM_RECIPES, PermissionDefault.TRUE)
            .executor(this::recipesCommand).tab(this::recipesTab).register();
        core.commands().command("skilltop").aliases("skillboard").description("Skill leaderboards")
            .usage("/skilltop [skill|average]").permission(PERM_TOP, PermissionDefault.TRUE)
            .executor(this::top).tab((s, c, l, a) -> a.length == 1 ? matching(boardNames(), a[0]) : List.of())
            .register();
        core.commands().command("skilladmin").description("Administer skills, collections and XP boosts")
            .usage("/skilladmin <setxp|addxp|setlevel|reset|setcollection|info|boost|boosts|unboost|reload> ...")
            .permission(PERM_ADMIN, PermissionDefault.OP)
            .executor(this::admin).tab(this::adminTab).register();
    }

    // ---------------------------------------------------------------- player commands

    private boolean skills(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can open the skills menu.");
            return true;
        }
        if (args.length == 0) {
            menus.openOverview(player);
            return true;
        }
        Skill skill = SkillsService.parseSkill(args[0]);
        if (skill == null) {
            core.msg().error(sender, "Unknown skill '" + args[0] + "'. Skills: " + String.join(", ", skillNames()) + ".");
            return true;
        }
        menus.openSkill(player, skill);
        return true;
    }

    private boolean collections(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can open the collections menu.");
            return true;
        }
        if (args.length == 0) {
            menus.openCategories(player);
            return true;
        }
        Skill category = SkillsService.parseSkill(args[0]);
        if (category == null || !categoryNames().contains(category.key())) {
            core.msg().error(sender, "Unknown category '" + args[0] + "'. Categories: " + String.join(", ", categoryNames()) + ".");
            return true;
        }
        menus.openCategory(player, category);
        return true;
    }

    private boolean recipesCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can browse recipes.");
            return true;
        }
        if (args.length == 0) {
            viewer.openList(player);
            return true;
        }
        boolean craft = args[0].equalsIgnoreCase("craft");
        String id = craft ? (args.length > 1 ? args[1] : "") : args[0];
        SkillsConfig.RecipeDef def = service.config().recipes.get(id.toLowerCase(Locale.ROOT));
        if (def == null) {
            core.msg().error(sender, craft && args.length < 2 ? "Usage: /recipes craft <recipe>"
                : "Unknown recipe '" + id + "'. Use /recipes to browse them.");
            return true;
        }
        if (!craft) {
            viewer.open(player, def);
            return true;
        }
        switch (recipes.quickCraft(player, def)) {
            case OK -> core.msg().ok(player, "Crafted " + def.amount() + " " + service.resultName(def) + ".");
            case LOCKED -> core.msg().warn(player, "This recipe is locked. Requires "
                + String.join(", ", recipes.missingRequirements(player.getUniqueId(), def)) + ".");
            case MISSING -> core.msg().warn(player, "You do not have enough ingredients: "
                + String.join(", ", RecipeText.totals(service.config(), core, def)) + ".");
            case NO_ROOM -> core.msg().warn(player, "Your inventory has no room for the result.");
            case UNAVAILABLE -> core.msg().warn(player, "You cannot craft right now.");
        }
        return true;
    }

    private List<String> recipesTab(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(service.config().recipes.keySet());
            options.add("craft");
            return matching(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("craft")) {
            return matching(new ArrayList<>(service.config().recipes.keySet()), args[1]);
        }
        return List.of();
    }

    private boolean top(CommandSender sender, Command command, String label, String[] args) {
        String key = args.length == 0 ? Leaderboard.AVERAGE : args[0].toLowerCase(Locale.ROOT);
        if (!boardNames().contains(key)) {
            core.msg().error(sender, "Unknown leaderboard '" + args[0] + "'. Boards: " + String.join(", ", boardNames()) + ".");
            return true;
        }
        boolean average = Leaderboard.AVERAGE.equals(key);
        List<Leaderboard.Entry> entries = leaderboard.top(key, service.config().leaderboardSize, service);
        String title = average ? "Skill Average" : SkillsService.parseSkill(key).displayName();
        sender.sendMessage(Msg.component("<dark_aqua><strike>        </strike> <aqua><bold>" + title
            + " leaderboard</bold> <dark_aqua><strike>        </strike>"));
        if (entries.isEmpty()) {
            sender.sendMessage(Msg.component("<gray>Nobody is ranked yet."));
        }
        UUID self = sender instanceof Player p ? p.getUniqueId() : null;
        int shown = Math.min(10, entries.size());
        for (int i = 0; i < shown; i++) {
            sender.sendMessage(line(i + 1, entries.get(i), average, entries.get(i).id().equals(self)));
        }
        if (self != null) {
            for (int i = shown; i < entries.size(); i++) {
                if (entries.get(i).id().equals(self)) {
                    sender.sendMessage(Msg.component("<dark_gray>..."));
                    sender.sendMessage(line(i + 1, entries.get(i), average, true));
                }
            }
            if (entries.stream().noneMatch(e -> e.id().equals(self))) {
                sender.sendMessage(Msg.component("<gray>You are not in the top " + service.config().leaderboardSize + " yet."));
            }
        }
        long age = leaderboard.builtAt() == 0 ? -1 : System.currentTimeMillis() - leaderboard.builtAt();
        sender.sendMessage(Msg.component("<dark_gray>Offline players are refreshed "
            + (age < 0 ? "soon" : Msg.duration(age) + " ago") + "; online players are live."));
        return true;
    }

    private Component line(int rank, Leaderboard.Entry entry, boolean average, boolean self) {
        Component name = Component.text(entry.name(), self ? NamedTextColor.GREEN : NamedTextColor.WHITE);
        String detail = average
            ? String.format(Locale.ROOT, " <gray>- level <aqua>%.2f", entry.score())
            : " <gray>- level <aqua>" + (int) entry.level() + " <dark_gray>(" + Msg.number(entry.score()) + " xp)";
        return Msg.component("<yellow>" + rank + ". ").append(name).append(Msg.component(detail));
    }

    // ---------------------------------------------------------------- admin

    private boolean admin(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setxp", "addxp", "setlevel" -> xpCommand(sender, sub, args);
            case "reset" -> resetCommand(sender, args);
            case "setcollection" -> collectionCommand(sender, args);
            case "info" -> infoCommand(sender, args);
            case "boost" -> boostCommand(sender, args);
            case "boosts" -> boostsCommand(sender);
            case "unboost" -> {
                if (args.length < 2) {
                    core.msg().error(sender, "Usage: /skilladmin unboost <name|all>");
                } else if (args[1].equalsIgnoreCase("all")) {
                    service.boosts().clear();
                    core.msg().ok(sender, "All boosts removed.");
                } else if (service.boosts().remove(args[1])) {
                    core.msg().ok(sender, "Boost '" + args[1] + "' removed.");
                } else {
                    core.msg().error(sender, "No boost named '" + args[1] + "'.");
                }
            }
            case "reload" -> {
                reload.run();
                SkillsConfig cfg = service.config();
                core.msg().ok(sender, "Skills configuration reloaded: " + cfg.recipes.size() + " recipes, "
                    + cfg.collections.size() + " collections" + (cfg.warnings.isEmpty() ? "." : ", "
                    + cfg.warnings.size() + " warning(s) in the console."));
            }
            default -> {
                core.msg().error(sender, "Unknown sub-command '" + args[0] + "'.");
                help(sender, label);
            }
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        core.msg().info(sender, "Skill administration:");
        for (String line : List.of(
            "/" + label + " setxp <player> <skill> <xp>  - set total experience (no rewards paid)",
            "/" + label + " addxp <player> <skill> <xp>  - grant experience as if earned (online player)",
            "/" + label + " setlevel <player> <skill> <level>  - set the level (no rewards paid)",
            "/" + label + " reset <player> [skill]  - reset one skill or all of them",
            "/" + label + " setcollection <player> <collection> <amount>  - set a collection counter",
            "/" + label + " info <player>  - show levels and collections",
            "/" + label + " boost <multiplier> <minutes|0> [skill|all] [name]  - timed XP boost for everybody",
            "/" + label + " boosts | unboost <name|all>  - list or remove boosts",
            "/" + label + " reload  - reload skills.yml")) {
            sender.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
        }
    }

    /** Resolves a player name or UUID, online first. Returns null (and tells the sender) when unknown. */
    private UUID resolve(CommandSender sender, String text) {
        Player online = Bukkit.getPlayerExact(text);
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        OfflinePlayer known = null;
        try {
            known = Bukkit.getOfflinePlayerIfCached(text);
        } catch (RuntimeException ignored) {
            // lookup not supported
        }
        if (known == null || !known.hasPlayedBefore()) {
            core.msg().error(sender, "Unknown player '" + text + "' (they must have joined this server).");
            return null;
        }
        return known.getUniqueId();
    }

    /** Runs {@code work} now for an online player, off-thread for an offline one (profile read), then reports. */
    private void run(UUID id, Runnable work, Runnable done) {
        if (Bukkit.getPlayer(id) != null) {
            work.run();
            done.run();
        } else {
            core.scheduler().async(() -> {
                work.run();
                core.scheduler().sync(done);
            });
        }
    }

    private void xpCommand(CommandSender sender, String sub, String[] args) {
        if (args.length < 4) {
            core.msg().error(sender, "Usage: /skilladmin " + sub + " <player> <skill> <" + (sub.equals("setlevel") ? "level" : "xp") + ">");
            return;
        }
        UUID id = resolve(sender, args[1]);
        Skill skill = SkillsService.parseSkill(args[2]);
        if (id == null) {
            return;
        }
        if (skill == null) {
            core.msg().error(sender, "Unknown skill '" + args[2] + "'. Skills: " + String.join(", ", skillNames()) + ".");
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            core.msg().error(sender, "'" + args[3] + "' is not a number.");
            return;
        }
        if (!(value >= 0) || Double.isInfinite(value)) {
            core.msg().error(sender, "The value must be zero or positive.");
            return;
        }
        SkillsConfig cfg = service.config();
        switch (sub) {
            case "addxp" -> {
                Player online = Bukkit.getPlayer(id);
                if (online == null) {
                    core.msg().error(sender, "addxp needs an online player (use setxp for offline players).");
                    return;
                }
                double gained = service.grant(online, skill, value, "admin");
                core.msg().ok(sender, "Granted " + Msg.number(gained) + " " + skill.displayName() + " XP to " + args[1]
                    + (gained == value ? "." : " (after multipliers and events; the base amount was " + Msg.number(value) + ")."));
            }
            case "setlevel" -> {
                int level = (int) Math.min(value, cfg.maxLevel(skill));
                run(id, () -> service.adminSetXp(id, skill, cfg.levels.xpFor(level)),
                    () -> core.msg().ok(sender, args[1] + "'s " + skill.displayName() + " level is now " + level + "."));
            }
            default -> run(id, () -> service.adminSetXp(id, skill, value),
                () -> core.msg().ok(sender, args[1] + "'s " + skill.displayName() + " XP is now " + Msg.number(value)
                    + " (level " + cfg.levels.levelOf(value, cfg.maxLevel(skill)) + ")."));
        }
    }

    private void resetCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.msg().error(sender, "Usage: /skilladmin reset <player> [skill]");
            return;
        }
        UUID id = resolve(sender, args[1]);
        if (id == null) {
            return;
        }
        Skill skill = null;
        if (args.length > 2 && !args[2].equalsIgnoreCase("all")) {
            skill = SkillsService.parseSkill(args[2]);
            if (skill == null) {
                core.msg().error(sender, "Unknown skill '" + args[2] + "'.");
                return;
            }
        }
        Skill target = skill;
        run(id, () -> service.adminReset(id, target), () -> core.msg().ok(sender,
            "Reset " + (target == null ? "every skill" : target.displayName()) + " of " + args[1] + "."));
    }

    private void collectionCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            core.msg().error(sender, "Usage: /skilladmin setcollection <player> <collection> <amount>");
            return;
        }
        UUID id = resolve(sender, args[1]);
        SkillsConfig.CollectionDef def = service.config().collections.get(args[2].toUpperCase(Locale.ROOT));
        if (id == null) {
            return;
        }
        if (def == null) {
            core.msg().error(sender, "Unknown collection '" + args[2] + "'. Known: "
                + String.join(", ", service.config().collections.keySet()) + ".");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            core.msg().error(sender, "'" + args[3] + "' is not a whole number.");
            return;
        }
        if (amount < 0) {
            core.msg().error(sender, "The amount must be zero or positive.");
            return;
        }
        run(id, () -> service.adminSetCollection(id, def, amount), () -> core.msg().ok(sender,
            args[1] + "'s " + def.name() + " collection is now " + Msg.grouped(amount) + " (tier " + def.tierOf(amount) + ")."));
    }

    private void infoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            core.msg().error(sender, "Usage: /skilladmin info <player>");
            return;
        }
        UUID id = resolve(sender, args[1]);
        if (id == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        Consumer<UUID> collect = uuid -> {
            SkillsConfig cfg = service.config();
            SkillsData data = service.data(uuid);
            for (Skill skill : Skill.values()) {
                double xp = data.xpOf(skill.key());
                lines.add(skill.displayName() + ": level " + cfg.levels.levelOf(xp, cfg.maxLevel(skill)) + " (" + Msg.number(xp) + " xp)");
            }
            lines.add("Average: " + String.format(Locale.ROOT, "%.2f", service.average(uuid)));
            lines.add("Collections tracked: " + data.collections.size() + ", claimed rewards: " + data.claimed.size());
            lines.add("Pending coins: " + Msg.coins(service.pendingCoins(uuid)));
        };
        run(id, () -> collect.accept(id), () -> {
            core.msg().info(sender, "Skills of " + args[1] + ":");
            lines.forEach(l -> sender.sendMessage(Component.text("  " + l, NamedTextColor.GRAY)));
        });
    }

    private void boostCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.msg().error(sender, "Usage: /skilladmin boost <multiplier> <minutes|0> [skill|all] [name]");
            return;
        }
        double multiplier;
        long minutes;
        try {
            multiplier = Double.parseDouble(args[1]);
            minutes = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            core.msg().error(sender, "The multiplier and the minutes must be numbers.");
            return;
        }
        if (!(multiplier > 1) || multiplier > 100 || minutes < 0 || minutes > 60 * 24 * 30) {
            core.msg().error(sender, "The multiplier must be above 1 (at most 100) and the duration 0 (never ends) to 43200 minutes.");
            return;
        }
        Skill skill = null;
        if (args.length > 3 && !args[3].equalsIgnoreCase("all")) {
            skill = SkillsService.parseSkill(args[3]);
            if (skill == null) {
                core.msg().error(sender, "Unknown skill '" + args[3] + "'.");
                return;
            }
        }
        String name = args.length > 4 ? args[4] : "admin-" + (skill == null ? "all" : skill.key());
        service.boosts().add(name, skill, multiplier, minutes * 60_000L);
        core.msg().ok(sender, "Boost '" + name + "' x" + multiplier + " on " + (skill == null ? "every skill" : skill.displayName())
            + (minutes == 0 ? " until removed." : " for " + minutes + " minute(s)."));
    }

    private void boostsCommand(CommandSender sender) {
        var active = service.boosts().active();
        if (active.isEmpty()) {
            core.msg().info(sender, "No active boost.");
            return;
        }
        for (XpBoosts.Boost boost : active) {
            sender.sendMessage(Component.text("  " + boost.name() + ": x" + boost.multiplier() + " on "
                + (boost.skill() == null ? "every skill" : boost.skill().displayName())
                + (boost.expiresAt() > 0 ? ", ends in " + Msg.duration(boost.expiresAt() - service.boosts().now()) : ", no end"),
                NamedTextColor.GRAY));
        }
    }

    // ---------------------------------------------------------------- tab completion

    private List<String> adminTab(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(ADMIN_SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean takesPlayer = List.of("setxp", "addxp", "setlevel", "reset", "setcollection", "info").contains(sub);
        if (args.length == 2 && takesPlayer) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return matching(names, args[1]);
        }
        if (args.length == 3 && List.of("setxp", "addxp", "setlevel", "reset").contains(sub)) {
            List<String> names = new ArrayList<>(skillNames());
            if (sub.equals("reset")) {
                names.add("all");
            }
            return matching(names, args[2]);
        }
        if (args.length == 3 && sub.equals("setcollection")) {
            return matching(new ArrayList<>(service.config().collections.keySet()), args[2]);
        }
        if (args.length == 4 && sub.equals("boost")) {
            List<String> names = new ArrayList<>(skillNames());
            names.add("all");
            return matching(names, args[3]);
        }
        if (args.length == 2 && sub.equals("unboost")) {
            List<String> names = new ArrayList<>(List.of("all"));
            service.boosts().active().forEach(b -> names.add(b.name()));
            return matching(names, args[1]);
        }
        return List.of();
    }

    static List<String> matching(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    static List<String> skillNames() {
        List<String> names = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            names.add(skill.key());
        }
        return names;
    }

    private List<String> categoryNames() {
        List<String> names = new ArrayList<>();
        for (SkillsConfig.CollectionDef def : service.config().collections.values()) {
            if (!names.contains(def.category().key())) {
                names.add(def.category().key());
            }
        }
        return names;
    }

    private List<String> boardNames() {
        return Leaderboard.keys();
    }
}
