package win.vampyuyu.survival.api;

import java.util.UUID;
import org.bukkit.Location;

/** Temporary boosts of the Miners (the Artisan's Overclock, GDD 11.3). Implemented by the {@code quarry} feature. */
public interface MinerBoostApi {
    /**
     * Makes the Miner of {@code owner} nearest to {@code at} within {@code range} blocks run {@code speed} times faster
     * for {@code millis}. False when the owner has no Miner in range.
     */
    boolean overclockNearest(UUID owner, Location at, double range, long millis, double speed);
}
