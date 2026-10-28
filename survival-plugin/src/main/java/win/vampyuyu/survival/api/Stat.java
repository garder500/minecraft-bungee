package win.vampyuyu.survival.api;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Every combat and utility statistic of the Skyblock. A stat value is the sum of its base value
 * ({@link #base()}) and the modifiers contributed through {@link StatsApi#addModifier}.
 */
public enum Stat {
    HEALTH("Health", NamedTextColor.RED, "❤", 100, false),
    DEFENSE("Defense", NamedTextColor.GREEN, "❈", 0, false),
    TRUE_DEFENSE("True Defense", NamedTextColor.WHITE, "❂", 0, false),
    STRENGTH("Strength", NamedTextColor.RED, "❁", 0, false),
    CRIT_CHANCE("Crit Chance", NamedTextColor.BLUE, "☠", 30, true),
    CRIT_DAMAGE("Crit Damage", NamedTextColor.BLUE, "☠", 50, true),
    ATTACK_SPEED("Bonus Attack Speed", NamedTextColor.YELLOW, "⚔", 0, true),
    FEROCITY("Ferocity", NamedTextColor.RED, "⫽", 0, false),
    ABILITY_DAMAGE("Ability Damage", NamedTextColor.RED, "๑", 0, true),
    INTELLIGENCE("Intelligence", NamedTextColor.AQUA, "✎", 100, false),
    SPEED("Speed", NamedTextColor.WHITE, "✦", 100, false),
    MINING_SPEED("Mining Speed", NamedTextColor.GOLD, "⸕", 0, false),
    MINING_FORTUNE("Mining Fortune", NamedTextColor.GOLD, "☘", 0, false),
    FARMING_FORTUNE("Farming Fortune", NamedTextColor.GOLD, "☘", 0, false),
    FORAGING_FORTUNE("Foraging Fortune", NamedTextColor.GOLD, "☘", 0, false),
    FISHING_SPEED("Fishing Speed", NamedTextColor.AQUA, "☂", 0, false),
    SEA_CREATURE_CHANCE("Sea Creature Chance", NamedTextColor.DARK_AQUA, "α", 20, true),
    MAGIC_FIND("Magic Find", NamedTextColor.AQUA, "✦", 0, false),
    PET_LUCK("Pet Luck", NamedTextColor.LIGHT_PURPLE, "♣", 0, false),
    /** Fuel of the Wings: seconds of flight are derived from it by the wings feature. */
    FLIGHT_ENERGY("Flight Energy", NamedTextColor.LIGHT_PURPLE, "❄", 100, false);

    private final String displayName;
    private final TextColor color;
    private final String symbol;
    private final double base;
    private final boolean percent;

    Stat(String displayName, TextColor color, String symbol, double base, boolean percent) {
        this.displayName = displayName;
        this.color = color;
        this.symbol = symbol;
        this.base = base;
        this.percent = percent;
    }

    /** Human readable English name, e.g. "Crit Chance". */
    public String displayName() {
        return displayName;
    }

    /** Colour used for the stat everywhere (lore, menus, HUD). */
    public TextColor color() {
        return color;
    }

    /** One character icon drawn next to the value. */
    public String symbol() {
        return symbol;
    }

    /** Value every player has before any modifier. */
    public double base() {
        return base;
    }

    /** True when the value is a percentage and is displayed with a trailing "%". */
    public boolean percent() {
        return percent;
    }

    /** Lower case key used in PDC and data files, e.g. "crit_chance". */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
