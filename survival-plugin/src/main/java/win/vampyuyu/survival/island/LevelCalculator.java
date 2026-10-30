package win.vampyuyu.survival.island;

import java.util.Map;
import org.bukkit.Material;

/** Island value and level from block counts. Pure. */
public final class LevelCalculator {
    private LevelCalculator() {}

    public static double value(Map<Material, Long> counts, Map<Material, Double> weights, double defaultWeight) {
        double total = 0;
        for (Map.Entry<Material, Long> entry : counts.entrySet()) {
            double weight = weights.getOrDefault(entry.getKey(), defaultWeight);
            if (weight > 0 && entry.getValue() > 0) {
                total += weight * entry.getValue();
            }
        }
        return total;
    }

    public static long level(double value, double divisor) {
        if (value <= 0 || divisor <= 0) {
            return 0;
        }
        return (long) Math.floor(value / divisor);
    }
}
