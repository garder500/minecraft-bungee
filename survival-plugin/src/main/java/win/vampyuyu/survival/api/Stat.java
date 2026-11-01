package win.vampyuyu.survival.api;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Every combat and utility statistic of the Skyblock (the house stats). A stat value is the sum of its base value
 * ({@link #base()}) and the modifiers contributed through {@link StatsApi#addModifier}.
 *
 * <p>Names stored by older versions (Health, Strength, Crit Damage...) are still understood: {@link #resolve} maps
 * an old or a current name to the current stats, so data written before the rework (skills, wings, fairy souls, item
 * bonuses) keeps working.
 */
public enum Stat {
    /** Hit points of the player (was Health). */
    VITALITY("Vitality", NamedTextColor.RED, "❤", 100, false),
    /** Damage taken is multiplied by {@code 100 / (100 + Ward)} (was Defense and True Defense). */
    WARD("Ward", NamedTextColor.GREEN, "⛨", 0, false),
    /** Percent bonus on weapon damage (was Strength; Crit Damage, Ferocity and Ability Damage convert into it). */
    MIGHT("Might", NamedTextColor.RED, "⚒", 0, false),
    /** Percent added to the elemental multiplier when the target is weak to the weapon's element. */
    ATTUNEMENT("Attunement", NamedTextColor.LIGHT_PURPLE, "✧", 0, true),
    /** Ability energy (was Intelligence). */
    AETHER("Aether", NamedTextColor.AQUA, "✦", 100, false),
    /** Movement speed (was Speed). */
    SWIFTNESS("Swiftness", NamedTextColor.WHITE, "➶", 100, false),
    MINING_YIELD("Mining Yield", NamedTextColor.GOLD, "⛏", 0, false),
    FARMING_YIELD("Farming Yield", NamedTextColor.GOLD, "✿", 0, false),
    FORAGING_YIELD("Foraging Yield", NamedTextColor.GOLD, "♣", 0, false),
    /** Breaking speed (was Mining Speed). */
    HASTE("Haste", NamedTextColor.GOLD, "⸕", 0, false),
    /** Fishing speed (was Fishing Speed). */
    LURE("Lure", NamedTextColor.AQUA, "☂", 0, false),
    /** Chance of a sea creature (was Sea Creature Chance). */
    DEEP_LURE("Deep Lure", NamedTextColor.DARK_AQUA, "α", 20, true),
    /** Rare loot luck (was Magic Find and Pet Luck). */
    FORTUNE("Fortune", NamedTextColor.YELLOW, "☘", 0, false),
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

    /** Human readable English name, e.g. "Deep Lure". */
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

    /** Lower case key used in PDC and data files, e.g. "deep_lure". */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** A current stat and how much of an old stat's value it receives. */
    public record Converted(Stat stat, double factor) {}

    private static final java.util.Map<String, java.util.List<Converted>> LEGACY = legacy();

    private static java.util.Map<String, java.util.List<Converted>> legacy() {
        java.util.Map<String, java.util.List<Converted>> m = new java.util.HashMap<>();
        m.put("health", java.util.List.of(new Converted(VITALITY, 1)));
        m.put("defense", java.util.List.of(new Converted(WARD, 1)));
        m.put("true_defense", java.util.List.of(new Converted(WARD, 1)));
        m.put("strength", java.util.List.of(new Converted(MIGHT, 1)));
        m.put("crit_damage", java.util.List.of(new Converted(MIGHT, 0.25)));
        m.put("ferocity", java.util.List.of(new Converted(MIGHT, 0.5)));
        m.put("ability_damage", java.util.List.of(new Converted(MIGHT, 1)));
        m.put("crit_chance", java.util.List.of());
        m.put("attack_speed", java.util.List.of());
        m.put("intelligence", java.util.List.of(new Converted(AETHER, 1)));
        m.put("speed", java.util.List.of(new Converted(SWIFTNESS, 1)));
        m.put("mining_speed", java.util.List.of(new Converted(HASTE, 1)));
        m.put("mining_fortune", java.util.List.of(new Converted(MINING_YIELD, 1)));
        m.put("farming_fortune", java.util.List.of(new Converted(FARMING_YIELD, 1)));
        m.put("foraging_fortune", java.util.List.of(new Converted(FORAGING_YIELD, 1)));
        m.put("fishing_speed", java.util.List.of(new Converted(LURE, 1)));
        m.put("sea_creature_chance", java.util.List.of(new Converted(DEEP_LURE, 1)));
        m.put("magic_find", java.util.List.of(new Converted(FORTUNE, 1)));
        m.put("pet_luck", java.util.List.of(new Converted(FORTUNE, 1)));
        return java.util.Map.copyOf(m);
    }

    /** The stat with this exact current key ({@code "ward"}, any case, spaces or dashes allowed), or null. */
    public static Stat parse(String name) {
        String key = normalize(name);
        for (Stat stat : values()) {
            if (stat.key().equals(key)) {
                return stat;
            }
        }
        return null;
    }

    /**
     * Maps a stat name, current or from before the rework, to the current stats it contributes to. A current name
     * maps to itself (factor 1); {@code crit_damage} gives a quarter of its value as Might; the dropped stats
     * ({@code crit_chance}, {@code attack_speed}) and unknown names give an empty list.
     */
    public static java.util.List<Converted> resolve(String name) {
        Stat current = parse(name);
        if (current != null) {
            return java.util.List.of(new Converted(current, 1));
        }
        return LEGACY.getOrDefault(normalize(name), java.util.List.of());
    }

    /** True when {@code name} is a current or a legacy stat name (even a dropped one). */
    public static boolean known(String name) {
        return parse(name) != null || LEGACY.containsKey(normalize(name));
    }

    /** Every stat name used before the rework (for example {@code strength}), lower case. */
    public static java.util.Set<String> legacyKeys() {
        return LEGACY.keySet();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
