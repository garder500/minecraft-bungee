package win.vampyuyu.survival.utility;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** /hat, /playtime, /near, /seen and /ping. */
final class MiscCommands {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
        .withZone(ZoneOffset.UTC);

    private final Core core;
    private final Msg msg;
    private final Supplier<UtilityConfig> config;

    MiscCommands(Core core, Supplier<UtilityConfig> config) {
        this.core = core;
        this.msg = core.msg();
        this.config = config;
    }

    void register() {
        core.commands().command("hat").description("Wear the item in your hand on your head").usage("/hat")
            .permission(Perms.HAT, PermissionDefault.TRUE).executor(this::hat).register();
        core.commands().command("playtime").aliases("pt").description("Show how long you have played")
            .usage("/playtime [player]").permission(Perms.INFO, PermissionDefault.TRUE)
            .executor(this::playtime).tab((s, c, l, a) -> a.length == 1 ? onlineNames(s, a[0]) : List.of())
            .register();
        core.commands().command("near").description("List the players close to you").usage("/near [radius]")
            .permission(Perms.INFO, PermissionDefault.TRUE).executor(this::near).register();
        core.commands().command("seen").description("When was a player last online").usage("/seen <player>")
            .permission(Perms.INFO, PermissionDefault.TRUE).executor(this::seen)
            .tab((s, c, l, a) -> a.length == 1 ? onlineNames(s, a[0]) : List.of()).register();
        core.permissions().register(Perms.PING_OTHERS, "Ping of other players", PermissionDefault.TRUE);
        core.commands().command("ping").description("Show your latency").usage("/ping [player]")
            .permission(Perms.INFO, PermissionDefault.TRUE).executor(this::ping)
            .tab((s, c, l, a) -> a.length == 1 ? onlineNames(s, a[0]) : List.of()).register();
    }

    private static List<String> onlineNames(CommandSender sender, String typed) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player self) || self.canSee(p)) {
                names.add(p.getName());
            }
        }
        return Cmd.match(names, typed);
    }

    // ---------------------------------------------------------------- /hat

    private boolean hat(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        ItemStack head = inventory.getHelmet();
        boolean handEmpty = hand == null || hand.getType().isAir();
        boolean headEmpty = head == null || head.getType().isAir();
        if (handEmpty && headEmpty) {
            msg.error(player, "Hold an item in your hand to wear it as a hat.");
            return true;
        }
        if (!headEmpty && head.containsEnchantment(Enchantment.BINDING_CURSE)
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            msg.error(player, "Your helmet is cursed with binding and cannot be taken off.");
            return true;
        }
        if (handEmpty) {
            // Take the hat off into the hand.
            inventory.setHelmet(null);
            inventory.setItemInMainHand(head);
            msg.ok(player, "Hat removed.");
            return true;
        }
        if (hand.getAmount() > 1) {
            // One goes on the head, the old helmet needs a free place so nothing is lost or dropped.
            if (!headEmpty && inventory.firstEmpty() == -1) {
                msg.error(player, "Your inventory is full: free a slot first.");
                return true;
            }
            ItemStack one = hand.clone();
            one.setAmount(1);
            ItemStack rest = hand.clone();
            rest.setAmount(hand.getAmount() - 1);
            inventory.setHelmet(one);
            inventory.setItemInMainHand(rest);
            if (!headEmpty) {
                inventory.addItem(head);
            }
        } else {
            ItemStack worn = hand.clone();
            inventory.setHelmet(worn);
            inventory.setItemInMainHand(headEmpty ? null : head);
        }
        msg.ok(player, "Enjoy your new hat.");
        return true;
    }

    // ---------------------------------------------------------------- /playtime

    private boolean playtime(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Player player = Cmd.player(msg, sender);
            if (player != null) {
                msg.info(player, "You have played " + Msg.duration(ticks(player) * 50L) + ".");
            }
            return true;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null && (!(sender instanceof Player self) || self.canSee(online))) {
            msg.info(sender, online.getName() + " has played " + Msg.duration(ticks(online) * 50L) + ".");
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (offline == null || !offline.hasPlayedBefore()) {
            msg.error(sender, "I do not know a player called '" + args[0] + "'.");
            return true;
        }
        // Reading the statistics file of an offline player is disk IO: keep it off the main thread.
        core.scheduler().async(() -> {
            long ticks = ticks(offline);
            core.scheduler().sync(() -> msg.info(sender, (offline.getName() == null ? args[0] : offline.getName())
                + " has played " + Msg.duration(ticks * 50L) + "."));
        });
        return true;
    }

    private static long ticks(OfflinePlayer player) {
        try {
            return Math.max(0, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- /near

    private boolean near(CommandSender sender, Command command, String label, String[] args) {
        Player player = Cmd.player(msg, sender);
        if (player == null) {
            return true;
        }
        UtilityConfig cfg = config.get();
        int radius = cfg.nearDefault();
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                msg.error(player, "'" + args[0] + "' is not a number. Usage: /near [radius]");
                return true;
            }
        }
        if (radius < 1) {
            msg.error(player, "The radius must be at least 1.");
            return true;
        }
        radius = Math.min(radius, cfg.nearMax());
        double max = (double) radius * radius;
        List<Player> found = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && player.canSee(other)
                    && other.getLocation().distanceSquared(player.getLocation()) <= max) {
                found.add(other);
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(player.getLocation())));
        if (found.isEmpty()) {
            msg.info(player, "Nobody within " + radius + " blocks.");
            return true;
        }
        StringBuilder out = new StringBuilder("Players within " + radius + " blocks: ");
        for (int i = 0; i < found.size(); i++) {
            Player other = found.get(i);
            out.append(i == 0 ? "" : ", ").append(other.getName()).append(" (")
                .append((int) Math.round(other.getLocation().distance(player.getLocation()))).append("m)");
        }
        msg.info(player, out.toString());
        return true;
    }

    // ---------------------------------------------------------------- /seen

    private boolean seen(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            msg.info(sender, "Usage: /seen <player>");
            return true;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        boolean visible = online != null && (!(sender instanceof Player self) || self.canSee(online));
        if (visible) {
            long since = Math.max(0, System.currentTimeMillis() - online.getLastLogin());
            msg.info(sender, online.getName() + " is online right now (for " + Msg.duration(since) + ").");
            return true;
        }
        OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
        if (offline == null || (!offline.hasPlayedBefore() && online == null)) {
            msg.error(sender, "I do not know a player called '" + args[0] + "'.");
            return true;
        }
        long last = offline.getLastSeen();
        String name = offline.getName() == null ? args[0] : offline.getName();
        if (last <= 0) {
            msg.info(sender, name + " has not been seen yet.");
        } else {
            msg.info(sender, name + " was last seen " + Msg.duration(Math.max(0, System.currentTimeMillis() - last))
                + " ago (" + DATE.format(Instant.ofEpochMilli(last)) + ").");
        }
        return true;
    }

    // ---------------------------------------------------------------- /ping

    private boolean ping(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length == 0) {
            target = Cmd.player(msg, sender);
            if (target == null) {
                return true;
            }
        } else {
            if (!sender.hasPermission(Perms.PING_OTHERS)) {
                msg.error(sender, "You can only check your own ping.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null || (sender instanceof Player self && !self.canSee(target))) {
                msg.error(sender, "Player '" + args[0] + "' is not online.");
                return true;
            }
        }
        String who = target.equals(sender) ? "Your" : target.getName() + "'s";
        msg.info(sender, who + " ping is " + target.getPing() + " ms.");
        return true;
    }
}
