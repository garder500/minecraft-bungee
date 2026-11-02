package win.vampyuyu.survival.hellgao;

import java.util.ArrayList;
import java.util.List;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** The {@code hellgao.*} settings, validated. A bad entry is skipped and listed in {@link #problems()}. */
public final class HellGaoConfig {
    /** A title won by a clear at this Ascension rank or higher (0 = any clear). */
    public record Title(String id, int minAscension, String name) {}

    /** A lore fragment, handed out in order, one per clear. */
    public record Lore(String id, String text) {}

    public boolean enabled = true;
    public String bastionId = "hell_gao_bastion";
    public boolean paragonRequired = true;
    /**
     * Length of one pulse cycle and of the window that ends it, in seconds: the clock fallback when the Celestial Breath is
     * off. With the Breath, the window is the last {@link #windowSeconds} before the next pulsation.
     */
    public int pulsePeriodSeconds = 3600;
    public int windowSeconds = 300;
    public int paragonPointsClear = 3;
    public int paragonPointsPerAscension = 2;
    /** Resonance V needed for Ascension: this many of these Guardians cleared at rank V. */
    public int ascensionResonanceMin = 3;
    public final List<String> ascensionBosses = new ArrayList<>();
    public final List<Title> titles = new ArrayList<>();
    public final List<Lore> lore = new ArrayList<>();
    /** The title that unlocks the aura. */
    public String auraTitle = "between_pulses";
    private final List<String> problems = new ArrayList<>();

    public static HellGaoConfig load(Db db) {
        return from(Settings.load(db, "hellgao"));
    }

    public static HellGaoConfig from(Settings s) {
        HellGaoConfig c = new HellGaoConfig();
        c.enabled = s.getBoolean("enabled", true);
        c.bastionId = s.getString("bastion", c.bastionId);
        c.paragonRequired = s.getBoolean("paragon-required", true);
        c.pulsePeriodSeconds = c.clamp(s.getInt("pulse.period-seconds", 3600), 60, 86400, "pulse.period-seconds");
        c.windowSeconds = c.clamp(s.getInt("pulse.window-seconds", 300), 30, c.pulsePeriodSeconds, "pulse.window-seconds");
        c.paragonPointsClear = c.clamp(s.getInt("paragon-points.clear", 3), 0, 100, "paragon-points.clear");
        c.paragonPointsPerAscension = c.clamp(s.getInt("paragon-points.per-ascension", 2), 0, 100, "paragon-points.per-ascension");
        c.ascensionResonanceMin = c.clamp(s.getInt("ascension.resonance-min", 3), 0, 50, "ascension.resonance-min");
        c.ascensionBosses.addAll(s.getList("ascension.resonance-bosses"));
        for (String line : s.getList("titles")) {
            String[] p = line.split("\\|", 3);
            try {
                int min = Integer.parseInt(p[1].trim());
                if (p.length != 3 || p[0].isBlank() || min < 0 || min > HellGaoMath.MAX_ASCENSION) {
                    throw new NumberFormatException();
                }
                c.titles.add(new Title(p[0].trim(), min, p[2]));
            } catch (RuntimeException e) {
                c.problems.add("titles entry '" + line + "' is not id|minAscension|name, skipped");
            }
        }
        for (String line : s.getList("lore")) {
            String[] p = line.split("\\|", 2);
            if (p.length != 2 || p[0].isBlank()) {
                c.problems.add("lore entry '" + line + "' is not id|text, skipped");
            } else {
                c.lore.add(new Lore(p[0].trim(), p[1]));
            }
        }
        c.auraTitle = s.getString("aura-title", c.auraTitle);
        c.problems.addAll(s.problems());
        return c;
    }

    private int clamp(int v, int lo, int hi, String name) {
        if (v < lo || v > hi) {
            problems.add("hellgao." + name + " = " + v + " is outside " + lo + ".." + hi + ", clamped");
        }
        return Math.max(lo, Math.min(hi, v));
    }

    public List<String> problems() { return java.util.Collections.unmodifiableList(problems); }

    public java.util.Optional<Title> title(String id) {
        return titles.stream().filter(t -> t.id().equals(id)).findFirst();
    }
}
