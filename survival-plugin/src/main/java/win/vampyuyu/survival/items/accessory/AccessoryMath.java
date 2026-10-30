package win.vampyuyu.survival.items.accessory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Rarity;

/** Pure rules of the accessory bag: one counted accessory per family, Magical Power and its stat bonus. */
public final class AccessoryMath {
    private AccessoryMath() {}

    /** One accessory in the bag, reduced to what the rules need. */
    public record Entry(String itemId, String family, Rarity rarity, Map<Stat, Double> stats, int slot) {
        double statSum() {
            return stats.values().stream().mapToDouble(Math::abs).sum();
        }
    }

    /** Outcome of the computation. */
    public record Result(List<Entry> counted, List<Entry> ignored, int magicalPower, Map<Stat, Double> stats,
            Map<Stat, Double> powerStats) {}

    /**
     * Keeps the best accessory of every family (highest rarity, then the larger stat sum, then the lower slot),
     * sums their stats, then adds {@code perMagicalPower * magicalPower} for each configured stat.
     */
    public static Result compute(List<Entry> bag, Map<String, Integer> magicalPower, Map<Stat, Double> perPower) {
        Map<String, Entry> best = new LinkedHashMap<>();
        List<Entry> ignored = new ArrayList<>();
        Comparator<Entry> order = Comparator.comparingInt((Entry e) -> e.rarity().ordinal())
            .thenComparingDouble(Entry::statSum).thenComparingInt(e -> -e.slot());
        for (Entry entry : bag) {
            Entry current = best.get(entry.family());
            if (current == null) {
                best.put(entry.family(), entry);
            } else if (order.compare(entry, current) > 0) {
                ignored.add(current);
                best.put(entry.family(), entry);
            } else {
                ignored.add(entry);
            }
        }
        int mp = 0;
        Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        for (Entry entry : best.values()) {
            mp += magicalPower.getOrDefault(entry.rarity().name(), 0);
            entry.stats().forEach((stat, value) -> stats.merge(stat, value, Double::sum));
        }
        final int totalPower = mp;
        Map<Stat, Double> power = new EnumMap<>(Stat.class);
        perPower.forEach((stat, perPoint) -> power.put(stat, Math.round(perPoint * totalPower * 10.0) / 10.0));
        power.forEach((stat, value) -> stats.merge(stat, value, Double::sum));
        stats.values().removeIf(v -> Math.abs(v) < 1e-9);
        return new Result(List.copyOf(best.values()), List.copyOf(ignored), mp, stats, power);
    }

    /** Number of unlocked slots: {@code base + upgrades * perUpgrade}, at most {@code max}. */
    public static int slots(int base, int upgrades, int perUpgrade, int max) {
        return Math.max(base, Math.min(max, base + Math.max(0, upgrades) * perUpgrade));
    }

    /** Coins the next slot upgrade costs. */
    public static double upgradeCost(int upgradesDone, double base, double growth) {
        return Math.floor(base * Math.pow(growth, Math.max(0, upgradesDone)));
    }
}
