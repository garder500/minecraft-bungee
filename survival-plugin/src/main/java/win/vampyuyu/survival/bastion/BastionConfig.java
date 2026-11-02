package win.vampyuyu.survival.bastion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;

/** The {@code bastion} table and the {@code bastion.*} settings, validated. A bad row is skipped and listed in {@link #problems()}. */
public final class BastionConfig {
    /** One Bastion. */
    public record Bastion(String id, String realm, String name, String tagline, String guardian, String keyItem,
            String palette, int radius, int minPlayers, int maxPlayers, int timeLimitSeconds, int deathsAllowed) {}

    private final Map<String, Bastion> bastions = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();
    public boolean enabled = true;
    public int maxConcurrent = 4;
    public int countdownSeconds = 8;
    public int exitDelaySeconds = 15;

    public static BastionConfig load(Db db) {
        BastionConfig c = new BastionConfig();
        Settings s = Settings.load(db, "bastion");
        c.enabled = s.getBoolean("enabled", true);
        c.maxConcurrent = clamp(s.getInt("max-concurrent", 4), 1, 20, "max-concurrent", c);
        c.countdownSeconds = clamp(s.getInt("countdown-seconds", 8), 0, 60, "countdown-seconds", c);
        c.exitDelaySeconds = clamp(s.getInt("exit-delay-seconds", 15), 3, 300, "exit-delay-seconds", c);
        db.read(conn -> {
            for (Object[] r : Db.query(conn, "SELECT id, realm, name, tagline, guardian, key_item, palette, radius, min_players, "
                + "max_players, time_limit_seconds, deaths_allowed FROM bastion ORDER BY position",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getString(7), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12)})) {
                c.add(r);
            }
            return null;
        });
        if (c.bastions.isEmpty()) {
            c.problems.add("the bastion table has no valid row");
        }
        return c;
    }

    private void add(Object[] r) {
        String id = String.valueOf(r[0]).toLowerCase(Locale.ROOT);
        String guardian = String.valueOf(r[4]);
        String key = String.valueOf(r[5]);
        if (guardian.isBlank() || key.isBlank()) {
            problems.add("bastion " + id + " needs a guardian and a key item, skipped");
            return;
        }
        for (Bastion b : bastions.values()) {
            if (b.keyItem().equals(key)) {
                problems.add("bastion " + id + " uses the key of " + b.id() + ", skipped");
                return;
            }
        }
        bastions.put(id, new Bastion(id, String.valueOf(r[1]), String.valueOf(r[2]), String.valueOf(r[3]), guardian, key,
            String.valueOf(r[6]), (Integer) r[7], (Integer) r[8], (Integer) r[9], (Integer) r[10], (Integer) r[11]));
    }

    private static int clamp(int v, int lo, int hi, String name, BastionConfig c) {
        if (v < lo || v > hi) {
            c.problems.add("bastion." + name + " = " + v + " is outside " + lo + ".." + hi + ", clamped");
        }
        return Math.max(lo, Math.min(hi, v));
    }

    public Map<String, Bastion> bastions() { return java.util.Collections.unmodifiableMap(bastions); }

    public Optional<Bastion> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(bastions.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Bastion> ofKey(String itemId) {
        return bastions.values().stream().filter(b -> b.keyItem().equals(itemId)).findFirst();
    }

    public List<String> problems() { return java.util.Collections.unmodifiableList(problems); }
}
