package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.event.CoinsChangeEvent;
import win.vampyuyu.survival.core.Core;

/**
 * The coin economy: {@link EconomyApi} plus the cents level operations used by the rest of the feature.
 * Everything goes through {@code PlayerProfiles.edit}, so it is atomic and safe from any thread. Events and
 * transaction listeners are fired AFTER the profile lock is released (a listener may call back into the economy).
 * Never call the offline-capable methods from the main thread for a player that is not online unless you accept a
 * blocking disk read; the market and auction code therefore pays offline players through claims.
 */
public final class EconomyService implements EconomyApi {
    public static final String SECTION = "economy";

    /** One applied change in cents, as recorded in the ledger. */
    public record Tx(UUID player, long delta, long after, String reason, long t) {}

    /** Outcome of {@link #debitForPay}. */
    public enum PayDebit { OK, COOLDOWN, DAILY_LIMIT, FUNDS }

    private final Core core;
    private volatile EconomySettings settings;
    private final List<Consumer<Transaction>> listeners = new CopyOnWriteArrayList<>();
    private final LedgerLog fileLog;
    private final Flows flows = new Flows();

    public EconomyService(Core core, EconomySettings settings) {
        this.core = core;
        this.settings = settings;
        this.fileLog = settings.ledgerFile() ? new LedgerLog(core.store().root(), core.getPlugin().getLogger()) : null;
    }

    public EconomySettings settings() {
        return settings;
    }

    /** The faucet and sink table. */
    Flows flows() {
        return flows;
    }

    void settings(EconomySettings next) {
        this.settings = next;
    }

    void close() {
        if (fileLog != null) {
            fileLog.close();
        }
    }

    // ---------------------------------------------------------------- generic access

    /**
     * Runs {@code change} on the player's data under the profile lock, then publishes the transactions it recorded.
     * The function must only touch the data and the tx list (no events, no Bukkit calls).
     */
    <R> R update(UUID player, BiFunction<EconomyData, List<Tx>, R> change) {
        List<Tx> txs = new ArrayList<>(2);
        Object[] result = new Object[1];
        core.profiles().edit(player, SECTION, EconomyData.class, EconomyData::new,
            data -> result[0] = change.apply(data, txs));
        publish(txs);
        @SuppressWarnings("unchecked")
        R value = (R) result[0];
        return value;
    }

    /** Live data (reads only). Blocks on disk for an offline player who is not cached. */
    EconomyData data(UUID player) {
        return core.profiles().section(player, SECTION, EconomyData.class, EconomyData::new);
    }

    /** Adds to the purse inside an {@link #update}; returns the cents actually added (the cap may clip it). */
    long applyCredit(EconomyData d, UUID player, long cents, String reason, List<Tx> txs) {
        if (cents <= 0) {
            return 0;
        }
        long next = Money.addCapped(d.cents, cents, settings.maxBalanceCents());
        long delta = next - d.cents;
        if (delta <= 0) {
            return 0;
        }
        d.cents = next;
        d.totalEarned += delta;
        record(d, player, delta, reason, txs);
        return delta;
    }

    /** Removes from the purse inside an {@link #update}; false (nothing changed) when the purse is too small. */
    boolean applyDebit(EconomyData d, UUID player, long cents, String reason, List<Tx> txs) {
        if (cents <= 0 || d.cents < cents) {
            return false;
        }
        d.cents -= cents;
        d.totalSpent += cents;
        record(d, player, -cents, reason, txs);
        return true;
    }

    private void record(EconomyData d, UUID player, long delta, String reason, List<Tx> txs) {
        long now = System.currentTimeMillis();
        String why = reason == null || reason.isBlank() ? "unknown" : reason;
        if (why.length() > 64) {
            why = why.substring(0, 64);
        }
        d.appendLedger(new EconomyData.Entry(now, delta, d.cents, why), settings.ledgerSize());
        txs.add(new Tx(player, delta, d.cents, why, now));
    }

    private void publish(List<Tx> txs) {
        for (Tx tx : txs) {
            flows.record(tx.reason(), tx.delta());
            if (fileLog != null) {
                fileLog.append(tx);
            }
            Transaction api = new Transaction(tx.player(), Money.toCoins(tx.delta()), Money.toCoins(tx.after()),
                tx.reason(), tx.t());
            for (Consumer<Transaction> listener : listeners) {
                try {
                    listener.accept(api);
                } catch (RuntimeException e) {
                    core.getPlugin().getLogger().log(Level.WARNING, "Economy transaction listener failed", e);
                }
            }
            try {
                Bukkit.getPluginManager().callEvent(
                    new CoinsChangeEvent(tx.player(), Money.toCoins(tx.delta()), Money.toCoins(tx.after()), tx.reason()));
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "CoinsChangeEvent handler failed", e);
            }
        }
    }

    // ---------------------------------------------------------------- cents level API

    public long balanceCents(UUID player) {
        return data(player).cents;
    }

    public long bankCents(UUID player) {
        return data(player).bankCents;
    }

    public boolean takeCents(UUID player, long cents, String reason) {
        if (cents <= 0) {
            return false;
        }
        return update(player, (d, txs) -> applyDebit(d, player, cents, reason, txs));
    }

    /** Adds coins and returns the cents actually credited (less than asked only at the balance cap). */
    public long addCents(UUID player, long cents, String reason) {
        if (cents <= 0) {
            return 0;
        }
        return update(player, (d, txs) -> applyCredit(d, player, cents, reason, txs));
    }

    /** Sets the purse to an exact value (admin). Returns the new balance. */
    public long setCents(UUID player, long cents, String reason) {
        long target = Math.max(0, Math.min(settings.maxBalanceCents(), cents));
        return update(player, (d, txs) -> {
            long delta = target - d.cents;
            if (delta > 0) {
                applyCredit(d, player, delta, reason, txs);
            } else if (delta < 0) {
                applyDebit(d, player, -delta, reason, txs);
            }
            return d.cents;
        });
    }

    /** Admin reset: purse, bank, tier and interest clock back to nothing. */
    public void reset(UUID player) {
        update(player, (d, txs) -> {
            if (d.cents > 0) {
                applyDebit(d, player, d.cents, "admin:reset", txs);
            }
            if (d.bankCents > 0) {
                d.appendBank(new EconomyData.BankEntry(System.currentTimeMillis(), "admin reset", -d.bankCents, 0),
                    settings.bankHistorySize());
            }
            d.bankCents = 0;
            d.bankTier = 0;
            d.bankInterestAt = 0;
            return null;
        });
    }

    /**
     * Checks and applies the /pay limits and takes the money in one atomic step. {@code now} is injected for tests.
     */
    PayDebit debitForPay(UUID player, long cents, long now, String reason) {
        EconomySettings s = settings;
        return update(player, (d, txs) -> {
            if (now - d.lastPayAt < s.payCooldownMillis()) {
                return PayDebit.COOLDOWN;
            }
            long day = Math.floorDiv(now, 86_400_000L);
            long sent = d.paySentDay == day ? d.paySentToday : 0;
            if (sent + cents > s.payDailyCents()) {
                return PayDebit.DAILY_LIMIT;
            }
            if (!applyDebit(d, player, cents, reason, txs)) {
                return PayDebit.FUNDS;
            }
            d.paySentDay = day;
            d.paySentToday = sent + cents;
            d.lastPayAt = now;
            return PayDebit.OK;
        });
    }

    /** Cents the player may still send today. */
    long payRemainingToday(UUID player, long now) {
        EconomyData d = data(player);
        long day = Math.floorDiv(now, 86_400_000L);
        long sent = d.paySentDay == day ? d.paySentToday : 0;
        return Math.max(0, settings.payDailyCents() - sent);
    }

    /** Destroys part of a purse on death. Returns the cents lost. */
    long loseOnDeath(UUID player, long cents, String reason) {
        return update(player, (d, txs) -> {
            long lose = Math.min(cents, d.cents);
            if (lose > 0 && applyDebit(d, player, lose, reason, txs)) {
                d.coinsLostToDeath += lose;
                return lose;
            }
            return 0L;
        });
    }

    // ---------------------------------------------------------------- EconomyApi

    @Override
    public double coins(UUID player) {
        return Money.toCoins(balanceCents(player));
    }

    @Override
    public boolean has(UUID player, double amount) {
        OptionalLong cents = Money.toCents(amount);
        return cents.isPresent() && balanceCents(player) >= cents.getAsLong();
    }

    @Override
    public boolean take(UUID player, double amount, String reason) {
        OptionalLong cents = Money.toCents(amount);
        return cents.isPresent() && cents.getAsLong() > 0 && takeCents(player, cents.getAsLong(), reason);
    }

    @Override
    public void add(UUID player, double amount, String reason) {
        OptionalLong cents = Money.toCents(amount);
        if (cents.isPresent() && cents.getAsLong() > 0) {
            addCents(player, cents.getAsLong(), reason);
        }
    }

    @Override
    public void addTransactionListener(Consumer<Transaction> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
}
