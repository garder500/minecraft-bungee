package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.minions.MinionConfig.Type;

/** {@code /minion}: player help and list, admin give / list / purge. */
final class MinionCommand implements CommandExecutor, TabCompleter {
    static final String ADMIN = "vampyuyu.sb.minions.admin";
    private static final int LIST_LIMIT = 25;

    private final Core core;
    private final MinionManager manager;

    MinionCommand(Core core, MinionManager manager) {
        this.core = core;
        this.manager = manager;
    }

    private MinionConfig config() {
        return manager.config();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "?" -> help(sender);
            case "info" -> info(sender);
            case "list" -> list(sender, args);
            case "give" -> give(sender, args);
            case "purge" -> purge(sender, args);
            default -> {
                core.msg().error(sender, "Unknown sub-command '" + args[0] + "'.");
                help(sender);
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        core.msg().info(sender, "Minions work for you on your island, even while you are away.");
        core.msg().info(sender, "Craft one (8 resources around a tool), right-click a block to place it, right-click it to open it.");
        core.msg().info(sender, "/minion info: your slots and how to unlock more");
        core.msg().info(sender, "/minion list: where your minions are");
        if (sender.hasPermission(ADMIN)) {
            core.msg().info(sender, "/minion list <player|all>: someone else's minions");
            core.msg().info(sender, "/minion give <type> <tier> [player]: give a minion item");
            core.msg().info(sender, "/minion give fuel|upgrade|hat <id> [amount] [player]: give a fuel, upgrade or hat");
            core.msg().info(sender, "/minion purge <player|orphans> [confirm]: remove minions, owing their content back");
        }
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            core.msg().error(sender, "Only players have minion slots.");
            return;
        }
        int unique = manager.profileOf(p.getUniqueId()).crafted.size();
        int slots = manager.slotsFor(p.getUniqueId());
        core.msg().info(p, "Minion slots: " + manager.placedCount(p.getUniqueId()) + " used of " + slots + ".");
        int next = SlotRules.untilNext(config(), unique);
        core.msg().info(p, "Different minions crafted: " + unique + (next < 0 ? ". You have every slot."
            : ". Craft or upgrade to " + next + " more different minions (type and tier) for the next slot."));
    }

    private void list(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        List<MinionData> minions;
        String title;
        if (args.length >= 2) {
            if (!admin) {
                core.msg().error(sender, "You can only list your own minions.");
                return;
            }
            if (args[1].equalsIgnoreCase("all")) {
                minions = manager.store().all();
                title = "all minions";
            } else {
                OfflinePlayer target = lookup(args[1]);
                if (target == null) {
                    core.msg().error(sender, "No player named '" + args[1] + "' is known.");
                    return;
                }
                minions = manager.store().ownedBy(target.getUniqueId().toString());
                title = "minions of " + args[1];
            }
        } else if (sender instanceof Player p) {
            minions = manager.store().ownedBy(p.getUniqueId().toString());
            title = "your minions";
        } else {
            core.msg().error(sender, "Usage: /minion list <player|all>");
            return;
        }
        minions.sort(Comparator.comparing((MinionData d) -> d.world).thenComparing(d -> d.type).thenComparingInt(d -> d.tier));
        core.msg().info(sender, minions.size() + " " + title + ":");
        int shown = 0;
        for (MinionData d : minions) {
            if (shown++ >= LIST_LIMIT) {
                core.msg().info(sender, "... and " + (minions.size() - LIST_LIMIT) + " more.");
                break;
            }
            Type type = config().types.get(d.type);
            String name = type == null ? d.type + " (removed type)" : MinionItems.minionName(type, d.tier);
            core.msg().info(sender, name + " at " + d.world + " " + d.x + ", " + d.y + ", " + d.z + ": "
                + manager.statusOf(d).label() + ", " + Msg.grouped(manager.logic().storage(d).total()) + " items stored"
                + (admin && args.length >= 2 ? " [" + ownerLabel(d) + "]" : ""));
        }
    }

    private String ownerLabel(MinionData d) {
        try {
            return core.profiles().lastName(UUID.fromString(d.owner));
        } catch (RuntimeException e) {
            return d.owner;
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().error(sender, "You do not have permission to give minions.");
            return;
        }
        if (args.length < 3) {
            core.msg().error(sender, "Usage: /minion give <type> <tier> [player]  or  /minion give fuel|upgrade|hat <id> [amount] [player]");
            return;
        }
        String first = args[1].toLowerCase(Locale.ROOT);
        if (first.equals("fuel") || first.equals("upgrade") || first.equals("hat")) {
            giveExtra(sender, first, args);
            return;
        }
        Type type = config().types.get(first);
        if (type == null) {
            core.msg().error(sender, "Unknown minion type '" + args[1] + "'. Types: " + String.join(", ", config().types.keySet()));
            return;
        }
        int tier;
        try {
            tier = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            core.msg().error(sender, "The tier must be a number from 1 to " + MinionConfig.MAX_TIER + ".");
            return;
        }
        if (tier < 1 || tier > MinionConfig.MAX_TIER) {
            core.msg().error(sender, "The tier must be a number from 1 to " + MinionConfig.MAX_TIER + ".");
            return;
        }
        Player target = target(sender, args.length > 3 ? args[3] : null);
        if (target == null) {
            return;
        }
        ItemStack stack = manager.freshMinionItem(type.id(), tier);
        deliver(sender, target, stack, MinionItems.minionName(type, tier));
    }

    private void giveExtra(CommandSender sender, String kind, String[] args) {
        String id = args[2].toLowerCase(Locale.ROOT);
        String key;
        String name;
        switch (kind) {
            case "fuel" -> {
                var fuel = config().fuels.get(id);
                key = fuel == null ? null : fuel.key();
                name = fuel == null ? null : fuel.name();
            }
            case "upgrade" -> {
                var up = config().upgrades.get(id);
                key = up == null ? null : up.key();
                name = up == null ? null : up.name();
            }
            default -> {
                var skin = config().skins.get(id);
                key = skin == null ? null : skin.key();
                name = skin == null ? null : skin.name();
            }
        }
        if (key == null) {
            core.msg().error(sender, "Unknown " + kind + " '" + args[2] + "'.");
            return;
        }
        int amount = 1;
        int next = 3;
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
                next = 4;
            } catch (NumberFormatException e) {
                amount = 1;
            }
        }
        if (amount < 1 || amount > 2304) {
            core.msg().error(sender, "The amount must be from 1 to 2304.");
            return;
        }
        Player target = target(sender, args.length > next ? args[next] : null);
        if (target == null) {
            return;
        }
        long left = manager.give(target, key, amount);
        if (left > 0) {
            core.msg().warn(sender, left + " items did not fit in the inventory of " + target.getName() + " and were dropped.");
            manager.giveDropped(target, key, left);
        }
        core.msg().ok(sender, "Gave " + amount + " x " + name + " to " + target.getName() + ".");
    }

    private void deliver(CommandSender sender, Player target, ItemStack stack, String name) {
        Map<Integer, ItemStack> left = target.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        core.msg().ok(sender, "Gave " + name + " to " + target.getName() + ".");
        if (sender != target) {
            core.msg().info(target, "You received a " + name + ".");
        }
    }

    private Player target(CommandSender sender, String name) {
        if (name == null) {
            if (sender instanceof Player p) {
                return p;
            }
            core.msg().error(sender, "Name an online player.");
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            core.msg().error(sender, name + " is not online.");
        }
        return online;
    }

    private OfflinePlayer lookup(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached;
    }

    private void purge(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            core.msg().error(sender, "You do not have permission to purge minions.");
            return;
        }
        if (args.length < 2) {
            core.msg().error(sender, "Usage: /minion purge <player|orphans> [confirm]");
            return;
        }
        boolean orphans = args[1].equalsIgnoreCase("orphans");
        OfflinePlayer owner = orphans ? null : lookup(args[1]);
        if (!orphans && owner == null) {
            core.msg().error(sender, "No player named '" + args[1] + "' is known.");
            return;
        }
        int count = 0;
        for (MinionData d : manager.store().all()) {
            if (orphans ? (manager.statusOf(d) == Status.ISLAND_MISSING || !config().types.containsKey(d.type))
                    : d.owner.equals(owner.getUniqueId().toString())) {
                count++;
            }
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            core.msg().warn(sender, count + " minions would be removed. Their content is owed back to their owners. "
                + "Run '/minion purge " + args[1] + " confirm' to do it.");
            return;
        }
        int removed = manager.purge(orphans ? null : owner.getUniqueId(), orphans);
        core.msg().ok(sender, "Removed " + removed + " minions. Their items are owed to their owners.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("help", "info", "list"));
            if (admin) {
                out.addAll(List.of("give", "purge"));
            }
        } else if (admin && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                out.addAll(config().types.keySet());
                out.addAll(List.of("fuel", "upgrade", "hat"));
            } else if (args.length == 3) {
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "fuel" -> out.addAll(config().fuels.keySet());
                    case "upgrade" -> out.addAll(config().upgrades.keySet());
                    case "hat" -> out.addAll(config().skins.keySet());
                    default -> {
                        for (int t = 1; t <= MinionConfig.MAX_TIER; t++) {
                            out.add(String.valueOf(t));
                        }
                    }
                }
            } else {
                players(out);
            }
        } else if (admin && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("purge")) && args.length == 2) {
            out.add(args[0].equalsIgnoreCase("list") ? "all" : "orphans");
            players(out);
        } else if (admin && args[0].equalsIgnoreCase("purge") && args.length == 3) {
            out.add("confirm");
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }

    private static void players(List<String> out) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
        }
    }
}
