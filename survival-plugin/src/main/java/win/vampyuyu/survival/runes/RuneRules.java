package win.vampyuyu.survival.runes;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;

/** Pure rules of the rune system: what is stored on an item, how many slots it has, what a rune adds. */
public final class RuneRules {
    private RuneRules() {}

    /** Parses the {@code runes} value of an item ({@code "vitality:3,scorch:0"}); unreadable entries are skipped. */
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
                out.put(part.substring(0, colon).trim(), Integer.parseInt(part.substring(colon + 1).trim()));
            } catch (NumberFormatException ignored) {
                // a damaged entry is dropped
            }
        }
        return out;
    }

    public static String format(Map<String, Integer> runes) {
        StringBuilder out = new StringBuilder();
        runes.forEach((id, rank) -> out.append(out.length() == 0 ? "" : ",").append(id).append(':').append(rank));
        return out.toString();
    }

    /** The stat points the engraved runes add (Realm runes add none). Unknown ids count for nothing. */
    public static Map<Stat, Double> bonus(RuneConfig config, Map<String, Integer> runes) {
        Map<Stat, Double> out = new EnumMap<>(Stat.class);
        runes.forEach((id, rank) -> config.rune(id).ifPresent(rune -> {
            if (rune.stat() != null && rank >= 1) {
                out.merge(rune.stat(), (double) config.value(rank), Double::sum);
            }
        }));
        return out;
    }

    /**
     * Why {@code rune} cannot be engraved on an item, or null when it can.
     *
     * @param rank the rank of the rune item (0 for a Realm rune)
     * @param tier the item's power tier (1-5, 1 when it has none)
     */
    public static @Nullable String problem(RuneConfig config, Rune rune, int rank, Map<String, Integer> engraved,
            boolean weapon, boolean armor, int tier) {
        if (!weapon && !armor) {
            return "Runes can only be engraved on weapons and armor.";
        }
        if (!rune.fits(weapon, armor)) {
            String where = switch (rune.target()) {
                case ARMOR -> "armor";
                case WEAPON -> "weapons";
                case ANY -> "weapons and armor";
            };
            return rune.name() + " Runes go on " + where + " only.";
        }
        if (engraved.containsKey(rune.id())) {
            return "This item already carries a " + rune.name() + " Rune.";
        }
        int slots = config.slotsFor(tier);
        if (engraved.size() >= slots) {
            return "No free rune slot (" + engraved.size() + "/" + slots + " for a tier " + tier + " item).";
        }
        if (!rune.realm() && (rank < 1 || rank > 3)) {
            return "This rune is damaged.";
        }
        return null;
    }
}
