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
 * The Guardians of the Realms (GDD 12.2): bosses of the table that keep a Sarcophagus, with what is specific to
 * them on top of the generic boss rules: a local resource every participant gets, a first-clear bonus, a component with
 * a hard pity (GDD 15.2) and a few spoken lines. Read from the {@code boss.guardian.*} setting rows.
 */
public record Guardians(int hardPity, int hardPityResonance, int resonanceRank, Map<String, Guardian> byBoss,
        Map<String, Guardian> speech) {

    /**
     * A spoken line and its recorded voice: the pack sound {@code vampyuyu:voice.<boss>.<event>.<n>}, n being the
     * line's position in its setting list (resource-pack/voices/manifest.json; a client without it just hears nothing).
     */
    public record Spoken(Component text, String sound) {}

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
        return new Guardians(4, 3, 3, Map.of(), Map.of());
    }

    /** Reads the {@code boss.guardian.*} rows; a Guardian without a component or resource is still valid (it just pays less). */
    public static Guardians parse(Settings s) {
        Map<String, Guardian> out = new LinkedHashMap<>();
        for (String id : s.getList("guardian.ids")) {
            String p = "guardian." + id + ".";
            Map<String, List<String>> lines = lines(s, p);
            int min = Math.max(0, s.getInt(p + "resource-min", 0));
            out.put(id, new Guardian(id, s.getString(p + "realm", ""), s.getString(p + "speaker", id),
                s.getString(p + "resource", ""), min, Math.max(min, s.getInt(p + "resource-max", min)),
                Math.max(0, s.getInt(p + "first-clear-resource", 0)), s.getString(p + "component", ""), Map.copyOf(lines)));
        }
        // bosses that speak without being Guardians (the Echo, the Devourer, Hell Gao): lines only, no Guardian loot
        Map<String, Guardian> speech = new LinkedHashMap<>();
        for (String id : s.getList("speech.ids")) {
            String p = "speech." + id + ".";
            speech.put(id, new Guardian(id, "", s.getString(p + "speaker", id), "", 0, 0, 0, "", Map.copyOf(lines(s, p))));
        }
        return new Guardians(Math.max(0, s.getInt("guardian.hard-pity", 4)), Math.max(0, s.getInt("guardian.hard-pity-resonance", 3)),
            Math.max(1, s.getInt("guardian.resonance-rank", 3)), Map.copyOf(out), Map.copyOf(speech));
    }

    private static Map<String, List<String>> lines(Settings s, String prefix) {
        Map<String, List<String>> lines = new HashMap<>();
        for (String event : List.of(INTRO, phaseEvent(1), phaseEvent(2), phaseEvent(3), DEATH, FIRST_CLEAR)) {
            List<String> list = s.getList(prefix + event);
            if (!list.isEmpty()) {
                lines.put(event, list);
            }
        }
        return lines;
    }

    /** Who speaks for this boss: its Guardian, or its speech-only row, or null. */
    public @Nullable Guardian speaker(String bossId) {
        Guardian g = byBoss.get(bossId);
        return g != null ? g : speech.get(bossId);
    }

    /** Pack sound key of a line. */
    public static String voiceKey(String bossId, String event, int index) {
        return "vampyuyu:voice." + bossId + "." + event + "." + index;
    }

    /** Plays a line's voice to a player, on the voice channel, where they stand. A voice never breaks what follows. */
    public static void voice(org.bukkit.entity.Player player, Spoken spoken) {
        try {
            player.playSound(player.getLocation(), spoken.sound(), org.bukkit.SoundCategory.VOICE, 1f, 1f);
        } catch (RuntimeException e) {
            // a server or test double without sound support: the chat line is enough
        }
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

    /** A random spoken line of the event (chat component and voice), null when the speaker has none for it. */
    public static @Nullable Spoken say(Guardian g, String event, java.util.Random random) {
        List<String> options = g.line(event);
        if (options.isEmpty()) {
            return null;
        }
        int index = random.nextInt(options.size());
        String text = options.get(index);
        return new Spoken(MiniMessage.miniMessage().deserialize(g.speaker() + " <dark_gray>» <white><italic>" + MiniEsc.esc(text)),
            voiceKey(g.bossId(), event, index));
    }
}
