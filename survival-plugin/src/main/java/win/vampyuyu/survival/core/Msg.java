package win.vampyuyu.survival.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * English chat messages with the Skyblock prefix, plus pure formatters.
 * {@code info/ok/warn/error} take PLAIN text (never parsed, so player names cannot inject formatting);
 * use {@link #mini} for MiniMessage markup you wrote yourself.
 */
public final class Msg {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Component PREFIX = MINI.deserialize(
        "<gradient:#7b2ff7:#00d4ff><bold>Skyblock</bold></gradient> <dark_gray>»</dark_gray> ");

    /** The prefix component ("Skyblock » "). */
    public Component prefix() {
        return PREFIX;
    }

    /** Parses MiniMessage and removes the default italic. */
    public static Component component(String miniMessage) {
        return MINI.deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Plain text, no italic, given colour. */
    public static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private void send(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(PREFIX.append(Component.text(text, color)));
    }

    /** Neutral information (gray). */
    public void info(CommandSender to, String text) {
        send(to, text, NamedTextColor.GRAY);
    }

    /** Success (green). */
    public void ok(CommandSender to, String text) {
        send(to, text, NamedTextColor.GREEN);
    }

    /** Something to be careful about (yellow). */
    public void warn(CommandSender to, String text) {
        send(to, text, NamedTextColor.YELLOW);
    }

    /** Failure or refusal (red). */
    public void error(CommandSender to, String text) {
        send(to, text, NamedTextColor.RED);
    }

    /** Prefix + MiniMessage markup written by the caller. */
    public void mini(CommandSender to, String miniMessage) {
        to.sendMessage(PREFIX.append(MINI.deserialize(miniMessage)));
    }

    // ---------------------------------------------------------------- formatters

    private static final String[] SUFFIX = {"", "k", "M", "B", "T", "Q"};

    /**
     * Compact number: 999 -> "999", 1234 -> "1.2k", 3_400_000 -> "3.4M", 1e9 -> "1B". At most one decimal,
     * truncated (never rounded up), trailing ".0" removed. NaN and infinities give "0".
     */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        double abs = Math.abs(value);
        int tier = 0;
        while (abs >= 1000 && tier < SUFFIX.length - 1) {
            abs /= 1000;
            tier++;
        }
        BigDecimal cut = BigDecimal.valueOf(abs).setScale(1, RoundingMode.DOWN);
        if (cut.compareTo(BigDecimal.valueOf(1000)) >= 0 && tier < SUFFIX.length - 1) {
            cut = BigDecimal.ONE.setScale(1);
            tier++;
        }
        String text = cut.stripTrailingZeros().toPlainString();
        if (cut.signum() == 0) {
            return "0";
        }
        return (value < 0 ? "-" : "") + text + SUFFIX[tier];
    }

    /** Exact amount with thousands separators, fractions dropped: 1234567.8 -> "1,234,567". */
    public static String grouped(double value) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.DOWN);
        return format.format(value);
    }

    /** Exact coin amount: "1,234,567 coins" ("1 coin" for exactly one). */
    public static String coins(double value) {
        return grouped(value) + (Math.floor(Math.abs(value)) == 1 ? " coin" : " coins");
    }

    /** Compact coin amount: "1.2M coins". */
    public static String coinsShort(double value) {
        return number(value) + " coins";
    }

    /**
     * Duration for humans: "2d 3h", "1h 5m", "45s". Shows the two largest consecutive units (3605s gives "1h"), rounds sub-second
     * remainders up to a whole second, and never returns an empty string ("0s").
     */
    public static String duration(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long total = (millis + 999) / 1000;
        long days = total / 86400;
        long hours = total % 86400 / 3600;
        long minutes = total % 3600 / 60;
        long seconds = total % 60;
        long[] values = {days, hours, minutes, seconds};
        String[] units = {"d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < values.length && shown < 2; i++) {
            if (values[i] > 0 || shown > 0) {
                if (values[i] > 0) {
                    if (out.length() > 0) {
                        out.append(' ');
                    }
                    out.append(values[i]).append(units[i]);
                }
                shown++;
            }
        }
        return out.toString();
    }
}
