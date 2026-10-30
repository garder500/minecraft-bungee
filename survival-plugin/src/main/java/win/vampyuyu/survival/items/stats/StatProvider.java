package win.vampyuyu.survival.items.stats;

import java.util.Map;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.api.Stat;

/** A source of stats computed by the engine on each recompute (accessories, pet, armor set...). */
public interface StatProvider {
    /** Source name used in the engine's modifier table, e.g. "accessory". */
    String source();

    /** Stat contribution right now. Main thread. */
    Map<Stat, Double> stats(Player player);

    /** Cheap value that changes whenever {@link #stats} may return something different. */
    int signature(Player player);
}
