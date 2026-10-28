package win.vampyuyu.survival.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Coin arithmetic. Balances are kept as {@code long} cents (1 coin = 100 cents) so additions are exact, overflow is
 * checked and the ledger chain can be audited without floating point drift. Doubles only exist at the
 * {@link win.vampyuyu.survival.api.EconomyApi} boundary.
 */
public final class Money {
    /** Hard ceiling of any single balance: 10 trillion coins. */
    public static final long HARD_CAP_CENTS = 1_000_000_000_000_000L;

    private Money() {}

    /**
     * Converts coins to cents, rounding half up.
     *
     * @return empty for NaN, infinite, negative or absurdly large values
     */
    public static OptionalLong toCents(double coins) {
        if (Double.isNaN(coins) || Double.isInfinite(coins) || coins < 0 || coins > HARD_CAP_CENTS / 100.0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(coins * 100.0));
    }

    public static double toCoins(long cents) {
        return cents / 100.0;
    }

    /** {@code a + b} clamped to {@code [0, cap]}; never overflows. */
    public static long addCapped(long a, long b, long cap) {
        long sum;
        try {
            sum = Math.addExact(a, b);
        } catch (ArithmeticException e) {
            sum = b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return Math.max(0, Math.min(cap, sum));
    }

    /** {@code a * b} or empty on overflow. */
    public static OptionalLong mul(long a, long b) {
        try {
            return OptionalLong.of(Math.multiplyExact(a, b));
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * Parses a player typed amount: {@code 1500}, {@code 1,500}, {@code 1.5k}, {@code 2m}, {@code 3b}, {@code 1t}.
     * At most two decimals survive. Empty when the text is not a positive finite amount.
     */
    public static OptionalLong parse(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        String s = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        if (s.isEmpty()) {
            return OptionalLong.empty();
        }
        BigDecimal multiplier = BigDecimal.ONE;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> multiplier = BigDecimal.valueOf(1_000L);
            case 'm' -> multiplier = BigDecimal.valueOf(1_000_000L);
            case 'b' -> multiplier = BigDecimal.valueOf(1_000_000_000L);
            case 't' -> multiplier = BigDecimal.valueOf(1_000_000_000_000L);
            default -> { }
        }
        if (!multiplier.equals(BigDecimal.ONE)) {
            s = s.substring(0, s.length() - 1);
        }
        if (!s.matches("\\d{1,15}(\\.\\d{1,6})?")) {
            return OptionalLong.empty();
        }
        BigDecimal cents = new BigDecimal(s).multiply(multiplier).multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.DOWN);
        if (cents.signum() <= 0 || cents.compareTo(BigDecimal.valueOf(HARD_CAP_CENTS)) > 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(cents.longValueExact());
    }

    private static DecimalFormat format(String pattern) {
        DecimalFormat f = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
        f.setRoundingMode(RoundingMode.DOWN);
        return f;
    }

    /** "1,234,567.5" - exact, up to two decimals, no unit. */
    public static String plain(long cents) {
        return format("#,##0.##").format(cents / 100.0);
    }

    /** "1,234,567.5 coins". */
    public static String coins(long cents) {
        return plain(cents) + (cents == 100 ? " coin" : " coins");
    }

    /** Compact "2.5k" (truncated). */
    public static String compact(long cents) {
        return win.vampyuyu.survival.core.Msg.number(cents / 100.0);
    }

    /** Percentage of an amount, floored: {@code cents * basisPoints / 10000}, overflow safe. */
    public static long basisPoints(long cents, int basisPoints) {
        if (basisPoints <= 0 || cents <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(basisPoints))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.DOWN).longValueExact();
    }
}
