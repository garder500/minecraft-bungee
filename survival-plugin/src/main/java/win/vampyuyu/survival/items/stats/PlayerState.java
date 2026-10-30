package win.vampyuyu.survival.items.stats;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import win.vampyuyu.survival.api.Stat;

/** Everything the engine keeps in memory for one online player. */
public final class PlayerState {
    public final UUID id;
    public final ManaPool mana = new ManaPool();

    private final Map<String, EnumMap<Stat, Double>> modifiers = new LinkedHashMap<>();
    private volatile EnumMap<Stat, Double> totals = baseTotals();
    private volatile Map<String, Integer> enchantLevels = Map.of();
    private volatile int signature = Integer.MIN_VALUE;
    private volatile int modVersion;
    volatile boolean everApplied;
    volatile boolean attributesActive;

    // HUD
    public volatile String xpPopup = "";
    public volatile long xpPopupUntil;
    public volatile String notice = "";
    public volatile long noticeUntil;
    public volatile String lastHud = "";
    public volatile long lastHudAt;

    // short lived combat effects
    public volatile long shieldUntil;
    public volatile double soulEaterBonus;
    public volatile long phoenixReadyAt;
    public volatile long mitigationUntil;

    PlayerState(UUID id) {
        this.id = id;
    }

    static EnumMap<Stat, Double> baseTotals() {
        EnumMap<Stat, Double> map = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            map.put(stat, stat.base());
        }
        return map;
    }

    public EnumMap<Stat, Double> totals() { return totals; }

    void totals(EnumMap<Stat, Double> value) { this.totals = value; }

    public double stat(Stat stat) {
        return totals.getOrDefault(stat, stat.base());
    }

    public Map<String, Integer> enchantLevels() { return enchantLevels; }

    void enchantLevels(Map<String, Integer> levels) { this.enchantLevels = levels; }

    public int enchantLevel(String id) { return enchantLevels.getOrDefault(id, 0); }

    int signature() { return signature; }

    void signature(int value) { this.signature = value; }

    int modVersion() { return modVersion; }

    synchronized void setModifier(String source, Stat stat, double amount) {
        modifiers.computeIfAbsent(source, k -> new EnumMap<>(Stat.class)).put(stat, amount);
        modVersion++;
    }

    synchronized void replaceSource(String source, Map<Stat, Double> stats) {
        if (stats.isEmpty()) {
            modifiers.remove(source);
        } else {
            EnumMap<Stat, Double> copy = new EnumMap<>(Stat.class);
            copy.putAll(stats);
            modifiers.put(source, copy);
        }
    }

    synchronized void removeSource(String source) {
        if (modifiers.remove(source) != null) {
            modVersion++;
        }
    }

    /** Sum of every source (external and engine owned) per stat. */
    synchronized EnumMap<Stat, Double> sumModifiers() {
        EnumMap<Stat, Double> sum = new EnumMap<>(Stat.class);
        for (EnumMap<Stat, Double> map : modifiers.values()) {
            map.forEach((stat, value) -> sum.merge(stat, value, Double::sum));
        }
        return sum;
    }

    /** Modifiers of one source (copy), for diagnostics and the stats menu. */
    public synchronized Map<String, Map<Stat, Double>> sources() {
        Map<String, Map<Stat, Double>> copy = new LinkedHashMap<>();
        modifiers.forEach((source, map) -> copy.put(source, new EnumMap<>(map)));
        return copy;
    }
}
