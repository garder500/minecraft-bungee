package win.vampyuyu.survival.boss.slayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.boss.BossData;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** {@code /slayer}: menu, start, spawn, cancel, status and admin tools. */
public final class SlayerCommand implements CommandExecutor, TabCompleter {
    public static final String ADMIN = "vampyuyu.sb.boss.admin";

    private final Core core;
    private final SlayerManager manager;
    private final SlayerGui gui;

    public SlayerCommand(Core core, SlayerManager manager, SlayerGui gui) {
        this.core = core;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            return admin(sender, args);
        }
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can run slayer quests. Admins: /slayer admin ...");
            return true;
        }
        switch (sub) {
            case "gui", "menu" -> gui.openMain(player);
            case "start" -> {
                if (args.length < 3) {
                    core.msg().info(player, "Usage: /slayer start <slayer> <tier 1-5>");
                    return true;
                }
                SlayerDefinition def = manager.slayer(args[1].toLowerCase(Locale.ROOT)).orElse(null);
                if (def == null) {
                    core.msg().error(player, "Unknown slayer '" + args[1] + "'. Options: " + names());
                    return true;
                }
                int tier = parseTier(args[2]);
                String refusal = manager.start(player, def, tier);
                if (refusal != null) {
                    core.msg().error(player, refusal);
                }
            }
            case "spawn" -> {
                String refusal = manager.spawnBoss(player);
                if (refusal != null) {
                    core.msg().error(player, refusal);
                }
            }
            case "cancel" -> {
                if (manager.cancel(player)) {
                    core.msg().ok(player, "Quest cancelled. The coins are not refunded.");
                } else {
                    core.msg().error(player, "You have no slayer quest.");
                }
            }
            case "status" -> status(player);
            case "help" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void help(CommandSender to) {
        core.msg().info(to, "Slayer commands:");
        core.msg().info(to, "/slayer - open the menu");
        core.msg().info(to, "/slayer start <slayer> <tier> - pay and start a quest");
        core.msg().info(to, "/slayer status - show your quest and levels");
        core.msg().info(to, "/slayer spawn - summon your boss when the quest is ready");
        core.msg().info(to, "/slayer cancel - give up the quest (no refund)");
        if (to.hasPermission(ADMIN)) {
            core.msg().info(to, "/slayer admin <setxp|addxp|reset> <player> <slayer> [xp]");
        }
    }

    private String names() {
        List<String> ids = new ArrayList<>();
        manager.slayers().forEach(s -> ids.add(s.id()));
        return String.join(", ", ids);
    }

    private static int parseTier(String text) {
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> {
                try {
                    yield Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
        };
    }

    private void status(Player player) {
        BossData data = manager.data(player);
        BossData.Quest quest = data.quest;
        if (quest == null) {
            core.msg().info(player, "No active quest. Open /slayer to start one.");
        } else {
            core.msg().info(player, "Quest: " + quest.slayer + " tier " + SlayerDefinition.roman(quest.tier) + " - "
                + quest.state + " (" + Math.round(quest.progress) + "/" + Math.round(quest.required) + ")");
        }
        for (SlayerDefinition def : manager.slayers()) {
            long xp = data.slayerXp.getOrDefault(def.id(), 0L);
            core.msg().info(player, def.name() + ": level " + SlayerMath.level(xp, def.levelXp()) + " (" + Msg.number(xp) + " XP)");
        }
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().error(sender, "You do not have permission.");
            return true;
        }
        if (args.length < 4) {
            core.msg().info(sender, "Usage: /slayer admin <setxp|addxp|reset> <player> <slayer> [xp]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            core.msg().error(sender, "Player must be online: " + args[2]);
            return true;
        }
        SlayerDefinition def = manager.slayer(args[3].toLowerCase(Locale.ROOT)).orElse(null);
        if (def == null) {
            core.msg().error(sender, "Unknown slayer. Options: " + names());
            return true;
        }
        BossData data = manager.data(target);
        String action = args[1].toLowerCase(Locale.ROOT);
        long amount = 0;
        if (action.equals("setxp") || action.equals("addxp")) {
            try {
                amount = Long.parseLong(args.length > 4 ? args[4] : "x");
            } catch (NumberFormatException e) {
                core.msg().error(sender, "Give the XP as a whole number.");
                return true;
            }
            if (amount < 0 || amount > 1_000_000_000L) {
                core.msg().error(sender, "XP must be between 0 and 1,000,000,000.");
                return true;
            }
        }
        switch (action) {
            case "setxp" -> data.slayerXp.put(def.id(), amount);
            case "addxp" -> data.slayerXp.merge(def.id(), amount, Long::sum);
            case "reset" -> {
                data.slayerXp.remove(def.id());
                data.slayerKills.keySet().removeIf(k -> k.startsWith(def.id() + ":"));
            }
            default -> {
                core.msg().error(sender, "Unknown action. Use setxp, addxp or reset.");
                return true;
            }
        }
        core.profiles().markDirty(target.getUniqueId());
        manager.applyPerks(target);
        core.msg().ok(sender, target.getName() + " is now level " + SlayerQuests.level(data, def) + " in " + def.name() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("gui", "start", "spawn", "cancel", "status", "help"));
            if (sender.hasPermission(ADMIN)) {
                out.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            manager.slayers().forEach(s -> out.add(s.id()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            out.addAll(List.of("1", "2", "3", "4", "5"));
        } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission(ADMIN)) {
            if (args.length == 2) {
                out.addAll(List.of("setxp", "addxp", "reset"));
            } else if (args.length == 3) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 4) {
                manager.slayers().forEach(s -> out.add(s.id()));
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
