package win.vampyuyu.survival.economy;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.TimeZone;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;

/** /ecoadmin (give, take, set, reset) and /economy (audit, ledger, reload). Permission vampyuyu.sb.economy.admin. */
final class EconomyAdmin {
    private static final String PERM = "vampyuyu.sb.economy.admin";

    private final Core core;
    private final EconomyService economy;
    private final EconomyAudit audit;
    private final Runnable reload;

    EconomyAdmin(Core core, EconomyService economy, EconomyAudit audit, Runnable reload) {
        this.core = core;
        this.economy = economy;
        this.audit = audit;
        this.reload = reload;
    }

    void register() {
        core.commands().command("ecoadmin")
            .description("Give, take, set or reset a player's coins")
            .usage("/ecoadmin <give|take|set|reset> <player> [amount]")
            .permission(PERM, PermissionDefault.OP)
            .executor((s, c, l, a) -> ecoadmin(s, a))
            .tab((s, c, l, a) -> switch (a.length) {
                case 1 -> Targets.filter(List.of("give", "take", "set", "reset"), a[0]);
                case 2 -> Targets.onlineNames(a[1]);
                case 3 -> Targets.filter(List.of("1000", "100k", "1m"), a[2]);
                default -> List.of();
            }).register();
        core.commands().command("economy")
            .description("Economy diagnostics")
            .usage("/economy <audit|ledger <player> [count]|reload>")
            .permission(PERM, PermissionDefault.OP)
            .executor((s, c, l, a) -> economyCmd(s, a))
            .tab((s, c, l, a) -> switch (a.length) {
                case 1 -> Targets.filter(List.of("audit", "ledger", "reload"), a[0]);
                case 2 -> a[0].equalsIgnoreCase("ledger") ? Targets.onlineNames(a[1]) : List.of();
                default -> List.of();
            }).register();
    }

    private boolean ecoadmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        boolean needsAmount = !action.equals("reset");
        if (!List.of("give", "take", "set", "reset").contains(action) || (needsAmount && args.length != 3)
                || (!needsAmount && args.length != 2)) {
            return false;
        }
        var target = Targets.resolve(core, args[1]);
        if (target.isEmpty()) {
            core.msg().error(sender, "Unknown player: " + args[1] + " (they must have joined the Skyblock before).");
            return true;
        }
        long cents = 0;
        if (needsAmount) {
            OptionalLong parsed = action.equals("set") && args[2].equals("0") ? OptionalLong.of(0) : Money.parse(args[2]);
            if (parsed.isEmpty()) {
                core.msg().error(sender, "'" + args[2] + "' is not a valid amount.");
                return true;
            }
            cents = parsed.getAsLong();
        }
        Targets.Target t = target.get();
        long amount = cents;
        String by = sender.getName();
        Runnable job = () -> {
            String text;
            switch (action) {
                case "give" -> {
                    long got = economy.addCents(t.id(), amount, "admin:give:" + by);
                    text = "Gave " + Money.coins(got) + " to " + t.name() + ".";
                }
                case "take" -> {
                    long have = economy.balanceCents(t.id());
                    long take = Math.min(have, amount);
                    boolean ok = take > 0 && economy.takeCents(t.id(), take, "admin:take:" + by);
                    text = ok ? "Took " + Money.coins(take) + " from " + t.name() + "." : t.name() + " has no coins.";
                }
                case "set" -> text = t.name() + " now has "
                    + Money.coins(economy.setCents(t.id(), amount, "admin:set:" + by)) + ".";
                default -> {
                    economy.reset(t.id());
                    text = "Reset the purse, bank and bank tier of " + t.name() + ".";
                }
            }
            String message = text;
            core.scheduler().sync(() -> core.msg().ok(sender, message));
        };
        if (t.online() != null) {
            job.run();
        } else {
            core.scheduler().async(job);
        }
        return true;
    }

    private boolean economyCmd(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reload.run();
                core.msg().ok(sender, "economy.yml reloaded (shops, prices, bank, bazaar and auction settings).");
            }
            case "audit" -> {
                core.msg().info(sender, "Auditing every profile and market, this can take a moment...");
                audit.run(report -> sendReport(sender, report));
            }
            case "ledger" -> {
                if (args.length < 2) {
                    return false;
                }
                var target = Targets.resolve(core, args[1]);
                if (target.isEmpty()) {
                    core.msg().error(sender, "Unknown player: " + args[1]);
                    return true;
                }
                int count = 10;
                if (args.length > 2) {
                    try {
                        count = Math.max(1, Math.min(50, Integer.parseInt(args[2])));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                int n = count;
                Targets.Target t = target.get();
                core.scheduler().async(() -> {
                    List<EconomyData.Entry> entries = List.copyOf(economy.data(t.id()).ledger);
                    core.scheduler().sync(() -> sendLedger(sender, t.name(), entries, n));
                });
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void sendLedger(CommandSender sender, String name, List<EconomyData.Entry> entries, int count) {
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        core.msg().info(sender, "Last " + Math.min(count, entries.size()) + " purse changes of " + name + " (UTC):");
        for (int i = Math.max(0, entries.size() - count); i < entries.size(); i++) {
            EconomyData.Entry e = entries.get(i);
            core.msg().info(sender, fmt.format(new java.util.Date(e.t)) + "  " + (e.delta >= 0 ? "+" : "")
                + Money.plain(e.delta) + "  = " + Money.plain(e.after) + "  " + e.reason);
        }
    }

    private void sendReport(CommandSender sender, EconomyAudit.Report r) {
        core.msg().info(sender, "Economy audit: " + r.players + " profiles");
        core.msg().info(sender, "  Purses: " + Money.coins(r.purseCents));
        core.msg().info(sender, "  Banks: " + Money.coins(r.bankCents));
        r.extras.forEach((k, v) -> core.msg().info(sender, "  " + k + ": " + Money.coins(v)));
        core.msg().info(sender, "  Total in the economy: " + Money.coins(r.totalCents()));
        if (r.problems.isEmpty()) {
            core.msg().ok(sender, "No inconsistency found.");
        } else {
            core.msg().error(sender, r.problems.size() + " problem(s):");
            r.problems.forEach(p -> core.msg().warn(sender, "  " + p));
        }
    }
}
