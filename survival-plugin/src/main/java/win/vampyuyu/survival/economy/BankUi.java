package win.vampyuyu.survival.economy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.Guis;

/** The /bank command and its menus. */
final class BankUi {
    private final Core core;
    private final EconomyService economy;
    private final Bank bank;

    BankUi(Core core, EconomyService economy, Bank bank) {
        this.core = core;
        this.economy = economy;
        this.bank = bank;
    }

    void register() {
        core.commands().command("bank").aliases("sbbank")
            .description("Your Skyblock bank")
            .usage("/bank [deposit|withdraw <amount|all|half>] [upgrade] [history]")
            .permission("vampyuyu.sb.economy.bank", PermissionDefault.TRUE)
            .executor((s, c, l, a) -> command(s, a))
            .tab((s, c, l, a) -> switch (a.length) {
                case 1 -> Targets.filter(List.of("deposit", "withdraw", "upgrade", "history", "info"), a[0]);
                case 2 -> a[0].equalsIgnoreCase("deposit") || a[0].equalsIgnoreCase("withdraw")
                    ? Targets.filter(List.of("all", "half", "1k", "10k", "100k", "1m"), a[1]) : List.of();
                default -> List.of();
            }).register();
    }

    private boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only players have a bank.");
            return true;
        }
        if (args.length == 0) {
            open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "deposit", "withdraw" -> {
                if (args.length != 2) {
                    return false;
                }
                boolean deposit = args[0].equalsIgnoreCase("deposit");
                EconomyData d = economy.data(player.getUniqueId());
                long available = deposit ? d.cents : d.bankCents;
                long amount;
                if (args[1].equalsIgnoreCase("all")) {
                    amount = available;
                } else if (args[1].equalsIgnoreCase("half")) {
                    amount = available / 2;
                } else {
                    OptionalLong parsed = Money.parse(args[1]);
                    if (parsed.isEmpty()) {
                        core.msg().error(sender, "'" + args[1] + "' is not a valid amount. Try 500, 1.5k, all or half.");
                        return true;
                    }
                    amount = parsed.getAsLong();
                }
                transfer(player, deposit, amount);
            }
            case "upgrade" -> upgrade(player);
            case "history" -> history(player);
            case "info" -> info(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void info(Player player) {
        Bank.Tier tier = bank.tierOf(economy.data(player.getUniqueId()));
        EconomyData d = economy.data(player.getUniqueId());
        core.msg().mini(player, "<gray>Bank tier <gold>" + tier.name() + "</gold>, holds <gold>"
            + Money.coins(tier.capacityCents()) + "</gold>, pays <gold>" + (tier.rateBp() / 100.0)
            + "%</gold> every <gold>" + win.vampyuyu.survival.core.Msg.duration(bank.periodMs())
            + "</gold> (up to <gold>" + Money.coins(tier.maxInterestCents()) + "</gold>). Balance <gold>"
            + Money.coins(d.bankCents) + "</gold>.");
    }

    private void announce(Player player, Bank.Move move, String done) {
        if (move.interest() > 0) {
            core.msg().mini(player, "<green>Your bank paid <gold>" + Money.coins(move.interest()) + "</gold> in interest.");
        }
        if (move.ok()) {
            core.msg().ok(player, done);
        } else {
            core.msg().error(player, move.error());
        }
    }

    private void transfer(Player player, boolean deposit, long amount) {
        Bank.Move move = deposit ? bank.deposit(player.getUniqueId(), amount) : bank.withdraw(player.getUniqueId(), amount);
        announce(player, move, (deposit ? "Deposited " : "Withdrew ") + (move.ok() ? Money.coins(move.moved()) : "") + ".");
    }

    private void upgrade(Player player) {
        Bank.Move move = bank.upgrade(player.getUniqueId());
        announce(player, move, "Bank upgraded for " + Money.coins(move.moved()) + ".");
    }

    // ---------------------------------------------------------------- menus

    void open(Player player) {
        long paid = bank.settle(player.getUniqueId());
        if (paid > 0) {
            core.msg().mini(player, "<green>Your bank paid <gold>" + Money.coins(paid) + "</gold> in interest.");
        }
        Gui gui = core.guis().create(4, "<gold>Bank");
        gui.fill(Ui.glass());
        render(gui, player);
        gui.open(player);
    }

    private void render(Gui gui, Player player) {
        EconomyData d = economy.data(player.getUniqueId());
        Bank.Tier tier = bank.tierOf(d);
        long now = System.currentTimeMillis();
        String next = d.bankCents <= 0 || d.bankInterestAt <= 0 ? "<gray>Deposit coins to start earning."
            : "<gray>Next interest in <yellow>"
                + win.vampyuyu.survival.core.Msg.duration(Math.max(0, d.bankInterestAt + bank.periodMs() - now));
        long projected = Math.min(Money.basisPoints(d.bankCents, tier.rateBp()), tier.maxInterestCents());
        gui.set(4, Ui.icon(Material.GOLD_BLOCK, "bank", "<gold>Bank <gray>(" + tier.name() + ")",
            "<gray>Purse: <gold>" + Money.coins(d.cents), "<gray>Bank: <gold>" + Money.coins(d.bankCents)
                + " <dark_gray>/ " + Money.compact(tier.capacityCents()),
            "", "<gray>Interest: <green>" + (tier.rateBp() / 100.0) + "%</green> every <yellow>"
                + win.vampyuyu.survival.core.Msg.duration(bank.periodMs()),
            "<gray>Max per period: <gold>" + Money.coins(tier.maxInterestCents()),
            "<gray>Next payout: <gold>~" + Money.coins(projected), next,
            "<gray>Earned so far: <gold>" + Money.coins(d.bankInterestTotal),
            "", "<dark_gray>Coins in the bank survive death and are safe from PvP."));

        gui.set(10, Ui.icon(Material.CHEST, "deposit", "<green>Deposit everything",
            "<gray>Moves your whole purse to the bank.", "", "<yellow>Click to deposit <gold>" + Money.coins(d.cents)),
            c -> act(gui, c, true, null));
        gui.set(11, Ui.icon(Material.CHEST_MINECART, "deposit", "<green>Deposit half",
            "<gray>Moves half of your purse.", "", "<yellow>Click to deposit <gold>" + Money.coins(d.cents / 2)),
            c -> act(gui, c, true, d.cents / 2));
        gui.set(12, Ui.plain(Material.OAK_SIGN, "<green>Deposit an amount", "<gray>Type it in chat:",
            "<yellow>/bank deposit [amount]", "<dark_gray>Examples: 500, 1.5k, 2m"));

        gui.set(14, Ui.icon(Material.HOPPER, "withdraw", "<red>Withdraw everything",
            "<gray>Moves your whole bank balance to the purse.", "",
            "<yellow>Click to withdraw <gold>" + Money.coins(d.bankCents)), c -> act(gui, c, false, null));
        gui.set(15, Ui.icon(Material.HOPPER_MINECART, "withdraw", "<red>Withdraw half",
            "<gray>Moves half of your bank balance.", "",
            "<yellow>Click to withdraw <gold>" + Money.coins(d.bankCents / 2)), c -> act(gui, c, false, d.bankCents / 2));
        gui.set(16, Ui.plain(Material.OAK_SIGN, "<red>Withdraw an amount", "<gray>Type it in chat:",
            "<yellow>/bank withdraw [amount]", "<dark_gray>Examples: 500, 1.5k, half"));

        if (d.bankTier < bank.tiers().size() - 1) {
            Bank.Tier nextTier = bank.tiers().get(d.bankTier + 1);
            gui.set(22, Ui.icon(Material.ENDER_CHEST, "upgrade", "<light_purple>Upgrade to " + nextTier.name(),
                "<gray>Capacity: <gold>" + Money.compact(nextTier.capacityCents()),
                "<gray>Interest: <green>" + (nextTier.rateBp() / 100.0) + "%</green>, up to <gold>"
                    + Money.compact(nextTier.maxInterestCents()), "",
                "<gray>Cost: <gold>" + Money.coins(nextTier.costCents()) + " <gray>from your purse",
                d.cents >= nextTier.costCents() ? "<yellow>Click to upgrade" : "<red>You cannot afford this yet"),
                c -> {
                    upgrade(c.player());
                    render(gui, c.player());
                });
        } else {
            gui.set(22, Ui.icon(Material.ENDER_CHEST, "upgrade", "<light_purple>Top tier reached",
                "<gray>Your bank cannot be upgraded further."));
        }
        gui.set(31, Ui.icon(Material.BOOK, "history", "<aqua>Transaction history",
            "<gray>Your recent bank movements.", "", "<yellow>Click to view"), c -> history(c.player()));
        gui.set(27, Ui.plain(Material.BARRIER, "<red>Close"), Gui.Click::close);
    }

    private void act(Gui gui, Gui.Click click, boolean deposit, Long amount) {
        Player player = click.player();
        EconomyData d = economy.data(player.getUniqueId());
        long cents = amount != null ? amount : (deposit ? d.cents : d.bankCents);
        transfer(player, deposit, cents);
        render(gui, player);
    }

    void history(Player player) {
        List<EconomyData.BankEntry> entries = new ArrayList<>(economy.data(player.getUniqueId()).bankHistory);
        Collections.reverse(entries);
        Gui gui = core.guis().create(6, "<aqua>Bank history");
        gui.border(Ui.glass());
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d HH:mm", Locale.ROOT);
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        gui.paginate(entries, gui.innerSlots(), e -> {
            boolean gain = e.delta > 0;
            return Ui.plain(gain ? Material.LIME_DYE : e.delta < 0 ? Material.RED_DYE : Material.GRAY_DYE,
                (gain ? "<green>+" : e.delta < 0 ? "<red>" : "<gray>") + Money.coins(e.delta) + " <gray>" + Ui.esc(e.kind),
                "<gray>Balance after: <gold>" + Money.coins(e.after), "<dark_gray>" + fmt.format(new Date(e.t)) + " UTC");
        }, (c, e) -> { }, 48, 50);
        gui.set(49, Ui.plain(Material.ARROW, "<yellow>Back to the bank"), c -> open(c.player()));
        gui.open(player);
    }

}
