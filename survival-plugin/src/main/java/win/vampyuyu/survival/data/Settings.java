package win.vampyuyu.survival.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single tuning values of one domain ({@code setting} table), read once. Paths are the id without the domain:
 * {@code Settings.load(db, "economy").getDouble("bank.interest", 0.02)}. A missing value falls back to the default given
 * by the caller; a value that does not parse also falls back and is reported in {@link #problems()}.
 */
public final class Settings {
    private final String domain;
    private final Map<String, String> values;
    private final Map<String, List<String>> lists;
    private final List<String> problems = new ArrayList<>();

    private Settings(String domain, Map<String, String> values, Map<String, List<String>> lists) {
        this.domain = domain;
        this.values = values;
        this.lists = lists;
    }

    /** Empty settings (every getter returns its default). */
    public static Settings empty(String domain) {
        return new Settings(domain, Map.of(), Map.of());
    }

    public static Settings load(Db db, String domain) {
        String prefix = domain + ".";
        Map<String, String> values = new HashMap<>();
        Map<String, List<String>> lists = new HashMap<>();
        db.read(c -> {
            for (String[] row : Db.query(c, "SELECT id, value FROM setting WHERE id GLOB ?",
                    rs -> new String[] {rs.getString(1), rs.getString(2)}, prefix + "*")) {
                values.put(row[0].substring(prefix.length()), row[1]);
            }
            for (String[] row : Db.query(c, "SELECT setting_id, value FROM setting_item WHERE setting_id GLOB ? "
                    + "ORDER BY setting_id, position", rs -> new String[] {rs.getString(1), rs.getString(2)}, prefix + "*")) {
                lists.computeIfAbsent(row[0].substring(prefix.length()), k -> new ArrayList<>()).add(row[1]);
            }
            return null;
        });
        return new Settings(domain, values, lists);
    }

    public boolean has(String path) {
        return values.containsKey(path);
    }

    public String getString(String path, String fallback) {
        return values.getOrDefault(path, fallback);
    }

    public int getInt(String path, int fallback) {
        String v = values.get(path);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            problems.add(domain + "." + path + ": '" + v + "' is not a whole number");
            return fallback;
        }
    }

    public long getLong(String path, long fallback) {
        String v = values.get(path);
        if (v == null) return fallback;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            problems.add(domain + "." + path + ": '" + v + "' is not a whole number");
            return fallback;
        }
    }

    public double getDouble(String path, double fallback) {
        String v = values.get(path);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            problems.add(domain + "." + path + ": '" + v + "' is not a number");
            return fallback;
        }
    }

    public boolean getBoolean(String path, boolean fallback) {
        String v = values.get(path);
        if (v == null) return fallback;
        return switch (v.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> {
                problems.add(domain + "." + path + ": '" + v + "' is not true or false");
                yield fallback;
            }
        };
    }

    /** The entries of a list setting, in order; empty when missing. */
    public List<String> getList(String path) {
        return List.copyOf(lists.getOrDefault(path, List.of()));
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }
}
