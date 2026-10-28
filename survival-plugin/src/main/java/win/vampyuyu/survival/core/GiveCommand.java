package win.vampyuyu.survival.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /sbgive <id> [amount] [player]}: admin hook to hand out any registered custom item. */
final class GiveCommand implements CommandExecutor, TabCompleter {
    private final Items items;
    private final Msg msg;

    GiveCommand(Items items, Msg msg) {
        this.items = items;
        this.msg = msg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 3) {
            return false;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                msg.error(sender, "Amount must be a number.");
                return true;
            }
            if (amount < 1 || amount > 2304) {
                msg.error(sender, "Amount must be between 1 and 2304.");
                return true;
            }
        }
        Player target;
        if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                msg.error(sender, "Player not found: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            msg.error(sender, "Console must name a player: /" + label + " <id> [amount] <player>");
            return true;
        }
        if (items.def(args[0]).isEmpty()) {
            msg.error(sender, "Unknown item: " + args[0]);
            return true;
        }
        int max = items.create(args[0]).getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int batch = Math.min(left, max);
            ItemStack stack = items.create(args[0], batch);
            target.getInventory().addItem(stack).values()
                .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
            left -= batch;
        }
        msg.ok(sender, "Gave " + amount + " x " + args[0] + " to " + target.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            items.ids().stream().filter(id -> id.startsWith(prefix)).sorted().forEach(out::add);
        } else if (args.length == 3) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(out::add);
        }
        return out;
    }
}
