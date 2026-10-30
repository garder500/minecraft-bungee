package win.vampyuyu.survival.items.enchant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.items.gear.ItemKind;

/** Pure rules for storing and combining enchants (no Bukkit state). */
public final class EnchantRules {
    private EnchantRules() {}

    /** Why an enchant could not be applied. */
    public enum Refusal {
        NOT_APPLICABLE("This enchant cannot go on this kind of item."),
        ALREADY_ULTIMATE("This item already has an ultimate enchant."),
        NO_BETTER("The item already has this enchant at an equal or higher level."),
        UNKNOWN("Unknown enchant.");

        private final String message;

        Refusal(String message) {
            this.message = message;
        }

        public String message() { return message; }
    }

    /** Outcome of {@link #combine}: the new level, or the refusal. */
    public record Result(int level, @Nullable Refusal refusal) {
        public boolean ok() { return refusal == null; }
    }

    /** Parses {@code "sharpness:5,critical:3"}; malformed entries are skipped, levels clamped to at least 1. */
    public static Map<String, Integer> parse(@Nullable String text) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String part : text.split(",")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                int level = Integer.parseInt(part.substring(colon + 1).trim());
                if (level >= 1) {
                    out.put(part.substring(0, colon).trim(), level);
                }
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        return out;
    }

    public static String format(Map<String, Integer> enchants) {
        StringBuilder builder = new StringBuilder();
        enchants.forEach((id, level) -> {
            if (level > 0) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(id).append(':').append(level);
            }
        });
        return builder.toString();
    }

    /** True when the enchant map already holds an ultimate other than {@code except}. */
    public static boolean hasOtherUltimate(Map<String, Integer> current, EnchantRegistry registry, String except) {
        for (String id : current.keySet()) {
            if (!id.equals(except) && registry.get(id).map(Enchant::ultimate).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combines {@code incomingLevel} of {@code enchant} with the item's current enchants: same level gives one
     * more (capped at the max), a higher level replaces, a lower or equal one is refused when nothing improves.
     */
    public static Result combine(Map<String, Integer> current, EnchantRegistry registry, @Nullable Enchant enchant,
            int incomingLevel, ItemKind kind) {
        if (enchant == null) {
            return new Result(0, Refusal.UNKNOWN);
        }
        if (!enchant.appliesTo(kind)) {
            return new Result(0, Refusal.NOT_APPLICABLE);
        }
        if (enchant.ultimate() && hasOtherUltimate(current, registry, enchant.id())) {
            return new Result(0, Refusal.ALREADY_ULTIMATE);
        }
        int have = current.getOrDefault(enchant.id(), 0);
        int incoming = Math.min(incomingLevel, enchant.maxLevel());
        int result;
        if (have == 0) {
            result = incoming;
        } else if (have == incoming) {
            result = Math.min(enchant.maxLevel(), have + 1);
        } else {
            result = Math.max(have, incoming);
        }
        if (result <= have) {
            return new Result(have, Refusal.NO_BETTER);
        }
        return new Result(result, null);
    }

    /** Coins (or levels) an anvil operation costs: sum of the resulting levels times the unit price. */
    public static double anvilCost(int resultingLevel, double perLevel, boolean ultimate) {
        return resultingLevel * perLevel * (ultimate ? 4 : 1);
    }

    /** Enchanting table price: {@code base * growth^(level-1) * level}. */
    public static double tableCost(int level, double base, double growth) {
        return Math.floor(base * Math.pow(growth, level - 1) * level);
    }

    /** Parses a stored book value {@code "id:level"}. */
    public static Optional<Map.Entry<String, Integer>> parseBook(@Nullable String text) {
        Map<String, Integer> parsed = parse(text);
        return parsed.entrySet().stream().findFirst().map(e -> Map.entry(e.getKey(), e.getValue()));
    }
}
