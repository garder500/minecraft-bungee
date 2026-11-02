package win.vampyuyu.survival.promotion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The small pure rules of the promotions: which profile a player has, what a rank needs, which events a step counts. */
public final class PromotionRules {
    public static final String KILLS = "kills";
    public static final String CRAFTS = "crafts";
    public static final String CASTS = "casts";
    public static final String COOP = "coop";
    public static final String ENTRIES = "entries";
    public static final String BOSS_SOLO = "boss_solo";
    public static final String BOSS_GROUP = "boss_group";
    public static final String BASTIONS = "bastions";

    /** Another player this close makes a kill a teamwork kill. */
    public static final double COOP_RADIUS = 16;
    /** Two entries into the same Realm closer than this do not both count (a portal bounce). */
    public static final long ENTRY_DEBOUNCE_MILLIS = 60_000;

    private PromotionRules() {}

    /** The rank after {@code rank}, null once Master (Paragon is another feature). */
    public static String nextRank(String rank) {
        return switch (rank == null ? "NOVICE" : rank.toUpperCase(Locale.ROOT)) {
            case "NOVICE" -> "ADEPT";
            case "ADEPT" -> "MASTER";
            default -> null;
        };
    }

    private static int count(Map<String, Integer> counters, String key) {
        return counters.getOrDefault(key, 0);
    }

    /** How strongly each profile shows in the history, 1.0 being a player who did the usual amount of that. */
    public static double score(Profile profile, Map<String, Integer> counters, int realmsVisited) {
        return switch (profile) {
            case COMBAT -> count(counters, KILLS) / 150.0;
            case CRAFT -> count(counters, CRAFTS) / 80.0;
            case EXPLORE -> realmsVisited * 0.5 + count(counters, ENTRIES) / 20.0;
            case COOP -> count(counters, COOP) / 60.0;
            case BOSS -> (count(counters, BOSS_SOLO) + count(counters, BOSS_GROUP)) / 2.0;
        };
    }

    /** The profiles, strongest first (a tie keeps the order of the enum, and an empty history starts with Combat). */
    public static List<Profile> ranking(Map<String, Integer> counters, int realmsVisited) {
        List<Profile> all = new ArrayList<>(List.of(Profile.values()));
        all.sort(Comparator.comparingDouble((Profile p) -> score(p, counters, realmsVisited)).reversed());
        return all;
    }

    /** What is missing to start the promotion to {@code target}; empty when the player may start it. */
    public static List<String> missing(String target, int echoKills, int bastionClears, int guardians, int needClears, int needGuardians) {
        List<String> out = new ArrayList<>();
        if ("ADEPT".equals(target)) {
            if (echoKills < 1) {
                out.add("Defeat the Echo of the Rift");
            }
            if (bastionClears < needClears) {
                out.add("Clear " + needClears + " Bastions (" + bastionClears + " so far)");
            }
        } else if ("MASTER".equals(target)) {
            if (guardians < needGuardians) {
                out.add("Defeat " + needGuardians + " different Guardians in their Bastions (" + guardians + " so far)");
            }
        }
        return out;
    }

    /** Whether an event of {@code kind} on {@code target} counts for a step. */
    public static boolean matches(PromotionConfig.Step step, StepKind kind, String target) {
        if (step.kind() != kind) {
            return false;
        }
        return step.target().equalsIgnoreCase("any") || step.target().equalsIgnoreCase(target);
    }
}
