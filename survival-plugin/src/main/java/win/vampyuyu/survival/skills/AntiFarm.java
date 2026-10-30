package win.vampyuyu.survival.skills;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Diminishing returns for repeating the same thing in the same place (mob grinders, cobblestone generators).
 * Every call to {@link #next} records one event in the player's cell and returns the multiplier to apply to its
 * experience: 1 for the first {@code free} events inside the sliding window, then {@code step} less for each further one, never
 * below {@code min}. Memory is bounded: a player keeps at most {@link #MAX_CELLS} cells and quitting drops them.
 */
public final class AntiFarm {
    static final int MAX_CELLS = 64;

    private final int cell;
    private final long windowMillis;
    private final int free;
    private final double step;
    private final double min;
    private final LongSupplier clock;
    private final Map<UUID, Map<String, Deque<Long>>> players = new ConcurrentHashMap<>();

    public AntiFarm(SkillsConfig.AntiFarmCfg cfg, LongSupplier clock) {
        this.cell = cfg.cell();
        this.windowMillis = cfg.windowMillis();
        this.free = cfg.free();
        this.step = cfg.step();
        this.min = cfg.min();
        this.clock = clock;
    }

    /** Records an event at the position and returns the experience multiplier (between {@code min} and 1). */
    public double next(UUID player, String world, int x, int y, int z) {
        long now = clock.getAsLong();
        String key = world + ':' + Math.floorDiv(x, cell) + ':' + Math.floorDiv(y, cell) + ':' + Math.floorDiv(z, cell);
        Map<String, Deque<Long>> cells = players.computeIfAbsent(player, id -> new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Long>> eldest) {
                return size() > MAX_CELLS;
            }
        });
        synchronized (cells) {
            Deque<Long> times = cells.computeIfAbsent(key, k -> new ArrayDeque<>());
            while (!times.isEmpty() && now - times.peekFirst() > windowMillis) {
                times.pollFirst();
            }
            int over = Math.max(0, times.size() + 1 - free);
            times.addLast(now);
            return Math.max(min, 1 - over * step);
        }
    }

    public void forget(UUID player) {
        players.remove(player);
    }

    public void clear() {
        players.clear();
    }
}
