package win.vampyuyu.survival.api;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Skyblock coins. Separate from the network currency (see docs/skyblock-design.md, section 11).
 * Implemented by the {@code economy} feature; balances live in the profile section {@code "economy"}.
 * Every method is safe to call from any thread.
 */
public interface EconomyApi {
    /** One completed balance change. */
    record Transaction(UUID player, double delta, double balanceAfter, String reason, long timeMillis) {}

    /** Current balance, 0 for an unknown player. */
    double coins(UUID player);

    /** True when the balance is at least {@code amount}. */
    boolean has(UUID player, double amount);

    /**
     * Removes coins atomically.
     *
     * @param reason short machine friendly cause, e.g. "shop:buy" (shown in logs)
     * @return false (and nothing changes) when the player cannot afford {@code amount} or it is not positive
     */
    boolean take(UUID player, double amount, String reason);

    /** Adds coins. {@code amount} must be positive; non-positive values are ignored. */
    void add(UUID player, double amount, String reason);

    /** Admin reset: purse, bank and bank tier back to nothing. The default does nothing, for fakes that hold no money. */
    default void reset(UUID player) {}

    /**
     * Registers a listener called after each successful change, on the thread that made the change
     * (which may be async). A {@code CoinsChangeEvent} is fired as well.
     */
    void addTransactionListener(Consumer<Transaction> listener);
}
