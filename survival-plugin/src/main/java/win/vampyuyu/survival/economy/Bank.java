package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Core;

/**
 * Bank accounts: deposit, withdraw, upgrade tiers and real-time interest. Interest is computed lazily from the
 * stored clock ({@code bankInterestAt}) every time the account is touched, so offline players earn too, and it is
 * ALWAYS settled before a deposit or withdrawal so a late deposit never earns the periods that already passed.
 */
public final class Bank {
    /** A bank tier. {@code rateBp} is the interest per period in basis points. */
    public record Tier(String name, long capacityCents, int rateBp, long maxInterestCents, long costCents) {}

    /** Result of a pure interest computation. */
    public record Accrual(long bank, long clock, long interest, int periods) {}

    /** Result of a bank operation. {@code error} is null on success. */
    public record Move(long moved, long interest, String error) {
        boolean ok() { return error == null; }
    }

    private final Core core;
    private final EconomyService economy;
    private volatile List<Tier> tiers = List.of();
    private volatile long periodMs = 24 * 3_600_000L;
    private volatile int maxCatchup = 3;

    public Bank(Core core, EconomyService economy, ConfigurationSection config) {
        this.core = core;
        this.economy = economy;
        reload(config);
    }

    void reload(ConfigurationSection c) {
        periodMs = Math.max(60_000L, (long) (c.getDouble("bank.interest-period-hours", 24.0) * 3_600_000L));
        maxCatchup = EconomySettings.clamp(c.getInt("bank.max-catchup-periods", 3), 1, 1000);
        List<Tier> out = new ArrayList<>();
        for (java.util.Map<?, ?> raw : c.getMapList("bank.tiers")) {
            Object name = raw.get("name");
            long capacity = coins(raw.get("capacity"), 5_000_000);
            double rate = num(raw.get("rate"), 0.5);
            long maxInterest = coins(raw.get("max-interest"), 100_000);
            long cost = coins(raw.get("cost"), 0);
            out.add(new Tier(name == null ? "Tier " + (out.size() + 1) : name.toString(), capacity,
                (int) Math.round(Math.max(0, Math.min(100, rate)) * 100), maxInterest, cost));
        }
        if (out.isEmpty()) {
            out.add(new Tier("Starter", 500_000_000L, 50, 2_500_000L, 0));
        }
        tiers = List.copyOf(out);
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    private static long coins(Object o, double fallback) {
        return Money.toCents(num(o, fallback)).orElse(Math.round(fallback * 100));
    }

    public List<Tier> tiers() { return tiers; }

    public long periodMs() { return periodMs; }

    public Tier tierOf(EconomyData d) {
        List<Tier> t = tiers;
        return t.get(Math.max(0, Math.min(t.size() - 1, d.bankTier)));
    }

    // ---------------------------------------------------------------- pure interest

    /**
     * Interest due for the periods elapsed between {@code clock} and {@code now}. Each period pays
     * {@code min(bank * rate, maxInterest)} limited by the room left under {@code capacity} and compounds into the
     * next one. More than {@code maxPeriods} elapsed periods are forfeited (the clock jumps to {@code now}).
     */
    public static Accrual accrue(long bank, long clock, long now, long periodMs, int maxPeriods, int rateBp,
            long maxInterest, long capacity) {
        if (bank <= 0) {
            return new Accrual(bank, 0, 0, 0);
        }
        if (clock <= 0) {
            return new Accrual(bank, now, 0, 0);
        }
        if (now < clock) {
            return new Accrual(bank, now, 0, 0); // clock went backwards: restart it, never pay
        }
        long elapsed = (now - clock) / periodMs;
        if (elapsed <= 0) {
            return new Accrual(bank, clock, 0, 0);
        }
        int n = (int) Math.min(elapsed, maxPeriods);
        long newClock = elapsed > maxPeriods ? now : clock + elapsed * periodMs;
        long total = 0;
        long balance = bank;
        for (int i = 0; i < n; i++) {
            long interest = Math.min(Money.basisPoints(balance, rateBp), maxInterest);
            interest = Math.min(interest, Math.max(0, capacity - balance));
            if (interest <= 0) {
                break;
            }
            balance += interest;
            total += interest;
        }
        return new Accrual(balance, newClock, total, n);
    }

    /** Settles interest inside an {@link EconomyService#update}; returns the interest paid. */
    long settle(EconomyData d, long now) {
        Tier tier = tierOf(d);
        Accrual a = accrue(d.bankCents, d.bankInterestAt, now, periodMs, maxCatchup, tier.rateBp(),
            tier.maxInterestCents(), tier.capacityCents());
        d.bankInterestAt = a.clock();
        if (a.interest() > 0) {
            d.bankCents = a.bank();
            d.bankInterestTotal += a.interest();
            economy.flows().record("bank:interest", a.interest());
            d.appendBank(new EconomyData.BankEntry(now, "Interest", a.interest(), d.bankCents),
                economy.settings().bankHistorySize());
        }
        return a.interest();
    }

    // ---------------------------------------------------------------- operations

    /** Pays any due interest now (for the GUI, join and the periodic task). */
    public long settle(UUID player) {
        return economy.update(player, (d, txs) -> settle(d, System.currentTimeMillis()));
    }

    /** True when at least one period is due (cheap read, no change). */
    boolean due(UUID player, long now) {
        EconomyData d = economy.data(player);
        return d.bankCents > 0 && d.bankInterestAt > 0 && now - d.bankInterestAt >= periodMs;
    }

    public Move deposit(UUID player, long cents) {
        if (cents <= 0) {
            return new Move(0, 0, "Enter an amount above zero.");
        }
        return economy.update(player, (d, txs) -> {
            long now = System.currentTimeMillis();
            long interest = settle(d, now);
            Tier tier = tierOf(d);
            long room = Math.max(0, tier.capacityCents() - d.bankCents);
            if (room <= 0) {
                return new Move(0, interest, "Your bank is full (" + tier.name() + " holds "
                    + Money.coins(tier.capacityCents()) + "). Upgrade it for more room.");
            }
            long amount = Math.min(cents, room);
            if (d.cents < amount) {
                return new Move(0, interest, "You only have " + Money.coins(d.cents) + " in your purse.");
            }
            if (!economy.applyDebit(d, player, amount, "bank:deposit", txs)) {
                return new Move(0, interest, "You do not have enough coins.");
            }
            if (d.bankCents == 0 || d.bankInterestAt <= 0) {
                d.bankInterestAt = now;
            }
            d.bankCents += amount;
            d.appendBank(new EconomyData.BankEntry(now, "Deposit", amount, d.bankCents),
                economy.settings().bankHistorySize());
            return new Move(amount, interest, null);
        });
    }

    public Move withdraw(UUID player, long cents) {
        if (cents <= 0) {
            return new Move(0, 0, "Enter an amount above zero.");
        }
        return economy.update(player, (d, txs) -> {
            long now = System.currentTimeMillis();
            long interest = settle(d, now);
            long amount = Math.min(cents, d.bankCents);
            if (amount <= 0) {
                return new Move(0, interest, "Your bank is empty.");
            }
            long room = Math.max(0, economy.settings().maxBalanceCents() - d.cents);
            amount = Math.min(amount, room);
            if (amount <= 0) {
                return new Move(0, interest, "Your purse cannot hold more coins.");
            }
            d.bankCents -= amount;
            if (d.bankCents == 0) {
                d.bankInterestAt = 0;
            }
            economy.applyCredit(d, player, amount, "bank:withdraw", txs);
            d.appendBank(new EconomyData.BankEntry(now, "Withdrawal", -amount, d.bankCents),
                economy.settings().bankHistorySize());
            return new Move(amount, interest, null);
        });
    }

    public Move upgrade(UUID player) {
        return economy.update(player, (d, txs) -> {
            long now = System.currentTimeMillis();
            long interest = settle(d, now);
            if (d.bankTier >= tiers.size() - 1) {
                return new Move(0, interest, "Your bank is already at the highest tier.");
            }
            Tier next = tiers.get(d.bankTier + 1);
            if (!economy.applyDebit(d, player, next.costCents(), "bank:upgrade", txs) && next.costCents() > 0) {
                return new Move(0, interest, "You need " + Money.coins(next.costCents()) + " in your purse to buy "
                    + next.name() + ".");
            }
            d.bankTier++;
            d.appendBank(new EconomyData.BankEntry(now, "Upgrade: " + next.name(), 0, d.bankCents),
                economy.settings().bankHistorySize());
            return new Move(next.costCents(), interest, null);
        });
    }

    /** Pays interest to every online player whose period is due. Called once a minute on the main thread. */
    void tick() {
        long now = System.currentTimeMillis();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            try {
                if (due(player.getUniqueId(), now)) {
                    long paid = settle(player.getUniqueId());
                    if (paid > 0) {
                        core.msg().mini(player, "<green>Your bank paid <gold>" + Money.coins(paid)
                            + "</gold> in interest.");
                    }
                }
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().warning("Bank interest failed for " + player.getName() + ": " + e);
            }
        }
    }
}
