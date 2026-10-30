package win.vampyuyu.survival.menu;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Target matching shared by the quest definitions and the listeners. A definition target is compared with the
 * "subject" an event produced (a material name, an entity type, a boss id...), case-insensitively. Empty or
 * {@code ANY} matches everything. {@code custom:<prefix>} matches a custom item id by prefix, {@code custom:re:<regex>}
 * matches the whole id with a regular expression (a prefix such as {@code custom:minion} also catches
 * {@code minion_fuel_*}, {@code minion_enchanted_*}...).
 */
public final class QuestMatcher {
    /** Prefix of a regex target for custom items: {@code custom:re:minion_[a-z_]+_[0-9]+} (whole id, case-insensitive). */
    public static final String REGEX_PREFIX = "custom:re:";

    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    private QuestMatcher() {}

    /** True for a {@code custom:re:<regex>} target. */
    public static boolean isRegexTarget(String target) {
        return target != null && target.trim().regionMatches(true, 0, REGEX_PREFIX, 0, REGEX_PREFIX.length());
    }

    /**
     * Compiles (and caches) the regex of a {@code custom:re:} target at load time so a bad pattern is reported when
     * the config is read and never costs anything per event.
     *
     * @throws IllegalArgumentException when the regex is invalid
     */
    public static void validate(String target) {
        if (isRegexTarget(target)) {
            pattern(target);
        }
    }

    private static Pattern pattern(String target) {
        String regex = target.trim().substring(REGEX_PREFIX.length());
        Pattern cached = PATTERNS.get(regex);
        if (cached != null) {
            return cached;
        }
        try {
            Pattern compiled = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            PATTERNS.put(regex, compiled);
            return compiled;
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("bad regex in target '" + target + "': " + e.getDescription(), e);
        }
    }

    /**
     * True when the definition target accepts the subject. A subject may list alternatives separated by {@code |}
     * (a command and its aliases); any of them can match.
     */
    public static boolean matches(String defTarget, String subject) {
        if (defTarget == null || defTarget.isBlank() || defTarget.equalsIgnoreCase("ANY")) {
            return true;
        }
        if (subject == null) {
            return false;
        }
        if (subject.indexOf('|') >= 0) {
            for (String alternative : subject.split("\\|")) {
                if (matchesOne(defTarget, alternative)) {
                    return true;
                }
            }
            return false;
        }
        return matchesOne(defTarget, subject);
    }

    private static boolean matchesOne(String defTarget, String subject) {
        if (isRegexTarget(defTarget)) {
            String have = subject.trim();
            if (!have.regionMatches(true, 0, "custom:", 0, 7)) {
                return false;
            }
            try {
                return pattern(defTarget).matcher(have.substring(7)).matches();
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        String want = defTarget.trim().toUpperCase(Locale.ROOT);
        String have = subject.trim().toUpperCase(Locale.ROOT);
        if (want.equals(have)) {
            return true;
        }
        if (want.startsWith("CUSTOM:")) {
            return have.startsWith("CUSTOM:") && have.substring(7).startsWith(want.substring(7));
        }
        Material material = Material.matchMaterial(have);
        return material != null && inGroup(want, material);
    }

    /** Named material groups usable as quest targets. */
    public static boolean inGroup(String group, Material material) {
        return switch (group) {
            case "LOGS" -> Tag.LOGS.isTagged(material);
            case "PLANKS" -> Tag.PLANKS.isTagged(material);
            case "STONE" -> material == Material.STONE || material == Material.COBBLESTONE
                || material == Material.DEEPSLATE || material == Material.COBBLED_DEEPSLATE
                || material == Material.ANDESITE || material == Material.DIORITE || material == Material.GRANITE;
            case "ORES" -> material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
            case "CROPS" -> Tag.CROPS.isTagged(material) || material == Material.SUGAR_CANE
                || material == Material.NETHER_WART || material == Material.PUMPKIN || material == Material.MELON
                || material == Material.COCOA || material == Material.CACTUS;
            case "SAPLINGS" -> Tag.SAPLINGS.isTagged(material);
            case "FLOWERS" -> Tag.FLOWERS.isTagged(material);
            default -> false;
        };
    }

    /** Subject string of a custom item (prefix with {@code custom:}); matched by {@code custom:<prefix>} targets. */
    public static String customSubject(String itemId) {
        return "CUSTOM:" + itemId;
    }
}
