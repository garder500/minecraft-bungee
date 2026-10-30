package win.vampyuyu.survival.economy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** /bal, /coins, /pay and /baltop. */
final class CoinCommands {
    /** A /pay waiting for "/pay confirm". */
    record PendingPay(UUID target, String targetName, long cents, long expiresAt) {}

    private static final long CONFIRM_WINDOW_MS = 30_000;
    private static final int PAGE = 10;

    private final Core core;
    private final EconomyService economy;
    private final BalTop balTop;
    private final Map<UUID, PendingPay> pending = new HashMap<>();

    CoinCommands(Core core, EconomyService economy, BalTop balTop) {
        this.core = core;
        this.economy = economy;
        this.balTop = balTop;
    }

    void register() {
        core.commands().command("balance").aliases("bal", "coins", "purse")
            .description("Show your coins (or another player's)")
            .usage("/balance [player]")
            .permission("vampyuyu.sb.economy.balance", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> balance(s, a))
            .tab((s, c, l, a) -> a.length == 1 ? Targets.onlineNames(a[0]) : List.of()).register();
        core.permissions().register("vampyuyu.sb.economy.balance.others", "See other players' balances",
            PermissionDefault.TRUE);
        core.commands().command("pay")
            .description("Send coins to another player")
            .usage("/pay <player> <amount> | /pay confirm | /pay cancel")
            .permission("vampyuyu.sb.economy.pay", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> pay(s, a))
            .tab((s, c, l, a) -> switch (a.length) {
                case 1 -> {
                    List<String> out = new java.util.ArrayList<>(Targets.onlineNames(a[0]));
                    out.addAll(Targets.filter(List.of("confirm", "cancel"), a[0]));
                    yield out;
                }
                case 2 -> Targets.filter(List.of("1000", "10k", "100k"), a[1]);
                default -> List.of();
            }).register();
        core.commands().command("baltop").aliases("topcoins")
            .description("The richest players")
            .usage("/baltop [page]")
            .permission("vampyuyu.sb.economy.baltop", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> baltop(s, a)).register();
    }

    private static String esc(String text) {
        return MiniMessage.miniMessage().escapeTags(text);
    }

    // ---------------------------------------------------------------- /balance

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length > 1) {
            return false;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                core.msg().error(sender, "Console: use /balance <player>.");
                return true;
            }
            show(sender, player.getUniqueId(), "Your");
            return true;
        }
        if (!sender.hasPermission("vampyuyu.sb.economy.balance.others")) {
            core.msg().error(sender, "You may only look at your own balance.");
            return true;
        }
        Optional<Targets.Target> target = Targets.resolve(core, args[0]);
        if (target.isEmpty()) {
            core.msg().error(sender, "Unknown player: " + args[0]);
            return true;
        }
        Targets.Target t = target.get();
        if (t.online() != null) {
            show(sender, t.id(), t.name() + "'s");
        } else {
            core.scheduler().async(() -> {
                long purse = economy.balanceCents(t.id());
                long bank = economy.bankCents(t.id());
                core.scheduler().sync(() -> sendBalance(sender, t.name() + "'s", purse, bank));
            });
        }
        return true;
    }

    private void show(CommandSender sender, UUID id, String owner) {
        sendBalance(sender, owner, economy.balanceCents(id), economy.bankCents(id));
    }

    private void sendBalance(CommandSender sender, String owner, long purse, long bank) {
        core.msg().mini(sender, "<gray>" + esc(owner) + " purse: <gold>" + Money.coins(purse)
            + "</gold> <dark_gray>|</dark_gray> bank: <gold>" + Money.coins(bank) + "</gold>");
    }

    // ---------------------------------------------------------------- /pay

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players can pay.");
            return true;
        }
        EconomySettings s = economy.settings();
        if (!s.payEnabled()) {
            core.msg().error(sender, "Paying other players is disabled.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            PendingPay p = pending.remove(player.getUniqueId());
            if (p == null || p.expiresAt() < System.currentTimeMillis()) {
                core.msg().error(sender, "You have no payment waiting for confirmation.");
                return true;
            }
            execute(player, p.target(), p.targetName(), p.cents());
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            core.msg().info(sender, pending.remove(player.getUniqueId()) != null
                ? "Payment cancelled." : "Nothing to cancel.");
            return true;
        }
        if (args.length != 2) {
            return false;
        }
        OptionalLong parsed = Money.parse(args[1]);
        if (parsed.isEmpty()) {
            core.msg().error(sender, "'" + args[1] + "' is not a valid amount. Try 500, 1.5k or 2m.");
            return true;
        }
        long cents = parsed.getAsLong();
        Optional<Targets.Target> target = Targets.resolve(core, args[0]);
        if (target.isEmpty()) {
            core.msg().error(sender, "Unknown player: " + args[0]);
            return true;
        }
        Targets.Target t = target.get();
        if (t.id().equals(player.getUniqueId())) {
            core.msg().error(sender, "You cannot pay yourself.");
            return true;
        }
        if (cents < s.payMinCents()) {
            core.msg().error(sender, "The minimum payment is " + Money.coins(s.payMinCents()) + ".");
            return true;
        }
        if (cents > s.payMaxCents()) {
            core.msg().error(sender, "The maximum per payment is " + Money.coins(s.payMaxCents()) + ".");
            return true;
        }
        if (economy.balanceCents(player.getUniqueId()) < cents) {
            core.msg().error(sender, "You only have " + Money.coins(economy.balanceCents(player.getUniqueId())) + ".");
            return true;
        }
        if (cents > s.payConfirmCents()) {
            pending.put(player.getUniqueId(), new PendingPay(t.id(), t.name(), cents,
                System.currentTimeMillis() + CONFIRM_WINDOW_MS));
            core.msg().mini(sender, "<yellow>Pay <gold>" + Money.coins(cents) + "</gold> to <white>" + esc(t.name())
                + "</white>? Type <green>/pay confirm</green> within 30 seconds, or <red>/pay cancel</red>.");
            return true;
        }
        execute(player, t.id(), t.name(), cents);
        return true;
    }

    /** Debits the sender (with all limits) and credits the target; refunds the sender if the credit cannot happen. */
    private void execute(Player sender, UUID target, String targetName, long cents) {
        EconomySettings s = economy.settings();
        EconomyService.PayDebit result = economy.debitForPay(sender.getUniqueId(), cents, System.currentTimeMillis(),
            "pay:to:" + targetName);
        switch (result) {
            case COOLDOWN -> core.msg().error(sender, "Slow down: wait a few seconds between payments.");
            case DAILY_LIMIT -> core.msg().error(sender, "Daily payment limit reached. You can still send "
                + Money.coins(economy.payRemainingToday(sender.getUniqueId(), System.currentTimeMillis())) + " today.");
            case FUNDS -> core.msg().error(sender, "You do not have enough coins.");
            case OK -> {
                long received = cents - Money.basisPoints(cents, s.payTaxBp());
                Player online = org.bukkit.Bukkit.getPlayer(target);
                if (online != null) {
                    credit(sender, target, targetName, cents, received);
                } else {
                    core.scheduler().async(() -> {
                        try {
                            credit(sender, target, targetName, cents, received);
                        } catch (RuntimeException e) {
                            economy.addCents(sender.getUniqueId(), cents, "pay:refund");
                            core.scheduler().sync(() -> core.msg().error(sender,
                                "The payment failed and you were refunded."));
                            core.getPlugin().getLogger().log(java.util.logging.Level.WARNING, "Offline pay failed", e);
                        }
                    });
                }
            }
        }
    }

    private void credit(Player sender, UUID target, String targetName, long sent, long received) {
        long credited = economy.addCents(target, received, "pay:from:" + sender.getName());
        if (credited < received) {
            // The receiver hit the balance cap: give the rest back so no coin is destroyed.
            economy.addCents(sender.getUniqueId(), received - credited, "pay:refund-cap");
        }
        long taken = sent - (received - credited);
        core.scheduler().sync(() -> {
            core.msg().mini(sender, "<green>Sent <gold>" + Money.coins(taken) + "</gold> to " + esc(targetName) + ".");
            Player online = org.bukkit.Bukkit.getPlayer(target);
            if (online != null && credited > 0) {
                core.msg().mini(online, "<green>" + esc(sender.getName()) + " sent you <gold>"
                    + Money.coins(credited) + "</gold>.");
            }
        });
    }

    // ---------------------------------------------------------------- /baltop

    private boolean baltop(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length == 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        int wanted = page;
        if (balTop.fresh()) {
            sendTop(sender, wanted, balTop.rows());
        } else {
            core.msg().info(sender, "Counting coins...");
            balTop.refresh(rows -> {
                if (!(sender instanceof Player p) || p.isOnline()) {
                    sendTop(sender, wanted, rows);
                }
            });
        }
        return true;
    }

    private void sendTop(CommandSender sender, int page, List<BalTop.Row> rows) {
        if (rows.isEmpty()) {
            core.msg().info(sender, "Nobody owns any coins yet.");
            return;
        }
        int pages = (rows.size() + PAGE - 1) / PAGE;
        int p = Math.min(page, pages);
        core.msg().mini(sender, "<gold>Richest players</gold> <gray>(page " + p + "/" + pages + ")");
        for (int i = (p - 1) * PAGE; i < Math.min(rows.size(), p * PAGE); i++) {
            BalTop.Row r = rows.get(i);
            core.msg().mini(sender, "<yellow>#" + (i + 1) + "</yellow> <white>" + esc(r.name())
                + "</white> <dark_gray>-</dark_gray> <gold>" + Money.coins(r.total()) + "</gold>");
        }
        core.msg().info(sender, "Purse plus bank. Updated every few minutes.");
    }

    /** Pending confirmations are in memory only; forget a player's on quit. */
    void forget(UUID id) {
        pending.remove(id);
    }

}
