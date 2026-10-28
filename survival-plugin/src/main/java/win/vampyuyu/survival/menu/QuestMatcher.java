package win.vampyuyu.survival.menu;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Target matching shared by the quest definitions and the listeners. A definition target is compared with the
 * "subject" an event produced (a material name, an entity type, a boss id...), case-insensitively. Empty or
 * {@code ANY} matches everything.
 */
public final class QuestMatcher {
    private QuestMatcher() {}

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
