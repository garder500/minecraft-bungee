package win.vampyuyu.survival.breath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.breath.BreathEvent.Effect;
import win.vampyuyu.survival.breath.BreathEvent.Type;

/** The pure rules of the Celestial Breath: parsing, the draw, the factors, the banner text and the fragments. */
public final class BreathRules {
    private BreathRules() {}

    /**
     * Parses {@code id|title|weight|minutes|effect;effect...}. An effect is {@code yield:<resource|*>:<percent>},
     * {@code machine:<percent>}, {@code monster:<percent>}, {@code route:<realm>}, {@code fracture:<realm>:<floor>} or
     * {@code invade:<hub|realm>}. Throws {@link IllegalArgumentException} with the reason.
     */
    public static BreathEvent parse(String row) {
        String[] p = row.split("\\|", -1);
        if (p.length != 5) {
            throw new IllegalArgumentException("expected id|title|weight|minutes|effects");
        }
        String id = p[0].trim();
        if (!id.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("bad id '" + id + "'");
        }
        String title = p[1].trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("empty title");
        }
        int weight = integer(p[2], "weight");
        int minutes = integer(p[3], "minutes");
        if (weight < 1 || minutes < 1) {
            throw new IllegalArgumentException("weight and minutes must be at least 1");
        }
        List<Effect> effects = new ArrayList<>();
        for (String raw : p[4].split(";")) {
            if (!raw.isBlank()) {
                effects.add(effect(raw.trim()));
            }
        }
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("no effect");
        }
        BreathEvent event = new BreathEvent(id, title, weight, minutes, List.copyOf(effects));
        if (!event.isChoice()) {
            throw new IllegalArgumentException("an event must give something and cost something");
        }
        return event;
    }

    static Effect effect(String raw) {
        String[] f = raw.split(":");
        String kind = f[0].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "yield" -> {
                need(f, 3, raw);
                return new Effect(Type.YIELD, f[1], percent(f[2]), 0);
            }
            case "machine" -> {
                need(f, 2, raw);
                return new Effect(Type.MACHINE, "*", percent(f[1]), 0);
            }
            case "monster" -> {
                need(f, 2, raw);
                return new Effect(Type.MONSTER, "*", percent(f[1]), 0);
            }
            case "route" -> {
                need(f, 2, raw);
                return new Effect(Type.ROUTE, realm(f[1], raw), 0, 0);
            }
            case "fracture" -> {
                need(f, 3, raw);
                int floor = integer(f[2], "floor");
                if (floor < 1) {
                    throw new IllegalArgumentException("floor must be at least 1 in '" + raw + "'");
                }
                return new Effect(Type.FRACTURE, realm(f[1], raw), 0, floor);
            }
            case "invade" -> {
                need(f, 2, raw);
                String target = f[1].trim().toLowerCase(Locale.ROOT);
                return new Effect(Type.INVADE, target.equals("hub") ? "hub" : realm(target, raw), 0, 0);
            }
            default -> throw new IllegalArgumentException("unknown effect '" + raw + "'");
        }
    }

    private static void need(String[] f, int n, String raw) {
        if (f.length != n) {
            throw new IllegalArgumentException("bad effect '" + raw + "'");
        }
    }

    private static String realm(String id, String raw) {
        Realm realm = Realm.byId(id.trim());
        if (realm == null) {
            throw new IllegalArgumentException("unknown realm in '" + raw + "'");
        }
        return realm.id();
    }

    private static int percent(String s) {
        int v = integer(s, "percent");
        if (v < -90 || v > 500) {
            throw new IllegalArgumentException("percent out of -90..500: " + v);
        }
        return v;
    }

    private static int integer(String s, String what) {
        try {
            return Integer.parseInt(s.trim().replace("+", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad " + what + " '" + s + "'");
        }
    }

    // ---------------------------------------------------------------- the draw

    /** A weighted draw. The event that ran last is left out when another one exists, so two pulsations never repeat. */
    public static BreathEvent pick(List<BreathEvent> events, String lastId, Random rng) {
        List<BreathEvent> pool = new ArrayList<>();
        for (BreathEvent e : events) {
            if (!e.id().equals(lastId)) {
                pool.add(e);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(events);
        }
        if (pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (BreathEvent e : pool) {
            total += e.weight();
        }
        int roll = rng.nextInt(total);
        for (BreathEvent e : pool) {
            roll -= e.weight();
            if (roll < 0) {
                return e;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ---------------------------------------------------------------- factors

    /** Product of {@code 1 + percent / 100} over the effects of this type that match the target (never below 0). */
    public static double factor(BreathEvent event, Type type, String target) {
        double factor = 1;
        if (event != null) {
            for (Effect e : event.effects()) {
                if (e.type() == type && (e.target().equals("*") || e.target().equals(target))) {
                    factor *= 1 + e.value() / 100.0;
                }
            }
        }
        return Math.max(0, factor);
    }

    // ---------------------------------------------------------------- banner

    /** The lines of the announcement (GDD 19.3), without the CELESTIAL BREATH heading. */
    public static List<String> banner(BreathEvent event) {
        List<String> lines = new ArrayList<>();
        for (Effect e : event.effects()) {
            lines.add(line(e));
        }
        return lines;
    }

    public static String line(Effect e) {
        return switch (e.type()) {
            case YIELD -> (e.target().equals("*") ? "Realm resource" : BreathEvent.label(e.target())) + " yield " + signed(e.value());
            case MACHINE -> "Machine Flux consumption " + signed(e.value());
            case MONSTER -> "Monster damage " + signed(e.value());
            case ROUTE -> "The Sky Gate to the " + title(e.target()) + " is shut";
            case FRACTURE -> "A Fracture has opened in the " + title(e.target()) + " (/breath fracture)";
            case INVADE -> "Stellar immune cells invade " + (e.target().equals("hub") ? "the Hub" : "the " + title(e.target()));
        };
    }

    private static String title(String realmId) {
        Realm r = Realm.byId(realmId);
        return r == null ? realmId : r.title();
    }

    static String signed(int percent) {
        return (percent >= 0 ? "+" : "-") + Math.abs(percent) + " %";
    }

    // ---------------------------------------------------------------- fragments

    /**
     * Whether a player may still find a Fragment during this pulsation: one per cycle at most, so that a key takes
     * several cycles of Respiration (GDD 19.4).
     */
    public static boolean mayFindFragment(int lastFragmentCycle, int cycle) {
        return cycle > 0 && lastFragmentCycle != cycle;
    }

    /** Keys that {@code fragments} Fragments assemble, and the Fragments left over. */
    public static int[] assemble(int fragments, int perKey) {
        int per = Math.max(1, perKey);
        return new int[] {fragments / per, fragments % per};
    }
}
