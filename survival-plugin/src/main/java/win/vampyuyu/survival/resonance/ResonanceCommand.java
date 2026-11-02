package win.vampyuyu.survival.resonance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.Resonance;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.resonance.ResonanceConfig.Bastion;

/** {@code /resonance}: progression display, rank choice, Shard forge. {@code give} is for admins. */
final class ResonanceCommand implements CommandExecutor, TabCompleter {
    static final String ADMIN = "vampyuyu.sb.resonance.admin";

    private final Core core;
    private final ResonanceService service;

    ResonanceCommand(Core core, ResonanceService service) {
        this.core = core;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Players only.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status", "info" -> status(player);
            case "rank" -> rank(player, args);
            case "forge" -> forge(player, args);
            case "give" -> give(player, args);
            default -> core.msg().info(player, "/resonance [status | rank <1-5> | forge <boss> | give <n>]");
        }
        return true;
    }

    private void status(Player player) {
        ResonanceData d = service.data(player.getUniqueId());
        ResonanceConfig cfg = service.config();
        core.msg().mini(player, "<gold><bold>Resonance</bold> <gray>- chosen rank <white>" + Resonance.roman(d.selected)
            + " <gray>(boss health x" + trim(Resonance.hpMultiplier(d.selected)) + ", damage x"
            + trim(Resonance.damageMultiplier(d.selected)) + ")");
        core.msg().mini(player, "<gray>Memory Shards: <aqua>" + d.shards + " <dark_gray>(" + d.earned + " earned) <gray>- "
            + cfg.forgeCost() + " forge one component of a boss whose component you already found.");
        for (Bastion b : cfg.ordered()) {
            int best = d.best.getOrDefault(b.bossId(), 0);
            String place = b.realm() == null ? "Echo of the Rift" : b.realm().title() + " Bastion";
            StringBuilder bar = new StringBuilder();
            for (int r = 1; r <= Resonance.MAX_RANK; r++) {
                bar.append(r <= best ? "<green>■" : "<dark_gray>■");
            }
            int dryNow = d.dry.getOrDefault(b.bossId(), 0);
            String found = d.found.contains(b.bossId()) ? "<aqua>found" : "<dark_gray>not found yet";
            core.msg().mini(player, "<white>" + place + " " + bar + " <gray>" + d.clears.getOrDefault(b.bossId(), 0)
                + " clears, component " + found + "<gray>, " + dryNow + " run(s) since the last one");
        }
        int left = Math.max(0, cfg.weeklyForgeLimit()
            - (ResonanceMath.week(System.currentTimeMillis()).equals(d.forgeWeek) ? d.forgedThisWeek : 0));
        core.msg().mini(player, "<gray>Forges left this week: <white>" + left);
    }

    private void rank(Player player, String[] args) {
        if (args.length < 2) {
            core.msg().info(player, "Usage: /resonance rank <1-5>");
            return;
        }
        int wanted;
        try {
            wanted = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            core.msg().error(player, "The rank is a number from 1 to " + Resonance.MAX_RANK + ".");
            return;
        }
        if (wanted < 1 || wanted > Resonance.MAX_RANK) {
            core.msg().error(player, "The rank is a number from 1 to " + Resonance.MAX_RANK + ".");
            return;
        }
        ResonanceData d = service.data(player.getUniqueId());
        d.selected = wanted;
        core.profiles().markDirty(player.getUniqueId());
        core.msg().ok(player, "Resonance " + Resonance.roman(wanted) + " chosen. Each Bastion boss runs at this rank "
            + "once you cleared the rank below it there (otherwise one rank above your best clear).");
    }

    private void forge(Player player, String[] args) {
        ResonanceConfig cfg = service.config();
        Bastion bastion = args.length < 2 ? null : cfg.bastions().get(args[1].toLowerCase(Locale.ROOT));
        if (bastion == null) {
            core.msg().error(player, "Usage: /resonance forge <" + String.join("|", cfg.bastions().keySet()) + ">");
            return;
        }
        ItemStack stack = service.component(bastion, 1);
        if (stack == null) {
            core.msg().error(player, "That component does not exist on this server.");
            return;
        }
        ResonanceData d = service.data(player.getUniqueId());
        ResonanceRules.Forge result = ResonanceRules.forge(d, bastion, System.currentTimeMillis(), cfg);
        switch (result) {
            case NOT_FOUND -> core.msg().error(player, "You never found that component: win it once first.");
            case NOT_ENOUGH -> core.msg().error(player, "You need " + cfg.forgeCost() + " Memory Shards (you have "
                + d.shards + ").");
            case WEEKLY_LIMIT -> core.msg().error(player, "You forged " + cfg.weeklyForgeLimit()
                + " components this week already.");
            case OK -> {
                core.profiles().markDirty(player.getUniqueId());
                service.give(player, stack);
                core.msg().ok(player, "Forged a component for " + cfg.forgeCost() + " Memory Shards.");
            }
        }
    }

    private void give(Player player, String[] args) {
        if (!player.hasPermission(ADMIN)) {
            core.msg().error(player, "You cannot do that.");
            return;
        }
        long amount;
        try {
            amount = args.length < 2 ? 0 : Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (amount == 0) {
            core.msg().info(player, "Usage: /resonance give <shards>");
            return;
        }
        ResonanceData d = service.data(player.getUniqueId());
        d.shards = Math.max(0, d.shards + amount);
        core.profiles().markDirty(player.getUniqueId());
        core.msg().ok(player, "You now hold " + d.shards + " Memory Shards.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("status", "rank", "forge"));
            if (sender.hasPermission(ADMIN)) {
                out.add("give");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("rank")) {
            out.addAll(List.of("1", "2", "3", "4", "5"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("forge")) {
            out.addAll(service.config().bastions().keySet());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
