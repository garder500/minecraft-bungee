package win.vampyuyu.survival.economy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Server-wide sell pressure. Every item sold to an NPC adds to the key's pressure; pressure decays with a half life.
 * The sell price multiplier is {@code 1 / (1 + pressure / softCap)} (never below {@code minMultiplier}), so a farm
 * that dumps endlessly sees the price fall while a normal player barely notices. Thread confined to the main thread.
 *
 * <p>Compacted items share the pool of the raw item they are made of: {@code minion_enchanted_wheat} records into
 * {@code WHEAT} weighted by the compaction ratio ({@code pressure.compact-prefixes}), so one enchanted item moves the
 * price as much as the raw items it stands for and selling compacted goods cannot dodge (or exploit) the raw price.
 */
final class PricePressure {
    /** Persisted state of one key. */
    static final class Entry {
        double value;
        long at;
    }

    /** Persisted file. */
    static final class State {
        Map<String, Entry> pressure = new HashMap<>();
    }

    private boolean enabled = true;
    private double softCap = 20_000;
    private long halfLifeMs = 2 * 3_600_000L;
    private double minMultiplier = 0.2;
    private final Map<String, Entry> entries = new HashMap<>();
    private Map<String, Double> prefixes = defaultPrefixes();

    /** The pool a key records into and how many raw units one item counts for. */
    record Pool(String key, double weight) {}

    private static Map<String, Double> defaultPrefixes() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("minion_super_", 22_500.0);
        m.put("minion_enchanted_", 150.0);
        return m;
    }

    Pool pool(String key) {
        for (var e : prefixes.entrySet()) {
            if (key.startsWith(e.getKey()) && key.length() > e.getKey().length()) {
                String raw = key.substring(e.getKey().length()).toUpperCase(Locale.ROOT);
                Material m = Material.matchMaterial(raw);
                if (m != null && m.name().equals(raw)) {
                    return new Pool(raw, e.getValue());
                }
            }
        }
        return new Pool(key, 1.0);
    }

    void reload(ConfigurationSection c) {
        ConfigurationSection compact = c.getConfigurationSection("pressure.compact-prefixes");
        if (compact == null) {
            prefixes = defaultPrefixes();
        } else {
            Map<String, Double> m = new LinkedHashMap<>();
            for (String prefix : compact.getKeys(false)) {
                m.put(prefix, Math.max(1.0, compact.getDouble(prefix, 1.0)));
            }
            // longest prefix first so "minion_super_" is not shadowed by a shorter one
            Map<String, Double> sorted = new LinkedHashMap<>();
            m.entrySet().stream().sorted((a, b) -> b.getKey().length() - a.getKey().length())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
            prefixes = sorted;
        }
        enabled = c.getBoolean("pressure.enabled", true);
        softCap = Math.max(1, c.getDouble("pressure.soft-cap", 20_000));
        halfLifeMs = Math.max(60_000L, (long) (c.getDouble("pressure.half-life-hours", 2.0) * 3_600_000L));
        minMultiplier = Math.max(0.01, Math.min(1.0, c.getDouble("pressure.min-multiplier", 0.2)));
    }

    boolean enabled() {
        return enabled;
    }

    private double decayed(Entry e, long now) {
        if (e == null || e.value <= 0) {
            return 0;
        }
        double halves = Math.max(0, now - e.at) / (double) halfLifeMs;
        return e.value * Math.pow(0.5, halves);
    }

    /** Pressure of the pool the key belongs to, in raw units. */
    double pressure(String key, long now) {
        return decayed(entries.get(pool(key).key()), now);
    }

    /** Multiplier for selling {@code qty} more items (priced at the middle of the batch). */
    double multiplier(String key, int qty, long now) {
        if (!enabled) {
            return 1.0;
        }
        Pool pool = pool(key);
        double p = decayed(entries.get(pool.key()), now) + Math.max(0, qty) * pool.weight() / 2.0;
        return Math.max(minMultiplier, 1.0 / (1.0 + p / softCap));
    }

    void record(String key, int qty, long now) {
        if (!enabled || qty == 0) {
            return;
        }
        Pool pool = pool(key);
        Entry e = entries.computeIfAbsent(pool.key(), k -> new Entry());
        e.value = Math.max(0, decayed(e, now) + qty * pool.weight());
        e.at = now;
    }

    State export(long now) {
        State s = new State();
        entries.forEach((k, e) -> {
            double v = decayed(e, now);
            if (v >= 1) {
                Entry copy = new Entry();
                copy.value = v;
                copy.at = now;
                s.pressure.put(k, copy);
            }
        });
        return s;
    }

    void load(State s) {
        entries.clear();
        if (s != null && s.pressure != null) {
            entries.putAll(s.pressure);
        }
    }
}
