package win.vampyuyu.survival.economy;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Server-wide sell pressure. Every item sold to an NPC adds to the key's pressure; pressure decays with a half life.
 * The sell price multiplier is {@code 1 / (1 + pressure / softCap)} (never below {@code minMultiplier}), so a farm
 * that dumps endlessly sees the price fall while a normal player barely notices. Thread confined to the main thread.
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

    void reload(ConfigurationSection c) {
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

    double pressure(String key, long now) {
        return decayed(entries.get(key), now);
    }

    /** Multiplier for selling {@code qty} more items (priced at the middle of the batch). */
    double multiplier(String key, int qty, long now) {
        if (!enabled) {
            return 1.0;
        }
        double p = pressure(key, now) + Math.max(0, qty) / 2.0;
        return Math.max(minMultiplier, 1.0 / (1.0 + p / softCap));
    }

    void record(String key, int qty, long now) {
        if (!enabled || qty == 0) {
            return;
        }
        Entry e = entries.computeIfAbsent(key, k -> new Entry());
        e.value = Math.max(0, decayed(e, now) + qty);
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
