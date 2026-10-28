package win.vampyuyu.survival.skills;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Msg;

/** MiniMessage fragments shared by the chat messages and the menus. All text is English. */
final class RewardText {
    private RewardText() {}

    private static String decimal(double value) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    /** "+0.2% Crit Chance ☠" coloured with the stat's colour. */
    static String statLine(Stat stat, double amount) {
        String sign = amount >= 0 ? "+" : "-";
        String value = decimal(Math.abs(amount)) + (stat.percent() ? "%" : "");
        return "<" + stat.color().asHexString() + ">" + sign + value + " " + stat.displayName() + " " + stat.symbol();
    }

    /** Stat bonuses, coins and unlock text for exactly one level. */
    static List<String> levelLines(SkillsConfig cfg, Skill skill, int level) {
        List<String> lines = new ArrayList<>();
        SkillsConfig.SkillDef def = cfg.skill(skill);
        for (SkillsConfig.StatReward reward : def.stats()) {
            double amount = reward.at(level);
            if (amount != 0) {
                lines.add(statLine(reward.stat(), amount));
            }
        }
        double coins = cfg.levelCoins.at(level);
        if (coins > 0) {
            lines.add("<gold>+" + Msg.coins(coins));
        }
        String unlock = def.unlocks().get(level);
        if (unlock != null && !unlock.isBlank()) {
            lines.add("<yellow>" + unlock);
        }
        return lines;
    }

    /** A progress bar of {@code width} squares, green for the filled part. */
    static String bar(double fraction, int width) {
        double clamped = Math.max(0, Math.min(1, Double.isNaN(fraction) ? 0 : fraction));
        int filled = (int) Math.round(clamped * width);
        return "<green>" + "■".repeat(filled) + "<gray>" + "■".repeat(width - filled);
    }

    static String percent(double fraction) {
        double clamped = Math.max(0, Math.min(1, Double.isNaN(fraction) ? 0 : fraction));
        return decimal(Math.floor(clamped * 1000) / 10) + "%";
    }

    static String roman(int number) {
        if (number <= 0 || number > 3999) {
            return Integer.toString(number);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int rest = number;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                out.append(symbols[i]);
                rest -= values[i];
            }
        }
        return out.toString();
    }
}
