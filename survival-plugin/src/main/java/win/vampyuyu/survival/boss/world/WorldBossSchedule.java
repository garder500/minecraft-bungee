package win.vampyuyu.survival.boss.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pure scheduling rules of the world boss: when to announce, when to spawn, who is picked. */
public final class WorldBossSchedule {
    private final WorldBossConfig config;
    private final Set<Integer> announced = new HashSet<>();

    public WorldBossSchedule(WorldBossConfig config) {
        this.config = config;
    }

    /** Next spawn time after a fresh install or a reset. */
    public long initial(long now) {
        return now + config.firstDelayMinutes() * 60_000L;
    }

    /** Next spawn after an event that started at {@code now}. */
    public long afterSpawn(long now) {
        announced.clear();
        return now + config.intervalMinutes() * 60_000L;
    }

    /** Next check after too few players were online. */
    public long retry(long now) {
        announced.clear();
        return now + config.retryMinutes() * 60_000L;
    }

    public boolean due(long now, long nextSpawnAt) {
        return nextSpawnAt > 0 && now >= nextSpawnAt;
    }

    /**
     * Announcement thresholds (seconds before spawn) that were just crossed and not yet announced. Each threshold is
     * returned once; the closest remaining one wins when several were skipped (a long pause).
     */
    public List<Integer> announcements(long now, long nextSpawnAt) {
        List<Integer> out = new ArrayList<>();
        if (nextSpawnAt <= 0 || now >= nextSpawnAt) {
            return out;
        }
        long left = (nextSpawnAt - now + 999) / 1000;
        Integer best = null;
        for (int threshold : config.announceBeforeSeconds()) {
            if (left <= threshold && !announced.contains(threshold)) {
                announced.add(threshold);
                if (best == null || threshold < best) {
                    best = threshold;
                }
            }
        }
        if (best != null) {
            out.add(best);
        }
        return out;
    }

    /** Weighted pick of a boss id. */
    public String pick(Random random) {
        double total = 0;
        for (WorldBossConfig.Entry e : config.pool()) {
            total += e.weight();
        }
        double roll = random.nextDouble() * total;
        for (WorldBossConfig.Entry e : config.pool()) {
            roll -= e.weight();
            if (roll < 0) {
                return e.boss();
            }
        }
        return config.pool().get(config.pool().size() - 1).boss();
    }

    public WorldBossConfig.Spot pickSpot(Random random) {
        return config.locations().get(random.nextInt(config.locations().size()));
    }
}
