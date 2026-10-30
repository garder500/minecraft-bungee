package win.vampyuyu.survival.economy;

import org.bukkit.configuration.ConfigurationSection;

/** Core, pay, death and baltop options of economy.yml (other subsystems parse their own sections). */
public record EconomySettings(
    long maxBalanceCents, int ledgerSize, int bankHistorySize, boolean ledgerFile,
    boolean payEnabled, long payMinCents, long payMaxCents, long payConfirmCents, long payCooldownMillis,
    long payDailyCents, int payTaxBp,
    String deathCause, int deathPercent, long deathMaxCents, long deathMinBalanceCents, boolean deathToKiller,
    long baltopCacheMillis, int baltopSize, boolean saveInventoryOnEscrow) {

    public static EconomySettings load(ConfigurationSection c) {
        return new EconomySettings(
            cents(c, "currency.max-balance", 1_000_000_000_000.0, Money.HARD_CAP_CENTS),
            clamp(c.getInt("currency.ledger-size", 50), 5, 500),
            clamp(c.getInt("currency.bank-history-size", 40), 5, 500),
            c.getBoolean("currency.ledger-file", false),
            c.getBoolean("pay.enabled", true),
            cents(c, "pay.min", 1, Money.HARD_CAP_CENTS),
            cents(c, "pay.max", 100_000_000, Money.HARD_CAP_CENTS),
            cents(c, "pay.confirm-over", 50_000, Money.HARD_CAP_CENTS),
            Math.max(0, c.getLong("pay.cooldown-seconds", 5)) * 1000,
            cents(c, "pay.daily-limit", 250_000_000, Money.HARD_CAP_CENTS),
            clamp((int) Math.round(c.getDouble("pay.tax-percent", 0) * 100), 0, 5000),
            c.getString("death.cause", "pvp").toLowerCase(java.util.Locale.ROOT),
            clamp(c.getInt("death.percent", 50), 0, 100),
            cents(c, "death.max", 1_000_000, Money.HARD_CAP_CENTS),
            cents(c, "death.min-balance", 100, Money.HARD_CAP_CENTS),
            c.getBoolean("death.drop-to-killer", false),
            Math.max(10, c.getLong("baltop.cache-seconds", 300)) * 1000,
            clamp(c.getInt("baltop.size", 100), 10, 1000),
            c.getBoolean("safety.save-player-data", true));
    }

    static long cents(ConfigurationSection c, String path, double fallbackCoins, long cap) {
        double coins = c.getDouble(path, fallbackCoins);
        long cents = Money.toCents(coins).orElse(Math.round(fallbackCoins * 100));
        return Math.min(cap, cents);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
