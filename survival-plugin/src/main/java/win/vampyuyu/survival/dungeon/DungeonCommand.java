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
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.dungeon.party.Party;
import win.vampyuyu.survival.dungeon.party.PartyService;
import win.vampyuyu.survival.dungeon.run.DungeonRun;
import win.vampyuyu.survival.dungeon.run.RunListener;

/** {@code /dungeon}: floor menu, start, leave, statistics, leaderboard, claim and the admin tools. */
public final class DungeonCommand implements CommandExecutor, TabCompleter {
    public static final String ENTER = "vampyuyu.sb.dungeon.enter";
    private static final List<String> SUB = List.of("start", "leave", "floors", "stats", "top", "claim", "forge", "help", "admin");
    private static final List<String> ADMIN = List.of("list", "abort", "start", "cleanup", "reload");

    private final DungeonService svc;

    public DungeonCommand(DungeonService svc) {
        this.svc = svc;
    }

    public void register() {
        svc.core.permissions().register(RunListener.ADMIN, "Dungeon administration", PermissionDefault.OP);
        svc.core.commands().command("dungeon").aliases("dg", "catacombs").description("Enter the Catacombs")
            .usage("/dungeon [start <floor>|leave|floors|stats|top [floor]|claim|forge|help]")
            .permission(ENTER, PermissionDefault.TRUE).executor(this).tab(this).register();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Msg msg = svc.core.msg();
        if (args.length == 0) {
            if (sender instanceof Player p) {
                svc.guis.floors(p);
            } else {
                help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            admin(sender, args);
            return true;
        }
        if (sub.equals("help")) {
            help(sender);
            return true;
        }
        if (!(sender instanceof Player p)) {
            msg.error(sender, "Only players can use this command. Admins: /dungeon admin.");
            return true;
        }
        switch (sub) {
            case "start", "play" -> {
                if (args.length < 2) {
                    svc.guis.floors(p);
                } else {
                    Integer floor = parseFloor(args[1]);
                    if (floor == null) {
                        msg.error(p, "Pick a floor number from 1 to " + svc.cfg().floorCount() + ".");
                    } else {
                        start(p, floor, null);
                    }
                }
            }
            case "leave", "quit" -> {
                if (svc.runs.leave(p)) {
                    msg.ok(p, "You left the dungeon.");
                } else if (svc.runs.sendOutStray(p)) {
                    msg.ok(p, "You were sent out of the dungeon.");
                } else {
                    msg.error(p, "You are not in a dungeon run.");
                }
            }
            case "floors" -> svc.guis.floors(p);
            case "stats" -> stats(p);
            case "top", "leaderboard", "lb" -> {
                if (args.length >= 2 && parseFloor(args[1]) != null) {
                    svc.guis.leaderboard(p, parseFloor(args[1]));
                } else {
                    svc.guis.leaderboard(p, 1);
                }
            }
            case "claim" -> claim(p);
            case "forge", "shop" -> forge(p, args);
            default -> help(p);
        }
        return true;
    }

    private Integer parseFloor(String s) {
        try {
            int n = Integer.parseInt(s.replaceFirst("^[fF]", ""));
            return svc.cfg().floor(n) != null ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void help(CommandSender to) {
        Msg msg = svc.core.msg();
        msg.mini(to, "<dark_red><bold>Catacombs");
        for (String line : List.of(
            "/dungeon <dark_gray>- floor menu",
            "/dungeon start <floor> <dark_gray>- start a run with your party (or alone)",
            "/dungeon leave <dark_gray>- abandon the current run",
            "/dungeon floors | stats | top [floor] | claim",
            "/dungeon forge <dark_gray>- trade dungeon materials (also <white>/dungeon forge buy <trade><dark_gray>)",
            "/party <dark_gray>- form a party of up to " + svc.parties.maxSize())) {
            msg.mini(to, "<gray>" + line);
        }
        if (to.hasPermission(RunListener.ADMIN)) {
            msg.mini(to, "<gray>/dungeon admin <list|abort <id|all>|start <floor> [seed]|cleanup|reload>");
        }
    }

    /** Ready check for parties, then the run. Shared with the menus. */
    public void start(Player p, int floor, Long seed) {
        Msg msg = svc.core.msg();
        UUID id = p.getUniqueId();
        if (svc.runs.runOf(id) != null) {
            msg.error(p, "You are already in a dungeon run.");
            return;
        }
        Party party = svc.parties.ensure(id, p.getName());
        String problem = svc.runs.check(party, id, floor);
        if (problem != null) {
            msg.error(p, problem);
            return;
        }
        if (party.size() == 1) {
            String err = svc.runs.start(party, id, floor, seed);
            if (err != null) {
                msg.error(p, err);
            }
            return;
        }
        PartyService.Result r = svc.parties.startReadyCheck(id, outcome -> {
            Player leader = Bukkit.getPlayer(id);
            if (leader == null) {
                return;
            }
            switch (outcome) {
                case ALL_READY -> {
                    Party now = svc.parties.partyOf(id);
                    if (now == null) {
                        return;
                    }
                    String err = svc.runs.start(now, id, floor, seed);
                    if (err != null) {
                        msg.error(leader, err);
                    }
                }
                case DECLINED -> msg.warn(leader, "Someone is not ready. The run was not started.");
                case EXPIRED -> msg.warn(leader, "The ready check timed out.");
                default -> msg.warn(leader, "The ready check was cancelled.");
            }
        });
        if (r.ok()) {
            msg.ok(p, "Ready check for floor " + floor + " started. The run begins when everyone types /party ready.");
        } else {
            msg.error(p, r.message());
        }
    }

    private void forge(Player p, String[] args) {
        Msg msg = svc.core.msg();
        if (args.length >= 3 && args[1].equalsIgnoreCase("buy")) {
            var trade = svc.forge.trade(args[2]);
            if (trade == null) {
                msg.error(p, "There is no trade called " + args[2] + ". Open /dungeon forge to see them.");
                return;
            }
            var r = svc.forge.buy(p, trade);
            if (r.ok()) {
                msg.ok(p, r.message());
            } else {
                msg.error(p, r.message());
            }
            return;
        }
        if (svc.runs.runOf(p.getUniqueId()) != null) {
            msg.error(p, "The forge is closed while you are in a dungeon run.");
            return;
        }
        svc.forge.open(p);
    }

    private void stats(Player p) {
        DungeonData d = svc.core.profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        Msg msg = svc.core.msg();
        msg.mini(p, "<dark_red><bold>Your Catacombs record");
        DungeonConfig cfg = svc.cfg();
        int level = Catacombs.level(cfg, d.catacombsXp);
        String next = level >= cfg.catacombsMaxLevel ? "max level"
            : (Catacombs.xpForLevel(cfg, level + 1) - d.catacombsXp) + " XP to level " + (level + 1);
        msg.mini(p, "<gray>Catacombs level: <white>" + level + " <dark_gray>(" + next + ")<gray>, reward bonus <white>+"
            + String.format("%.0f", Catacombs.bonusPercent(cfg, level)) + "%");
        msg.mini(p, "<gray>Runs: <white>" + d.totalRuns + "<gray>  Clears: <white>" + d.totalClears
            + "<gray>  Highest floor: <white>" + d.highestFloorCleared);
        msg.mini(p, "<gray>Secrets: <white>" + d.totalSecrets + "<gray>  Kills: <white>" + d.totalKills
            + "<gray>  Deaths: <white>" + d.totalDeaths);
        for (int f = 1; f <= svc.cfg().floorCount(); f++) {
            DungeonData.FloorStats s = d.floors.get(Integer.toString(f));
            if (s != null && s.runs > 0) {
                msg.mini(p, "<dark_gray>F" + f + " <gray>" + s.clears + "/" + s.runs + " cleared"
                    + (s.bestMillis > 0 ? ", best <white>" + Msg.duration(s.bestMillis) + " <gray>(" + s.bestGrade + ")" : ""));
            }
        }
    }

    private void claim(Player p) {
        Msg msg = svc.core.msg();
        DungeonData d = svc.core.profiles().section(p.getUniqueId(), "dungeon", DungeonData.class, DungeonData::new);
        if (d.pending.isEmpty()) {
            msg.info(p, "You have no rewards waiting.");
            return;
        }
        int given = RewardDelivery.claim(svc.core, p);
        int left = d.pendingCount();
        msg.ok(p, "Claimed " + given + " item(s)." + (left > 0 ? " " + left + " more are waiting: free some inventory space." : ""));
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String[] args) {
        Msg msg = svc.core.msg();
        if (!sender.hasPermission(RunListener.ADMIN)) {
            msg.error(sender, "You do not have permission to administrate the dungeon.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                List<DungeonRun> runs = svc.runs.all();
                msg.info(sender, runs.size() + " run(s) alive, load " + svc.runs.load() + "/" + svc.cfg().maxConcurrentRuns + ".");
                for (DungeonRun r : runs) {
                    msg.info(sender, "#" + r.id + " floor " + r.floorNumber() + " " + r.state() + " players "
                        + r.participants().size() + " seed " + r.seed);
                }
            }
            case "abort" -> {
                if (args.length < 3) {
                    msg.error(sender, "Usage: /dungeon admin abort <id|all>");
                    return;
                }
                int n = 0;
                for (DungeonRun r : svc.runs.all()) {
                    if (args[2].equalsIgnoreCase("all") || r.id.equals(args[2])) {
                        svc.runs.abort(r, "An administrator ended the run.");
                        n++;
                    }
                }
                msg.ok(sender, n == 0 ? "No such run." : "Aborted " + n + " run(s).");
            }
            case "start" -> {
                if (!(sender instanceof Player p)) {
                    msg.error(sender, "Players only.");
                    return;
                }
                Integer floor = args.length > 2 ? parseFloor(args[2]) : null;
                if (floor == null) {
                    msg.error(sender, "Usage: /dungeon admin start <floor> [seed]");
                    return;
                }
                Long seed = null;
                if (args.length > 3) {
                    try {
                        seed = Long.parseLong(args[3]);
                    } catch (NumberFormatException e) {
                        msg.error(sender, "The seed must be a number.");
                        return;
                    }
                }
                Party party = svc.parties.ensure(p.getUniqueId(), p.getName());
                String err = svc.runs.start(party, p.getUniqueId(), floor, seed);
                if (err != null) {
                    msg.error(sender, err);
                }
            }
            case "cleanup" -> {
                svc.runs.cleanOrphans();
                msg.ok(sender, "Leftover dungeon world folders are being removed.");
            }
            case "reload" -> {
                List<String> problems = svc.reload();
                msg.ok(sender, "dungeon.yml reloaded." + (problems.isEmpty() ? "" : " " + problems.size() + " value(s) were corrected, see the log."));
                problems.forEach(pr -> svc.core.getPlugin().getLogger().warning("dungeon.yml: " + pr));
            }
            default -> msg.error(sender, "Unknown admin command. Try list, abort, start, cleanup, reload.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            SUB.stream().filter(s -> s.startsWith(last) && (!s.equals("admin") || sender.hasPermission(RunListener.ADMIN))).forEach(out::add);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "forge", "shop" -> out.add("buy");
                case "start", "top", "play" -> {
                    for (int f = 1; f <= svc.cfg().floorCount(); f++) {
                        if (Integer.toString(f).startsWith(last)) {
                            out.add(Integer.toString(f));
                        }
                    }
                }
                case "admin" -> {
                    if (sender.hasPermission(RunListener.ADMIN)) {
                        ADMIN.stream().filter(s -> s.startsWith(last)).forEach(out::add);
                    }
                }
                default -> { }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("forge") || args[0].equalsIgnoreCase("shop"))
                && args[1].equalsIgnoreCase("buy")) {
            svc.cfg().forgeTrades.forEach(t -> out.add(t.id()));
            out.removeIf(s -> !s.startsWith(last));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission(RunListener.ADMIN)) {
            if (args[1].equalsIgnoreCase("abort")) {
                out.add("all");
                svc.runs.all().forEach(r -> out.add(r.id));
            } else if (args[1].equalsIgnoreCase("start")) {
                for (int f = 1; f <= svc.cfg().floorCount(); f++) {
                    out.add(Integer.toString(f));
                }
            }
            out.removeIf(s -> !s.startsWith(last));
        }
        return out;
    }
}
