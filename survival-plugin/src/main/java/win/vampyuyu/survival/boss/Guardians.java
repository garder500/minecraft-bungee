package win.vampyuyu.survival.boss;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.data.Settings;

/**
 * The Guardians of the Realms (GDD 12.2): bosses of the table that keep a Cosmic Sarcophagus, with what is specific to
 * them on top of the generic boss rules: a local resource every participant gets, a first-clear bonus, a component with
 * a hard pity (GDD 15.2) and a few spoken lines. Read from the {@code boss.guardian.*} setting rows.
 */
public record Guardians(int hardPity, int hardPityResonance, int resonanceRank, Map<String, Guardian> byBoss) {

    /** Spoken line events, in the order a fight goes through them. */
    public static final String INTRO = "intro";
    public static final String DEATH = "death";
    public static final String FIRST_CLEAR = "first-clear";

    /** Event of the line spoken when phase {@code index} (1-based among the phases after the first) starts. */
    public static String phaseEvent(int index) {
        return "phase" + index;
    }

    /**
     * One Guardian.
     *
     * @param bossId technical id of the boss
     * @param realm Realm id (see {@code api.Realm})
     * @param speaker MiniMessage name in front of its lines
     * @param resource custom item id of the local resource, may name an item that is not registered yet
     * @param resourceMin local resource per kill, lowest
     * @param resourceMax local resource per kill, highest
     * @param firstClearResource extra local resource the first time a player defeats it
     * @param component custom item id of the component the hard pity guarantees
     * @param lines spoken lines by event
     */
    public record Guardian(String bossId, String realm, String speaker, String resource, int resourceMin, int resourceMax,
            int firstClearResource, String component, Map<String, List<String>> lines) {

        public List<String> line(String event) {
            return lines.getOrDefault(event, List.of());
        }
    }

    public static Guardians none() {
        return new Guardians(4, 3, 3, Map.of());
    }

    /** Reads the {@code boss.guardian.*} rows; a Guardian without a component or resource is still valid (it just pays less). */
    public static Guardians parse(Settings s) {
        Map<String, Guardian> out = new LinkedHashMap<>();
        for (String id : s.getList("guardian.ids")) {
            String p = "guardian." + id + ".";
            Map<String, List<String>> lines = new HashMap<>();
            for (String event : List.of(INTRO, phaseEvent(1), phaseEvent(2), phaseEvent(3), DEATH, FIRST_CLEAR)) {
                List<String> list = s.getList(p + event);
                if (!list.isEmpty()) {
                    lines.put(event, list);
                }
            }
            int min = Math.max(0, s.getInt(p + "resource-min", 0));
            out.put(id, new Guardian(id, s.getString(p + "realm", ""), s.getString(p + "speaker", id),
                s.getString(p + "resource", ""), min, Math.max(min, s.getInt(p + "resource-max", min)),
                Math.max(0, s.getInt(p + "first-clear-resource", 0)), s.getString(p + "component", ""), Map.copyOf(lines)));
        }
        return new Guardians(Math.max(0, s.getInt("guardian.hard-pity", 4)), Math.max(0, s.getInt("guardian.hard-pity-resonance", 3)),
            Math.max(1, s.getInt("guardian.resonance-rank", 3)), Map.copyOf(out));
    }

    public @Nullable Guardian get(String bossId) {
        return byBoss.get(bossId);
    }

    /**
     * Runs without the component after which it is guaranteed, for a player of this Resonance rank: the Resonance
     * shortcut applies from {@link #resonanceRank()}. 0 = no hard pity.
     */
    public int hardPityFor(int resonance) {
        if (resonance >= resonanceRank && hardPityResonance > 0) {
            return hardPity > 0 ? Math.min(hardPity, hardPityResonance) : hardPityResonance;
        }
        return hardPity;
    }

    /** The hard pity of every Guardian component, as the {@code custom:<id>} item spec to the runs, for {@link LootRoller}. */
    public Map<String, Integer> hardPityMap(String bossId, int resonance) {
        Guardian g = byBoss.get(bossId);
        int runs = hardPityFor(resonance);
        if (g == null || g.component().isEmpty() || runs <= 0) {
            return Map.of();
        }
        return Map.of("custom:" + g.component(), runs);
    }

    /** Whole resource amount of one kill: between the bounds, plus the first-clear bonus. */
    public static int resourceAmount(Guardian g, boolean firstClear, java.util.Random random) {
        int base = g.resourceMax() <= g.resourceMin() ? g.resourceMin()
            : g.resourceMin() + random.nextInt(g.resourceMax() - g.resourceMin() + 1);
        return base + (firstClear ? g.firstClearResource() : 0);
    }

    /** A random spoken line of the event as a chat component, null when the Guardian has none for it. */
    public static @Nullable Component say(Guardian g, String event, java.util.Random random) {
        List<String> options = g.line(event);
        if (options.isEmpty()) {
            return null;
        }
        String text = options.get(random.nextInt(options.size()));
        return MiniMessage.miniMessage().deserialize(g.speaker() + " <dark_gray>» <white><italic>" + MiniEsc.esc(text));
    }
}
